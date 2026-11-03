# Second-Hand Auction Platform

## Project goal

Build a web application where users can list second-hand items and participate in timed auctions. Participants receive live price updates without refreshing the page.

This is a Programming 5 course project. The repository currently contains the project design; the application is not implemented yet.

## Target users

- Sellers who want to auction second-hand items.
- Buyers who want to browse items and place bids.

A registered user may act as both buyer and seller.

## Minimum scope

- Register and log in.
- Publish and browse item listings.
- Create an auction with a starting price, minimum bid increment and end time.
- View the current price and bid history.
- Submit a bid while the auction is active.
- Receive live price updates and auction-ended notifications.
- Display the winner after an auction ends, or indicate that no bids were placed.

Payment processing, shipping, private chat and automatic proxy bidding are outside the first version.

## Course requirement mapping

| Requirement | Planned implementation |
|---|---|
| At least four independently running services | User, Item, Auction and Notification services |
| At least one client | Browser-based web client |
| Real-time communication | WebSocket price and auction-status updates |
| Project description in Markdown under docs | Documents in this directory |
| Mermaid diagrams | Architecture and communication diagrams |

## Core rules

- Only authenticated users can publish items and place bids.
- Only an item's owner can create its auction.
- A seller cannot bid on their own auction.
- The first bid must meet the starting price; subsequent bids must meet the current price plus the minimum increment.
- The Auction Service uses server time to decide whether bidding is open.
- Accepted bids and the corresponding current price are committed atomically.
- The highest accepted bid wins when the auction ends.
- Listing details are frozen while their auction is active.

## Demonstration

Open the same auction in two browser sessions. Submit a valid bid in one session and observe the new price in both sessions without refreshing. Also demonstrate a rejected low bid and the final auction result.

## Implementation stages

1. Agree on the design and select the technology stack.
2. Implement User and Item services and a basic web client.
3. Implement auctions, bid validation and concurrency control.
4. Implement the Notification Service and WebSocket subscriptions.
5. Verify the complete workflow, disconnection recovery and concurrent bidding.

See [Architecture](architecture.md) and [Communication](communication.md).
