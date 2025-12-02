---
title: "Architecture Overview"
permalink: /03-ARCHITECTURE-OVERVIEW/
layout: single
sidebar:
  nav: "docs"
---

# Drift Platform - Architecture Overview

## Table of Contents
1. [System Architecture](#system-architecture)
2. [Technology Stack](#tech-stack-used)
3. [Core Components](#core-components)
4. [Deployment View](#deployment-view)

---

## System Architecture

### High-Level Architecture Diagram

<img width="904" height="760" alt="Screenshot 2025-11-28 at 11 19 49 PM" src="https://github.com/user-attachments/assets/1d0948c8-db63-4e4a-a853-f5de8b66f337" />

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

**HBase** — Workflow context, workflow and node spec storage; more connectors to follow.

---

**Next**: [High-Level Design (HLD)](/04-HIGH-LEVEL-DESIGN/)

