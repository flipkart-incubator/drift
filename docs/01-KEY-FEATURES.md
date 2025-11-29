# Drift Platform - Key Features

## Introduction

**Drift** is a Temporal-powered, low-code visual workflow orchestration platform that enables users to design, deploy, and execute workflows using **native reusable nodes**. The platform abstracts the complexity of workflow management and provides a flexible, extensible architecture for building business process automation.

### Key Features
- **Low-Code/No-Code**: Create workflows on-the-fly with predefined generic node types.
- **DSL-Based workflow**: Defines the workflow structure by orchestrating native nodes in a Directed Graph. Executes mostly as a sequential DAG, with explicit support for loops to handle complex logic.
- **Temporal Integration**: Reliable, fault-tolerant workflow execution.
- **Distributed Architecture**: Horizontally scalable with separate API and Worker components.
- **Multi-Tenancy**: Isolated tenant configurations.
- **Context Offloading**: Persists context in HBase (more connectors planned) to bypass the Temporal backend, maximizing throughput by keeping data and control plane decoupled.
- **Workflow Versioning**: Version-controlled workflows with hot deployments, zero downtime updates and rollbacks.
- **Real-time Visibility**: Live workflow execution tracking and monitoring.
- **Widgetized Responses**: UI-ready responses for human-in-the-loop workflows and systematic resumes.
- **Generic Contracts**: Standardized contracts for seamless data flow.
- **Interaction Modes**: Supports both sync and async modes of workflow interactions.

---

**Next**: [Architecture](02-ARCHITECTURE-OVERVIEW)

