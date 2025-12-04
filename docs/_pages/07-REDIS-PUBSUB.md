---
title: "Redis Pub/Sub"
permalink: /07-REDIS-PUBSUB/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform - Redis Pub/Sub System

## Overview

Drift uses Redis Pub/Sub messaging for real-time communication between services. This document details the pub/sub architecture, use cases, and implementation.

## Use Cases

### 1. Cache Invalidation
**Purpose:** Ensure all service instances have consistent cache state when definitions are updated

### 2. Async Workflow Communication
**Purpose:** Enable API to wait for workflow responses asynchronously without polling

---

## Architecture

### System Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                  Redis Pub/Sub Architecture                    │
└────────────────────────────────────────────────────────────────┘

                    ┌─────────────────┐
                    │  Redis Sentinel │
                    │    Cluster      │
                    │                 │
                    │  Pub/Sub Engine │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
  ┌──────────┐        ┌──────────┐        ┌──────────┐
  │ API      │        │ Worker 1 │        │ Worker 2 │
  │ Service  │        │ Service  │        │ Service  │
  │          │        │          │        │          │
  │Publisher │        │Subscriber│        │Subscriber│
  │Subscriber│        │          │        │          │
  └──────────┘        └──────────┘        └──────────┘
```

---

## 1. Cache Invalidation Pub/Sub

### Architecture

```
┌────────────────────────────────────────────────────────────┐
│            Cache Invalidation Flow                         │
└────────────────────────────────────────────────────────────┘

Admin/API Updates Node Definition
         │
         ▼
  ┌──────────────┐
  │ Update HBase │
  └──────┬───────┘
         │
         ▼
  ┌──────────────────────┐
  │ Publish to Redis     │
  │ Channel: "dsl_update"│
  │ Message:             │
  │  "NODE node_001_v1"  │
  │  "WORKFLOW wf_001_v1"│
  │  "NODE ALL"          │
  └──────┬───────────────┘
         │
         │ Redis Pub/Sub
         ├─────────────────────┐
         │                     │
         ▼                     ▼
  ┌──────────────┐      ┌──────────────┐
  │  Worker 1    │      │  Worker 2    │
  │ (Subscriber) │      │ (Subscriber) │
  │              │      │              │
  │ Receives msg │      │ Receives msg │
  │ Invalidates  │      │ Invalidates  │
  │ local cache  │      │ local cache  │
  └──────────────┘      └──────────────┘
```

### Implementation: RedisCacheInvalidator

**Location:** `worker/src/main/java/com/drift/worker/bootstrap/RedisCacheInvalidator.java`

**Lifecycle:**
- Implements `Managed` interface (Dropwizard lifecycle)
- Starts when Worker service starts
- Stops when Worker service stops

**Key Features:**
- Dedicated single-threaded executor for subscription
- Daemon thread (doesn't block JVM shutdown)
- Auto-reconnect on Redis connection failure
- Graceful shutdown with 5-second timeout

**Message Format:**
```
"NODE <rowKey>"        - Invalidate specific node
"WORKFLOW <rowKey>"    - Invalidate specific workflow
"NODE ALL"             - Invalidate all nodes
"WORKFLOW ALL"         - Invalidate all workflows
```

**Processing Logic:**
1. Receive message on `dsl_update` channel
2. Parse message (split by space)
3. Determine cache type (NODE or WORKFLOW)
4. Invalidate specific key or entire cache
5. Log invalidation action

**Error Handling:**
- Parse errors → Log warning and continue
- Redis connection errors → Retry after 1 second
- Subscription errors → Log and reconnect

**Code Flow:**
```
start()
  └─> Submit task to executor
       └─> while (running)
            └─> Get Jedis resource
                 └─> Subscribe to channel
                      └─> onMessage()
                           └─> Parse message
                                └─> Invalidate cache

stop()
  └─> Set running = false
       └─> Shutdown executor
            └─> Wait 5 seconds
                 └─> Force shutdown if needed
```

---

## 2. Async Workflow Communication Pub/Sub

### Use Case

Enable synchronous HTTP API responses for asynchronous workflow executions:
- Client calls API to execute disconnected node
- API starts workflow execution via Temporal
- API subscribes to Redis channel for this workflow
- Workflow completes and publishes result
- API receives message and returns response

### Architecture

```
┌────────────────────────────────────────────────────────────┐
│         Async Workflow Response Pattern                    │
└────────────────────────────────────────────────────────────┘

Client → API: POST /workflow/{id}/disconnected-node/execute
         │
         ├─ 1. Start workflow via Temporal
         │
         ├─ 2. Subscribe to Redis channel
         │    Channel: "async_await_{workflowId}"
         │    Timeout: 5 seconds
         │
         └─ 3. Wait for message...
                │
                │ (Workflow executing in Worker)
                │
         ┌──────▼──────┐
         │   Worker    │
         │             │
         │ Executes    │
         │ workflow    │
         │             │
         │ Publishes   │
         │ to channel  │
         └──────┬──────┘
                │
         ┌──────▼──────────────┐
         │ Redis Pub/Sub       │
         │ Message received    │
         └──────┬──────────────┘
                │
         ┌──────▼──────┐
         │    API      │
         │ Unsubscribes│
         │ Returns     │
         │ response    │
         └──────┬──────┘
                │
Client ← API: 200 OK {result}
```

### Implementation: RedisPubSubService

**Location:** `api/src/main/java/com/drift/api/service/RedisPubSubService.java`

**Lifecycle:**
- Singleton service
- Implements `Managed` interface
- Thread pool created on instantiation
- Shutdown on service stop

**Thread Pool Configuration:**
- Core threads: 10
- Max threads: 50
- Keep-alive: 60 seconds
- Queue: `SynchronousQueue` (strong back-pressure)
- Rejection policy: `AbortPolicy` (throws exception)

**Key Method: subscribeAndExecute()**

**Signature:**
```java
public void subscribeAndExecute(
    String workflowId, 
    Callable<Void> onSubscribeAction, 
    String action
)
```

**Parameters:**
- `workflowId` - Workflow identifier for channel name
- `onSubscribeAction` - Action to execute after subscription
- `action` - Metric label for timing

**Flow:**
```
1. Create CompletableFuture for response
2. Build channel name: "async_await_{workflowId}"
3. Create JedisPubSub listener
   - onSubscribe(): Execute workflow action
   - onMessage(): Complete future and unsubscribe
4. Submit subscription task to thread pool
5. Wait for future (5 second timeout)
6. Cancel task in finally block
```

**Listener Callbacks:**

**onSubscribe(channel, subscribedChannels):**
- Called when successfully subscribed
- Executes the workflow action (start/resume)
- Measures latency with timer
- On exception: unsubscribe and fail future

**onMessage(channel, message):**
- Called when message received
- Completes future successfully
- Unsubscribes from channel

**Error Scenarios:**

| Error | HTTP Status | Action |
|-------|-------------|--------|
| Timeout (5s) | 408 Request Timeout | Unsubscribe, throw ApiException |
| Redis connection error | 500 Internal Server Error | Unsubscribe, throw ApiException |
| Thread pool exhausted | 500 Internal Server Error | Throw ApiException immediately |
| Workflow not found | 404 Not Found | Propagate Temporal exception |
| Subscription interrupted | 500 Internal Server Error | Log and throw ApiException |

---

## Monitoring & Metrics

### RedisPubSubService Metrics

**Jedis Pool Metrics:**
- `jedis.numActiveConnections` - Currently active connections
- `jedis.numIdleConnections` - Idle connections in pool
- `jedis.numWaiters` - Threads waiting for connection

**Thread Pool Metrics:**
- `threadpool.activeThreads` - Active subscription threads
- `threadpool.poolSize` - Current pool size
- `threadpool.completedTasks` - Total completed tasks
- `threadpool.totalTasks` - Total tasks submitted
- `threadpool.largestPoolSize` - Peak pool size

**Subscription Metrics:**
- `subscribe.timeout` - Count of timeouts
- `subscribe.exception` - Count of exceptions
- `threadPool.rejected.exception` - Thread pool rejections

### RedisCacheInvalidator Logs

**Key Log Messages:**
- `"Starting Redis cache invalidation listener"` - Service starting
- `"Subscribing to cache invalidation channel"` - Subscription attempt
- `"Successfully subscribed to channel"` - Subscription success
- `"Received cache invalidation message: {}"` - Message received
- `"Redis subscription error, will retry"` - Connection failure
- `"Stopping Redis cache invalidation listener"` - Service stopping

---

## Configuration

### Redis Configuration

```yaml
redisConfiguration:
  password: ${REDIS_PASSWORD}
  master: ${REDIS_MASTER}
  sentinels: ${REDIS_SENTINELS}  # host1:port1,host2:port2
  prefix: ${REDIS_PREFIX}
  maxTotal: 50
  maxIdle: 25
  minIdle: 25
  testOnBorrow: true
  blockWhenExhausted: true
```

### Channel Names

| Channel | Purpose | Publisher | Subscriber |
|---------|---------|-----------|------------|
| `dsl_update` | Cache invalidation | API/Admin | All Workers |
| `async_await_{workflowId}` | Workflow responses | Worker | API (per request) |

**Channel Name Constants:**
- `DSL_UPDATE_CHANNEL` - Defined in `Constants.Workflow`
- `ASYNC_AWAIT_CHANNEL` - Defined in `Constants.Workflow`

---

## Best Practices

### For Publishers
1. **Keep Messages Small** - Use identifiers, not full payloads
2. **Use Structured Format** - Consistent message format for parsing
3. **Handle Publish Failures** - Log errors, don't fail the operation
4. **Consider Ordering** - Pub/Sub doesn't guarantee order

### For Subscribers
1. **Always Unsubscribe** - Use try-finally to ensure cleanup
2. **Set Timeouts** - Don't wait indefinitely
3. **Handle Reconnection** - Auto-retry on connection failures
4. **Validate Messages** - Parse defensively, handle malformed data
5. **Avoid Blocking** - Process messages quickly or offload to queue

### Operational
1. **Monitor Metrics** - Track connection pool health
2. **Set Alerts** - Alert on high timeout/error rates
3. **Tune Thread Pool** - Adjust based on load patterns
4. **Test Failure Modes** - Ensure graceful degradation

---

## Troubleshooting

### Issue: Subscription Timeouts

**Symptoms:** High `subscribe.timeout` metric, 408 errors

**Causes:**
- Worker service down
- Workflow execution taking > 5 seconds
- Network latency between Redis and API
- Message not published by worker

**Resolution:**
- Check worker service health
- Review workflow performance
- Verify Redis connectivity
- Check worker logs for publish errors

### Issue: Thread Pool Exhausted

**Symptoms:** `threadPool.rejected.exception` metric increasing, 500 errors

**Causes:**
- Too many concurrent workflow executions
- Subscriptions not cleaning up (leak)
- Thread pool too small for load

**Resolution:**
- Increase thread pool size
- Review subscription cleanup logic
- Scale API service horizontally
- Add rate limiting

### Issue: Cache Not Invalidating

**Symptoms:** Stale data served after updates

**Causes:**
- Worker not subscribed to channel
- Redis connection failure
- Message parsing error
- Wrong channel name

**Resolution:**
- Check worker logs for subscription status
- Verify Redis connectivity from worker
- Review cache invalidation message format
- Validate channel name constants

---

## Security Considerations

1. **Redis Password** - Always use password authentication
2. **Network Isolation** - Redis should not be publicly accessible
3. **Message Validation** - Validate all pub/sub messages
4. **Rate Limiting** - Prevent pub/sub abuse
5. **Monitoring** - Detect unusual pub/sub patterns

---

## Performance Characteristics

### Cache Invalidation
- **Latency:** < 100ms from publish to all subscribers
- **Throughput:** 1000+ messages/second
- **Reliability:** At-most-once delivery (not guaranteed)

### Async Workflow Communication
- **Timeout:** 5 seconds (configurable)
- **Overhead:** ~50ms for pub/sub round-trip
- **Concurrent Requests:** Limited by thread pool (50)

---

## Future Enhancements

1. **Message Persistence** - Use Redis Streams for guaranteed delivery
2. **Message Ordering** - Add sequence numbers
3. **Batching** - Batch multiple invalidations
4. **Pattern Subscriptions** - Use pattern-based subscriptions
5. **Dead Letter Queue** - Handle failed message processing

---

**See Also:**
- [High-Level Design - Caching Strategy](/04-HIGH-LEVEL-DESIGN/#caching-strategy)
- [Architecture Overview - Redis Component](/03-ARCHITECTURE-OVERVIEW/#caching)
