# Deployment and Infrastructure as Code (IaC) Guide

**Document:** 14  
**Status:** Draft  
**Owner:** [Decision DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md#DR-DOC-01) (Founder / Lead Architect)  
**Last updated:** October 2026  

> **Assumption note:** Cloud configurations, instance types, and cost projections in this document represent baseline engineering estimates for a 2,000 decisions/second launch target. Every financial figure is marked as an "estimate, to validate". Clean-room reproduction and blue/green failover timings are hypotheses to validate during staging drills.

---

## 1. Purpose and Architecture Goals

This document establishes the infrastructure architecture, Terraform IaC layout, deployment automation, and operational cost model for Project Aegis.

The deployment infrastructure is designed around four operational imperatives:
1. **India Data Localisation Invariant:** All infrastructure, primary data, backups, and encryption keys are strictly hosted in AWS India (`ap-south-1` Mumbai primary, with `ap-south-2` Hyderabad reserved for disaster recovery evaluation; see [doc 01 §6.1](file:///c:/dev/aegis/docs/01-production-readiness-discovery.md#L234-L265)).
2. **Clean-Room Reproducibility:** The entire production or staging stack can be provisioned from clean-room Terraform scripts in under 45 minutes (hypothesis, to validate).
3. **Zero-Downtime Blue/Green Deployments:** Application containers deploy without interrupting real-time payment decision streams.
4. **Least-Privilege Secret Isolation:** No plain-text secrets exist in repositories, container images, or CI environment variables.

---

## 2. Environments

Aegis maintains three strictly isolated environments:

| Environment | Purpose | Target Audience | Data Type | Infrastructure Footprint |
|---|---|---|---|---|
| **Sandbox** | Customer integration testing, partner API validation | External developer teams | 100% Synthetic data only | Single-AZ, burstable instances, shared RDS/Redis |
| **Staging** | Pre-production testing, load benchmarks, chaos drills | Internal engineering, QA, Security | Synthetic load-test datasets | Multi-AZ, 100% parity with production instance sizes |
| **Production** | Live transaction decision processing | Regulated financial institutions | Live tokenised payment transactions | Multi-AZ, dedicated RDS/Redis/MSK, strict change control |

### 2.1 Deployment Tiers Across the Unified Codebase (Decision D1)

Aegis is architected to deploy across three distinct tiers from a single, unified codebase (Decision D1):

| Tier | Name | Target Environment | Footprint & Topology | Operational Role & Cost |
|---|---|---|---|---|
| **Tier 0** | Developer Laptop | Local Docker Compose | Single-node Compose (Postgres 16, Redis 7, Kafka KRaft); decision-api, stream-processor, rules-service, replay-engine in ONE Spring Boot process (Decision D7). No Schema Registry, Keycloak, or observability stack by default (Decision D6). | Development & CI; Memory budget $\le 50\%$ RAM (max 3.89 GB on an 8 GB developer machine; measured 7.79 GB total RAM via Win32 API). **$0.00 / month**. |
| **Tier 1** | Pilot Deployment | AWS Mumbai (`ap-south-1`) | Single-AZ minimal footprint: 2x EC2/ECS tasks, single RDS Postgres (`db.t4g.medium`), single ElastiCache Redis (`cache.t4g.small`), Kafka KRaft on EC2 or 2-broker MSK. | Shadow mode only (asynchronous mirror traffic, zero blocking risk to customer checkout). Cost recorded as estimate: **~$450–$650 / month**. |
| **Tier 2** | Enterprise Production | AWS Mumbai (`ap-south-1`) | Multi-AZ (3 AZs): EKS managed cluster, Multi-AZ RDS Postgres with read replica, 3-shard Multi-AZ ElastiCache Redis (9 nodes), 3-AZ MSK Kafka, redundant ALBs. | Synchronous inline decision engine with p99 $\le 100\text{ ms}$ budget and 24/7 SRE pager. Cost: **~$6,940 / month (unverified placeholder, do not quote)**. |

---

## 3. AWS Layout in an India Region (`ap-south-1`)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ AWS VPC: 10.100.0.0/16 (Region: ap-south-1 Mumbai)                                      │
│                                                                                         │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Public Subnets (AZ-1a, AZ-1b, AZ-1c) — 10.100.1.0/24, 10.100.2.0/24, 10.100.3.0/24│  │
│  │ - Internet Gateway                                                                │  │
│  │ - NAT Gateways (3x redundant across AZs)                                          │  │
│  │ - AWS Application Load Balancers (ALB) + AWS WAF                                  │  │
│  └────────────────────────────────────────┬──────────────────────────────────────────┘  │
│                                           │                                             │
│                                           ▼                                             │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Private Compute Subnets (EKS Worker Nodes) — 10.100.10.0/22, 10.100.14.0/22       │  │
│  │ - decision-api pods (HPA: 6 to 24 pods)                                           │  │
│  │ - stream-processor pods (Kafka consumers)                                         │  │
│  │ - rules-service, console-api, identity-service pods                               │  │
│  │ - replay-engine (ephemeral batch jobs)                                            │  │
│  └────────────────────────────────────────┬──────────────────────────────────────────┘  │
│                                           │                                             │
│                                           ▼                                             │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Private Isolated Data Subnets (Persistence Layer) — 10.100.20.0/22, 10.100.24.0/22│  │
│  │ - Amazon RDS PostgreSQL 16 (Multi-AZ Primary + Hot Standby + Read Replica)       │  │
│  │ - Amazon ElastiCache Redis 7 (Cluster Mode, 3 Shards x 2 Replicas)                 │  │
│  │ - Amazon MSK Managed Kafka (3 Brokers across AZ-1a, 1b, 1c)                       │  │
│  └───────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                         │
│  ┌───────────────────────────────────────────────────────────────────────────────────┐  │
│  │ AWS VPC Gateway & Interface Endpoints (Private Network Isolation)                  │  │
│  │ - S3 Gateway Endpoint (Zero internet NAT traversal for audit logs & WAL archives)  │  │
│  │ - Secrets Manager, KMS, ECR, CloudWatch Interface Endpoints                       │  │
│  └───────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Network Security Groups and Ingress Rules
- **Public ALB:** Accepts inbound HTTPS (443) from public internet (filtered by AWS WAF). Egress restricted strictly to Private Compute subnets on port 8080.
- **EKS Worker Nodes:** Accepts ingress strictly from ALB security group. Outbound traffic to internet routes through NAT Gateways for external webhooks.
- **Data Subnets:** Complete denial of direct internet access. PostgreSQL (5432), Redis (6379), and Kafka (9094) accept connections strictly from the EKS worker node security group.

---

## 4. Terraform Infrastructure as Code (IaC) Layout

Infrastructure is modularized and version-controlled under `infrastructure/terraform/`:

```
infrastructure/terraform/
├── modules/
│   ├── vpc/                    # Multi-AZ VPC, subnets, NAT Gateways, route tables
│   ├── endpoints/              # S3 gateway, KMS, Secrets Manager VPC endpoints
│   ├── security_groups/        # Least-privilege ingress/egress rules
│   ├── eks_cluster/            # EKS managed node groups, OIDC provider, IAM roles
│   ├── rds_postgres/           # Multi-AZ PostgreSQL, parameter groups, pg_partman
│   ├── elasticache_redis/      # Redis cluster, TLS, auth, parameter groups
│   ├── msk_kafka/              # Amazon MSK cluster, SASL/SCRAM, storage auto-scaling
│   ├── s3_audit_worm/          # S3 buckets with Object Lock (Compliance mode)
│   └── kms_keys/               # Customer Managed Keys (CMKs) per tenant/environment
└── environments/
    ├── sandbox/
    │   ├── main.tf
    │   ├── terraform.tfvars
    │   └── backend.tf          # S3 remote state: aegis-tf-state-sandbox
    ├── staging/
    │   ├── main.tf
    │   ├── terraform.tfvars
    │   └── backend.tf          # S3 remote state: aegis-tf-state-staging
    └── production/
        ├── main.tf
        ├── terraform.tfvars
        └── backend.tf          # S3 remote state: aegis-tf-state-production
```

### 4.1 State Management and Locking
- Terraform remote state is stored in an encrypted, versioned Amazon S3 bucket (`aegis-tf-state-<env>-ap-south-1`).
- Concurrency locking is enforced via an Amazon DynamoDB table (`aegis-tf-lock-<env>`).
- State access is restricted to CI/CD IAM execution roles; direct developer access is prohibited.

---

## 5. CI/CD Pipeline Architecture

```
Developer Commit
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Continuous Integration (GitHub Actions)                                  │
│ - Spotless code format & Checkstyle                                         │
│ - ArchUnit test: Ban System.currentTimeMillis() in evaluation-core         │
│ - Unit Tests & CEL Compiler validation                                      │
│ - Integration Tests (Testcontainers: Concurrency, Idempotency, Equivalence) │
│ - Trivy Container Vulnerability Scan (Zero CRITICAL CVEs)                   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼ (Images pushed to AWS ECR with Git SHA)
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. Automated Staging Deployment                                             │
│ - Run Flyway DB schema migration against staging PostgreSQL                 │
│ - Deploy Helm charts to EKS staging cluster                                 │
│ - Automated Smoke Tests & Replay Determinism Verification                   │
│ - Automated k6 15-minute load test (2,000 TPS baseline)                      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. Production Deployment Gate (Human Approval Required)                     │
│ - Maker-Checker Gate: Engineering Lead + Security Reviewer Sign-off         │
│ - Flyway migration review and pre-apply snapshot                            │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. Production Blue/Green Deployment                                         │
│ - Deploy new revision (Green) alongside active revision (Blue)              │
│ - Health checks verify green pods (readiness probes pass)                   │
│ - Traffic shifted: 10% -> 50% -> 100% over 5 minutes                        │
│ - Automated rollback on error budget breach                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Blue/Green Deployment Strategy

To ensure zero downtime on the synchronous decision API during code updates, deployments use **Blue/Green target group switching** via AWS ALB and Kubernetes (Argo Rollouts or native deployment canary):

1. **Green Environment Provisioning:** The new container version (`Green`) is deployed to the EKS cluster. Pods initialize, run Spring Boot warmup routines, load active rulesets into memory, and establish database pools.
2. **Readiness Verification:** Synthetic health-check requests verify that `Green` pods evaluate decisions within < 25 ms.
3. **Canary Shift (10% Traffic):** 10% of incoming payment gateway requests are routed to `Green`. CloudWatch monitors latency and error rates for 3 minutes.
4. **Full Cutover (100% Traffic):** If `Green` error rate is < 0.05% and p99 latency is ≤ 100 ms, 100% of traffic is switched to `Green`.
5. **Automated Rollback:** If the `Green` revision emits any HTTP 5xx errors exceeding 0.05% or p99 latency breaches 150 ms, the ALB immediately shifts 100% of traffic back to `Blue` in < 5 seconds.
6. **Blue Decommissioning:** `Blue` pods remain warm for 15 minutes post-cutover before being safely scaled down.

---

## 7. Secrets Management

All sensitive values (database credentials, Redis auth passwords, Kafka SASL tokens, tenant HMAC master keys) are managed through **AWS Secrets Manager** and integrated into EKS using the **External Secrets Operator (ESO)**.

```
┌────────────────────────────┐
│ AWS Secrets Manager        │
│ (AES-256 KMS CMK Encrypted)│
└─────────────┬──────────────┘
              │ Syncs every 1 hour via VPC Endpoint
              ▼
┌────────────────────────────┐
│ External Secrets Operator  │
│ (EKS in-cluster controller)│
└─────────────┬──────────────┘
              │ Reconciles to
              ▼
┌────────────────────────────┐
│ Kubernetes Secret (Opaque) │
│ (Mounted as tmpfs volume)  │
└────────────────────────────┘
```

- **Zero Git Secrets:** Pre-commit hooks (`gitleaks`) and GitHub Secret Scanning prevent accidental credential commits.
- **Filesystem Mounts over Environment Variables:** Secrets are mounted as in-memory volumes (`/etc/secrets/`) rather than environment variables, preventing exposure in process crash dumps or `/proc/*/environ`.
- **Automated Rotation:** PostgreSQL master credentials rotate automatically every 90 days via an AWS Lambda rotation function without application downtime.

---

## 8. Database Migration Process with Human Review

Database schema migrations are executed using **Flyway**, governed by a strict backward-compatible protocol:

### 8.1 The "Expand and Contract" Rule
Schema changes must never break currently running application instances (`Blue`).
- **Phase 1 (Expand):** Add nullable columns, new tables, or new indexes. Deploy code that writes to both old and new columns.
- **Phase 2 (Migrate):** Backfill historical data asynchronously in background batches.
- **Phase 3 (Contract):** Once all traffic is on the new code version, drop old columns or constraints in a subsequent release.

### 8.2 Production Migration Execution Protocol
1. **Migration Script Review:** Flyway script `V{version}__{description}.sql` is submitted via Pull Request. Requires explicit review and sign-off by the Database Reviewer.
2. **Staging Validation:** The migration script runs in the staging pipeline against a staging database clone. Execution time is recorded. Any migration taking > 10 seconds is rejected and must be refactored into concurrent/non-locking steps.
3. **Pre-Deployment Backup:** An automated RDS snapshot is triggered immediately prior to applying migrations in production.
4. **Migration Runner:** An ephemeral Kubernetes Job executes `flyway migrate` using the dedicated `app_migration_user` role *before* the application container rollout commences.

---

## 9. Infrastructure Cost Model Across Deployment Tiers (Decision D1)

### 9.1 Tier 0: Developer Laptop Cost Model
- **Cost:** **$0.00 / month**.
- Runs locally on the developer's workstation via Docker Compose.
- Budget: $\le 50\%$ installed RAM (measured machine: 7.79 GB total RAM; Tier 0 envelope $\le 3.89\text{ GB}$).
- Zero cloud expenditures.

### 9.2 Tier 1: Pilot Deployment Cost Model (AWS ap-south-1, Shadow Mode)
The Tier 1 pilot footprint operates in single-AZ shadow mode in AWS Mumbai (`ap-south-1`). It processes asynchronous mirrored transaction feeds without sitting in the synchronous payment blocking path.

> **Disclaimer:** All figures below are **clearly marked estimates, to validate** based on AWS India list pricing (October 2026).

| Component / Service | Sizing / Specification | Redundancy | Estimated Monthly Cost (USD) |
|---|---|---|---|
| **Compute (EC2 / ECS)** | 2x `t4g.xlarge` (4 vCPU, 16 GB RAM) running unified modular app container | Single AZ | **$98** *(estimate, to validate)* |
| **Amazon RDS PostgreSQL** | `db.t4g.medium` (2 vCPU, 4 GB RAM), 100 GB GP3 SSD | Single AZ with automated snapshots | **$72** *(estimate, to validate)* |
| **Amazon ElastiCache Redis** | `cache.t4g.small` (1.37 GB RAM), single node | Single AZ | **$28** *(estimate, to validate)* |
| **Kafka Ingest (KRaft on EC2)** | 1x `t4g.large` (2 vCPU, 8 GB RAM, 250 GB GP3) self-hosted KRaft single-broker | Single AZ | **$85** *(estimate, to validate)* |
| **AWS Application Load Balancer** | 1x ALB (External webhook/mirror ingest) | Single AZ | **$32** *(estimate, to validate)* |
| **AWS S3 & Backups** | S3 Standard (Backtest datasets + nightly snapshots, ~500 GB) | Multi-AZ storage | **$25** *(estimate, to validate)* |
| **NAT Gateway & Data Transfer** | 1x NAT Gateway + Data Transfer (~2 TB/month) | Single AZ | **$65** *(estimate, to validate)* |
| **AWS KMS, Secrets Manager, CloudWatch** | Basic monitoring, log retention (14 days), secrets | Standard | **$45** *(estimate, to validate)* |
| **TOTAL TIER 1 ESTIMATED MONTHLY COST** | | | **~$450 – $650 / month** *(clearly marked estimate, to validate)* |

### 9.3 Tier 2: Production Multi-AZ Managed Infrastructure Cost Model
The production model reflects a multi-AZ deployment in **AWS ap-south-1 (Mumbai)** sized for a sustained baseline of **2,000 decisions/second** with peak headroom up to 5,000 decisions/second.

> **Disclaimer:** Every line below is marked as an **unverified placeholder, do not quote**. Line items represent rough architectural estimates and require formal verification against AWS Cost Calculator and quote verification before quoting in financial or commercial discussions.

| Component / Service | Sizing / Specification | Redundancy | Monthly Cost (USD) — Status |
|---|---|---|---|
| **EKS Cluster & Compute** | EKS Control Plane + 6x `m6i.xlarge` instances (4 vCPU, 16 GB RAM) | Multi-AZ (3 AZs) | **$850** *(unverified placeholder, do not quote)* |
| **Amazon RDS PostgreSQL** | `db.r6g.2xlarge` (8 vCPU, 64 GB RAM), 1 TB GP3 SSD (10,000 IOPS) | Multi-AZ Primary + Standby + 1 Read Replica | **$2,250** *(unverified placeholder, do not quote)* |
| **Amazon ElastiCache Redis** | `cache.r6g.xlarge` (4 vCPU, 26 GB RAM), 3 Shards x 2 Replicas (9 nodes total) | Multi-AZ with Auto-Failover | **$2,227** *(unverified placeholder, do not quote)* |
| **Amazon MSK (Kafka)** | 3x `kafka.m5.large` brokers, 500 GB storage per broker | 3-AZ Broker Placement | **$680** *(unverified placeholder, do not quote)* |
| **AWS Application Load Balancer** | 2x ALB (Public Ingress + Internal BFF), WAF rules | Multi-AZ | **$200** *(unverified placeholder, do not quote)* |
| **AWS S3 & Backup Storage** | S3 Standard (Audit logs, WAL) + S3 Glacier (Archives) + RDS Backups (~5 TB total) | Multi-AZ with Object Lock | **$160** *(unverified placeholder, do not quote)* |
| **NAT Gateways & Data Transfer** | 3x NAT Gateways + Inter-AZ Data Transfer (~15 TB/month) | High Availability | **$260** *(unverified placeholder, do not quote)* |
| **AWS KMS, Secrets Manager, CloudWatch** | Secrets Manager APIs, CloudWatch Metrics/Logs, Custom KMS CMKs | High Availability | **$250** *(unverified placeholder, do not quote)* |
| **TOTAL TIER 2 ESTIMATED MONTHLY COST** | | | **~$6,877 / month** *(unverified placeholder, do not quote)* |

### 9.4 Unit Cost Economics
- At a sustained baseline of 2,000 decisions/second, Aegis processes approximately **5.18 billion decisions per month** ($2,000 \times 86,400 \times 30 = 5,184,000,000$).
- Estimated infrastructure cost per 1,000 transactions at Tier 2 capacity:
  $$\text{Unit Cost} = \frac{\$6,877}{5,184,000\text{ thousand txns}} \approx \mathbf{\$0.00133}\text{ per 1,000 transactions (unverified placeholder, do not quote)}$$
- Unit cost during Tier 1 pilot phase (processing ~100 TPS shadow traffic $\approx 259\text{M decisions/month}$):
  $$\text{Pilot Unit Cost} = \frac{\$550}{259,200\text{ thousand txns}} \approx \mathbf{\$0.00212}\text{ per 1,000 transactions (estimate, to validate)}$$

---

## 10. Architectural Decisions and Recorded Questions

| Decision ID | Question / Topic | Impact | Resolution & Link |
|---|---|---|---|
| [Decision DR-IAC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md#DR-IAC-01) | **[interview-driven] Reserved Instances / Savings Plans:** Should Aegis commit to a 1-year or 3-year AWS Compute Savings Plan immediately at launch? | Capital commitment vs. runway. | **Deferred:** Operate on-demand in Tier 1 pilot; commit upon Gate G2 commercial pilot contract. |
| [Decision DR-IAC-02](file:///c:/dev/aegis/docs/00-decisions/decision-register.md#DR-IAC-02) | **Secondary Region Hot Standby (ap-south-2):** Does any pilot customer require an active-passive hot standby deployed in Hyderabad? | Doubles infrastructure bills (~$8,500/mo). | **Decided:** Single region (`ap-south-1` Mumbai) for Tier 0/1; evaluate secondary region for Tier 2 enterprise. |
| [Decision DR-IAC-03](file:///c:/dev/aegis/docs/00-decisions/decision-register.md#DR-IAC-03) | **Kubernetes Distribution:** Should the team run vanilla Amazon EKS or evaluate serverless container platforms (ECS Fargate)? | Control plane toil vs. cold starts. | **Decided:** Compose for Tier 0; ECS Fargate / EC2 for Tier 1 pilot; vanilla Amazon EKS for Tier 2 enterprise. |
| [Decision DR-IAC-04](file:///c:/dev/aegis/docs/00-decisions/decision-register.md#DR-IAC-04) | **Self-Managed vs. Managed NAT Gateways:** At high outbound webhook volumes, AWS NAT Gateway fees exceed $500/month. | Staging budget optimization. | **Decided:** Lightweight `fck-nat` EC2 instance in staging/sandbox; AWS managed NAT Gateways in production. |
