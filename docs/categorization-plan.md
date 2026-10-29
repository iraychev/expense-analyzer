# Transaction categorization: implementation plan

## Why

Findings from the current code and the sample bank data (`src/main/resources/cached_transactions_*.json`):

- Sync categorizes every new transaction with its own blocking Gemini call, in sequence, inside the request.
- The vendor regex `авт\.код:\d+-([A-Za-z\s]+)` keeps only Latin letters and spaces, so one merchant yields several keys (`hk bar gyueshevo`, `hk gyueshevo k`, `sof`).
- Transactions without a match all use the key `Unknown Vendor`; the first AI answer is cached for all of them (e.g. a salary and an insurance premium share a category).
- `vendor_category_mapping.vendor` has no unique constraint; concurrent syncs can create duplicates and `findByVendor` then throws.
- Any failure (timeout, 429, parse error) and a genuine "Other" answer are both stored as `"Other"` and share one retry counter.
- The category list has no income/transfer categories.
- De-duplication compares `description` only, so a repeated purchase with an identical description is silently dropped. The banks provide a unique `transactionId` that we do not store.
- GoCardless allows **4 transaction fetches per account per day**, but delete/finalize/sync flows each trigger a full fetch.

## Target pipeline

```
bank transaction ──► parse ──► categorize (cheapest first) ──► store
                     │          1. user override (per user + merchant)
                     │          2. merchant cache (exact key)
                     │          3. deterministic rules (type codes, keywords)
                     │          4. AI, batched per distinct merchant
                     ▼
        merchant key, direction (income/expense/transfer), bank type code
```

Stored per transaction: `externalId`, `merchant`, `category`, `categoryStatus`, `categorySource`, `categorizerVersion`.

`categoryStatus`: `PENDING` -> `CATEGORIZED` | `FAILED` (retryable) | `UNCATEGORIZABLE` (model/rules had no answer).
`categorySource`: `USER` | `RULE` | `CACHE` | `AI`.

## Phases

Each phase is its own branch/PR and leaves the app working.

### Phase 0 - Protect the GoCardless quota (prerequisite)
- Add `lastSyncedAt` per bank account; skip the remote fetch if synced less than N hours ago (default 6h) and return stored data.
- Delete no longer triggers a sync. Finalize does exactly one sync.
- Manual "Sync" returns a clear message when throttled ("Last synced 2h ago, next sync available in 4h").
- Map GoCardless 429 to a user-readable error.

### Phase 1 - Correctness fixes
- New column `transactions.external_id` (bank `transactionId`, fall back to `internalTransactionId`); unique per `bank_account_id`.
- Sync ignores transactions whose `external_id` already exists. For existing rows without an id, fall back to a multiset match on (date, amount, description) so old data is not duplicated; backfill `external_id` when a match is found.
- Never cache under the placeholder `Unknown Vendor`.
- Unique index on `vendor_category_mapping.vendor` (+ normalize/merge existing duplicates in a migration).
- Tests: repeated identical purchases are both kept; re-sync is idempotent.

### Phase 2 - Parser, categories, layered pipeline
- `TransactionParser` interface with per-bank implementations (UniCredit, Revolut, DSK, generic fallback):
  - output: canonical merchant key, direction, bank type code, redacted text for the AI.
  - UniCredit: text after `авт.код:\d+-` up to the first `/`, keeping Cyrillic and digits; non-card rows use `creditorName`/`debtorName` and `bankTransactionCode`.
  - Revolut: `creditorName` / `debtorName`, `CARD_PAYMENT` / `TRANSFER`.
  - Redact masked PANs, IBANs, terminal ids and the account holder's own name.
- Categories become data (table or enum with i18n-free keys). Add: Salary, Other income, Transfer in, Transfer out, Fees. Keep the current expense categories.
- `CategorizationPipeline` runs the layers in order; rules cover transfer/fee/salary type codes and a small keyword list.
- Gemini call uses a response schema constrained to the category list; model id pinned (no `-latest` alias), configurable.
- Golden test: run the three cached JSON files through the pipeline with a stubbed AI and a hand-labelled sample; assert merchant keys and rule coverage.

### Phase 3 - Async categorization
- Sync stores new transactions as `PENDING` and returns immediately.
- `CategorizationWorker` (scheduled + triggered after sync):
  - claims `PENDING` rows in batches (`FOR UPDATE SKIP LOCKED`),
  - groups by merchant key, resolves via layers 1-3, sends the remainder to AI in batches of ~25 distinct merchants,
  - bounded concurrency, exponential backoff on 429/5xx, circuit breaker after repeated failures.
- Failure handling: transient error -> stays `PENDING`/`FAILED` with `nextRetryAt`; after max retries -> `FAILED` (shown as "Uncategorized", retried by the nightly job). Model says unknown -> `UNCATEGORIZABLE`.
- API: transaction DTO exposes `categoryStatus`; `GET /transactions` stays cheap so the UI can poll while any row is `PENDING`.
- UI: "categorizing..." chip on pending rows; quiet re-fetch every few seconds while any are pending (stops when none).

### Phase 4 - Overrides and retries
- `PUT /transactions/{id}/category` with `applyToMerchant` flag; stored as a per-user rule (layer 1), source `USER`.
- Nightly job retries `FAILED` (backoff) and `UNCATEGORIZABLE` (rarely); also re-runs rows whose `categorizerVersion` is older than the current one (bumped when rules/prompt change) and rows whose merchant gained a new cache/rule mapping.
- Metrics/logging: counts by source and status per run.

## Out of scope / later
- Promoting user overrides into the global cache after several users agree.
- Multi-instance coordination beyond `SKIP LOCKED`.
- Accuracy dashboard (the golden test is the first measurement).

## Open questions
- Throttle window for sync (proposal: 6h).
- Whether `FAILED` rows should show a manual "retry" action in the UI.
