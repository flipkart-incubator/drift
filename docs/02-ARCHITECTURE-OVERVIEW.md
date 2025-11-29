# Drift Platform - Architecture Overview

## Table of Contents
1. [System Architecture](#system-architecture)
2. [Technology Stack](#tech-stack-used)
3. [Core Components](#core-components)
4. [Deployment View](#deployment-view)

---

## System Architecture

### High-Level Architecture Diagram

<img width="904" height="760" alt="Screenshot 2025-11-28 at 11 19 49 PM" src="https://github.com/user-attachments/assets/1d0948c8-db63-4e4a-a853-f5de8b66f337" />

**Workflow Studio (WiP)** — A low-code UI to design, configure, and manage workflows. Provides a drag-and-drop interface to create DAG using native nodes (HTTP calls, branching, parallel execution, wait, i/o, etc.)

**Client Gateway** — API layer for client integration. Allows external systems to trigger and interact with workflows.

**Temporal Server** — Backbone of Drift, providing durable execution, retries, and scheduling for workflows. Ensures fault tolerance and allows workflows to resume from failure points automatically by replaying event history.

**Worker Engine** — Implements native node functionalities. Executes the DAG. Written using Temporal's Java SDK.

**DSL Store** — API layer for workflow and node definition CRUD. Acts as the backend for Workflow Studio. Supports versioning and rollback.

**Monitoring & Debugging Tools** — Observability tools that provide real-time monitoring, debugging, and logging for running workflows. Includes Temporal's UI, SDK, and server dashboards powered by SDK metrics.

---

## Tech Stack Used

**Temporal** — Self-hosted Temporal for workflow orchestration designed to execute asynchronous long-running business logic. Distributed, durable, horizontally scalable, and highly available. 

**TiDB** — Persistence store for Temporal to store workflow state, history, and task queues. Horizontally scalable with high availability. Temporal provides other persistence [options](https://docs.temporal.io/temporal-service/persistence) as well.

**Redis** — Pub/sub to support sync API interaction for clients. Sentinel-based for high availability. Also used for caching workflow and node specs.

**ElasticSearch** — Visibility store for Temporal.

**HBase** — Workflow context and DSL storage; more connectors to follow.

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

**Next**: [High-Level Design (HLD)](./02-HIGH-LEVEL-DESIGN.md)

