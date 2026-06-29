# REST API v2 — Resource Catalog (companion to RSDEV-1187 booking design)

Captures the v2 resources designed alongside booking. The **generic v2 engine** (RSQL → blaze →
entity-view projections → keyset/offset envelope, the i18n error shape, and the per-row-authz write
engine) is specified in `RSDEV-1187-booking-design.md`; this doc only defines the additional
resources and their projections. Booking is the first consumer and the write engine's primary
justification; maintenance is the intended **second consumer** that validates generality.

Resource kinds:
- **Collection** — RSQL `filter`/`sort`/pagination/`select`/`count`/`POST /query`, optional writes.
- **Singleton / global** — `select`/`depth` only; no `where`/sort/pagination/bulk; cacheable.

---

## `users` collection + `me` singleton

- `GET /api/v2/users` — collection. Subsumes v1 `search` via RSQL (`filter=fullName=like=…`). Projection chosen in-query by viewer↔target connection:
  - `UserView` (self/connected): `id, username, email, firstName, lastName, homeFolderId, workbenchId, hasPiRole, hasSysAdminRole`.
  - `UserPublicView` (unconnected): `username, fullName`.
- `GET /api/v2/users/{id}` — single user (same projections).
- `GET /api/v2/users/me` — current user; **shape-identical to `/users/{id}`** + a `session` block (`operatedAs`, `lastSession`). Replaces v1 `/whoami` and folds in `/isOperatedAs`.
  - **Navigation is an opt-in block on `me`** (merges the old `uiNavigationData`): default `GET /me` is the lean user entity; `GET /me?depth=navigation` (or `select=navigation.*`) adds `{ bannerImgSrc, visibleTabs, extraHelpLinks }`. This removes the v1 duplication of user details + `operatedAs` across two endpoints.
  - `select` must prune **server-side computation**, not just the wire (don't compute `visibleTabs` unless selected).
- **Removed / deferred:** `me/group-members` — reimplemented when ShareDialog is moved (current consumers stay on v1 `/api/v1/userDetails/groupMembers`).
- v1→v2 changes: drop `_links` HATEOAS; dates as ISO-8601; unauthenticated `/me` → `401`.

## `config` (deploymentproperties) — read-only global

- `GET /api/v2/config` — singleton; `select`/`depth` only; strongly cacheable.
- **Read-only in this plan; the sysadmin write path is wired separately.**
- Projections: a **strict default-deny public projection** (curated allow-list) plus a sysadmin-only full projection. The field allow-list is **security-critical** — `IPropertyHolder` carries sensitive config (SSO, file stores, internal paths), so default-deny, security-reviewed, never a deny-list over all fields.

## `maintenances` collection

Full collection of `ScheduledMaintenance`. "Next maintenance" is a query
(`?filter=endDate>=<now>&sort=startDate&limit=1`), not a separate global.

**API fields (all stored fields):**
- `id`
- `startDate`, `endDate`, `stopUserLoginDate` — ISO-8601
- `message`

**Excluded from the API (do not expose):** `canUserLoginNow` and `activeNow`.
- Rationale: these are **server-side authorization/logic helpers**, not client data. They are read only by `MaintenanceLoginAuthorizer` (login gating), `PublicController` (pre-auth banner flag), and `ScheduledMaintenanceController` (action guard) — all working off the entity directly. The React `AppBar` consumes only `nextMaintenance.startDate`, never these booleans (they were serialized into v1 nav data but unused by the client).
- Excluding them avoids the virtual-field costs entirely (no non-deterministic, time-derived field on the wire → keeps responses cacheable; nothing non-queryable in the projection). The login-gating logic stays on the entity/manager, off the API.
- Also drop the v1 `formatted*` display helpers; clients format from the ISO values. If a future client needs "is maintenance active", derive from `startDate`/`endDate`, or add a queryable SQL-expression virtual then.

**Endpoints (full surface):** `GET` list / `{id}` / `count` / `POST /query`; `POST` / `PATCH {id}` / `DELETE {id}` and bulk `PATCH`/`DELETE` by `where`.

**Authorization:** read = any authenticated user (non-sensitive schedule); write = **sysadmin only** (per-row `AuthorizationPolicy` = `user.isSysadmin()`), routed through the existing `MaintenanceManager` as the pluggable write handler (owns schedule validation). This makes maintenance the low-risk **second consumer** that validates the generic write engine independently of booking.

---

## Cross-reference

- Generic engine, envelope, RSQL/blaze/entity-views, i18n error shape, per-row authz, bulk semantics, projections (full/restricted) → `RSDEV-1187-booking-design.md`.
- `booking` resource (the pilot consumer, with recurrence/availability/iCal) → `RSDEV-1187-booking-design.md`.
