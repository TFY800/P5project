# Second-Hand Auction Platform

## Goal and current state

A Programming 5 course application where sellers list second-hand items, buyers place timed bids, and viewers see accepted price updates without refreshing. The local basic version is implemented and verified as described in [verification](verification.md). The original design-stage text remains in [history](history/skeleton-2026-11-03/docs/project-description.md).

## Implemented scope

- Registration/login/current-user profile; unique normalized email and BCrypt hashes.
- Publish/browse/view/edit owned items, using HTTP(S) image URLs.
- Immediately starting auctions with positive integer HUF prices, minimum raises and future deadlines.
- Seller ownership and item freezing; recoverable creation with stable IDs.
- Transactional, concurrent bidding with server deadlines and idempotent retries.
- Background expiry, correct winner or unsold state, persisted data and restart recovery.
- Transactional outbox retry and auction-scoped WebSocket pushes.
- English responsive client with search, publish workflow, details, history, selling/participated/won views, session login, connection states and recovery.

A user may act as both seller and buyer, but cannot bid on their own auction. The seller comes from the verified JWT, not the request body. Whole HUF amounts are bounded at 10^12. An auction with no bids is not sold; its displayed current price is the starting price, not a winning bid.

## Course requirements

| Requirement | Implementation |
| --- | --- |
| Four independently built/running services | Four Maven Wrapper projects, ports 8081–8084 |
| At least one client | Plain HTML/CSS/JS on fixed HTTP port 8080 |
| Real-time communication | Subscription-scoped WebSocket hints and authoritative snapshot recovery |
| Persisted data | Separate PostgreSQL databases and Flyway migrations |
| Markdown description and diagrams | Current docs, Mermaid architecture and sequence/state diagrams |
| Testable business rules | Real PostgreSQL concurrency/recovery HTTP suite and client synchronization tests |

## Limits

No payments, shipping, private chat, file upload, proxy bidding, email verification, password reset or complex roles. No Docker, message broker, registry or gateway. One successful auction per item, including ended auctions; create a new item for another auction. No manual auction cancellation. Persistent PREPARING requests require fixing an unavailable service/configuration; they are retried automatically.

The client stores a one-hour JWT in sessionStorage per tab. This is susceptible to same-origin script access and does not offer refresh tokens or server-side logout/revocation. Auctions and bidder UUIDs are public, but emails are not in auction events. Image URLs load external resources and may be unavailable or reveal the viewer's IP to the image host; credentials/referrers are not sent by the application. Choose trusted image hosts.

The local version has no production rate limiting, automated outbox retention, email deliverability verification, operational dashboard or load-test guarantee. HMAC and internal shared secrets are intended for one developer's loopback setup. Public deployment needs further security/operations work and HTTPS.

## Demonstration

Register in two separate tabs. Publish an item and auction as seller. Open its details as buyer, place a valid bid, then a higher one and observe the seller's price/history update. Use a short deadline to observe sold/unsold results. Stop Notification, submit a bid, then restart Notification: the committed price remains and subscribers resynchronize. Automated tests also demonstrate rejected bids, ownership checks, parallel bids, idempotency and restart recovery.
