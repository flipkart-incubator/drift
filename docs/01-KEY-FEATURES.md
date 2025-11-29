# Drift Platform - Key Features

## Introduction

***Drift*** is a Temporal-powered, low-code workflow orchestration platform that enables users to design, deploy, and execute workflows using **native reusable nodes**. The platform abstracts the complexity of workflow creation and management, provides a flexible, extensible architecture for building business flows, and also offers an easy, user-friendly way to interact with workflows.
### Key Features
- **Low-Code/No-Code**: Create workflows on-the-fly with predefined native nodes.
- **DSL-Based workflow**: Defines the workflow structure by stitching nodes in a Directed Graph. Executes mostly as a sequential DAG, with explicit support for loops to handle complex logic.
- **Temporal Integration**: For durable, fault-tolerant workflow execution.
- **Workflow Versioning**: Version-controlled workflows with hot deployments, zero downtime updates and rollbacks.
- **Real-time Visibility**: Live workflow execution tracking and monitoring.
- **Widgetized Responses**: UI-ready responses for human-in-the-loop workflows and systematic resumes.
- **Generic Contracts**: Standardized contracts for seamless data flow.
- **Interaction Modes**: Supports both sync and async modes of workflow interactions.

---

### Terminologies

### 1. Native Nodes

**Native Nodes** are reusable, self-contained building blocks that represent a single unit of work within a workflow.

#### **Reusability**
A single node instance (or sub-workflow composed of nodes) can be integrated across multiple distinct workflows.

#### **Node Types**

| Node Type   | Purpose                                                                                  |
|-------------|------------------------------------------------------------------------------------------|
| **HTTP**    | Executes external API calls (e.g., fetching data, triggering services).                  |
| **WAIT**    | Pauses workflow execution for a specified duration or until a scheduled time.            |
| **BRANCH**  | Implements conditional logic to direct the workflow to different paths.                  |
| **GROOVY**  | Executes custom transformation and business logic using Groovy scripts.                  |
| **INSTRUCTION** | Handles UI interactions and necessary I/O (human-in-the-loop).                     |
| **SUCCESS / FAILURE** | Terminal nodes that explicitly mark the conclusion or failure state of the workflow. |
| **PARALLEL** | Executes specified nodes or sub-workflows concurrently.                                |

---

### 2. Domain-Specific Language (DSL)

The **Domain-Specific Language (DSL)** is the JSON specification that defines the complete structure and execution flow of a workflow.

#### **Function**
Dictates how native nodes are stitched together, orchestrating the sequence of operations.

#### **Structure**
Typically follows a **Directed Acyclic Graph (DAG)** structure, where nodes execute in a predefined, sequential order.

#### **Capability**
Supports complex, iterative use cases by allowing **explicit looping mechanisms**.


**Next**: [Architecture](02-ARCHITECTURE-OVERVIEW.md)

