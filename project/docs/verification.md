# Testing SecondRound

From `project/`, start the app and run the test suite.

```sh
python3 scripts/dev.py db-init
python3 scripts/dev.py build
python3 scripts/dev.py start
python3 tests/integration.py -v
node tests/live.test.mjs
```

The Python suite exercises the running services with HTTP, WebSocket and PostgreSQL. It creates test listings and accounts and restarts services during the recovery checks. Start the app using the scripts for this suite. The JavaScript test needs Node and has no npm dependencies. Maven `verify` builds the services separately.

## Recorded results

The earlier local run on macOS with JDK 21 and PostgreSQL 17.6 passed eight integration tests. The suite covered registration and login, item ownership, auction creation after a restart, bid rules, concurrent bids, auction expiry, notification recovery and a bid held by a database lock until after the deadline.

The JavaScript test checks what happens when a message arrives during an HTTP read, when messages arrive twice or out of order, and when the WebSocket reconnects. The price on the page should stay at the newest version it has read.

In the two-tab browser test, a buyer bid 2,500 and then 3,000 HUF. The seller's tab showed 3,000 HUF and two bids without a refresh. With Notification stopped, a 3,500 HUF bid was still accepted. After Notification restarted, the seller's tab reconnected and showed 3,500 HUF and three bids. The price and bid history were still there after the local database and services restarted.

The earlier browser test used a 662 px-wide viewport. A separate phone or Safari/Firefox run remains to be checked.

## Repeat the demo

Follow [Try live bidding](../README.md#try-live-bidding). For the reconnect case, stop Notification with `python3 scripts/dev.py stop notification`, place a bid and start it again with `python3 scripts/dev.py start notification`. Watch the seller's tab reconnect and show the accepted bid. For expiry, let one auction end with a bid and another end without any bids.
