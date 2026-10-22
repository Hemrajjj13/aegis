# ADR-0008: Dataset Import Capability for Free Backtests

**Status:** Accepted  
**Date:** October 2026  
**Deciders:** Founder / Engineering Lead  
**Supersedes:** Extends ADR-0003  
**Superseded by:** —  

---

## Context

A primary sales obstacle when selling real-time fraud engines to Indian fintechs and payment aggregators is risk aversion: prospective buyers hesitate to integrate a live API into their synchronous checkout flow without empirical proof that the ruleset outperforms their legacy fraud rules.

Project Aegis establishes a **Free Historical Backtest** as its primary go-to-market wedge (Decision D8). Prospective customers can benchmark Aegis against months of historical transactions to observe improved fraud catch rates, reduced false positives, and microsecond latency profiles.

However, prospective customer data cannot be injected directly into the live production database (`decisions` or `processed_events`) due to security, tenant isolation, and strict compliance boundaries (Decision D2: Gate G1). Furthermore, customer historical exports come in varied, non-standard tabular shapes (custom CSV and Apache Parquet formats with divergent header names).

## Decision

Project Aegis will provide an isolated **Dataset Import Capability** (Decision D8) governed by strict privacy controls:

### 1. Ingestion & Storage Architecture
- **Presigned Uploads:** Customers initiate imports via `POST /v1/datasets/import` and stream raw CSV or Parquet files directly to dedicated per-tenant S3 prefixes encrypted with customer KMS keys (`s3://aegis-backtest-data/{tenant_id}/{import_id}/raw`).
- **Metadata Tracking:** Import jobs are tracked in PostgreSQL in the `dataset_imports` table, enforced under Row-Level Security (RLS) to guarantee strict multi-tenant isolation.
- **Client-Side Anonymisation Guidance:** Customers must hash account numbers client-side (`HMAC-SHA256`) and strip customer names, contact info, and tax IDs before export.
- **Zero Raw PAN Policy:** The ingestion worker scans all free-text fields with a Luhn-validation filter; files containing raw 13–19 digit PANs are rejected immediately.

### 2. Column-to-Event Mapping & Validation
- **Dynamic Header Mapper:** Supports mapping arbitrary source column names to canonical Aegis fields (`event_id`, `event_time`, `amount`, `currency`, `account_id_hash`, `mcc`, `device_id`, and `ground_truth_fraud`).
- **Asynchronous Validation Daemon:** Parses files in streaming chunks, verifies schema types, checks timestamp ranges, and produces a structured `validation_report` JSON detailing row counts, parse errors, and fraud label distributions.

### 3. Replay Integration & Automated Retention
- **Isolated Replay Execution:** The replay engine reads directly from the validated dataset in S3, evaluating transactions in-memory using injected event-time clocks (`InMemoryVelocityWindow`) per ADR-0003 and ADR-0005. No live production tables are written or locked.
- **Automated 30-Day Purge:** Imported datasets and derived evaluation records are automatically deleted 30 days after creation via automated database cleanup and S3 lifecycle rules, satisfying DPDP Act 2023 minimisation principles unless an active pilot SOW is executed (Gate G2).

## Consequences

### Positive
- **High-Velocity Go-to-Market:** Empowers the founder to deliver compelling, zero-risk backtest comparison reports to prospects within 48 hours of receiving historical data.
- **Strict Compliance Safety:** Real customer data is strictly gated behind Gate G1 (10+ interviews, DPDP Act, data localisation, PCI scope, and DPA legal sign-offs complete per Decision D2).
- **Zero Production Degradation:** Replay jobs on imported datasets run on ephemeral workers reading isolated files, ensuring zero performance interference with live decision streams.

### Negative
- Supporting diverse CSV dialects (delimiters, quote characters, timestamp formats) requires robust parsing libraries (`univocity-parsers` or Apache Commons CSV) with defensive memory allocation bounds.
- Processing multi-gigabyte Parquet files requires streaming readers to avoid JVM heap exhaustion in containerized worker tasks.
