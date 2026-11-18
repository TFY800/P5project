# Coding conventions

- Java 21, UTF-8, four spaces. Packages start with `com.p5auction.<service>`.
- `controller`: HTTP routes, validated request DTOs, authenticated identity extraction, status codes. No database queries or bidding decisions.
- `service`: business rules, orchestration and transaction boundaries. Constructor injection only.
- `repository`: Spring Data JPA queries. Access only the service's own database.
- `entity`: JPA persistence models, never serialize directly to external clients. This small project uses explicit field access without Lombok; public persistence fields are confined to service/repository code. API output always uses records.
- `dto`: explicit request/response records and Bean Validation. Do not expose password hashes or copy caller-supplied identity into business ownership.
- `config`: security and outbound HTTP/WebSocket configuration. `exception`: consistent Problem Details without SQL, stack traces or credentials.
- Whole HUF uses `long` / PostgreSQL `bigint`, bounded at 10^12 per amount. JSON decimals are rejected. IDs are UUIDs; UTC `Instant` is used for stored deadlines and server decisions.
- Flyway is the only schema migration mechanism. Add a new version after a migration has run; never edit a deployed migration checksum. JPA validates schema rather than modifying it.
- Transaction rules are documented at the row lock, recovery record and outbox boundaries. Avoid line-by-line comments and unnecessary shared modules. Small security/error classes are intentionally duplicated so each service remains independently buildable.
- Never log bearer tokens or secrets. Configuration comes from environment variables; real `.env` is ignored. Do not commit `.idea`, `*.iml`, `target`, `.local`, `.build-cache`, macOS files or credentials.
- Frontend uses DOM construction and `textContent`, never user-controlled `innerHTML`. Image URLs are HTTP(S) only, with no embedded credentials and no referrer. No framework or build tool is required.
- HTTP errors use 400 validation, 401 missing/invalid login, 403 ownership, 404 missing record, 409 business/idempotency conflict. Creation returns 202 because durable PREPARING may outlive the request; item/user creation and bids use 201. Internal event intake returns 204.
- Retry cross-service mutations only with stable operation IDs. Connections have bounded connect/read timeouts. Never compensate an ambiguous freeze by unlocking an item that may already back an active auction.
- Before merging: independently build all services; execute real PostgreSQL integration tests and frontend synchronization tests; inspect the Git diff and manual browser workflow. No automatic commit/push.
