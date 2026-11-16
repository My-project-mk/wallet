# Transaction Ledger & Idempotent Transfer Service

A wallet transfer microservice with a double-entry ledger, exactly-once transfer semantics through idempotency keys,
and safe handling of concurrent transfers against the same wallet.

**Stack:** Java 17, Spring Boot 4, PostgreSQL 17, Flyway, Testcontainers.

## Running it

```bash
docker compose up --build
```

The service listens on `http://localhost:8080`, with PostgreSQL on `localhost:5432` (user, password and database are all `wallet`).
API docs are at `http://localhost:8080/swagger-ui/index.html` (OpenAPI JSON at `/wallet-service-docs`).

To run the tests (Docker must be running, because tests use a real PostgreSQL container):

```bash
./mvnw test
```

## API

All amounts are decimal **strings** with at most 2 fraction digits, for example `"50.00"`. JSON fields are `snake_case`.
Timestamps use the format `yyyy-MM-dd HH:mm:ss`. Every error returns the same body:

```json
{
  "http_status": 400,
  "user_message": "Sorry. Insufficient funds",
  "developer_message": "Insufficient funds in wallet 6638a021-…",
  "validation_errors": [{ "field": "amount", "message": "…" }]
}
```

`validation_errors` is only present when request validation fails.

### `POST /v1/wallets`: open a wallet

```json
{
  "idempotency_key": "5f1d8c2e-9b7a-4c3e-8a21-0d6f4b9e7c13",
  "currency": "KES",
  "initial_balance": "100.00"
}
```

`201 Created` returns `{ wallet_id, currency, balance, created_date }`. Supported currencies are `KES` and `USD`.
The opening balance is optional. If present, it is posted as a double-entry deposit from that currency's treasury account.

Wallet creation is idempotent, like transfers. Retrying with the same `idempotency_key` returns the wallet already
created (`201`, `Idempotent-Replayed: true`), so a double-click or network retry never opens or funds a second wallet.
The same key with a different body gives `409`; a missing key gives `400`. A missing `initial_balance` and `"0.00"`
count as the same request.

### `POST /v1/transfers`: transfer between wallets

```json
{
  "idempotency_key": "7b0e3f0e-6a52-4c39-9a3f-2a3f1b6b9c11",
  "source_wallet_id": "…",
  "destination_wallet_id": "…",
  "amount": "50.00",
  "currency": "KES"
}
```

| Situation                                                   | Response                                     |
|-------------------------------------------------------------|----------------------------------------------|
| Transfer executed                                           | `201`, `Idempotent-Replayed: false`          |
| Same key, same payload, already completed                   | `201` with the original body, `Idempotent-Replayed: true` |
| Same key, different payload                                 | `409 Conflict`                               |
| Same key while the original is still processing             | `425 Too Early` (retry shortly)              |
| Insufficient funds, invalid amount, currency mismatch, same wallet | `400 Bad Request`                     |
| Unknown wallet                                              | `404 Not Found`                              |

### `GET /v1/wallets/{id}/balance?limit=20`: balance and history

Returns `{ wallet_id, currency, available_balance, recent_entries[] }`, with entries newest first and a limit of 1–100.

## Architecture

```
TransferRestController ──> IdempotentTransferService ──> IdempotencyService   (claim / complete / release a key)
                                    │
                                    └──> TransferService ──> WalletRepository  (SELECT … FOR UPDATE)
                                                │
                                                └──> LedgerService            (the only code that moves money)

WalletRestController ──> IdempotentWalletService ──> IdempotencyService       (same key lifecycle)
                                    │
                                    └──> WalletService ──> LedgerService       (funds the opening balance)
```

```
src/main/java/com/java/wallet
├── web/                       REST controllers (WalletRestController, TransferRestController)
├── service/                   Service interfaces and their *Impl classes
├── repository/                Spring Data JPA repositories (JPQL only, no native queries)
├── model/
│   ├── entity/                BaseEntity, Wallet, Transfer, LedgerEntry, IdempotencyRecord
│   ├── datatype/              Enums: EntryType, TransferType, IdempotencyStatus, ClaimOutcome
│   ├── dto/                   Response bodies
│   └── requests/              Validated request bodies
└── utils/
    ├── converters/            DtoConverter: entity to response DTO
    └── helpers/               Money, RequestFingerprint
        └── exceptions/        BadRequest / ResourceNotFound / Conflict / TooEarly RestApiExceptions
            └── web/rest/      RestApiException, RestApiError, RestControllerAdvice
```

Entities have an internal numeric `id`. The API only ever exposes `public_id` UUIDs.

### Data model

| Table              | Purpose                                                                                       |
|--------------------|-----------------------------------------------------------------------------------------------|
| `wallets`          | Current balance. `CHECK (balance >= 0)` for customer wallets enforces the balance guard in the database. `idempotency_key` is `UNIQUE` (empty for treasury accounts). |
| `transfers`        | One row per money movement. `idempotency_key` is `UNIQUE`.                                    |
| `ledger_entries`   | Immutable DEBIT and CREDIT rows, each with `balance_after`.                                   |
| `idempotency_keys` | Key, SHA-256 of the canonical payload, status (`IN_PROGRESS` / `COMPLETED`) and lease timestamp. |

**Double entry.** `LedgerService.post()` debits one wallet, credits the other and writes both entries in the same
transaction. Opening deposits are debited from a per-currency **treasury** system account, the only kind of account
allowed to go negative. This keeps two invariants true at all times, and every integration test checks them after it runs:

- the sum of all wallet balances is `0`;
- total debits equal total credits.

**Precision.** Amounts are `BigDecimal` in Java and `NUMERIC(19,2)` in PostgreSQL, and are serialised as strings.
Floating point is never used. Input with more than 2 decimals is rejected rather than rounded.

**Balance.** The stored balance changes in the same transaction as the ledger entries, so it always equals the sum of
the committed entries. Under PostgreSQL's `READ COMMITTED`, readers only ever see finalized, settled transfers.
Reading a stored balance is O(1) and gives a single row to lock; summing the ledger on every read would not.

### Locking strategy: pessimistic

A transfer runs in one transaction that:

1. locks both wallets with `SELECT … FOR UPDATE`, **always in ascending wallet-id order** so that A→B and B→A
   running at the same time cannot deadlock;
2. checks currency and the balance guard against the freshly locked rows;
3. updates both balances, inserts the transfer and both ledger entries, and marks the idempotency key `COMPLETED`;
4. commits, which releases the locks.

Two simultaneous $50 debits on a $60 wallet therefore run one after the other: the first succeeds and the second sees
$10 and gets `400 Insufficient funds`. The brief's wording ("must result in two success transaction") conflicts with
its own "balance cannot drop below $0.00" rule. We kept the balance guard and serialised both requests through the
row lock.

**Why pessimistic rather than optimistic?** Optimistic locking (a `@Version` column) detects the conflict only at
commit. The losing request must then retry the whole transfer, and on a hot wallet (salary disbursement, a merchant
during a flash sale) most attempts fail and retry repeatedly. Pessimistic locking makes concurrent writers to the
same wallet queue on the row lock, and each one sees the real balance. Contention is limited to one or two rows, and
the transaction is short (a few indexed statements, with no I/O to external systems inside it), so locks are held
for milliseconds. The `CHECK (balance >= 0)` constraint still protects the balance if a bug ever skipped the lock.

`ConcurrentTransferRestControllerTests` covers this. With the `FOR UPDATE` removed, three of its four tests fail with
lost updates.

### Idempotency

Both `POST /v1/transfers` and `POST /v1/wallets` use the same key lifecycle:

1. **Claim**, in its own short transaction: `INSERT … ON CONFLICT DO NOTHING`. Exactly one concurrent request
   inserts the row and owns the key. The others lock the existing row and compare the payload hash:
   - a different hash gives `409`;
   - `COMPLETED` replays the stored transfer or wallet;
   - `IN_PROGRESS` gives `425`.
2. **Execute**: the transfer (or wallet creation) transaction. Marking the key `COMPLETED` happens **inside** that
   transaction, so the change and the "done" flag commit atomically. They can never disagree.
3. **Release on failure**: if the operation fails (for example with insufficient funds), the key is deleted.
   Nothing was written, so a retry is safe and is evaluated again.

The claim commits before the operation starts. That is what lets a concurrent duplicate see `IN_PROGRESS` instead
of blocking. As defence in depth, `transfers.idempotency_key` and `wallets.idempotency_key` are also `UNIQUE`.

The payload hash is computed over canonical values, so `"50"` and `"50.00"` count as the same request. Wallet and
transfer requests hash differently, so a key reused across the two endpoints gives `409`.

## Handling network timeouts

**Client ↔ service.** If the client times out, it cannot know whether the transfer happened. It must **retry with
the same `idempotency_key`**:

- if the original committed, the retry replays the original `201` with `Idempotent-Replayed: true`;
- if it rolled back, the key was released and the retry runs normally;
- if it is still running, the client gets `425` and retries with backoff.

The client never has to guess, and money is never moved twice.

**Service ↔ database.**

- **Atomic commit.** The transfer, both ledger entries and the `COMPLETED` flag are in one transaction. If the
  connection drops mid-transaction, PostgreSQL rolls all of it back. There is no state where money moved but the key
  is not marked done.
- **Bounded waits.** Each connection sets `lock_timeout = 5s`, and the transfer transaction has a 10s timeout. A
  request stuck behind a hung lock fails fast with an error instead of exhausting the connection pool.
- **Crash between claim and completion.** If the process dies, the key stays `IN_PROGRESS`. Each claim records a
  lease (`locked_at`). A retry after the 30s lease has expired may reclaim the key. This is safe because the lease is
  longer than the 10s transaction timeout: by then the original transaction has committed (key `COMPLETED`, so the
  retry replays it) or has been rolled back (no money moved, so the retry processes it).
- **Ambiguous commit.** The connection can drop after `COMMIT` was sent but before the acknowledgement arrived. The
  service then returns an error, but the transfer may have committed. The client's retry with the same key resolves
  this: it finds the key `COMPLETED` and replays the result.

## Tests

| Test class                             | Covers                                                                   |
|----------------------------------------|---------------------------------------------------------------------------|
| `TransferRestControllerTests`          | Happy path with balanced entries; insufficient funds (400); validation; currency mismatch; unknown or treasury wallet |
| `TransferIdempotencyRestControllerTests` | Replay returns the original response without re-processing; 409 on a changed payload; 425 while in progress |
| `ConcurrentTransferRestControllerTests` | 2×$50 on $60 gives one 201 and one 400; 25×$10 on $100; opposing transfers without deadlock; one key fired 10× concurrently |
| `IdempotencyServiceImplTests`          | Key lifecycle: claim, conflict, replay, release, reclaim after lease expiry |
| `WalletRestControllerTests`            | Wallet creation, funding from the treasury, balance and history          |
| `WalletIdempotencyRestControllerTests` | Retried wallet creation returns the same wallet and funds it once; 409 on a changed payload; 10 simultaneous duplicates create one wallet |
| `RequestFingerprintTests`              | Unit tests for transfer and wallet request fingerprinting                |

Integration tests extend `WalletApplicationTests`, which starts the service on a random port against a PostgreSQL
Testcontainer. They call the API with REST Assured, and each one checks the global ledger invariants after it runs.
