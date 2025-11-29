# Drift Platform - High-Level Design (HLD)

## Table of Contents
1. [System Overview](#system-overview)
2. [Core Components](#core-components)
3. [Service Boundaries](#service-boundaries)
4. [Deployment View](#deployment-view)

---
## System Overview

<img width="1033" height="771" alt="Screenshot 2025-11-29 at 1 23 51 PM" src="https://github.com/user-attachments/assets/927dcfcf-db0c-4df9-b4d1-1524daa61d90" />

---
## Core Components

### 1. API Service (`api` module)

**Purpose**: REST API layer for workflow orchestration management

**Responsibilities**:
- Expose REST endpoints for workflow lifecycle operations
- Communicate with Temporal cluster to start/resume/terminate workflows
- Query workflow state and execution status
- Manage workflow and node definitions (CRUD operations)

**Key Classes**:
- `DriftApplication`: Main application entry point (Dropwizard)
- `WorkflowResource`: REST endpoints for workflow operations
- `NodeDefinitionResource`: CRUD operations for node definitions
- `WorkflowDefinitionResource`: CRUD operations for workflow definitions
- `TemporalService`: Service layer for Temporal interactions

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

## Service Boundaries

#### 1. API Service

| Category | Description |
|----------|-------------|
| **Responsibilities** | - REST API exposure and client request filtering.<br> - Request validation.<br> - Workflow orchestration commands (start, signal, terminate).<br> - Definition management (Workflows & Nodes CRUD). |
| **Does NOT** | - Execute workflow logic.<br> - Run activities. |

#### 2. Worker Service

| Category | Description |
|----------|-------------|
| **Responsibilities** | - Poll Temporal task queues for work.<br> - Execute workflow implementations (the core business logic).<br> - Run node activities (HTTP, Groovy, etc.).<br> - Manage workflow state.<br> - Persist context to HBase.<br> - Dynamic script evaluation.<br> - External API interactions. |
| **Does NOT** | - Expose REST APIs to clients.<br> - Handle client authentication. |
---
## Deployment View

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
│  │  ┌────────▼───────┐       ┌─────────▼──────┐   │  │
│  │  │   Service:     │       │   Service:     │   │  │
│  │  │   drift-api    │       │ drift-worker   │   │  │
│  │  │  (LoadBalancer)│       │  (ClusterIP)   │   │  │
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
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       External Infrastructure                                           │
│                                                                                                         │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Temporal Cluster │  │  Redis Sentinel  │  │ HBase Cluster│  │  ES Cluster  │  │   TiDB Cluster   │   │
│  │  (Self-hosted)   │  │  (Self-hosted)   │  │    (PaaS)    │  │ (K8s Hosted) │  │      (PaaS)      │   │
│  │                  │  │                  │  │              │  │              │  │                  │   │
│  │ • Frontend       │  │ • Master         │  │ • Master     │  │ • Master     │  │ • TiDB Server    │   │
│  │ • History        │  │ • Sentinels      │  │ • RegionSrvs │  │ • Data Nodes │  │ • TiKV           │   │
│  │ • Matching       │  │ • Replicas       │  │ • ZooKeeper  │  │              │  │ • PD             │   │
│  │ • Worker         │  │                  │  │              │  │              │  │                  │   │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  └──────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

> **Note**: Drift uses Temporal's pluggable architecture for persistence and visibility. Users can choose their preferred backing stores (e.g., PostgreSQL, Cassandra, MySQL for persistence; ElasticSearch, standard SQL for visibility) based on their infrastructure and scaling needs. The diagram above represents a reference implementation.

**Next**: [Low-Level Design (LLD)](05-LOW-LEVEL-DESIGN.md)

