# Second-Hand Auction Platform

Programming 5 course project: a microservices web application for auctioning second-hand items, with live bidding updates.

**Status:** Design stage. Application code and run instructions will be added during implementation.

## Project documentation

- [Project description and scope](docs/project-description.md)
- [Architecture and service responsibilities](docs/architecture.md)
- [HTTP, WebSocket and real-time bidding flow](docs/communication.md)

## Planned system

Four independent backend services (User, Item, Auction and Notification) and one browser client. The Auction Service handles bidding rules; the Notification Service pushes accepted price changes through WebSocket.
