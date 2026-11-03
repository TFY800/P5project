# Second-Hand Auction Platform

Programming 5 course project: a microservices web application for auctioning second-hand items, with live bidding updates.

**Status:** Project skeleton initialized with four independent Java 21 / Spring Boot 4.1.2 Maven services and a static HTML client. Database integration, registration/login, auction/bidding logic, notification business logic and WebSocket are not implemented yet.

## Project documentation

- [Local startup, health checks and IntelliJ IDEA setup](docs/local-development.md)
- [Project description and scope](docs/project-description.md)
- [Architecture and service responsibilities](docs/architecture.md)
- [HTTP, WebSocket and real-time bidding flow](docs/communication.md)

## Planned system

Four independent backend services (User, Item, Auction and Notification) and one browser client. The Auction Service handles bidding rules; the Notification Service pushes accepted price changes through WebSocket.

## Current repository structure

| Directory | Current implementation | Port |
| --- | --- | --- |
| `user-service/` | User service skeleton and `GET /api/health` | 8081 |
| `item-service/` | Item service skeleton and `GET /api/health` | 8082 |
| `auction-service/` | Auction service skeleton and `GET /api/health` | 8083 |
| `notification-service/` | Notification service skeleton and `GET /api/health` | 8084 |
| `client/` | Plain HTML page listing the services; no backend calls | — |
| `docs/` | Existing design documents and local development guide | — |

Each backend has its own `pom.xml`, Java 21 application, Spring Web MVC dependency,
configuration and executable Maven Wrapper. There is no root aggregator POM.
No Docker, message queue or frontend framework is included.

The original design documents describe planned functionality. The current implementation
is limited to this skeleton; see the local development guide for what can be run now.
