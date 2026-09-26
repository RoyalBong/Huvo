# common-libs/ - shared Maven libraries (not services)

Small, independently versioned local modules per Huvo_Backend_Context.md §10/§11.
Nothing here is a service: each is consumed by services as a regular Maven
dependency (`mvn install` locally; a lightweight internal artifact repo later
if the team grows).

Planned modules:

| Module              | Purpose                                                        |
|---------------------|----------------------------------------------------------------|
| `huvo-security-lib` | JWT validation - every service validates the token itself; there is no central auth proxy (§4.2). |
| `huvo-audit-client` | Writes to the `huvo_audit_log` table (§4.3).                   |
| `huvo-event-contracts` | Shared event DTOs so publishers and consumers don't drift (§7). |

TODO(phase-1): create `huvo-security-lib` together with the auth sub-domain;
`com.huvo.identity.event.EventEnvelope` moves to `huvo-event-contracts` once
attendance/worklife consumers appear (its current location carries the same TODO).

Not a reactor: each service stays independently buildable (§11), so services
depend on these only after `mvn install` puts them in the local repository.