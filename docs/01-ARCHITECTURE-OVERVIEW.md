# Drift Platform - Architecture Overview

## Table of Contents
1. [Introduction](#introduction)
2. [System Architecture](#system-architecture)
3. [Core Components](#core-components)
4. [Technology Stack](#technology-stack)
5. [Data Flow](#data-flow)
6. [Deployment Architecture](#deployment-architecture)

---

## Introduction

**Drift** is a Temporal-powered, low-code visual workflow orchestration platform that enables users to design, deploy, and execute complex workflows using a node-based system. The platform abstracts the complexity of workflow management and provides a flexible, extensible architecture for building business process automation.

### Key Features
- **Low-Code/No-Code**: Visual workflow builder with predefined node types
- **Temporal Integration**: Leverages Temporal.io for reliable, fault-tolerant workflow execution
- **Distributed Architecture**: Horizontally scalable with separate API and Worker components
- **Multi-Tenancy**: Supports multiple tenants/clients with isolated configurations
- **Extensible Node System**: Pluggable architecture for custom node types
- **State Persistence**: Durable workflow state management using HBase
- **Real-time Caching**: Redis-based caching for workflow definitions and node configurations

---

## System Architecture

### High-Level Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                           CLIENT APPLICATIONS                           │
│                    (Frontend, Mobile Apps, APIs)                        │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ REST API (HTTP/JSON)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                              API LAYER                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │              Drift API Service (Dropwizard)                      │  │
│  │  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐          │  │
│  │  │ Workflow   │  │    Node     │  │    Workflow      │          │  │
│  │  │ Resource   │  │ Definition  │  │   Definition     │          │  │
│  │  │ (REST)     │  │  Resource   │  │    Resource      │          │  │
│  │  └────────────┘  └─────────────┘  └──────────────────┘          │  │
│  │         │                │                  │                     │  │
│  │         └────────────────┴──────────────────┘                     │  │
│  │                          │                                         │  │
│  │                 ┌────────▼────────┐                               │  │
│  │                 │ Temporal Service│                               │  │
│  │                 │  (Client SDK)   │                               │  │
│  │                 └────────┬────────┘                               │  │
│  └──────────────────────────┼──────────────────────────────────────┘  │
└───────────────────────────┼──┼───────────────────────────────────────┘
                            │  │
                            │  │ Temporal gRPC Protocol
                            │  │
┌───────────────────────────▼──▼───────────────────────────────────────┐
│                      TEMPORAL CLUSTER                                 │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐                 │
│  │  Frontend   │  │   History    │  │   Matching  │                 │
│  │   Service   │◄─┤   Service    │◄─┤   Service   │                 │
│  └─────────────┘  └──────────────┘  └─────────────┘                 │
│         │                  │                                          │
│         │         ┌────────▼────────┐                                │
│         │         │  Persistence    │                                │
│         │         │  (Cassandra/    │                                │
│         │         │   PostgreSQL)   │                                │
│         │         └─────────────────┘                                │
└─────────┼───────────────────────────────────────────────────────────┘
          │
          │ Task Queue
          │
┌─────────▼──────────────────────────────────────────────────────────────┐
│                          WORKER LAYER                                    │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │         Drift Worker Service (Dropwizard + Temporal Worker)        │ │
│  │  ┌──────────────┐                                                  │ │
│  │  │   Temporal   │                                                  │ │
│  │  │    Worker    │                                                  │ │
│  │  │  (Polling)   │                                                  │ │
│  │  └──────┬───────┘                                                  │ │
│  │         │                                                          │ │
│  │  ┌──────▼──────────────────────────────────────────────────────┐  │ │
│  │  │               Workflow Execution Engine                     │  │ │
│  │  │  ┌────────────────┐  ┌────────────────┐                     │  │ │
│  │  │  │   Generic      │  │  Node          │                     │  │ │
│  │  │  │   Workflow     │◄─┤  Executor      │                     │  │ │
│  │  │  │   Impl         │  │                │                     │  │ │
│  │  │  └────────────────┘  └───────┬────────┘                     │  │ │
│  │  │                              │                               │  │ │
│  │  │         ┌────────────────────┴────────────────────┐         │  │ │
│  │  │         │                                          │         │  │ │
│  │  │  ┌──────▼──────┐  ┌──────────────┐  ┌────────────▼─────┐   │  │ │
│  │  │  │   Activity  │  │   Activity   │  │    Activity      │   │  │ │
│  │  │  │  HTTP Node  │  │ Groovy Node  │  │  Branch Node     │   │  │ │
│  │  │  └─────────────┘  └──────────────┘  └──────────────────┘   │  │ │
│  │  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │  │ │
│  │  │  │  Activity   │  │   Activity   │  │    Activity      │   │  │ │
│  │  │  │Instruction  │  │  Processor   │  │  Success/Failure │   │  │ │
│  │  │  │    Node     │  │    Node      │  │      Node        │   │  │ │
│  │  │  └─────────────┘  └──────────────┘  └──────────────────┘   │  │ │
│  │  └──────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │                                                        │
        ▼                                                        ▼
┌────────────────┐                                      ┌────────────────┐
│  PERSISTENCE   │                                      │    CACHING     │
│                │                                      │                │
│  ┌──────────┐  │                                      │  ┌──────────┐  │
│  │  HBase   │  │                                      │  │  Redis   │  │
│  │  Cluster │  │                                      │  │ Sentinel │  │
│  │          │  │                                      │  │  Cluster │  │
│  │ ┌──────┐ │  │                                      │  └──────────┘  │
│  │ │Nodes │ │  │                                      │                │
│  │ │      │ │  │                                      │  • Workflow    │
│  │ │WorkFL│ │  │                                      │    Definitions │
│  │ │Context│ │  │                                      │  • Node Defs   │
│  │ └──────┘ │  │                                      │  • Enum Config │
│  └──────────┘  │                                      │  • AB Testing  │
└────────────────┘                                      └────────────────┘
```

---

## Core Components

### 1. API Service (`api` module)

**Purpose**: REST API layer for workflow orchestration management

**Responsibilities**:
- Expose REST endpoints for workflow lifecycle operations
- Validate incoming requests
- Communicate with Temporal cluster to start/resume/terminate workflows
- Query workflow state and execution status
- Manage workflow and node definitions (CRUD operations)
- Handle client authentication and authorization

**Key Classes**:
- `DriftApplication`: Main application entry point (Dropwizard)
- `WorkflowResource`: REST endpoints for workflow operations
- `NodeDefinitionResource`: CRUD operations for node definitions
- `WorkflowDefinitionResource`: CRUD operations for workflow definitions
- `TemporalService`: Service layer for Temporal interactions
- `RequestFilter`: Request filtering and context extraction

**Technology**: Dropwizard 2.0.27, Jersey (JAX-RS), Google Guice

---

### 2. Worker Service (`worker` module)

**Purpose**: Temporal worker that executes workflow activities

**Responsibilities**:
- Poll Temporal task queues for work
- Execute workflow logic (GenericWorkflowImpl)
- Execute node activities (HTTP, Groovy, Branch, etc.)
- Manage workflow state and context
- Interact with HBase for workflow persistence
- Evaluate Groovy scripts for dynamic behavior
- Execute HTTP calls to external services
- Handle workflow errors and retries

**Key Classes**:
- `WorkerApplication`: Main worker entry point (Dropwizard)
- `GenericWorkflowImpl`: Core workflow execution implementation
- `WorkflowNodeExecutor`: Orchestrates node execution
- `TemporalWorkerManaged`: Manages Temporal worker lifecycle
- `*NodeActivityImpl`: Activity implementations for each node type
- `WorkflowContextHBService`: HBase persistence service
- `GroovyTranslator`: Dynamic script evaluation engine
- `HttpExecutor`: HTTP call execution

**Technology**: Dropwizard 2.0.27, Temporal SDK, Google Guice, Groovy 2.4.13

---

### 3. Commons Module (`commons`)

**Purpose**: Shared data models, enums, and utilities

**Responsibilities**:
- Define common data structures (DTOs, domain models)
- Provide shared enums and constants
- Define node type hierarchy and interfaces
- Workflow and node definition models
- Request/response contracts

**Key Components**:
- **Model Layer**:
  - `Workflow`: Workflow definition with states and transitions
  - `NodeDefinition`: Abstract base class for all node types
  - `WorkflowStartRequest`: Workflow initiation request
  - `WorkflowState`: Runtime workflow state
- **Node Types**:
  - `HttpNode`: HTTP API call node
  - `GroovyNode`: Script evaluation node
  - `BranchNode`: Conditional branching node
  - `InstructionNode`: UI instruction/form node
  - `ProcessorNode`: Data processing node
  - `SuccessNode`: Workflow success termination
  - `FailureNode`: Workflow failure termination
- **Enums**:
  - `NodeType`: Types of nodes (HTTP, GROOVY, BRANCH, etc.)
  - `WorkflowStatus`: Workflow execution states
  - `HttpMethod`: HTTP methods (GET, POST, PUT, DELETE)

---

### 4. HBase Entities Module (`hbase-entities`)

**Purpose**: HBase persistence layer with ORM mapping

**Responsibilities**:
- Define HBase table schemas
- Map Java objects to HBase rows
- Provide DAO (Data Access Object) layer
- Handle serialization/deserialization

**Key Classes**:
- `WorkflowHB`: HBase entity for workflow definitions
- `NodeHB`: HBase entity for node definitions
- `WorkflowContextHB`: HBase entity for workflow runtime context
- `AbstractEntityDao`: Generic DAO implementation

**HBase Tables**:
1. **WorkflowHB**: Stores workflow definitions
   - Row Key: `{workflowId}_{version}`
   - Column: `workflowData` (JSON serialized workflow)

2. **NodeHB**: Stores node definitions
   - Row Key: `{nodeId}_{version}`
   - Column: `nodeData` (JSON serialized node)

3. **WorkflowContextHB**: Stores workflow execution context
   - Row Key: `{workflowExecutionId}`
   - Column: `context` (JSON serialized context)

---

## Technology Stack

### Backend Framework
- **Dropwizard 2.0.27**: RESTful web services framework
- **Jersey (JAX-RS)**: REST API implementation
- **Jackson**: JSON serialization/deserialization
- **Google Guice 7.0.0**: Dependency injection

### Workflow Orchestration
- **Temporal.io**: Distributed workflow engine
  - Workflow SDK for Java
  - Activity SDK
  - Worker SDK
- **Temporal Server**: External temporal cluster (self-hosted or cloud)

### Data Storage
- **HBase**: NoSQL database for workflow and node definitions
  - Column-oriented, distributed storage
  - HBase Object Mapper for ORM
- **Apache Hadoop**: Underlying distributed filesystem for HBase

### Caching
- **Redis Sentinel**: Distributed caching
  - Workflow definition caching
  - Node definition caching
  - Enum/configuration store
  - Session management

### Scripting & Execution
- **Groovy 2.4.13**: Dynamic script evaluation
  - Script-based transformations
  - Conditional logic evaluation
  - Dynamic value resolution
- **OkHttp/Retrofit**: HTTP client for external API calls

### Monitoring & Metrics
- **Prometheus**: Metrics collection
- **Micrometer**: Metrics instrumentation
- **Dropwizard Metrics**: Application metrics

### Build & Deployment
- **Maven 3.6+**: Build automation
- **Java 17**: Runtime environment
- **Docker**: Containerization
- **Kubernetes/Helm**: Container orchestration

---

## Data Flow

### Workflow Execution Flow

```
┌─────────────┐
│   Client    │
│ Application │
└──────┬──────┘
       │
       │ 1. POST /v3/workflow/start
       │    {workflowId, issueDetail, context, ...}
       ▼
┌──────────────────┐
│   API Service    │
│                  │
│ WorkflowResource │
└──────┬───────────┘
       │
       │ 2. Validate Request
       ▼
┌──────────────────┐
│ TemporalService  │
│                  │
│ • Generate WF ID │
│ • Start WF       │
└──────┬───────────┘
       │
       │ 3. Start Workflow Execution
       │    WorkflowClient.start(GenericWorkflow)
       ▼
┌──────────────────────┐
│  Temporal Cluster    │
│                      │
│ • Persist WF state   │
│ • Add to task queue  │
└──────┬───────────────┘
       │
       │ 4. Poll Task Queue
       ▼
┌──────────────────────────────┐
│      Worker Service          │
│                              │
│  Temporal Worker (Polling)   │
└──────┬───────────────────────┘
       │
       │ 5. Receive Workflow Task
       ▼
┌───────────────────────────────┐
│   GenericWorkflowImpl         │
│                               │
│  startWorkflow()              │
│    ├─ Initialize state        │
│    ├─ Fetch workflow DSL      │
│    └─ Execute nodes           │
└──────┬────────────────────────┘
       │
       │ 6. Fetch Workflow Definition
       │    FetchWorkflowActivity.fetchWorkflowBasedOnRequest()
       ▼
┌────────────────────┐
│  Cache Check       │
│  (Redis)           │
└──────┬─────────────┘
       │
       │ Cache Miss?
       ▼
┌────────────────────┐
│  HBase Lookup      │
│  WorkflowHB table  │
└──────┬─────────────┘
       │
       │ 7. Return Workflow DSL
       │    {startNode, states, ...}
       ▼
┌───────────────────────────────┐
│  WorkflowNodeExecutor         │
│                               │
│  executeNode(currentNode)     │
└──────┬────────────────────────┘
       │
       │ 8. Execute Node Activity
       ▼
┌────────────────────────────────────────┐
│  Node Activity Execution               │
│                                        │
│  Based on Node Type:                   │
│  ┌──────────────────────────────────┐  │
│  │ HTTP Node:                       │  │
│  │  • Resolve URL/headers/body      │  │
│  │  • Execute HTTP call             │  │
│  │  • Transform response            │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ Groovy Node:                     │  │
│  │  • Load script                   │  │
│  │  • Evaluate with context         │  │
│  │  • Return result                 │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ Branch Node:                     │  │
│  │  • Evaluate conditions           │  │
│  │  • Determine next node           │  │
│  └──────────────────────────────────┘  │
│  ┌──────────────────────────────────┐  │
│  │ Instruction Node:                │  │
│  │  • Return UI instructions        │  │
│  │  • Wait for user input           │  │
│  └──────────────────────────────────┘  │
└─────────┬──────────────────────────────┘
          │
          │ 9. Update Workflow Context
          ▼
┌────────────────────────┐
│  WorkflowContextHBService │
│  • Persist context to HBase│
└────────┬───────────────┘
         │
         │ 10. Determine Next Node
         ▼
┌────────────────────────┐
│  WorkflowNodeExecutor  │
│  • Get nextNode ID     │
│  • Loop until null     │
└────────┬───────────────┘
         │
         │ 11. Workflow Complete?
         │     (nextNode == null)
         ▼
┌────────────────────────┐
│  Success/Failure Node  │
│  • Mark WF Complete    │
│  • Set final status    │
└────────┬───────────────┘
         │
         │ 12. Return to Temporal
         ▼
┌────────────────────────┐
│  Temporal Cluster      │
│  • Mark WF Complete    │
│  • Persist final state │
└────────┬───────────────┘
         │
         │ 13. Query Workflow State
         │     GET /v3/workflow/{workflowId}
         ▼
┌────────────────────────┐
│   Client Application   │
│   • Receive final state│
└────────────────────────┘
```

---

### Workflow Resume Flow

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ 1. PUT /v3/workflow/resume/{workflowId}
       │    {userInput, selectedOption, ...}
       ▼
┌──────────────────┐
│   API Service    │
└──────┬───────────┘
       │ 2. Signal Workflow
       ▼
┌──────────────────┐
│ Temporal Cluster │
└──────┬───────────┘
       │ 3. Wake Workflow
       ▼
┌──────────────────────┐
│ GenericWorkflowImpl  │
│ resumeWorkflow()     │
└──────┬───────────────┘
       │ 4. Resume from current node
       ▼
┌──────────────────────┐
│ Continue Execution   │
└──────────────────────┘
```

---

## Deployment Architecture

### Kubernetes Deployment

```
┌────────────────────────────────────────────────────────────────┐
│                      Kubernetes Cluster                         │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    Namespace: drift                      │  │
│  │                                                          │  │
│  │  ┌────────────────┐        ┌────────────────┐          │  │
│  │  │  API Pods      │        │  Worker Pods   │          │  │
│  │  │  (Replicas: 3) │        │  (Replicas: 5) │          │  │
│  │  │                │        │                │          │  │
│  │  │ • Port: 8000   │        │ • Port: 7200   │          │  │
│  │  │ • Port: 8001   │        │ • Port: 7201   │          │  │
│  │  │   (admin)      │        │   (admin)      │          │  │
│  │  │ • Port: 9090   │        │ • Port: 9090   │          │  │
│  │  │   (metrics)    │        │   (metrics)    │          │  │
│  │  └────────┬───────┘        └────────┬───────┘          │  │
│  │           │                         │                   │  │
│  │  ┌────────▼───────┐       ┌────────▼───────┐          │  │
│  │  │   Service:     │       │   Service:     │          │  │
│  │  │   drift-api    │       │ drift-worker   │          │  │
│  │  │   (LoadBalancer)│      │  (ClusterIP)   │          │  │
│  │  └────────────────┘       └────────────────┘          │  │
│  │                                                         │  │
│  │  ┌──────────────────────────────────────────┐         │  │
│  │  │           ConfigMap / Secrets            │         │  │
│  │  │  • Environment variables                 │         │  │
│  │  │  • Redis config                          │         │  │
│  │  │  • Temporal connection                   │         │  │
│  │  │  • HBase config                          │         │  │
│  │  └──────────────────────────────────────────┘         │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### External Dependencies

```
┌────────────────────────────────────────────────────────────────┐
│                    External Infrastructure                      │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐ │
│  │ Temporal Cluster │  │  Redis Sentinel  │  │ HBase Cluster│ │
│  │  (Self-hosted)   │  │    (3 nodes)     │  │  (5 nodes)   │ │
│  │                  │  │                  │  │              │ │
│  │ • Frontend       │  │ • Master         │  │ • Master     │ │
│  │ • History        │  │ • Sentinels      │  │ • RegionSrvs │ │
│  │ • Matching       │  │ • Replicas       │  │ • ZooKeeper  │ │
│  │ • Worker         │  │                  │  │              │ │
│  └──────────────────┘  └──────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Scalability & High Availability

### Horizontal Scaling
- **API Service**: Scale based on HTTP request rate
- **Worker Service**: Scale based on workflow task queue depth
- **Temporal Cluster**: Scale history and matching services independently
- **HBase**: Add region servers for increased capacity
- **Redis**: Use Redis Cluster for horizontal scaling

### Fault Tolerance
- **Temporal**: Automatic retry and compensation
- **Worker Pods**: Kubernetes automatically restarts failed pods
- **HBase**: Replication factor ensures data durability
- **Redis Sentinel**: Automatic failover for master node

---

## Security Considerations

### Authentication & Authorization
- JWT-based authentication (if implemented)
- Client ID/Secret validation via RequestFilter
- Multi-tenancy support with tenant isolation

### Data Security
- Encryption keys for sensitive data (configurable)
- Environment variable-based secrets management
- Redis password authentication
- HBase access control via Hadoop security

### Network Security
- TLS/SSL for all external communications
- Internal service-to-service mTLS (optional)
- Network policies in Kubernetes for pod isolation

---

**Next**: [High-Level Design (HLD)](./02-HIGH-LEVEL-DESIGN.md)

