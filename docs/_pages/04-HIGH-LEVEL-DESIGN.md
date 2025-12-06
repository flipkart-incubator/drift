---
title: "High-Level Design"
permalink: /04-HIGH-LEVEL-DESIGN/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform - High-Level Design (HLD)

## Table of Contents
1. [System Overview](#system-overview)
2. [Core Services](#core-services)
3. [Service Boundaries](#service-boundaries)
4. [Deployment View](#deployment-view)

---
## System Overview

<img width="1033" height="771" alt="Screenshot 2025-11-29 at 1 23 51 PM" src="https://github.com/user-attachments/assets/927dcfcf-db0c-4df9-b4d1-1524daa61d90" />

> **Note**: The above design illustrates the sync mode of workflow interaction. Drift also supports an async mode, in which steps 2.b and 2.g are skipped, and the client is notified via webhook triggered at I/O or terminal nodes.

---
## Core Services

### 1. API Service (`api` module)

**Purpose**: The REST API layer that serves as the entry point for all external clients. It manages the lifecycle of workflows and definitions without executing business logic.

**Key Responsibilities**:
- **Orchestration Management**: Expose endpoints to start, resume, signal, and terminate workflows.
- **Definition Management**: CRUD operations for Node and Workflow definitions.
- **State Querying**: Fetch real-time status and context of running workflows.
- **Authentication/Authorization**: First line of defense for client requests.
- **Temporal Interaction**: Acts as a client to the Temporal cluster to dispatch commands.

### 2. Worker Service (`worker` module)

**Purpose**: The execution engine that processes workflow tasks. It runs the actual business logic, executes nodes, and manages side effects.

**Key Responsibilities**:
- **Task Execution**: Polls Temporal task queues and executes assigned workflow tasks.
- **Node Processing**: Interprets different node types (HTTP, Groovy, Branch, etc.) and executes their specific logic.
- **State Persistence**: Manages the workflow context (`_global` state) and persists it to the database.
- **External Integrations**: Makes calls to external services (HTTP nodes) and evaluates dynamic scripts (Groovy nodes).
- **Extensibility**: Loads custom SPI implementations (e.g., custom A/B testing or Token Providers) at runtime.

---

## Service Boundaries

#### 1. API Service

| Category | Description |
|----------|-------------|
| **Responsibilities** | - REST API exposure and client request filtering.<br> - Request validation.<br> - Workflow orchestration commands (start, signal, terminate).<br> - Definition management (Workflows & Nodes CRUD). |
| **Does NOT** | - Execute workflow logic.<br> - Run activities.<br> - Evaluate scripts. |

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
│  │  └────────────────────────────────────────────────┘  │
│  └──────────────────────────────────────────────────────┘
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

**Next**: [Low-Level Design (LLD)](/05-LOW-LEVEL-DESIGN/)
