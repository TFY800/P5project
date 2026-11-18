# Architecture decisions

## ADR 1 — Four independent services and database ownership

**Accepted.** User handles identity; Item owns descriptions and freezing; Auction owns price, bids and finalization; Notification owns transient subscriptions. Each service has its own POM, Wrapper and process. User/Item/Auction have separate PostgreSQL databases and non-superuser roles. No shared entity module or cross-database joins.

Benefit: the boundaries satisfy the course requirement and keep authority explicit. Cost: cross-service item freezing needs a recovery workflow instead of a distributed database transaction. Notification can run without a database because HTTP auction state is authoritative.

## ADR 2 — Serialize each auction with a PostgreSQL row lock

**Accepted.** `AuctionTransactions.bid()` and `end()` both acquire `PESSIMISTIC_WRITE` on the same auction row. Check the server clock after acquiring it. Bid insertion, highest price, leader, monotonically increasing event version and outbox insertion commit together. Database constraints enforce positive amounts, per-auction versions and bidder/idempotency-key uniqueness.

This is easier to explain than optimistic retries and appropriate for a course project's scale. Different auctions can proceed concurrently. A hot auction serializes; this is a deliberate throughput limit. End time is not determined by a browser clock, scheduler punctuality or an HTTP request's arrival time.

## ADR 3 — Durable reservation before freezing an item

**Accepted.** Save PREPARING with seller, item, immutable creation parameters and idempotency key. A unique partial index prevents competing non-failed reservations for the same item. Call the authenticated Item freeze endpoint with the auction ID. Item locks its own row and repeats the same freeze safely. Activation stores the frozen description snapshot.

Definitive ownership/not-found/already-frozen rejection marks FAILED; transient HTTP/configuration failures leave PREPARING for periodic recovery and restart recovery. If a timeout occurs after freezing, retry the same ID until activation, instead of guessing the remote outcome and unfreezing. There is no manual cancellation in this version. A successful or ended auction permanently owns the item; relisting requires a new item. Stuck configuration failures require fixing configuration, not a new creation key.

## ADR 4 — Outbox and snapshots instead of a broker

**Accepted.** Business transactions persist outbox events. A dispatcher claims a small batch using PostgreSQL `FOR UPDATE SKIP LOCKED`, calls the authenticated notification endpoint with bounded timeouts, and marks delivered or schedules exponential retries capped at 60 seconds. A crash after remote delivery but before marking can duplicate an event, so delivery is at least once, not exactly once.

Notification sends only to sessions subscribed to that auction. Offline clients miss push messages. Each event contains UUID eventId, auctionId and a monotonic auction version. The frontend treats events only as hints and reads a repeatable-read HTTP snapshot; it ignores stale snapshots/events, coalesces reads and repeats a read if an event arrives in flight. After every resubscribe acknowledgement it fetches state again. Ten-second polling is a fallback if a final event is lost with no later version gap. This makes push loss recoverable without giving WebSocket authority to accept bids.

## ADR 5 — Local authentication and browser storage

**Accepted for local coursework.** BCrypt cost 12 stores password hashes; UTF-8 passwords are limited to 72 bytes. HS256 JWTs expire after one hour and require issuer/audience/subject/expiry validation. Mutations derive user IDs from the verified subject. Internal API credentials are distinct from JWT credentials and checked separately; a normal bearer token cannot call internal endpoints.

The tab keeps the token in sessionStorage, never in URLs or logs. Same-origin JavaScript can read it, logout is not server-side revocation, and tabs do not share login automatically. Public auctions and WebSocket subscriptions require no account; publishing/bidding/personal pages do. All local servers bind loopback. Before public deployment, add HTTPS, asymmetric keys or stronger service identity, abuse protection, key rotation/revocation and operational monitoring. No production security claim is made.
