# Code layout

Each service has its own Maven project under `project/`. Java packages start with `com.p5auction.<service>`.

| Package | What goes in it |
| --- | --- |
| `controller` and `dto` | HTTP handlers and the records sent to or received from the browser. The controller passes the user ID from the verified JWT. |
| `service` | Rules for things like editing an item, accepting a bid and closing an auction. |
| `repository` and `entity` | The service's own database queries and stored records. |
| `config` and `exception` | Authentication, connection settings and error responses. |

Use a UUID for IDs, an `Instant` for deadlines and a `long` for whole HUF. Add schema changes as new Flyway migrations. Keep database passwords and signing keys in the local environment.

On the client, `app.js` handles the pages and forms and `live.js` keeps the auction details in sync. Put user-provided text into the DOM with `textContent`.

See [the code walkthrough](code-walkthrough.md) and [how to run the tests](verification.md).
