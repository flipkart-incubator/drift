# Drift Platform - Complete Documentation

## Documentation Structure

This comprehensive documentation suite covers all aspects of the Drift platform, from high-level architecture to implementation details.

### 📚 Available Documents

#### ✅ Completed Documents

1. **[Architecture Overview](./01-ARCHITECTURE-OVERVIEW.md)**
   - System architecture and component overview
   - Technology stack
   - Data flow and deployment architecture
   - High-level system diagrams
   - Scalability and HA considerations

2. **[High-Level Design (HLD)](./02-HIGH-LEVEL-DESIGN.md)**
   - Component design and responsibilities
   - Workflow execution model
   - Node type system
   - State management strategy
   - Caching and performance optimization
   - Error handling and retry logic

3. **[Low-Level Design (LLD)](./03-LOW-LEVEL-DESIGN.md)**
   - Detailed class diagrams
   - Implementation algorithms
   - Sequence diagrams
   - Data structures
   - Database schemas
   - Configuration management

4. **[Redis Pub/Sub System](./04-REDIS-PUBSUB.md)**
   - Cache invalidation architecture
   - Async workflow communication
   - RedisPubSubService implementation
   - RedisCacheInvalidator implementation
   - Monitoring and troubleshooting
   - Best practices

#### 🔄 In Progress / To Be Created

5. **API Contracts & Endpoints**
   - REST API specifications
   - Request/response formats
   - Error codes and handling
   - Authentication & authorization
   - Rate limiting

6. **Data Models & Schemas**
   - Domain model details
   - Node type specifications
   - Workflow DSL format
   - Context structure
   - Validation rules

7. **Developer Guide**
   - Setting up development environment
   - Building and running locally
   - Creating custom node types
   - Writing workflow DSLs
   - Testing strategies

8. **Deployment Guide**
   - Docker containerization
   - Kubernetes deployment
   - Environment configuration
   - Monitoring and observability
   - Troubleshooting

9. **Operations Manual**
   - Day-to-day operations
   - Monitoring dashboards
   - Alert configurations
   - Backup and recovery
   - Performance tuning

---

## Quick Start

### For Developers
1. Read [Architecture Overview](./01-ARCHITECTURE-OVERVIEW.md) to understand the system
2. Review [Developer Guide](#) for local setup
3. Study [API Contracts](#) for integration
4. Check [Data Models](#) for understanding the domain

### For Architects
1. Start with [Architecture Overview](./01-ARCHITECTURE-OVERVIEW.md)
2. Deep dive into [High-Level Design](./02-HIGH-LEVEL-DESIGN.md)
3. Review [Low-Level Design](./03-LOW-LEVEL-DESIGN.md) for implementation details
4. Check [Deployment Guide](#) for production considerations

### For Operations Teams
1. Read [Deployment Guide](#) first
2. Review [Operations Manual](#) for daily tasks
3. Familiarize with monitoring and alerting
4. Understand troubleshooting procedures

---

## Document Navigation

```
┌──────────────────────────────────────────────────────────────┐
│                   DOCUMENTATION HIERARCHY                     │
└──────────────────────────────────────────────────────────────┘

Level 1: OVERVIEW
└─ 01-ARCHITECTURE-OVERVIEW.md
   ├─ What is Drift?
   ├─ System Components
   ├─ Technology Stack
   └─ Deployment Architecture

Level 2: DESIGN
├─ 02-HIGH-LEVEL-DESIGN.md
│  ├─ Component Design
│  ├─ Execution Model
│  ├─ State Management
│  └─ Performance Considerations
│
└─ 03-LOW-LEVEL-DESIGN.md
   ├─ Class Diagrams
   ├─ Algorithms
   ├─ Sequence Diagrams
   └─ Database Schemas

Level 3: SPECIFICATIONS
├─ 04-REDIS-PUBSUB.md
│  ├─ Cache Invalidation
│  ├─ Async Communication
│  ├─ Implementation Details
│  └─ Monitoring & Troubleshooting
│
├─ 05-API-CONTRACTS.md (To be created)
│  ├─ REST Endpoints
│  ├─ Request/Response Formats
│  └─ Error Codes
│
└─ 06-DATA-MODELS.md (To be created)
   ├─ Domain Models
   ├─ Node Types
   └─ Workflow DSL

Level 4: GUIDES
├─ 07-DEVELOPER-GUIDE.md (To be created)
│  ├─ Local Setup
│  ├─ Creating Nodes
│  └─ Testing
│
├─ 08-DEPLOYMENT-GUIDE.md (To be created)
│  ├─ Docker/Kubernetes
│  ├─ Configuration
│  └─ Monitoring
│
└─ 09-OPERATIONS-MANUAL.md (To be created)
   ├─ Day-to-Day Ops
   ├─ Troubleshooting
   └─ Performance Tuning
```

---

## Key Concepts

### What is Drift?

Drift is a **Temporal-powered, low-code visual workflow orchestration platform** that enables:
- Visual workflow design using a node-based system
- Reliable, fault-tolerant workflow execution via Temporal
- Extensible architecture with pluggable node types
- Multi-tenancy and horizontal scalability
- Durable state management

### Core Components

1. **API Service** - REST API layer for workflow management
2. **Worker Service** - Temporal worker executing workflow logic
3. **Temporal Cluster** - Distributed workflow engine
4. **HBase** - Persistent storage for workflows and context
5. **Redis** - Caching layer for hot data

### Node Types

- **HTTP Node** - Execute HTTP API calls
- **Groovy Node** - Run dynamic Groovy scripts
- **Branch Node** - Conditional flow control
- **Instruction Node** - Generate UI forms and wait for input
- **Processor Node** - Data transformation
- **Success/Failure Nodes** - Workflow termination

---

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

---

## Technology Stack Summary

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
| Containerization | Docker + Kubernetes | Deployment |

---

## Getting Help

### Internal Resources
- **Slack Channel**: #drift-platform
- **Confluence**: [Drift Wiki](#)
- **JIRA**: [DRIFT Project](#)

### External Resources
- [Temporal Documentation](https://docs.temporal.io/)
- [Dropwizard Documentation](https://www.dropwizard.io/en/stable/)
- [HBase Documentation](https://hbase.apache.org/book.html)

---

## Contributing to Documentation

### Document Standards
- Use Markdown format
- Include code examples
- Add diagrams for complex concepts
- Keep navigation consistent
- Update TOC when adding sections

### Diagram Standards
- Use ASCII art for simple diagrams
- Use Mermaid for complex flows
- Ensure diagrams are readable in text editors

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0.0 | 2025-11-21 | Initial comprehensive documentation | AI Assistant |

---

## Next Steps

To complete the documentation suite, the following documents need to be created:

1. **05-API-CONTRACTS.md** - Detailed API specifications
2. **06-DATA-MODELS.md** - Complete data model reference
3. **07-DEVELOPER-GUIDE.md** - Developer onboarding guide
4. **08-DEPLOYMENT-GUIDE.md** - Production deployment instructions
5. **09-OPERATIONS-MANUAL.md** - Operations and troubleshooting guide

**Priority**: Create API Contracts next as it's essential for client integration.

---

**Last Updated**: November 21, 2025
**Maintained By**: Drift Platform Team

