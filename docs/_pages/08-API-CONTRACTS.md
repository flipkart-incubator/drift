---
title: "Contracts"
permalink: /08-API-CONTRACTS/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform API Contracts

## Table of Contents
1. [Core Philosophy](#1-core-philosophy)
2. [Runtime API (`WorkflowResource`)](#2-runtime-api-workflowresource)
3. [Definition API (`WorkflowDefinitionResource`)](#3-definition-api-workflowdefinitionresource)
4. [Node Definition API (`NodeDefinitionResource`)](#4-node-definition-api-nodedefinitionresource)
5. [Widgetized Response Model](#5-widgetized-response-model)
6. [Client Responsibilities](#6-client-responsibilities)
7. [Versioning Semantics](#7-versioning-semantics)

---

## 1. Core Philosophy

> **Note to Adopters**: The philosophy described below reflects the guiding principles established by the original developers of Drift to ensure strict separation of concerns. All design decisions and contract structures in this document are influenced by this mindset. However, this does **not** restrict new users or contributors from experimenting with different patterns or extending the platform to suit their own architectural needs.

### Drift as Action Orchestrator and Data Provider

The Drift system operates on a fundamental principle of separation of concerns between the backend (Drift) and frontend clients. Drift serves as an **Action Orchestrator** and **Data Provider**, explicitly avoiding decisions related to presentation, design, or user experience.

### What Drift Provides

| Responsibility | Description |
|---------------|-------------|
| **Action Orchestration** | Triggers downstream actions (fetching data, processing logic) and aggregates results |
| **Data Provisioning** | Delivers data in a structured, uniform format (widgets) across all clients |
| **Widget Structure** | Defines canonical widget structure including static components and possible values |
| **Script Augmentation** | Provides mechanism to augment possible values with contextual instructions via Groovy |

### What Drift Does NOT Control

| Limitation | Description |
|-----------|-------------|
| **Design/Layout** | How data is visually presented |
| **Grouping/Hierarchy** | How multiple widgets are organized (cards, tabs, pages) |
| **Visibility** | When or where specific widgets appear |
| **Rendering Logic** | Code that displays data on screen |

> **Key Principle**: "Drift provides the data; you (the client) decide how to show it. Drift is not the UI controller; it is the data pipe."

---

## 2. Runtime API (`WorkflowResource`)

This resource exposes runtime workflow APIs that clients call to drive workflows. It handles starting, resuming, inspecting, and terminating running workflows, returning widgetized views for user interaction.

### Base Path
`/v3`

### Required Headers

| Header | Required | Description |
|--------|----------|-------------|
| `X_TENANT_ID` | Yes | Tenant identifier (e.g., `DEFAULT_TENANT`) |
| `X_CLIENT_ID` | Yes | Client/channel identifier (e.g., `web`, `mobile`) |
| `X_USERNAME_ID` | No | User identity (defaults to `X_CLIENT_ID` if absent) |

### 2.1 Start Workflow

**Endpoint**: `POST /v3/workflow/start`

Initiates a new workflow execution.

#### Request Body

```java
public class WorkflowStartRequest extends WorkflowRequest {
    @NotNull private IssueDetail issueDetail; // Identification of the issue/intent
    private QueueDetail queueDetail;          // Optional queue routing info
    @NotNull private Customer customer;       // Customer context
    @NotNull private Set<OrderDetail> orderDetails; // Domain-specific context (e.g., Orders)
}

// Base class
public abstract class WorkflowRequest {
    private String incidentId;      // Populated by Drift
    private String workflowId;      // Populated by Drift
    private Map<String, String> threadContext;
    private Map<String, Object> params;
}
```

#### Sample Request

```json
{
  "issueDetail": {
    "issueId": "6332",
    "issueName": "Refund"
  },
  "customer": {
    "customerId": "CUST_12345"
  },
  "orderDetails": [
    {
      "orderId": "OD_998877"
    }
  ],
  "params": {
    "channel": "WEB",
    "userLocale": "en_US"
  }
}
```

#### Response Body

```java
public class WorkflowResponse implements Serializable {
    private String incidentId;
    private String workflowId;
    private WorkflowStatus workflowStatus;
    private String disposition;
    private String errorMessage;
    private View view; // The Widgetized Response
}

public enum WorkflowStatus {
    CREATED, RUNNING, WAITING, COMPLETED, FAILED, TERMINATED
}
```

#### Sample Response (WAITING State)

```json
{
  "incidentId": "INC_20240414_001",
  "workflowId": "WF_REFUND_INC_20240414_001",
  "workflowStatus": "WAITING",
  "view": {
    "layoutId": "refund_options_screen",
    "inputOptions": [
      {
        "id": "refund_method_select",
        "description": "Select refund method",
        "tags": { "values": ["ui.dropdown"] },
        "instructions": [
          {
            "templateId": "select_refund_method_msg",
            "templateVariables": {}
          }
        ],
        "possibleValues": [
          { "displayValue": "Original Payment Mode", "value": "SOURCE" },
          { "displayValue": "Wallet", "value": "WALLET" }
        ]
      },
      {
        "id": "proceed_btn",
        "tags": { "values": ["ui.button"] },
        "possibleValues": [{ "value": "PROCEED" }]
      }
    ]
  }
}
```

### 2.2 Resume Workflow

**Endpoint**: `PUT /v3/workflow/resume/{workflowId}`

Continues a workflow that is in `WAITING` state by providing user selections.

#### Request Body

```java
public class WorkflowResumeRequest extends WorkflowRequest {
    @NotNull private ViewResponse viewResponse;
}

public class ViewResponse {
    Map<String, Object> selectedOptions; // Map of Widget ID -> Selected Value
}
```

#### Sample Request

```json
{
  "viewResponse": {
    "selectedOptions": {
      "refund_method_select": "WALLET",
      "proceed_btn": "PROCEED"
    }
  }
}
```

### 2.3 Terminate Workflow

**Endpoint**: `DELETE /v3/workflow/terminate/{workflowId}`

Terminates a running workflow.

### 2.4 Get Workflow State

**Endpoint**: `GET /v3/workflow/{workflowId}`

Retrieves the current state of a workflow (debugging/admin).

### 2.5 Disconnected Nodes & Utility Executions

**Endpoint**: `POST /v3/workflow/{workflowId}/disconnected-node/execute`

Disconnected nodes are nodes defined within the workflow definition but are not part of the main execution path (i.e., no other node points to them via `nextNode`). They are used for utility operations that do not advance the workflow state.

**Use Case:**
A common scenario is performing repetitive validations or intermediate actions without moving the workflow forward. For example, in a bank account addition flow:
1.  **Verify**: The user might try to verify a bank account multiple times (invalid account, wrong IFSC, etc.). This verification is handled by a disconnected "verify_account" node.
2.  **No State Change**: Hitting this node executes the logic (e.g., API call to bank) and returns the result but keeps the workflow in its current state (e.g., waiting at the "Add Account" screen).
3.  **Resume**: Once verification is successful, the client calls the standard `resume` endpoint to move the workflow to the next step (e.g., "submit_account").

#### Request Body

```java
public class WorkflowUtilityRequest extends WorkflowRequest {
    @NotNull private String node; // The name of the disconnected node to execute
    private Map<String, Object> parameters; // Parameters specific to this execution
}
```

#### Response Body

```java
public class WorkflowUtilityResponse implements Serializable {
    private String workflowId;
    private String node;
    private WorkflowUtilityStatus status; // e.g., COMPLETED, FAILED
    private Object response; // The output of the executed node
}
```

---

## 5. Widgetized Response Model

The widgetized response is how Drift expresses "what the user needs to see and choose" without dictating UI implementation.

### 5.1 Model Hierarchy

```
WorkflowResponse
  └── View
       ├── layoutId (String)
       └── inputOptions (List<Option>)
            └── Option (The Widget)
                 ├── id (String)
                 ├── parentId (String)
                 ├── description (String)
                 ├── tags (Tag)
                 ├── instructions (List<Instruction>)
                 ├── possibleValues (List<PossibleValue>)
                 └── possibleDependentValues (Map<String, Option>)
```

### 5.2 Core Models

#### View
Container for the screen/state.
```java
public class View {
    private String layoutId;            // Hint for client layout lookup
    private List<Option> inputOptions;  // List of widgets
}
```

#### Option (The Widget)
Represents a single interaction element (dropdown, button, text field).
```java
public class Option {
    private String id;                  // Unique ID (key for response)
    private String parentId;            // For hierarchical widgets
    private Tag tags;                   // Rendering hints (e.g., "ui.dropdown")
    private List<PossibleValue> possibleValues; // Selectable options
    private Map<String, Option> possibleDependentValues; // Cascading options
    private List<Instruction> instructions; // Static text content
}
```

#### PossibleValue
A selectable choice within a widget.
```java
public class PossibleValue {
    private String displayValue;  // Text to show user
    private String value;         // Token to send back to Drift
    private JsonNode metaData;    // Extra data (images, subtitles)
}
```

#### Tag
Hints for the client renderer.
```java
public class Tag {
    private List<String> values;  // e.g., ["ui.dropdown", "ui.required"]
}
```

### 5.3 Common Tag Vocabulary

| Tag Value | Intended UI Component |
|-----------|-----------------------|
| `ui.static_text` | Label / Text block |
| `ui.dropdown` | Select / Dropdown menu |
| `ui.button` | Action button |
| `ui.free_text` | Text input / Text area |
| `ui.date_picker` | Calendar control |
| `ui.complete` | Completion banner |

### 5.4 Hierarchical Widgets (Dependent Values)

For cascading selections (e.g., City -> Locality):
1. **Parent Widget**: Defines `possibleValues` (Cities).
2. **Child Logic**: Uses `possibleDependentValues` map.
   - Key: Value of selected parent option.
   - Value: Child `Option` definition specific to that parent selection.

---

## 6. Client Responsibilities

Drift supports both human-driven and machine-driven workflows. The responsibilities of the client differ based on the integration mode.

### 6.1 Integration Modes

**1. User-Interactive (Human-in-the-loop)**
When the workflow is intended for a human user (e.g., a Customer Support Agent), the client **must** implement a UI renderer. The client interprets the `View` response to generate the interface, allowing the user to read instructions and make selections.

**2. Systematic Integration (Context-Aware Caller)**
When the caller is another system or service (e.g., a chatbot backend or an automated job), no UI rendering is required.
*   The caller is "context-aware"—it knows which workflow it is running and what data is expected next.
*   The `view` / `widgets` section of the response can be ignored.
*   The client programmatically constructs the `resume` request based on its own internal logic.

### 6.2 UI Rendering (For User-Interactive Mode)
Clients must implement a renderer that:
1. **Reads** `View.layoutId` to determine screen structure.
2. **Iterates** `inputOptions`.
3. **Maps** `tags.values` to native UI components (React component, Android View, etc.).
4. **Displays** `possibleValues` as choices.

### 6.3 Resume Logic
When interacting with widgets:
1. Collect user input for each widget `id`.
2. Construct `ViewResponse`:
   ```json
   {
     "selectedOptions": {
       "widget_id_1": "selected_value_token",
       "widget_id_2": "entered_text"
     }
   }
   ```
3. Call `PUT /v3/workflow/resume/{id}`.

### 6.4 Template Interpolation
`Instruction` objects contain `templateId` and `variables`. Clients should look up localized strings from their CMS/ResourceBundle and interpolate the variables before display.

---
