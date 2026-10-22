# ADR-0006: Deployment Tiers on a Unified Codebase

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** Extends ADR-0001  
**Superseded by:** —  

---

## Context

Project Aegis is built by one bootstrapped founder with AI coding agents. The product must balance two competing operational constraints:
1. **Developer Workstation Constraints:** The system must run smoothly on a standard low-RAM developer laptop (measured 7.79 GB total RAM on Windows workstation) for local feature development, fast debugging, and automated CI test execution without requiring cloud connectivity.
2. **Enterprise Production Demands:** High-throughput production deployments require a multi-AZ, highly available, scalable architecture across AWS managed services (RDS Multi-AZ, ElastiCache Redis Cluster, Amazon MSK) capable of processing 2,000 to 5,000 decisions/second with sub-100ms p99 latency.
3. **Pilot Customer Shadow Deployments:** Early design partner pilots require an isolated, cost-efficient cloud footprint in AWS Mumbai (`ap-south-1`) running in shadow mode (asynchronous transaction mirroring) with zero blocking risk to customer checkout flows, without incurring the ~$7,000/month cost of full multi-AZ enterprise infrastructure.

Operating multiple codebases or divergent configuration repositories for different environments leads to drift, untracked bugs, and excessive maintenance toil.

## Decision

Project Aegis will deploy across **three standardized deployment tiers from a single, unified codebase** (Decision D1, D6, D7):

### 1. Tier 0: Developer Laptop (Local Workstation)
- **Deployment Mechanism:** Single `docker-compose.yml` with Spring Boot application profile `dev`.
- **Packaging (Decision D7):** `decision-api`, `stream-processor`, `rules-service`, and `replay-engine` run as in-process Gradle modules of **ONE unified Spring Boot process** (`aegis-monolith`). `console-api` and in-house `identity-service` run in-process or via Vite dev server for React frontend.
- **Backing Services (Decision D6):** Lightweight core services only:
  - PostgreSQL 16 (single container with Flyway migrations)
  - Redis 7.2 (single container with preloaded Lua scripts)
  - Kafka 3.x in KRaft mode (single broker, zero Zookeeper)
- **Omitted by Default (Decision D6):** Confluent Schema Registry, Keycloak SSO, and full Prometheus/Grafana observability stack are excluded from default startup to conserve resources.
- **Resource Budget:** Strict hard memory envelope $\le 50\%$ of installed RAM (max 3.89 GB on an 8 GB developer machine; measured 7.79 GB total RAM via Win32 API).
- **Cost:** **$0.00 / month**.
- **Data Source (Decision D5):** Synthetic transaction generator (`tools/synthetic-generator`) for local test suites and CI.

### 2. Tier 1: Pilot Deployment (AWS ap-south-1, Shadow Mode)
- **Deployment Mechanism:** Minimal AWS cloud footprint in Mumbai (`ap-south-1`) provisioned via Terraform.
- **Packaging:** Modular Spring Boot containers running on AWS ECS / EC2.
- **Backing Services:** Single-AZ managed services:
  - Amazon RDS PostgreSQL (`db.t4g.medium` with automated nightly snapshots and WAL archiving)
  - Amazon ElastiCache Redis (`cache.t4g.small` single node)
  - Single-broker Kafka KRaft on EC2 or Amazon MSK Serverless / Express
  - 1x Application Load Balancer
- **Operational Mode:** Shadow mode only (Decision D1). Mirrored asynchronous customer traffic; decisions logged and compared to legacy rules, zero synchronous checkout path blocking.
- **Cost:** Recorded as a clearly marked estimate: **~$450 – $650 / month**.
- **Customer Data Gate (Decision D2):** Customer datasets enter ONLY through the dataset import tool (Decision D8) after passing **Gate G1** (10+ interviews, DPDP Act, data localisation, PCI scope, and DPA legal review complete).

### 3. Tier 2: Enterprise Production (Multi-AZ Managed Cloud)
- **Deployment Mechanism:** Multi-AZ Amazon EKS cluster spanning 3 Availability Zones in `ap-south-1` as defined in doc 12 and doc 14.
- **Packaging (ADR-0001):** Separately scaled Kubernetes Deployments for `decision-api`, `stream-processor`, `rules-service`, `console-api`, `identity-service`, and ephemeral Kubernetes Jobs for `replay-engine`.
- **Backing Services:** Full multi-AZ high availability:
  - Amazon RDS PostgreSQL (`db.r6g.2xlarge` Multi-AZ Primary + Standby + Read Replica)
  - Amazon ElastiCache Redis (3 shards x 2 replicas = 9 nodes `cache.r6g.xlarge`)
  - Amazon MSK (3-AZ `kafka.m5.large` brokers)
  - S3 Object Lock in Compliance mode (WORM)
- **Operational Mode:** Synchronous blocking decision engine (`POST /v1/decisions` with p99 $\le 100\text{ ms}$ budget) with 24/7 on-call multi-burn-rate pager rotation.
- **Cost:** Estimated at **~$6,877 / month (unverified placeholder, do not quote)**.

## Consequences

### Positive
- **Zero Codebase Fragmentation:** Core rule evaluation (`evaluation-core`), velocity Lua scripts, and database migrations are 100% identical across developer laptops, pilots, and enterprise clusters.
- **Rapid Developer Onboarding:** A developer or coding agent can run `docker compose up -d` and have a working end-to-end environment running in < 60 seconds within 3.89 GB RAM.
- **Safe Customer Pilots:** Pilot customers can evaluate rule performance against live traffic streams in shadow mode without availability risk to their payment gateways.
- **Frugal Runway Management:** A single founder can run pilots for ~$500/month rather than committing to $7,000/month enterprise infrastructure before revenue.

### Negative
- Single-process dev packaging (Decision D7) requires careful dependency management to prevent accidental tight coupling between modules before Tier 2 split. Enforced via ArchUnit tests.
- Single-broker KRaft in Tier 0 lacks schema registry validation; Avro schemas must be validated via build-time Gradle plugins in Tier 0.
