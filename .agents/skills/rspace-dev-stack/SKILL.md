---
name: rspace-dev-stack
description: Run and diagnose the per-worktree RSpace Docker stack (MariaDB, Jetty, and Vite) for live runtime checks and end-to-end feature verification. Do not use for unit or integration tests or production deployment.
---

# RSpace dev stack

Run MariaDB, Jetty, and Vite together with `docker/dev/rspace-dev`, including
for frontend-only runtime checks. Read `docker/dev/README.md` for ports,
debugging, and hot reload. Requires Docker Engine and Compose v2, not local
Java, Node, or MariaDB.

Use the stack to reproduce crashes, inspect runtime state, diagnose regressions,
and verify fixes end to end. Code reading and covered unit/integration behavior
do not require booting it.

Commands affect only this worktree. Maven/pnpm caches are shared across
worktrees; `down` and `nuke` never delete them.

## Start and inspect

Run `./docker/dev/rspace-dev ps` first; reuse a running instance.

```bash
./docker/dev/rspace-dev up                # full stack, reuse DB
./docker/dev/rspace-dev up --fresh        # rebuild DB
./docker/dev/rspace-dev up --chemistry    # include chemistry microservice
./docker/dev/rspace-dev up --e2e          # include integration mocks
./docker/dev/rspace-dev logs app         # wait for Jetty "app started"
```

First boot takes several minutes. `up` and `ps` print this worktree's URLs and
ports. Logins: `user1a` / `user1234`; sysadmin `sysadmin1` / `sysWisc23!`.

`--e2e` runs integration mocks in the shared frontend/backend network namespace
and points backend integrations at them. Pass the printed mock port as
`E2E_MOCK_PORT` to Playwright. This setting persists until `up --no-e2e`.

## Enable chemistry

All three gates must be enabled, in order:

1. Start with `up --chemistry`. Check `RS.chemistryProvider` in the browser
   console for `indigo`.
2. As `sysadmin1`, open System → Configuration → System Settings,
   `#systemSettingsLink` on `/system/config`. Confirm the saved
   `chemistry.available` value is `ALLOWED`. It starts as `ALLOWED`, but a boot
   without a chemistry provider or service URL sets it to `DENIED`; later boots
   do not restore it. The edit dropdown defaults to "Allowed" even when the
   saved value is `DENIED`. Select Allowed, save, and check the displayed value.
3. As the working user, open Apps from the profile menu and ENABLE Chemistry.
   Its card stays disabled until the system setting allows it.

To check the saved setting directly, use `mariadb`; the image has no `mysql` alias:

```bash
docker exec <project>-db mariadb -urspacedbuser -prspacedbpwd rspace -e \
  "SELECT pd.name, spv.value FROM PropertyDescriptor pd \
   JOIN SystemProperty sp ON sp.descriptor_id = pd.id \
   JOIN SystemPropertyValue spv ON spv.property_id = sp.id \
   WHERE pd.name='chemistry.available';"
```

Reload the editor or log in again if needed. The toolbar gains "Ketcher Insert
chemical structure", "Insert reaction table", and "Insert PubChem Compound".
Drawing a structure in Ketcher enables its 3D Viewer, which loads `miew-react`.

## Apply changes

| Changed | Action |
| --- | --- |
| Frontend `.ts/.tsx/.css` | Vite HMR reloads automatically |
| JSP | Recompiles on next request |
| Java, existing bean | `./docker/dev/rspace-dev reload` or IDE hot code replace |
| Spring XML / web.xml / new beans / pom | `./docker/dev/rspace-dev restart` |
| Liquibase / schema | `./docker/dev/rspace-dev reset-db` or `up --fresh` |

See README sections "Hot reloading Java" and "What still requires a full
restart" if behavior remains stale.

```bash
./docker/dev/rspace-dev logs app          # Jetty/Spring
./docker/dev/rspace-dev logs frontend     # Vite
./docker/dev/rspace-dev logs db           # MariaDB
./docker/dev/rspace-dev shell [svc]       # container shell, default app
./docker/dev/rspace-dev db                # MariaDB client
./docker/dev/rspace-dev compose <args>    # pass through to docker compose
```

For Java debugging, attach to the JDWP port from `ps`; see README "Debugging".

## Verify in the browser

Use the URL from `ps`. Follow `AGENTS.md` runtime verification requirements:
Playwright MCP for repeatable flows and accessibility snapshots, Chrome DevTools
MCP for console/network errors, layout/reflow, performance, and Lighthouse.
Cover happy paths and likely regressions, including accessibility and responsive
behavior. Report missing tools and do not claim full verification without both.
Use connected `t3-code` `preview_*` tools or manual devtools when MCP is unavailable.

Navigate → snapshot → act on current element refs → assert. Never act on stale
coordinates. Use installed browser skills when available, or discover tool
schemas before calling MCP tools.

For failures, capture the request, browser error, and `logs app` stack trace.
For slowness, capture `performance_start_trace` / `performance_stop_trace` and
inspect `performance_analyze_insight`. `lighthouse_audit` excludes performance.
`emulate` combines CPU, network, viewport, theme, location, and header overrides.

## Stop

When finished, ask whether to tear down unless the user already instructed you.
Recommend `down`; run either command only with confirmation.

```bash
./docker/dev/rspace-dev down              # stop; retain DB and caches
./docker/dev/rspace-dev nuke              # permanently delete worktree volumes
```
