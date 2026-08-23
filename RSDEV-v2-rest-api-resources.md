# REST API v2 resource status

This file records the resources in the first REST API v2 release.

Use `DevDocs/DeveloperNotes/RestApiV2Collections.md` for the current implementation rules. The
OpenAPI document at `/api/v2/openapi.json` is the public HTTP contract.

## Users

The `users` collection has standard collection routes. Its policy gives a system administrator
access to all users. Another authenticated user can read only their own row.

The `/api/v2/users/me` route is a concrete controller route. It returns the effective subject and
API session state. During delegated use, the session state also identifies the original actor.
The `/api/v2/users/me/profile-image` route returns the current profile image as a PNG file. A user
who has never uploaded one has no image, and the route answers `404`. There is no default avatar, so
a client that points an `<img>` element straight at this route shows a broken image for a new
account. Fetch it and supply your own placeholder on `404`.

The `users` collection exposes only `LIST`, `COUNT`, and `READ`. A write reaches the generic
controller's mapping, but the exposure check refuses it with `405` and an `Allow` header before the
access policy runs. The operations class contains non-persistent placeholders that nothing calls.

## Configuration

The `/api/v2/config` route returns a public allowlist of deployment properties. The implementation
does not reflect over all properties. It does not expose credentials or secret paths.

This route is not a registered collection. Its controller supplies its OpenAPI documentation.

## OpenAPI

The public `/api/v2/openapi.json` route returns the generated OpenAPI 3.1 document. The document
contains registered collections, relationship targets, and annotated concrete routes.

Production caches the document privately for one hour. Production also supports conditional
requests through `ETag` and `If-None-Match`.

## Common request behavior

REST API v2 accepts an API key or an OAuth bearer token. Ordinary API keys and external OAuth
tokens do not use browser cookies or servlet sessions. The token-minting route uses the live
browser session. A session-bound UI token must also match that session.

The session-bound token identifies the effective subject and the original actor. Authorization
uses the subject. Throttling uses the actor.

Public routes do not require credentials. Collection routes apply their resource policy. Other
routes require authentication by default.

Errors use RFC 9457 problem details. Responses identify the selected language through
`Content-Language` and `Vary: Accept-Language`.

## Maintenance

The `maintenances` collection exposes stored `ScheduledMaintenance` fields.

An anonymous caller can read a maintenance row only when the row is not deleted and its end date
is in the future. A system administrator can read all rows and can run write operations.

The collection supports list, count, read, create, bulk create, update, bulk update, delete, and
bulk delete operations. `MaintenanceManager` owns domain validation and authorization.

The response does not contain formatted date fields. Clients use the stored ISO-8601 dates.

The response does contain `canUserLoginNow`, which the server evaluates at request time. It is
neither filterable nor sortable, because it has no column. Do not cache a maintenance document and
reuse this field.

## Booking configuration

The `booking-configurations` collection stores the booking state for an inventory target. The
current implementation supports instruments. The stored target uses a typed reference.

The collection uses the existing audit infrastructure. The default resource audit routes expose
the permitted audit data.

See `RSDEV-1187-booking-design.md` for future booking and scheduling decisions.
