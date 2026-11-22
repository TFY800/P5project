# SecondRound

A seller lists a second-hand item and sets a starting price, a minimum raise and an end time. Buyers bid in HUF. When someone places a bid, anyone watching that auction can see the new price without refreshing. The highest accepted bid wins when the auction closes.

## Run it on a Mac

Install JDK 21 and Python 3. In Finder, open `project/` and double-click [Start.command](Start.command). The script sets up PostgreSQL if needed and opens [localhost:8080](http://localhost:8080). Building PostgreSQL on the first run requires the Apple Command Line Tools. Use [Stop.command](Stop.command) to stop the website and services.

If the old layout is still running, use the new `Stop.command` before starting again. The [setup guide](docs/local-development.md) covers terminal commands, IntelliJ IDEA and reusing the local database.

## Try live bidding

1. Create a seller account and choose **Sell an item**. Enter an image URL, set the auction to end a few minutes from now and publish it.
2. Open the same auction in a separate browser window or profile. Create a buyer account there and place a bid.
3. Check the seller's window. The price and bid history should update without a page refresh. Wait for the deadline to see the result.

## Services

| Folder | Port | What it does |
| --- | --- | --- |
| [user-service](user-service/) | 8081 | Creates accounts and issues a login token. |
| [item-service](item-service/) | 8082 | Saves listings and locks their details once they are attached to an auction. |
| [auction-service](auction-service/) | 8083 | Accepts bids, updates prices and closes auctions. |
| [notification-service](notification-service/) | 8084 | Sends WebSocket updates to viewers of the auction. |
| [client](client/) | 8080 | Shows listings, auctions and bid history in the browser. |

The four services run separately with Spring Boot. User, Item and Auction each use their own PostgreSQL database. The client is HTML, CSS and JavaScript.

## Weekly progress

Week 1 starts on 8 September 2026. Each row covers Tuesday through Monday. The [PTE MIK semester calendar](https://english.mik.pte.hu/semester-calendar-1) lists the autumn break as 26–30 October. A dash means there is no dated project record for that week.

| Week | Dates | Work completed | Result |
| --- | --- | --- | --- |
| 01 | 8–14 Sep | — | — |
| 02 | 15–21 Sep | — | — |
| 03 | 22–28 Sep | — | — |
| 04 | 29 Sep–5 Oct | — | — |
| 05 | 6–12 Oct | — | — |
| 06 | 13–19 Oct | — | — |
| 07 | 20–26 Oct | Created the GitHub repository for the course project. | [First commit](https://github.com/TFY800/P5project/commit/47d9a04463d1293bccbf23169647a01a80acfb5e) |
| 08 | 27 Oct–2 Nov | Autumn break through 30 Oct. | — |
| 09 | 3–9 Nov | Wrote the auction design and set up the four Spring Boot services and web client. | [Starter version](https://github.com/TFY800/P5project/commit/3909532c4a5835642302ce983f0f990651ecb6fc) |
| 10 | 10–16 Nov | — | — |
| 11 | 17–23 Nov | Pushed the working auction flow, including live bids. Moved the application into `project/` and rewrote the project guide. | [Working version](https://github.com/TFY800/P5project/commit/26f1113ac9eeb01b7575c852685160488f185251), [folder change](https://github.com/TFY800/P5project/commit/fbd6209ab6651f0be838e6982a82cd5b8b4564d3) |

## Project notes

- [What the site covers](docs/project-description.md)
- [Architecture and Mermaid diagrams](docs/architecture.md)
- [HTTP and WebSocket messages](docs/communication.md)
- [Setup and IntelliJ IDEA](docs/local-development.md)
- [Tests and recorded results](docs/verification.md)
- [Design choices](docs/decisions.md) and [code layout](docs/coding-conventions.md)
- [中文代码导读](docs/code-walkthrough.md)
