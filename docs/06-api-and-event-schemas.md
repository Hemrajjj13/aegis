# API and Event Schemas

**Document:** 06  
**Status:** Draft-complete (docs v1.0, machine-checked, not yet reviewed)
**Owner:** Founder / Lead Architect (Decision [DR-DOC-01](file:///c:/dev/aegis/docs/00-decisions/decision-register.md))  
**Last updated:** October 2026

> **Assumption note:** every field name, type constraint, and topic name in
> this document is a starting hypothesis. Any field marked with
> `[interview-driven]` may change based on what pilot customers' systems
> actually produce. Do not implement the API against this spec until at
> least one design partner has reviewed the event schema.

---

## 1. Purpose

This document specifies:
- The OpenAPI 3.1 contract for the synchronous Decision API
- The Avro schemas for the `ingest` and `decisions` Kafka topics
- Idempotency and error model conventions
- Rate-limit headers
- Schema evolution and compatibility rules

The authoritative machine-readable OpenAPI 3.1 specification for all Project Aegis endpoints is maintained at [/api/openapi.yaml](file:///c:/dev/aegis/api/openapi.yaml) (per ADR-0009). It is continuously validated by `tools/check-openapi.mjs`.

---

## 2. OpenAPI 3.1 Specification Pointer

The complete OpenAPI 3.1 contract for all Aegis REST, SSE, and Management APIs (including `POST /v1/decisions`, `GET /v1/events/stream`, case queue operations, ruleset simulation and lifecycle, replay engine, dataset import, cryptographic audit, and identity/auth endpoints) is specified in:

👉 **[`/api/openapi.yaml`](file:///c:/dev/aegis/api/openapi.yaml)**

### Key Endpoint Groups Defined in `/api/openapi.yaml`:
- **Ingestion & Decisions:** `POST /v1/decisions`, `GET /v1/decisions/{decision_id}`
- **Real-Time Feeds:** `GET /v1/events/stream` (Server-Sent Events)
- **Case Review Queue:** `GET /v1/cases`, `GET /v1/cases/{case_id}`, `POST /v1/cases/{case_id}/assign`, `POST /v1/cases/{case_id}/resolve`
- **Rulesets & Simulation:** `GET /v1/rulesets`, `POST /v1/rulesets`, `GET /v1/rulesets/{id}`, `PUT /v1/rulesets/{id}`, `POST /v1/rulesets/{id}/simulate`, `POST /v1/rulesets/{id}/submit`, `POST /v1/rulesets/{id}/approve`, `POST /v1/rulesets/{id}/promote`, `POST /v1/rulesets/{id}/rollback`
- **Replay & Backtest Engine:** `POST /v1/replays`, `GET /v1/replays/{id}`, `GET /v1/replays/{id}/report`
- **Dataset Import:** `POST /v1/datasets/import`, `GET /v1/datasets/import/{id}`
- **Audit & Cryptographic Verification:** `GET /v1/audit/chains`, `GET /v1/audit/verify`
- **Identity & Authentication:** `POST /v1/auth/login`, `POST /v1/auth/mfa/challenge`, `POST /v1/auth/mfa/verify`, `POST /v1/auth/refresh`, `POST /v1/auth/logout`, `GET /v1/auth/me`, `POST /v1/auth/password/reset-request`, `POST /v1/auth/password/reset`, `GET /v1/auth/users`, `POST /v1/auth/users/invite`, `GET /v1/auth/api-keys`, `POST /v1/auth/api-keys`

---

## 3. Rate Limits

Decision [DR-API-10](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Rate limits at launch are established per deployment tier: Tier 0 unlimited local; Tier 1 pilot 500 req/s sustained (1,000 req/s burst); Tier 2 production 2,500 req/s sustained (5,000 req/s burst per tenant).

| Limit | Scope | Default | Configurable per tenant? |
|-------|-------|---------|--------------------------|
| Requests per second | Per tenant API key | 200 req/s | Yes (up to contracted limit) |
| Burst | Per tenant API key | 500 req in a 5-second window | Yes |
| Daily volume | Per tenant | Contracted | Yes |

Rate-limit errors (HTTP 429) include a `Retry-After` header with seconds
until the window resets. Clients must implement exponential backoff with
jitter. Idempotency keys make retrying safe.

---

## 4. Kafka Schemas (Avro)

Schema registry: Confluent Schema Registry (or AWS Glue Schema Registry).
Compatibility mode: **FULL** (forward- and backward-compatible) for all
topics. Schema changes that break compatibility require a new topic version.

### 4.1 Ingest topic: `aegis.{tenant_id}.ingest.v1`

The Kafka topic for asynchronous event submission. Kafka key:
`tenant_id:account_id_hash` (for ordering per entity, see ADR-0001).

```json
{
  "type": "record",
  "name": "IngestEvent",
  "namespace": "com.aegis.schema.v1",
  "doc": "Transaction event submitted for asynchronous decision",
  "fields": [
    {"name": "schema_version", "type": "string", "default": "1.0"},
    {"name": "event_id",        "type": "string", "doc": "UUID"},
    {"name": "event_time",      "type": "long",   "logicalType": "timestamp-millis",
     "doc": "Event time in milliseconds since epoch"},
    {"name": "received_time",   "type": "long",   "logicalType": "timestamp-millis",
     "doc": "Time Aegis received the event. Not used in rule evaluation."},
    {"name": "sequence_number", "type": "long"},
    {"name": "tenant_id",       "type": "string", "doc": "UUID"},
    {"name": "account_id_hash", "type": ["null", "string"], "default": null},
    {"name": "card_token",      "type": ["null", "string"], "default": null},
    {"name": "amount_minor_units", "type": "long"},
    {"name": "currency",        "type": "string"},
    {"name": "payment_method",  "type": {"type": "enum", "name": "PaymentMethod",
      "symbols": ["card","upi","wallet","netbanking","bnpl","other"]}},
    {"name": "merchant_id",     "type": ["null", "string"], "default": null},
    {"name": "merchant_category_code", "type": ["null", "string"], "default": null},
    {"name": "merchant_name",   "type": ["null", "string"], "default": null},
    {"name": "channel",         "type": ["null", {"type": "enum", "name": "Channel",
      "symbols": ["web","mobile_app","pos","ivr","api","other"]}], "default": null},
    {"name": "ip_address",      "type": ["null", "string"], "default": null},
    {"name": "device_id",       "type": ["null", "string"], "default": null},
    {"name": "user_agent",      "type": ["null", "string"], "default": null},
    {"name": "upi_vpa",         "type": ["null", "string"], "default": null},
    {"name": "custom_fields",   "type": {"type": "map", "values": "string"}, "default": {}}
  ]
}
```

**Compatibility notes:**
- Adding a new optional field (union with null, default null) is backward- and forward-compatible.
- Removing a field, changing a field type, or adding a required field breaks compatibility and requires a schema version bump.
- The `custom_fields` map absorbs pilot-customer-specific fields during integration without a schema change.

### 4.2 Decisions topic: `aegis.{tenant_id}.decisions.v1`

Published by the outbox relay after each decision is committed. Kafka key:
`decision_id`. Consumers must be idempotent on `decision_id`.

```json
{
  "type": "record",
  "name": "DecisionEvent",
  "namespace": "com.aegis.schema.v1",
  "doc": "Decision produced by the Aegis evaluation core",
  "fields": [
    {"name": "schema_version",       "type": "string", "default": "1.0"},
    {"name": "decision_id",          "type": "string", "doc": "UUID"},
    {"name": "event_id",             "type": "string", "doc": "UUID"},
    {"name": "tenant_id",            "type": "string", "doc": "UUID"},
    {"name": "outcome",              "type": {"type": "enum", "name": "Outcome",
      "symbols": ["approve","decline","review"]}},
    {"name": "score",                "type": "float"},
    {"name": "ruleset_version_hash", "type": "string"},
    {"name": "latency_ms",           "type": "int"},
    {"name": "degraded",             "type": "boolean"},
    {"name": "degraded_reason",      "type": ["null", "string"], "default": null},
    {"name": "decided_at",           "type": "long", "logicalType": "timestamp-millis"},
    {"name": "rules_fired",          "type": {
      "type": "array",
      "items": {
        "type": "record",
        "name": "FiredRule",
        "fields": [
          {"name": "rule_id",     "type": "string", "doc": "UUID"},
          {"name": "rule_name",   "type": "string"},
          {"name": "reason_code", "type": "string"},
          {"name": "weight",      "type": "float"},
          {"name": "fired",       "type": "boolean"}
        ]
      }
    }, "default": []}
  ]
}
```

---

## 5. Error Model Conventions

| Code | HTTP status | Retry? | Notes |
|------|-------------|--------|-------|
| VALIDATION_ERROR | 400 | No | Fix the payload |
| PAN_DETECTED | 400 | No | Remove PAN-like data; see doc 01 §5.1 |
| IDEMPOTENCY_CONFLICT | 409 | No | Payload mismatch for same event_id |
| CLOCK_SKEW | 400 | No | Fix originating system clock |
| TENANT_MISMATCH | 403 | No | tenant_id in payload ≠ authenticated tenant |
| AUTHENTICATION_FAILED | 401 | No | Refresh token or check API key |
| AUTHORIZATION_FAILED | 403 | No | Scope missing |
| RATE_LIMITED | 429 | Yes, after Retry-After | Use exponential backoff with jitter |
| SERVICE_UNAVAILABLE | 503 | Yes, after Retry-After | Use same Idempotency-Key |

**Never parse the `message` field programmatically.** It is for human
operators and may change without notice.

---

## 6. SDK and Integration Guidance

Decision [DR-API-11](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): Tier 0 and Tier 1 ship with Java and TypeScript/Node.js SDKs generated from OpenAPI. Python and Go SDKs are deferred to customer discovery.

- The SDK must set the `Idempotency-Key` header automatically from the
  `event_id` in the request body.
- The SDK must implement exponential backoff with jitter for 429 and 503
  responses, using the same `Idempotency-Key` on retry.
- The SDK must validate `event_time` is within the allowed clock-skew
  window before sending, to give a clear client-side error.

---

## 7. Schema Evolution Policy

| Change type | Allowed without version bump? | Process |
|-------------|------------------------------|---------|
| Add optional field (null union, null default) | Yes | Add to schema, update docs |
| Rename a field | No | Deprecate old field (keep both), add new field, remove old after migration window |
| Change field type | No | New topic version + migration |
| Remove a field | No | Deprecate (keep as null union), remove after all consumers are updated |
| Add enum value | Yes, for forward compatibility | Add to schema; existing consumers with unknown enum values must handle gracefully |
| Remove enum value | No | Treat as field removal |

Decision [DR-API-12](file:///c:/dev/aegis/docs/00-decisions/decision-register.md): A 90-day deprecation notice with backward-compatible dual-write / expand-and-contract support is required for any breaking schema modification.

---

## 8. Dataset Import API and Schema Mapping Specification (Decision D8)

The free backtest offer is a core go-to-market feature enabling prospective customers to evaluate Aegis against historical data without synchronous production integration (Decision D8). Real customer datasets may only enter through this import pipeline after passing Gate G1 (Decision D2).

### 8.1 Endpoints Specification

#### 1. Initiate Dataset Import (`POST /v1/datasets/import`)
Initiates an import session and returns a secure, presigned S3 upload URL.

- **Request Headers:**
  - `Authorization: Bearer <jwt_or_api_key>`
  - `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "filename": "q3_2026_transactions.csv",
    "file_format": "CSV",
    "estimated_row_count": 250000,
    "column_mapping": {
      "source_columns": {
        "txn_id": "event_id",
        "timestamp_utc": "event_time",
        "amount": "amount",
        "curr": "currency",
        "user_token": "account_id_hash",
        "terminal_mcc": "mcc",
        "pay_method": "payment_method",
        "is_fraud_label": "ground_truth_fraud"
      },
      "timestamp_format": "ISO_8601",
      "currency_default": "INR"
    }
  }
  ```
- **Response (HTTP 201 Created):**
  ```json
  {
    "import_id": "imp_98124ef1-77b3-46aa-a901-49b8120d88ef",
    "tenant_id": "ten_01918a22-21fa-7100-88df-bc8819001122",
    "status": "PENDING_UPLOAD",
    "upload_method": "PUT",
    "upload_url": "https://aegis-backtest-data.s3.ap-south-1.amazonaws.com/ten_01918a22/imp_98124ef1/raw.csv?X-Amz-Signature=...",
    "expires_at": "2026-10-22T10:35:00Z",
    "retention_expires_at": "2026-11-21T10:20:00Z"
  }
  ```

#### 2. Trigger Validation Job (`POST /v1/datasets/import/{import_id}/validate`)
Triggers asynchronous parsing, schema validation, and security sanitization of the uploaded file.

- **Response (HTTP 202 Accepted):**
  ```json
  {
    "import_id": "imp_98124ef1-77b3-46aa-a901-49b8120d88ef",
    "status": "VALIDATING",
    "message": "Validation job scheduled. Poll GET /v1/datasets/import/{import_id} for progress."
  }
  ```

#### 3. Get Import Status & Validation Report (`GET /v1/datasets/import/{import_id}`)
Returns validation metrics, ground-truth label counts, and error summaries.

- **Response (HTTP 200 OK):**
  ```json
  {
    "import_id": "imp_98124ef1-77b3-46aa-a901-49b8120d88ef",
    "status": "READY",
    "filename": "q3_2026_transactions.csv",
    "file_format": "CSV",
    "total_rows": 248910,
    "valid_rows": 248900,
    "invalid_rows": 10,
    "ground_truth_stats": {
      "total_labeled": 248900,
      "fraud_count": 1820,
      "non_fraud_count": 247080,
      "base_fraud_rate_pct": 0.731
    },
    "validation_report": {
      "pan_scan_status": "PASSED_ZERO_PAN_DETECTED",
      "timestamp_range": {
        "min_event_time": "2026-07-01T00:00:02Z",
        "max_event_time": "2026-09-30T23:59:45Z",
        "span_days": 91.99
      },
      "sample_errors": [
        {
          "line_number": 4120,
          "column": "amount",
          "raw_value": "N/A",
          "error": "Numeric amount required; failed to parse float"
        }
      ]
    },
    "retention_expires_at": "2026-11-21T10:20:00Z"
  }
  ```

#### 4. Delete / Crypto-Shred Imported Dataset (`DELETE /v1/datasets/import/{import_id}`)
Immediately purges raw and processed files from S3 and marks database record as `PURGED`.
- **Response (HTTP 204 No Content)**

---

### 8.2 Column-to-Event-Schema Mapping Specification

| Aegis Canonical Field | Data Type | Mandatory / Optional | Description & Constraints |
|---|---|---|---|
| `event_id` | String | **Mandatory** | Unique identifier of historical transaction in customer system. |
| `event_time` | ISO-8601 / Epoch Ms | **Mandatory** | Transaction timestamp; determines time-window evaluation during replay. |
| `amount` | Float / Decimal | **Mandatory** | Positive numeric transaction value in standard units (e.g. 1500.50). |
| `currency` | String (ISO 4217) | Optional (Def: `INR`) | Transaction currency code (e.g., `INR`, `USD`). |
| `account_id_hash` | String | **Mandatory** | Cryptographic hash or token representing account / card / VPA. |
| `mcc` | String (4 digits) | Optional | Merchant Category Code (e.g., `5411`, `7995`). |
| `merchant_id` | String | Optional | Merchant identifier or merchant terminal token. |
| `payment_method` | String | Optional | Channel: `UPI`, `CARD`, `NETBANKING`, `WALLET`. |
| `device_id` | String | Optional | Hashed device identifier or fingerprint token. |
| `ip_address` | String | Optional | IPv4 or IPv6 address (country extracted automatically). |
| `ground_truth_fraud` | Boolean / Int | Optional (Recommended)| Known fraud outcome (`1`/`true` for confirmed fraud / chargeback, `0`/`false` for genuine). |

---

### 8.3 Customer Anonymisation Guidance

Before prospective customers upload historical datasets for a backtest, they must apply the following anonymisation procedures:

1. **Strictly Zero Raw Card Numbers (PANs):**
   - Raw 13–19 digit PANs must NEVER be included in any CSV or Parquet column.
   - Replace PANs with merchant/gateway tokens or masked representations (first 6 digits BIN + last 4 digits only).
   - Any uploaded file containing Luhn-valid 13–19 digit strings will be rejected immediately by the automated validation scanner.
2. **Pseudonymisation of Identifiers:**
   - Account numbers, phone numbers, email addresses, and National IDs (Aadhaar, PAN tax numbers) must be hashed client-side before export:
     $$\text{account\_id\_hash} = \text{HMAC-SHA256}(\text{account\_number}, \text{customer\_secret\_salt})$$
   - Consistent salting within a dataset is required so temporal velocity features (e.g. transactions per 24 hours) can be reconstructed deterministically.
3. **PII Stripping:**
   - Exclude customer names, billing addresses, free-form customer support notes, and recipient names from exported columns.
4. **Isolated Storage and Automatic Retention:**
   - All uploaded datasets are stored in dedicated per-tenant S3 prefixes encrypted with tenant-specific KMS keys.
   - Imported datasets are retained for a maximum of **30 days** before automated hard deletion, unless the customer signs a pilot SOW (Gate G2) and requests an extended retention window.
