# Verification record and acceptance steps

## Actual environment and result (2026-11-04)

Repository: `/Users/shilo/Projects/P5project`, macOS arm64, Temurin JDK 21.0.10, Spring Boot 4.1.2, Maven Wrapper 3.3.4 / Maven 3.10.0, PostgreSQL 17.6 compiled from official source into the ignored repository cache. Source SHA-256 was checked. No existing database was deleted or reset; no system package installation or sudo was required.

All four independent POMs completed Maven `verify`. They started on 8081–8084 with real PostgreSQL for User/Item/Auction, and each health endpoint responded. Flyway created/validated the real schemas. A successful build is not treated as business verification.

`python3 tests/integration.py -v` most recently completed **8 tests in 21.666 seconds, OK**. These are integration groups containing multiple assertions, not eight mocked unit scenarios. No H2 or other in-memory substitute is used.

| Automated group | Verified behavior |
| --- | --- |
| Authentication / DB isolation | Register/login/me; wrong password; case/whitespace-normalized duplicate email; invalid fields; no login; bad JWT signature; expired/missing-expiry/wrong-issuer/wrong-audience signed JWT; no password/hash response; stored BCrypt; service-role cross-database connections rejected |
| Item ownership/freeze | Owner edit succeeds; other user edit forbidden; internal calls reject ordinary clients; duplicate auction rejected; item frozen; item creation-key replay; unsafe/relative image URLs rejected; pagination |
| Creation recovery | Non-seller receives durable FAILED without freezing; past end time rejected; Item outage leaves PREPARING; restart resumes with the same freeze ID |
| Bid rules/idempotency | First bid minimum; minimum raise; seller bid forbidden; decimal amount rejected; missing idempotency header rejected; same key returns same bid; changed amount conflicts; personal endpoints protected |
| Real concurrent bids | 12 parallel HTTP bids through actual PostgreSQL locks; accepted versions/prices strictly increase; final maximum is correct; 8 same-key concurrent calls produce one bid |
| Expiry/restart | Stop Auction before deadline, restart after; saved bid remains; overdue sold/unsold auctions close; correct winner; late bid rejected; won listing correct |
| WebSocket/outbox | Two subscribed sockets receive same event ID; another auction's subscriber receives no event; Notification outage does not prevent bid commit; pending outbox persists, retries and clears after recovery; resubscription recovers snapshot |
| Lock wait crosses deadline | Hold the real auction row lock in a separate PostgreSQL connection; queued bid obtains lock after expiry and is rejected with no bid inserted |

The tests create uniquely named course-test accounts/items/auctions and never delete existing records. They briefly stop/restart only processes whose PID/start time/command match `dev.py` records. Do not run this recovery suite against a production environment or an unmanaged IDE session.

A separate full lifecycle check stopped every managed service and the repository PostgreSQL cluster, restarted the cluster and all services, and verified that the existing browser-test seller, 3,500 HUF price, auction version and three accepted bids were unchanged. The services and client were left running for local inspection.

## Client synchronization automated test

`tests/live.test.mjs` ran successfully using the available bundled Node runtime (Node is not needed to run the website). No npm dependencies are required. It exercises the real `client/live.js` with controlled sockets/read timing:

- subscribe acknowledgement followed by snapshot;
- version gaps trigger a read;
- duplicate and old events cannot lower state;
- stale HTTP snapshots cannot replace a higher version;
- an event received during an in-flight read forces a second read;
- reconnect resubscribes and recovers a newer snapshot.

If Node is available locally, reproduce with `node tests/live.test.mjs`. The Maven build does not automatically execute this JavaScript test or the cross-service Python suite.

## Actual browser verification

Using the actual in-app browser and two separate tabs with separate sessionStorage logins:

1. Registered Browser Seller and Browser Buyer via the website; both logged in through real User API calls.
2. Seller used the publication form to create the book-collection listing and auction. Detail page displayed the real image/description, ACTIVE and Live; seller bid button was disabled.
3. Buyer subscribed to the same auction and submitted 2,500 then 3,000 HUF. Seller's separate tab updated to 3,000 and two history entries without refresh.
4. Stopped Notification through the managed script. Both pages showed `Offline · reconnecting`.
5. Buyer submitted 3,500 HUF while Notification was offline; page showed `Bid accepted` and the committed current price.
6. Restarted Notification. Seller automatically returned to Live with 3,500 HUF and three history entries.
7. Reloading the detail page preserved login via sessionStorage and restored the authoritative state.
8. Homepage search returned the actual book auction and its price/countdown. My auctions showed the seller listing; My items showed its frozen fields as read-only. The same detail recovered to Live after the full database/process restart.

The browser displayed a 662px-wide actual viewport. The viewport override capability did **not** change the measured width to 390px, so a real 390px/mobile-device pass is **not claimed**. Responsive CSS breakpoints are implemented, but the phone acceptance below remains manual. Cross-browser Safari/Firefox behavior, high-volume load testing and public HTTPS deployment are also not verified.

## Reproduce

```sh
python3 scripts/dev.py db-init
python3 scripts/dev.py build
python3 scripts/dev.py start
python3 tests/integration.py -v
# Optional if Node is installed:
node tests/live.test.mjs
```

Build stops managed JVMs before replacing JARs. Start them again before running integration tests. Runtime/build logs remain under ignored `.local/` and `.build-cache/`; never publish `.env` or bearer tokens.

## Additional manual acceptance

- Open `http://localhost:8080` in two independent tabs/profiles, register separate users, and reproduce the publication/bidding flow. Check search/pagination and Selling/Bidding/Won/My items tabs.
- Use a short future deadline, place a bid and wait for the final sold message. Repeat without bids and confirm unsold. Countdown is a display, not acceptance authority.
- In My items, edit an unfrozen item as seller. Publish it and confirm later edit fails; another user must never be able to edit it.
- Simulate an uncertain network response after POST, then retry with the same details/key. No duplicate auction/bid should appear. Discarding a publish draft must leave any saved backend item visible.
- At 390×844 and a wider desktop size, check navigation wrapping, readable cards, publication fields, bid input/button and history with no horizontal scrolling. Use actual browser responsive tools/device; the in-app viewport override did not work in this environment.
- Enter a listing title/description containing `<script>` or HTML markup. It must render as literal text, never execute. Use only trusted HTTP(S) images.
- Stop/restart Notification; confirm the connection label, committed bid and automatic reconnection. Leave a tab idle through expiry and return: snapshot recovery must show the final result.
- Log out: navigation becomes anonymous, personal views require login and bidding prompts login. Browser logout does not revoke a previously issued JWT before its one-hour expiry.

## Limits of this verification

No claim is made for crash injection at every machine instruction, indefinite network partitions, unlimited subscribers, production security hardening or exactly-once delivery. At-least-once outbox delivery plus snapshot recovery is intentional. Test records are real persisted data and remain visibly labeled as acceptance/demo listings; no fabricated frontend data is used to present a completed workflow.
