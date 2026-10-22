# Local Development Environment (Tier 0) Specification

**Document:** 21  
**Status:** Approved (docs v1.0, assumption-based)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026  

---

## 1. Purpose and System Context

This document defines the architecture, Docker Compose topology, resource allocation budgets, startup sequence, seed data, and operational instructions for the **Project Aegis Tier 0 local development environment**.

Per Decision [D1](file:///c:/dev/aegis/docs/00-decisions/decision-register.md) and [ADR-0006](file:///c:/dev/aegis/docs/adr/0006-deployment-tiers.md), Tier 0 provides a fully functional, self-contained single-workstation environment enabling the founder and AI coding agents to build, run, debug, and test the entire Aegis platform locally without requiring cloud infrastructure, paid SaaS dependencies, or third-party telephony APIs.

### 1.1 Installed Hardware Baseline and Strict Memory Budget
- **Installed Physical RAM (Measured):** **7.79 GB** (8,367,243,264 bytes physical memory, measured via Win32 system telemetry).
- **Decision D6 Memory Limit:** Tier 0 total container and process allocations must strictly remain $\le 50\%$ of physical RAM:
  $$\text{Budget Limit} \le 3.89\text{ GB } (3,988\text{ MB})$$
- **Budget Compliance:** The default `core` Compose profile is engineered to consume **3,072 MB** ($\approx 3.0\text{ GB}$), leaving an operational headroom of **916 MB** for local IDEs, background agent tools, and host operating system buffers.

---

## 2. Tier 0 Service Architecture and Profiles

Docker Compose services are segmented into three operational profiles to prevent unnecessary memory consumption:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Docker Compose Profiles & Footprint                  │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Profile: `core` (Default — Mandatory for Development)               │
│    - PostgreSQL 16 (Relational DB & RLS)          :  512 MB limit      │
│    - Redis 7 (Velocity Sorted Sets & Lua)         :  256 MB limit      │
│    - Kafka KRaft (Single-Broker Message Bus)      :  768 MB limit      │
│    - Spring Boot Monolith (`aegis-monolith`)      : 1280 MB limit      │
│    - React Vite Console (`apps/console`)          :  256 MB limit      │
│    Subtotal Core Budget                           : 3,072 MB (Pass)    │
├────────────────────────────────────────────────────────────────────────┤
│ 2. Profile: `observability` (Opt-in for Alert & Telemetry Testing)     │
│    - Prometheus (Metrics scraper)                 :  256 MB limit      │
│    - Grafana (Telemetry dashboard)                :  256 MB limit      │
├────────────────────────────────────────────────────────────────────────┤
│ 3. Profile: `tools` (Opt-in for Local S3 & Topic Inspection)           │
│    - MinIO / LocalStack (S3 Cold Replay Bucket)   :  256 MB limit      │
│    - Kafka UI (Browser-based topic browser)       :  256 MB limit      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Container Resource Allocations and Compose Topology

The following configuration specifies the complete `docker-compose.yml` service topology for Tier 0:

```yaml
version: "3.8"

services:
  # =========================================================================
  # 1. PostgreSQL 16 (Database with Row-Level Security)
  # =========================================================================
  postgres:
    image: postgres:16-alpine
    container_name: aegis-postgres-tier0
    profiles: ["core"]
    environment:
      POSTGRES_DB: aegis_db
      POSTGRES_USER: aegis_user
      POSTGRES_PASSWORD: aegis_local_password
      PGDATA: /var/lib/postgresql/data/pgdata
    command: >
      postgres
        -c shared_buffers=128MB
        -c max_connections=50
        -c work_mem=4MB
        -c maintenance_work_mem=32MB
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infra/tier0/init-scripts:/docker-entrypoint-initdb.d
    deploy:
      resources:
        limits:
          memory: 512M
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aegis_user -d aegis_db"]
      interval: 5s
      timeout: 3s
      retries: 5

  # =========================================================================
  # 2. Redis 7 (In-Memory Atomic Sliding Windows & Lua)
  # =========================================================================
  redis:
    image: redis:7-alpine
    container_name: aegis-redis-tier0
    profiles: ["core"]
    command: >
      redis-server
        --maxmemory 192mb
        --maxmemory-policy volatile-lru
        --save ""
        --appendonly no
    ports:
      - "6379:6379"
    deploy:
      resources:
        limits:
          memory: 256M
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  # =========================================================================
  # 3. Kafka with KRaft (Single-Broker Event Streaming without Zookeeper)
  # =========================================================================
  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: aegis-kafka-tier0
    profiles: ["core"]
    ports:
      - "9092:9092"
      - "29092:29092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:29093'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_LOG_DIRS: '/tmp/kraft-combined-logs'
      CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk'
      KAFKA_JVM_PERFORMANCE_OPTS: "-Xms256m -Xmx512m -XX:+UseG1GC"
    deploy:
      resources:
        limits:
          memory: 768M
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 10s
      timeout: 5s
      retries: 5

  # =========================================================================
  # 4. Spring Boot Backend Monolith (Decision Engine & APIs)
  # =========================================================================
  backend:
    build:
      context: .
      dockerfile: ./infra/tier0/Dockerfile.backend
    container_name: aegis-backend-tier0
    profiles: ["core"]
    environment:
      SPRING_PROFILES_ACTIVE: tier0
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/aegis_db
      SPRING_DATASOURCE_USERNAME: aegis_user
      SPRING_DATASOURCE_PASSWORD: aegis_local_password
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      JAVA_TOOL_OPTIONS: "-Xms256m -Xmx768m -XX:+UseG1GC"
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 1280M
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep UP"]
      interval: 10s
      timeout: 5s
      retries: 6

  # =========================================================================
  # 5. React Vite Console (Frontend Management Portal)
  # =========================================================================
  console:
    build:
      context: ./apps/console
      dockerfile: ../../infra/tier0/Dockerfile.console
    container_name: aegis-console-tier0
    profiles: ["core"]
    ports:
      - "3000:3000"
    depends_on:
      backend:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 256M

volumes:
  postgres_data:
    name: aegis_tier0_postgres_data
```

---

## 4. Deterministic Startup Sequence and Health Verification

To prevent race conditions, connection timeouts, and dirty startup crashes, Tier 0 enforces a strictly staged dependency DAG:

```
Step 1: Storage Layer (Parallel)
  ├──► PostgreSQL 16 (Health: pg_isready)
  └──► Redis 7       (Health: redis-cli ping)
         │
         ▼
Step 2: Message Bus
  └──► Kafka KRaft   (Health: kafka-topics list)
         │
         ▼
Step 3: Flyway Migrations & Monolith
  └──► Backend JVM   (Runs V1..V9 Flyway scripts; Health: /actuator/health)
         │
         ▼
Step 4: Presentation
  └──► Console SPA   (Vite server mounted on port 3000)
```

---

## 5. Seed Data and Default Operational Entities

Upon initial startup with the `tier0` profile, the application runs `V99__tier0_seed_data.sql` to populate a fully populated sandbox tenant:

### 5.1 Seed Organization
- **Tenant ID:** `11111111-1111-1111-1111-111111111111`
- **Tenant Name:** `Acme Fintech Sandbox`
- **Environment:** `SANDBOX`

### 5.2 Seed User Accounts (One per Role)
All seed users share the standard local development password: `DevPassword123!#` (hashed with Argon2id in `user_credentials`).

| Role | Name | Email | Mobile (E.164) | Default Landing Page |
|---|---|---|---|---|
| `owner` | Ananya Patel | `ananya.owner@acme.example` | `+919876500001` | `/settings` |
| `risk_manager` | Vikram Sharma | `vikram.manager@acme.example` | `+919876500002` | `/rules` |
| `risk_analyst` | Priya Nair | `priya.analyst@acme.example` | `+919876500003` | `/cases` |
| `developer` | Arjun Mehta | `arjun.dev@acme.example` | `+919876500004` | `/decisions` |
| `auditor` | Rahul Verma | `rahul.auditor@acme.example` | `+919876500005` | `/audit` |

*Seed TOTP Note:* For all seed accounts, the RFC 6238 Base32 secret is seeded as `JBSWY3DPEHPK3PXP`. Developers can use standard authenticator apps or the dev test generator in `aegis-identity`.

### 5.3 Baseline Ruleset (`rs_card_ecommerce_v1`)
Active version `1.0.0` containing 3 pre-compiled CEL rules:
1. `RUL-VEL-001` (Soft Rule, Weight: 30.0): Velocity $> 3$ txns in 60 seconds on the same account hash.
2. `RUL-AMT-002` (Soft Rule, Weight: 25.0): Transaction amount $> \text{₹}50,000.00$.
3. `RUL-GEO-003` (Hard Override, Verdict: `DECLINE`): Cross-border transaction originating from sanction-listed IP country code.

---

## 6. How to Run: Developer Operations Guide

### 6.1 Prerequisites
- Docker Engine $\ge 24.0$ with Docker Compose v2.
- Java 21 LTS (OpenJDK / Eclipse Temurin).
- Node.js $\ge 20.0$ and `npm` $\ge 10.0$.
- Git.

### 6.2 Starting the Backing Infrastructure Only (Recommended for Coding)
To maximize available host RAM and enable fast hot-reloading inside the IDE:
```bash
# 1. Start backing storage and Kafka KRaft (Total footprint: ~1.54 GB)
docker compose --profile core up -d postgres redis kafka

# 2. Verify all backing containers are healthy
docker compose ps
```

### 6.3 Running the Backend Locally
```bash
# Execute Spring Boot monolith with Tier 0 profile
./gradlew bootRun --args='--spring.profiles.active=tier0'
```
- API Base URL: `http://localhost:8080`
- OpenAPI Swagger UI: `http://localhost:8080/swagger-ui.html`
- Healthcheck: `http://localhost:8080/actuator/health`

### 6.4 Running the Frontend Console Locally
```bash
cd apps/console
npm install
npm run dev
```
- Console URL: `http://localhost:3000`

### 6.5 Running the Entire Stack in Containers
```bash
docker compose --profile core up -d
```

### 6.6 Running the Concurrency and Determinism CI Suite Locally
```bash
# Runs Testcontainers suite including 50-thread velocity limit concurrency test
./gradlew test --tests "*ConcurrencyTest" --tests "*EquivalenceTest"
```
