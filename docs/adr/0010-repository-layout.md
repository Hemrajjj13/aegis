# ADR-0010: Repository Layout and Workspace Separation

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** —  
**Superseded by:** —  

---

## Context

Project Aegis has transitioned from documentation-first specifications into implementation. The repository houses a multi-module Java 21 backend, a React/TypeScript operator console frontend, container topologies, OpenAPI 3.1 contracts, system documentation, and architecture verification tooling.

Leaving all modules and build files at the repository root created conflation between backend build boundaries, frontend dependencies, container artifacts, and operational scripts. Specifically:
- Gradle wrapper files and module directories polluted the repository root.
- Docker compose and container files were located inconsistently across the root and subdirectories.
- Build artifacts (`.gradle`, `build`, `node_modules`) required complex root-level ignore rules.

A clean, predictable, top-level layout is required to support autonomous coding agents and human engineers alike without ambiguity.

## Decision

Reorganize the Project Aegis repository into decoupled, top-level functional directories:

```text
aegis/
├── README.md               # Primary project introduction and quickstart
├── AGENTS.md               # Prime directives and constraints for coding agents
├── DESIGN.md               # Design principles and architectural guidelines
├── .gitignore              # Repository-wide ignore rules
├── .gitattributes          # Line-ending and attribute definitions
├── .editorconfig           # Editor indentation and encoding standard
├── backend/                # Gradle root for all Java 21 backend modules
│   ├── settings.gradle.kts # Subproject declarations
│   ├── build.gradle.kts    # Root plugin and dependency configurations
│   ├── gradle.properties   # JVM and Gradle daemon parameters
│   ├── gradlew / gradlew.bat / gradle/ # Gradle wrapper binaries and scripts
│   ├── aegis-core          # Pure domain models and deterministic EventClock
│   ├── aegis-storage       # PostgreSQL persistence, Flyway migrations, and RLS
│   ├── aegis-decision-api  # Synchronous POST /v1/decisions and SSE stream feed
│   ├── aegis-app           # Executable Spring Boot application runner
│   └── (module shells)     # velocity, rules, replay, identity, audit, dataset-importer
├── frontend/               # React operator and analyst console SPA
│   └── README.md           # Console architecture and setup guide
├── api/
│   └── openapi.yaml        # Authoritative OpenAPI 3.1 specification
├── infra/
│   ├── docker-compose.yml  # Tier 0 multi-container orchestration
│   ├── docker/             # Dockerfiles (Dockerfile.backend)
│   └── tier0/              # Local initialization scripts (init-scripts/)
├── docs/                   # Architectural specifications and governance registers
│   └── archive/            # Historical founding and superseded document suites
└── tools/                  # Verification and linting scripts (Node.js)
```

### Operational Rules:
1. **Backend Operations:** Gradle builds are invoked from `backend/`:
   ```bash
   cd backend && ./gradlew build
   ```
2. **Infrastructure Operations:** Docker Compose is invoked with an explicit path:
   ```bash
   docker compose -f infra/docker-compose.yml --profile core up -d
   ```
3. **Frontend Operations:** Frontend development is invoked from `frontend/`:
   ```bash
   cd frontend && npm run dev
   ```

## Consequences

- **Clarity of Scope:** Backend, frontend, infrastructure, contracts, and documentation are cleanly isolated.
- **Acyclic Isolation:** Backend Gradle tasks never inspect or traverse frontend `node_modules` or `dist/` folders.
- **Container Path Explicit:** Build context and volume mounts in `infra/docker-compose.yml` explicitly reference repository structure without root ambiguity.
- **Agent Context Efficiency:** AI coding agents can localize file inspection and command execution strictly within the relevant subsystem.
