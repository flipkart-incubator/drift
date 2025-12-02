---
title: "Welcome to Drift Platform Documentation"
layout: home
author_profile: false
---

**Drift** is a Temporal-powered, low-code workflow orchestration platform that enables users to design, deploy, and execute workflows using native reusable nodes.

## Quick Start

### For Developers
1. Read [Architecture Overview](/03-ARCHITECTURE-OVERVIEW/) to understand the system
2. Review [High-Level Design](/04-HIGH-LEVEL-DESIGN/) for component design
3. Study [Contracts](/06-CONTRACTS/) for integration details

### For Architects
1. Start with [Architecture Overview](/03-ARCHITECTURE-OVERVIEW/)
2. Deep dive into [High-Level Design](/04-HIGH-LEVEL-DESIGN/)
3. Review [Low-Level Design](/05-LOW-LEVEL-DESIGN/) for implementation details

### For Operations Teams
1. Review [Redis Pub/Sub](/07-REDIS-PUBSUB/) for system communication
2. Understand monitoring and troubleshooting procedures

## Documentation Structure

### Getting Started
- **[Key Features](/01-KEY-FEATURES/)** - Overview of platform capabilities
- **[Terminologies](/02-TERMINOLOGIES/)** - Core concepts and definitions

### Architecture & Design
- **[Architecture Overview](/03-ARCHITECTURE-OVERVIEW/)** - System architecture and components
- **[High-Level Design](/04-HIGH-LEVEL-DESIGN/)** - Component design and responsibilities
- **[Low-Level Design](/05-LOW-LEVEL-DESIGN/)** - Detailed implementation details

### Technical Details
- **[Contracts](/06-CONTRACTS/)** - API contracts and specifications
- **[Redis Pub/Sub](/07-REDIS-PUBSUB/)** - Cache invalidation and async communication

## Key Features

- **Low-Code/No-Code**: Create workflows on-the-fly with predefined native nodes
- **DSL-Based workflow**: Defines the workflow structure by stitching nodes in a Directed Graph
- **Temporal Integration**: For durable, fault-tolerant workflow execution
- **Workflow Versioning**: Version-controlled workflows with hot deployments
- **Real-time Visibility**: Live workflow execution tracking and monitoring
- **Widgetized Responses**: UI-ready responses for human-in-the-loop workflows

## Architecture at a Glance

```
┌─────────────┐
│   Clients   │
└──────┬──────┘
       │
       ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ API Service  │─────>│   Temporal   │<─────│   Worker     │
│ (REST)       │      │   Cluster    │      │   Service    │
└──────────────┘      └──────────────┘      └──────┬───────┘
       │                                            │
       │                                            │
       └────────────────────┬───────────────────────┘
                            │
                ┌───────────┴────────────┐
                │                        │
           ┌────▼────┐             ┌────▼────┐
           │  HBase  │             │  Redis  │
           │(Storage)│             │ (Cache) │
           └─────────┘             └─────────┘
```

## Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| API Framework | Dropwizard 2.0.27 | REST services |
| Workflow Engine | Temporal.io | Orchestration |
| Storage | HBase | Persistent data |
| Caching | Redis Sentinel | Hot data cache |
| Scripting | Groovy 2.4.13 | Dynamic evaluation |
| DI Framework | Google Guice 7.0.0 | Dependency injection |
| Build Tool | Maven 3.6+ | Build automation |
| Runtime | Java 17 | JVM platform |

