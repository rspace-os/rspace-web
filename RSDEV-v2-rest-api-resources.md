# REST API v2 Resource Catalog

This catalog defines the other REST API v2 resources. The booking document defines the shared query,
projection, paging, error, and write behavior.

Booking is the first consumer. Maintenance is the second and tests whether the design is reusable.

Resource kinds:
- **Collection:** filter, sort, page, select, count, query, and optional writes.
- **Singleton:** select and depth only. No filtering, sorting, paging, or bulk writes.

---

## `users` collection + `me` singleton

- `GET /api/v2/users` replaces v1 search with RSQL.
- `GET /api/v2/users/{id}` returns one user with the same field rules.
- `GET /api/v2/users/me` returns that user shape plus `session.operatedAs` and `session.lastSession`.

Use one SQL-masked view for all rows:

- self and connected users include ids, email, names, folders, workbench, and role flags
- unconnected users include only username and full name
- serialize masked fields as absent

Navigation is optional on `/me`. Request it with `depth=navigation` or `select=navigation.*`. Only
compute `bannerImgSrc`, `visibleTabs`, and `extraHelpLinks` when selected.

Defer `me/group-members` until ShareDialog moves. Current clients keep using the v1 endpoint.

Drop `_links`, use ISO-8601 dates, and return 401 for unauthenticated `/me` requests.

## `config` (deployment properties) — read-only global

- `GET /api/v2/config` returns a public-safe allow-list. Public caching is allowed because the result
  does not change by role.
- `GET /api/v2/admin/config` returns a larger sysadmin allow-list with
  `Cache-Control: private, no-store`.
- Both endpoints are read only. The existing sysadmin write path remains separate.

Deny fields by default. Never expose keys, signup codes, cloud secrets, OAuth secrets, passwords,
private keys, or credential paths.

Do not build either response by reflecting over every getter. Tests must prove that known sensitive
properties are absent from schemas and responses.

## `maintenances` collection

Expose all `ScheduledMaintenance` rows. Query the next maintenance with
`?filter=endDate>=<now>&sort=startDate&limit=1`; do not add a separate endpoint.

**API fields (all stored fields):**
- `id`
- `startDate`, `endDate`, `stopUserLoginDate` — ISO-8601
- `message`

Do not expose `canUserLoginNow`, `activeNow`, or the v1 `formatted*` fields.

The booleans are server-side helpers and current React code does not use them. Their values also
change with time, which makes caching and filtering harder.

Clients format dates and may derive active state from `startDate` and `endDate`.

Endpoints: list, get, count, query, create, patch, delete, and bulk patch/delete by `where`.

Any authenticated user may read maintenance. Only sysadmins may write it.

Use `MaintenanceManager` as the write handler. Share request DTO constraints between v1 and v2
controllers. Validate bulk patch DTOs before calling the manager.

The manager checks sysadmin access and invariants that need persisted state. Maintenance is the
Phase 5 test of the generic write engine.

---

## Cross-reference

- Generic engine, envelope, RSQL/blaze/entity-views, i18n error shape, per-row authz, bulk semantics, projections (full/restricted) → `RSDEV-1187-booking-design.md`.
- `booking` resource (the pilot consumer, with recurrence/availability/iCal) → `RSDEV-1187-booking-design.md`.
