# REST API v2 read collections

REST API v2 currently exposes read-only `instruments` and `users` collections.
A resource description declares public fields, relationships, access rules and default sorting.
An `ApiV2ResourceSpec` registers the description and an operations class that delegates to a
transactional domain manager. DAOs apply access constraints and client filters together, before
pagination and counting. Do not filter results in the controller after pagination.

## Routes

| Route | Access |
| --- | --- |
| `GET /api/v2/config` | Public deployment information. |
| `GET /api/v2/openapi.json` | Public generated OpenAPI 3.1 document. |
| `POST /api/v2/oauth/tokens` | Authenticated browser session; issues a session-bound UI token. |
| `GET /api/v2/users/me` | Authenticated current-user information. |
| `GET /api/v2/users/me/profile-image` | Authenticated current-user profile image. |
| `GET /api/v2/{resource}` | Collection policy, row and field authorization. |
| `GET /api/v2/{resource}/count` | The same access and filter rules as the list. |
| `GET /api/v2/{resource}/{id}` | The same row and field authorization as collection reads. |
| `GET /api/v2/{resource}/fields/{namespace}` | Caller-specific runtime field catalog. |

Collection mutations, access-management routes, audit routes and booking routes are not yet exposed.
The generated document is the contract for this prefix; internal mutation support types do not
enable HTTP write operations.

## Register a read-only collection

Use `AccessPolicy.readOnly(...)` and limit `exposedOperations` to `LIST`, `COUNT` and `READ`.
Implement `find`, `count` and `findById` in `ResourceOperations<T, ID>`. Follow
`InstrumentResourceOperations` or `UserResourceOperations`. A resource registration also registers
its relationship target; do not register the same name twice.

Use `AccessResult.allowedWhere(...)` for access rules expressible as row constraints. Internal
selectors let the database enforce ownership and sharing without allowing clients to query those
properties. `InventoryReadFilters` and `InstrumentReadAccess` demonstrate this pattern.

Public relationships must remain resolvable after the read transaction commits. Supply batched
read overrides or safe scalar projections rather than relying on lazy entity traversal.

## Authentication and request limits

The v2 servlet filter strips ambient cookies and blocks session access before Shiro handles
ordinary API requests. API keys and external OAuth tokens remain stateless and cannot inherit
run-as. The browser token endpoint and tokens declaring the dedicated UI audience retain the live
session; the audience is only a routing hint, not authentication. The authenticator still verifies
the JWT, stored token, subject, actor and rotating browser context. Entering or leaving run-as
rotates that context. Legacy v1 rejects v2 session-bound UI tokens.

Pre-authentication and authenticated throttles protect requests before controller work. Bucket4j
consumes limits atomically and stores credential fingerprints rather than raw credentials.
Authenticated responses use `Cache-Control: no-store, private`.

`api.permissiveCors.enabled=true` enables v2 CORS for API credentials. The browser-session token
endpoint is excluded from permissive CORS and retains origin/referer checks. Advertised CORS
methods are not a promise that a collection implements writes: consult OpenAPI for its operations.

Errors use localized RFC 9457 problem responses. Query validation rejects unsupported fields,
operators, values and excessive complexity before querying the database. Spring's JSON reader
also bounds document size, string size, token count and nesting depth.
