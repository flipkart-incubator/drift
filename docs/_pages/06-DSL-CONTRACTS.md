---
title: "Contracts"
permalink: /06-DSL-CONTRACTS/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform API Contracts

## Table of Contents
1. [Definition API (`WorkflowDefinitionResource`)](#3-definition-api-workflowdefinitionresource)
2. [Node Definition API (`NodeDefinitionResource`)](#4-node-definition-api-nodedefinitionresource)
3. [Versioning Semantics](#7-versioning-semantics)

---
## 1. Definition API (`WorkflowDefinitionResource`)

Manages workflow DSL definitions. These are configuration objects, not runtime instances.

### Base Path
`/workflowDefinition`

### 1.1 Workflow Model

```java
public class Workflow {
    private String id;                              // Unique workflow identifier
    private String comment;                         // Description
    private String startNode;                       // ID of starting node
    private String version;                         // Version number
    private String defaultFailureNode;              // Fallback failure handler
    private Map<String, WorkflowNode> states;       // State machine definition
    private List<String> postWorkflowCompletionNodes; // Cleanup hooks
}

public class WorkflowNode {
    private String instanceName;        // Instance name in this workflow
    private String resourceId;          // Reference to NodeDefinition ID
    private String resourceVersion;     // Reference version (e.g., "LATEST")
    private WorkflowNodeType type;      // NODE or SUBWORKFLOW
    private Map<String, String> parameters; // Input parameter mapping
    private String nextNode;            // Transition target
    private boolean end;                // Is terminal?
}
```

### 1.2 Operations

- **Create**: `POST /workflowDefinition/` (Creates initial mutable `SNAPSHOT`)
- **Update**: `PUT /workflowDefinition/` (Updates existing `SNAPSHOT`)
- **Get**: `GET /workflowDefinition/{id}?version={version}&enrichNodeDefinition={true|false}`
- **Publish**: `POST /workflowDefinition/{id}/publishWorkflow/` (Finalizes `SNAPSHOT` → Immutable Numbered Version)
- **Activate**: `POST /workflowDefinition/{id}/activate?version={v}` (Promotes Version -> ACTIVE)
- **Visualize**: `GET /workflowDefinition/treeView/{id}` (Returns PNG graph)

---

## 2. Node Definition API (`NodeDefinitionResource`)

Manages reusable node definitions. Each node encapsulates one atomic step.

### Base Path
`/nodeDefinition`

### 2.1 Node Types

| Type | Purpose |
|------|---------|
| **INSTRUCTION** | Generates widgetized UI responses for `WAITING` state. |
| **HTTP** | Executes external REST calls. |
| **GROOVY** | Runs dynamic scripts for logic/transformation. |
| **BRANCH** | Conditional routing logic. |
| **SUCCESS/FAILURE** | Terminal states. |
| **PROCESSOR** | *Deprecated* (Use Groovy). |

### 2.2 Definition Model

```java
public abstract class NodeDefinition {
    private String id;
    private String name;
    private NodeType type;
    private List<String> parameters;
    private String version;
}
```

#### Instruction Node (Widget Source)

```java
public class InstructionNode extends NodeDefinition {
    private List<Option> inputOptions;              // Widget definitions
    private VariableAttributeComponent disposition; // Dynamic disposition
    private VariableAttributeComponent workflowStatus; // Dynamic status
    private VariableAttributeComponent layoutId;    // Dynamic layout ID
}
```

### 2.3 Operations

- **Create**: `POST /nodeDefinition/` (Creates initial mutable `SNAPSHOT`)
- **Update**: `PUT /nodeDefinition/` (Updates existing `SNAPSHOT`)
- **Get**: `GET /nodeDefinition/{id}`
- **Publish**: `POST /nodeDefinition/{id}/publishNode/` (Finalizes `SNAPSHOT` → Immutable Numbered Version)

---

## 3. Versioning Semantics

Drift uses a consistent versioning scheme for all definitions:

| Version | Description |
|---------|-------------|
| `SNAPSHOT` | Mutable draft. Used during development. |
| `LATEST` | The most recently **published** (immutable) version. |
| `ACTIVE` | The version currently serving traffic. |
| `1`, `2`... | Specific immutable historical versions. |

### Publishing Flow
1. **Edit**: Update `SNAPSHOT`.
2. **Publish**: Promotes `SNAPSHOT` → `N+1`. Updates `LATEST`.
3. **Activate**: Promotes `N+1` → `ACTIVE`.
