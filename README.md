# Second-Hand Auction Platform · SecondRound

Programming 5 course project. A locally runnable auction website with **four independent Java 21 / Spring Boot 4.1.2 Maven services**, PostgreSQL, JWT login, transactional bidding and WebSocket updates. The client is plain HTML/CSS/JavaScript.

## Run on macOS

Requires JDK 21, Python 3 and PostgreSQL binaries. No Docker or global Maven is needed.

**One click on Mac:** double-click [`Start.command`](Start.command) in this repository. It prepares the local database, builds only if necessary, starts the services, waits for health checks and opens the website. Double-click [`Stop.command`](Stop.command) to stop the application; PostgreSQL and all data are preserved. Existing `.env` is never overwritten. Startup works even when launched outside the repository directory.

```sh
cd /Users/shilo/Projects/P5project
# If PostgreSQL is not installed: builds a repository-local copy; requires Apple command line tools.
./scripts/bootstrap-postgres.sh
python3 scripts/dev.py db-init
python3 scripts/dev.py build
python3 scripts/dev.py start
```

Open **[http://localhost:8080](http://localhost:8080)**. Register your own local account. Use a second browser tab/account to bid. Do not use `file://` or another origin.

```sh
python3 scripts/dev.py status
python3 scripts/dev.py stop
python3 scripts/dev.py db-stop
```

The initializer creates `.env` with private random secrets **only if missing**, a local PostgreSQL cluster on 55432, and three databases/roles. It never drops databases or changes existing passwords. Logs, credentials, database files and Maven caches are ignored by Git. Review scripts before running. `build` stops only the JVMs it previously started before replacing their JARs; run `start` afterwards.

## Implemented

| Component | Port | Responsibility |
| --- | --- | --- |
| `user-service/` | 8081 | Registration, normalized unique email, BCrypt, one-hour HS256 JWT, current user |
| `item-service/` | 8082 | Item creation, paging, details, seller-only editing, idempotent freezing |
| `auction-service/` | 8083 | Recoverable creation, integer HUF bidding, row locks, idempotency, expiry/winners, outbox retries |
| `notification-service/` | 8084 | Authenticated internal event intake and auction-scoped public WebSocket subscriptions |
| `client/` | 8080 | Responsive English website, search, publishing, personal views, live details and reconnection |

Each backend builds independently with its own Maven Wrapper and POM. User, Item and Auction own separate PostgreSQL databases/accounts; there are no cross-database queries. Notification has no database. `/api/health` is a process-liveness endpoint, not a readiness or dependency check.

## Documentation

- [Local setup, scripts, secrets and IntelliJ IDEA](docs/local-development.md)
- [Scope and current limits](docs/project-description.md)
- [Architecture and Mermaid diagrams](docs/architecture.md)
- [HTTP / WebSocket API and recovery protocol](docs/communication.md)
- [Coding conventions](docs/coding-conventions.md)
- [Architecture decisions](docs/decisions.md)
- [中文代码导读](docs/code-walkthrough.md)
- [Automated and browser verification](docs/verification.md)
- [Original skeleton documentation, preserved verbatim](docs/history/skeleton-2026-11-03/README.md)

## Verification

```sh
# First start the databases and all four services using the commands above.
python3 tests/integration.py -v
# Optional JavaScript state-machine test; uses Node if installed, no npm dependencies.
node tests/live.test.mjs
```

Integration tests use the real PostgreSQL instance, real HTTP, concurrent requests and WebSocket clients. They create uniquely named test records, preserve existing data, and briefly restart managed services for recovery tests. Run only in your local development environment. Maven `verify` builds each service but does not by itself execute these cross-service tests. See the verification document for actual results and limitations.

## Limits

No payment, shipping, chat, proxy bidding, email verification or password reset. This course version permits one successful auction per item (including ended auctions); create a new item to relist. Pending creation is retried automatically; there is no cancellation UI. Tokens live in this tab's `sessionStorage`, which is readable by same-origin JavaScript; logout clears the browser copy but does not revoke an already issued token. Local HTTP and shared HMAC/internal secrets are for local coursework, not public deployment. No Docker, message broker, service registry, API Gateway or frontend framework is included.

No automatic commit or push is performed.
