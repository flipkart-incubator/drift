---
title: "Welcome to Drift Platform Documentation"
layout: single
author_profile: false
---

**Drift** is a Temporal-powered, low-code workflow orchestration platform that enables users to design, deploy, and execute workflows using native reusable nodes.

## Quick Start

### For Developers
1. Read [Architecture Overview](_pages/03-ARCHITECTURE-OVERVIEW.md) to understand the system
2. Review [High-Level Design](_pages/04-HIGH-LEVEL-DESIGN.md) for component design
3. Study [Contracts](_pages/06-DSL-CONTRACTS.md) for integration details

### For Architects
1. Start with [Architecture Overview](_pages/03-ARCHITECTURE-OVERVIEW.md)
2. Deep dive into [High-Level Design](_pages/04-HIGH-LEVEL-DESIGN.md)
3. Review [Low-Level Design](_pages/05-LOW-LEVEL-DESIGN.md) for implementation details

## Documentation Structure

### Getting Started
- **[Key Features](_pages/01-KEY-FEATURES.md)** - Overview of platform capabilities
- **[Terminologies](_pages/02-TERMINOLOGIES.md)** - Core concepts and definitions

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

