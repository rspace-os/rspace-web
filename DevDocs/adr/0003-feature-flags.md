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
and the current node's snapshot. There is no database polling or multi-node
propagation; a future multi-node deployment will need shared state.

## API and access

`GET /api/v2/feature-flags` returns flags in manifest order. Each entry contains
`value`, `baselineValue`, `source`, and `canOverride`. Sources are `DEFAULT`,
`DATABASE`, `USER_OVERRIDE`, or `PROPERTIES_FILE`. Definition metadata is not
exposed. Unauthenticated callers receive baselines and cannot write;
authenticated callers receive values evaluated for their user.

`/api/v2/users/me` supplies three session capabilities:

- `canUseDevtools`
- `canOverrideFeatureFlags`
- `canChangeFeatureFlagBaselines`

Sysadmins receive these for their own sessions, but not while operating as
another user. `dev.mode.enabled` may grant Devtools and override access on test
systems, but never baseline editing. It defaults to enabled when Vite or another
development indicator is active, and disabled otherwise. Existing overrides
still apply after permission is lost, but become read-only.

Writes operate on one flag at a time:

- `PUT /api/v2/feature-flags/{flagName}/override`
- `DELETE /api/v2/feature-flags/{flagName}/override`
- `PUT /api/v2/feature-flags/{flagName}/baseline`

Unknown flags return `404`, missing permission returns `403`, and
properties-forced flags return `409`. Clearing a missing override and writing
the current baseline are successful no-ops. V1 has no bulk writes, cross-user
override administration, or special flag-write audit trail.

## Frontend

The frontend fetches flags once per app or session bootstrap and treats them as
stable until reload. A failed fetch disables feature checks without blocking
the rest of the application.

Permitted users edit flags in a lazily loaded TanStack Devtools panel. Users
manage their own overrides; sysadmins also get a separately saved baseline
control. The panel warns that baseline changes affect every user and are not
covered by RSpace support agreements when made without RSpace support
involvement. Properties-forced flags are read-only. After a write, the panel
refetches the flags and offers an explicit reload; it never reloads the page
automatically.
