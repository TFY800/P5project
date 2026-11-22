# Why the project works this way

## Why split listings and auctions?

The item service keeps the seller's description and image. The auction service owns bidding and deadlines. An auction stores a copy of the frozen listing, so its detail page can still load if the item service goes offline.

## What if two bids arrive together?

Both bids try to lock the same auction row. After each request gets the lock, it checks the latest price and the server clock. That also decides what happens if a bid was sent before the deadline but had to wait for a lock. Bids for different auctions can proceed independently.

## What if the item service times out while freezing an item?

Auction saves its ID with status `PREPARING` before calling Item. If the request times out, Item might already have frozen the listing. Auction retries with the same ID. Item can confirm the existing freeze for that ID, and Auction can activate the listing without creating a second auction.

## What if Notification is offline?

Auction saves the bid and a row for its notification in the same transaction. The scheduled dispatcher sends that row after the bid commits. It can retry delivery without resubmitting the bid. A viewer who misses a WebSocket message can read the current price and history over HTTP after reconnecting.

## Which requests need a login?

Reading public auctions and subscribing to their updates are open to visitors. Publishing, editing, bidding and account pages require a valid JWT. The auction service uses a separate internal credential for its calls to Item and Notification. The browser keeps its one-hour token in the current tab. Logging out clears that tab's token.
