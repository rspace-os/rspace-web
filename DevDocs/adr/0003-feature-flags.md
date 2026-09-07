# Feature Flags

## Purpose and definition

Feature Flags are internal boolean controls for rollouts, kill switches, and
temporary changes. They must not control authorization, licensing, tenant
isolation, secrets, or roadmap confidentiality.

Definitions live in
`src/main/resources/feature-flags/feature-flags.jsonc`. Each has a
lower-camel-case `name`, `description`, `owner`, and ISO `expires` date. The
optional `default` is `false` when omitted.

Java reads this file directly at runtime. `pnpm run feature-flags:generate`
creates committed backend constants and frontend types. `pnpm run
feature-flags:check` fails CI for generated-file drift, duplicate names, or
expired flags. Expiry does not block startup.

## Evaluation and storage

Values resolve in this order: properties file, user override, database
baseline, manifest default. The baseline is the value before a user override.
Request checks use the current user; startup and background code must request
the baseline explicitly. Anonymous requests also use the baseline.

The emergency properties file is separate from `deployment.properties` and
defaults to `src/main/resources/deployments/feature-flags.properties`. Its
case-sensitive keys must name known flags and its values must be `true` or
`false` after trimming. These values take effect after restart, report as
`PROPERTIES_FILE`, become the exposed baseline, and cannot be overridden
through the API.

Liquibase owns one table for baselines and one for per-user overrides. Manifest
defaults are not copied into the database. An explicit user override reports
as `USER_OVERRIDE` even when it matches the baseline; clearing it is the only
way to return to the baseline. Baseline changes do not alter user overrides.

Startup reconciles the database with the manifest and removes state for retired
flags. It fails for an invalid manifest, invalid properties entry, or failed
reconciliation. Code must not evaluate flags before reconciliation finishes.
Retired names may be reused later, but not in adjacent releases.

Baseline values are held in memory after startup. Writes update the database
inside a transaction and update the current node's snapshot only after commit.
There is no database polling or multi-node propagation. A future multi-node
deployment will need shared state.

## API and access

The `feature-flags` resource uses the standard REST API v2 collection routes.
The list response contains feature flag resources in the `docs` array.

Each resource contains `name`, `value`, `baselineValue`, `source`, and
`canOverride`. The `overrideValue` field accepts update input but does not appear
in a response.

The source is `DEFAULT`, `DATABASE`, `USER_OVERRIDE`, or `PROPERTIES_FILE`.
The API does not expose feature flag definition metadata.

Anonymous callers receive baseline values. A caller with API credentials
receives values for the effective subject. API keys and external OAuth tokens do
not use cookies or browser sessions. The browser uses a session-bound REST API
v2 UI token. The server verifies that token against the live browser session.

The resource access policy permits public reads and authenticated updates.
Invalid credentials return `401`. An anonymous update also returns `401`.

`/api/v2/users/me` supplies three session capabilities:

- `canUseDevtools`
- `canOverrideFeatureFlags`
- `canChangeFeatureFlagBaselines`

Sysadmins receive these for their own sessions, but not while operating as
another user. `dev.mode.enabled` may grant Devtools and override access on test
systems, but never baseline editing. It defaults to enabled when Vite or another
development indicator is active, and disabled otherwise. Existing overrides
still apply after permission is lost, but become read-only.

Use one `PATCH` request to change one feature flag:

- Set an override with `{"overrideValue": true}` or `{"overrideValue": false}`.
- Clear an override with `{"overrideValue": null}`.
- Set the instance baseline with `{"baselineValue": true}` or `{"baselineValue": false}`.

The route is `PATCH /api/v2/feature-flags/{flagName}`. Unknown flags return
`404`. Missing permission returns `403`. A properties-file value returns `409`.
The API does not expose create, bulk update, or delete operations.

`bookingEnabled` gates the whole Booking feature: its navigation and pages and
the REST API v2 `booking-configurations` resource. While disabled, collection
reads are empty, individual reads are absent, and single or bulk create
attempts return `403` without calling the booking service.

Each successful change publishes an audit event after the transaction commits.
The stable audit identifier is `feature-flags:{flagName}`. The standard REST API
v2 audit routes return these events.

## Frontend

The frontend uses the shared OAuth-token query. The frontend reads each result
page and converts the resources to a name map. A failed read disables feature
checks without blocking the application.

Permitted users edit flags in a lazily loaded TanStack Devtools panel. Users
manage their own overrides; sysadmins also get a separately saved baseline
control. The panel warns that baseline changes affect every user. The panel also
shows the RSpace support warning. Properties-file values are read-only. After a
write, the panel reads the flags again and offers a reload action. It does not
reload the page automatically.
