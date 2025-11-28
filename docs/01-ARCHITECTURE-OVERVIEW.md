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

**Drift** is a Temporal-powered, low-code visual workflow orchestration platform that enables users to design, deploy, and execute workflows using [native nodes](). The platform abstracts the complexity of workflow management and provides a flexible, extensible architecture for building business process automation.

### Key Features
- **Low-Code/No-Code**: On-the-fly workflow builder with predefined node types
- **DSL Based workflow** : Defines the workflow structure by orchestrating native nodes in a Directed Graph. Executes mostly as a sequential DAG, with explicit support for loops to handle complex logic.
- **Temporal Integration**: Reliable, fault-tolerant workflow execution
- **Distributed Architecture**: Horizontally scalable with separate API and Worker components
- **Multi-Tenancy**: Isolated tenant configurations
- **Extensible Node System**: Pluggable custom node types
- **Context Offloading**: Persists context in HBase (more connectors planned) to bypass the Temporal backend, maximizing throughput by keeping data and control plane decoupled.
- **Workflow Versioning**: Version-controlled workflows with hot deployments, zero downtime updates and rollbacks
- **Real-time Visibility**: Live workflow execution tracking and monitoring
- **Widgetized Responses**: UI-ready responses for human-in-the-loop workflows and systematic resumes
- **Generic Contracts**: Standardized contracts for seamless workflow interactions

---

## System Architecture

### High-Level Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────┐
│                           CLIENT APPLICATIONS                      │
│                             (Frontend, APIs)                       │
└────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ REST API (HTTP)
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                              API LAYER                              │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              Drift API Service (Dropwizard)                   │  │
│  │  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐        │  │
│  │  │ Workflow   │  │    Node     │  │    Workflow      │        │  │
│  │  │ Resource   │  │ Definition  │  │   Definition     │        │  │
│  │  │ (REST)     │  │  Resource   │  │    Resource      │        │  │
│  │  └────────────┘  └─────────────┘  └──────────────────┘        │  │
│  │         │                │                  │                 │  │
│  │         └────────────────┴──────────────────┘                 │  │
│  │                          │                                    │  │
│  │                 ┌────────▼────────┐                           │  │
│  │                 │ Temporal Service│                           │  │
│  │                 │  (Java SDK)     │                           │  │
│  │                 └────────┬────────┘                           │  │
│  └──────────────────────────┼────────────────────────────────────┘  │
└───────────────────────────┼──┼──────────────────────────────────────┘
                            │  │
                            │  │ Temporal gRPC Protocol
                            │  │
┌───────────────────────────▼──▼──────────────────────────────────────┐
│                      TEMPORAL CLUSTER                               │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐                 │
│  │  Frontend   │  │   History    │  │   Matching  │                 │
│  │   Service   │◄─┤   Service    │◄─┤   Service   │                 │
│  └─────────────┘  └──────────────┘  └─────────────┘                 │
│         │                  │                                        │
│         │         ┌────────▼────────┐                               │
│         │         │  Persistence    │                               │
│         │         │     (TiDB)      │                               │
│         │         └─────────────────┘                               │
└─────────┼───────────────────────────────────────────────────────────┘
          │
          │ Task Queue
          │
┌─────────▼──────────────────────────────────────────────────────────────┐
│                          WORKER LAYER                                  │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │         Drift Worker Service (Dropwizard + Temporal Worker)       │ │
│  │  ┌──────────────┐                                                 │ │
│  │  │   Temporal   │                                                 │ │
│  │  │    Worker    │                                                 │ │
│  │  │  (Polling)   │                                                 │ │
│  │  └──────┬───────┘                                                 │ │
│  │         │                                                         │ │
│  │  ┌──────▼──────────────────────────────────────────────────────┐  │ │
│  │  │               Workflow Execution Engine                     │  │ │
│  │  │  ┌────────────────┐  ┌────────────────┐                     │  │ │
│  │  │  │   Generic      │  │  Node          │                     │  │ │
│  │  │  │   Workflow     │◄─┤  Executor      │                     │  │ │
│  │  │  │   Impl         │  │                │                     │  │ │
│  │  │  └────────────────┘  └───────┬────────┘                     │  │ │
│  │  │                              │                              │  │ │
│  │  │         ┌────────────────────┴────────────────────┐         │  │ │
│  │  │         │                                         │         │  │ │
│  │  │  ┌──────▼──────┐  ┌──────────────┐  ┌─────────────▼────┐    │  │ │
│  │  │  │   Activity  │  │   Activity   │  │    Activity      │    │  │ │
│  │  │  │  HTTP Node  │  │ Groovy Node  │  │  Branch Node     │    │  │ │
│  │  │  └─────────────┘  └──────────────┘  └──────────────────┘    │  │ │
│  │  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐    │  │ │
│  │  │  │  Activity   │  │   Activity   │  │    Activity      │    │  │ │
│  │  │  │Instruction  │  │  Processor   │  │  Success/Failure │    │  │ │
│  │  │  │    Node     │  │    Node      │  │      Node        │    │  │ │
│  │  │  └─────────────┘  └──────────────┘  └──────────────────┘    │  │ │
│  │  └─────────────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │
        ┌───────────────────────────┴────────────────────────────┐
        │                                                        │
        ▼                                                        ▼
┌────────────────┐                                      ┌────────────────┐
│    CONTEXT     │                                      │    CACHING     │
│  PERSISTENCE   │                                      │                │
│  ┌──────────┐  │                                      │  ┌──────────┐  │
│  │  HBase   │  │                                      │  │  Redis   │  │
│  │  Cluster │  │                                      │  │ Sentinel │  │
│  │          │  │                                      │  │  Cluster │  │
│  │ ┌──────┐ │  │                                      │  └──────────┘  │
│  │ │      │ │  │                                      │                │
│  │ │      │ │  │                                      │  • Workflow    │
│  │ │      │ │  │                                      │    Definitions │
│  │ │      │ │  │                                      │  • Node Defs   │
│  │ └──────┘ │  │                                      │                │
└────────────────┘                                      └────────────────┘

```

---

## Core Components

### 1. API Service (`api` module)

**Purpose**: REST API layer for workflow orchestration management

**Responsibilities**:
- Expose REST endpoints for workflow lifecycle operations
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

**Technology**: Dropwizard 2.0.27, Temporal, Redis

---

### 2. Worker Service (`worker` module)

**Purpose**: Temporal worker that executes workflow activities

**Responsibilities**:
- Poll Temporal task queues for work
- Execute workflow logic (GenericWorkflowImpl)
- Execute node activities (HTTP, Groovy, Branch, etc.)
- Manage workflow state and context
- Handle workflow errors and retries

**Key Classes**:
- `WorkerApplication`: Main worker entry point (Dropwizard)
- `GenericWorkflowImpl`: Core workflow execution implementation
- `WorkflowNodeExecutor`: Orchestrates node execution
- `TemporalWorkerManaged`: Manages Temporal worker lifecycle
- `*NodeActivityImpl`: Activity implementations for each node type

**Technology**: Dropwizard 2.0.27, Temporal SDK, Hbase, Redis

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


---

## Deployment Architecture

### Kubernetes Deployment

```
┌──────────────────────────────────────────────────────┐
│                      Kubernetes Cluster              │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │                    Namespace: drift            │  │
│  │                                                │  │
│  │  ┌────────────────┐        ┌────────────────┐  │  │
│  │  │  API Pods      │        │  Worker Pods   │  │  │
│  │  │                │        │                │  │  │
│  │  │                │        │                │  │  │
│  │  │ • Port: 8000   │        │ • Port: 7200   │  │  │
│  │  │ • Port: 8001   │        │ • Port: 7201   │  │  │
│  │  │   (admin)      │        │   (admin)      │  │  │
│  │  │ • Port: 9090   │        │ • Port: 9090   │  │  │
│  │  │   (metrics)    │        │   (metrics)    │  │  │
│  │  └────────┬───────┘        └────────┬───────┘  │  │
│  │           │                         │          │  │
│  │  ┌────────▼───────┐       ┌────────▼───────┐   │  │
│  │  │   Service:     │       │   Service:     │   │  │
│  │  │   drift-api    │       │ drift-worker   │   │  │
│  │  │   (LoadBalancer)│      │  (ClusterIP)   │   │  │
│  │  └────────────────┘       └────────────────┘   │  │
│  │                                                │  │
│  │  ┌──────────────────────────────────────────┐  │  │
│  │  │           ConfigMap / Secrets            │  │  │
│  │  │  • Environment variables                 │  │  │
│  │  │  • Redis config                          │  │  │
│  │  │  • Temporal connection                   │  │  │
│  │  │  • HBase config                          │  │  │
│  │  └──────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

### External Dependencies

```
┌────────────────────────────────────────────────────────────────┐
│                    External Infrastructure                     │
│                                                                │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │ Temporal Cluster │  │  Redis Sentinel  │  │ HBase Cluster│  │
│  │  (Self-hosted)   │  │    (3 nodes)     │  │              │  │
│  │                  │  │                  │  │              │  │
│  │ • Frontend       │  │ • Master         │  │ • Master     │  │
│  │ • History        │  │ • Sentinels      │  │ • RegionSrvs │  │
│  │ • Matching       │  │ • Replicas       │  │ • ZooKeeper  │  │
│  │ • Worker         │  │                  │  │              │  │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

**Next**: [High-Level Design (HLD)](./02-HIGH-LEVEL-DESIGN.md)

