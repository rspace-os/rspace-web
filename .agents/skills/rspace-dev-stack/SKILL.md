---
name: rspace-dev-stack
description: Boot, drive, and tear down the per-worktree Dockerized RSpace dev stack (MariaDB + Jetty backend + Vite frontend) to reproduce bugs, diagnose issues, and manually test functionality against a real running instance. Use when a user asks to run/start RSpace locally, spin up the app to test or verify a change, reproduce a bug end-to-end, or check behaviour in a browser. Do not use for unit/integration tests (use mvn/pnpm directly) or for production deployment (use rspace-docker).
---

# RSpace Dev Stack Skill

Drive the experimental per-worktree Docker dev stack to get a full RSpace
instance (MariaDB + Jetty backend + Vite frontend) running so you can reproduce
issues and test functionality live. **RSpace is a SPA (single-page
application):** both frontend and backend must run together. Even for
frontend-only changes, boot the entire stack to verify the UI works against the
real API and database. The launcher is `docker/dev/rspace-dev`; full reference
is `docker/dev/README.md` — read it for ports, debugging, and hot-reload
details.

## Guardrails (read first)

- **Never run `up` (or otherwise launch containers) unless the user explicitly
  asks.** It is resource-heavy (full JVM + DB + Node per worktree). If a task
  would benefit from a running instance, say so and ask — do not start it on
  your own initiative. This mirrors the rule in `CLAUDE.md`.
- **Always boot the entire stack together: `./docker/dev/rspace-dev up`.**
  RSpace is a SPA; the frontend cannot work without the backend API and
  database. Do not try to run just the frontend (Vite) or just the backend
  (Jetty) in isolation — always use the full stack command to verify changes
  end-to-end.
- **`nuke` permanently deletes this worktree's DB and local data.** Confirm
  before running it unless the user was explicit. `down` is the reversible stop.
- Commands are scoped to the **current worktree**: they never affect another
  worktree's instance, and `down`/`nuke` never delete the shared Maven/pnpm
  caches. (Those caches *are* shared — every worktree reads and writes them — so
  they're reused across worktrees, just never destroyed by these commands.)
- Prerequisite: Docker Engine + Compose v2. No local Java/Node/MariaDB needed.

## When to launch over static analysis

Reading code answers "what does this do"; running the app answers "what is
actually happening". Prefer launching the stack (after asking the user) when:

- **A crash or error needs its real stack trace.** A React component blows up,
  a 500 comes back from an endpoint, or a Spring bean fails to wire — the live
  log (`logs app`) or browser console gives you the exact trace and line, which
  is faster and more reliable than tracing call paths by hand.
- **The bug is reported as a symptom, not a location** ("the sample editor
  freezes when I add a field"). Reproduce it first, capture the trace, then go
  to the code with a concrete failure in hand.
- **Behaviour depends on runtime state** the source can't show you: DB contents,
  session/permissions, integration toggles, async timing, rendered DOM, or
  network responses. Inspect them on a running instance (`db`, browser
  devtools).
- **Verifying a fix end-to-end** — confirm the change actually resolves the
  reported behaviour in the UI, not just that the code looks right.
- **A regression that static reading can't localise** — bisect or A/B the
  behaviour against a running build.

Stay with static analysis for: pure logic questions, "where is X defined",
refactors with test coverage, and anything a unit/integration test already
exercises. Don't boot the stack just to read code.

## Workflow

### 1. Check state before acting

```bash
./docker/dev/rspace-dev ps      # status + URLs/ports for this worktree
```

If nothing is running and the user asked to run the app, proceed; otherwise
reuse the running instance.

### 2. Boot (only when the user asked)

```bash
./docker/dev/rspace-dev up                # reuse existing DB (fast)
./docker/dev/rspace-dev up --fresh        # rebuild DB from scratch
./docker/dev/rspace-dev up --chemistry    # also start the chemistry microservice
```

**Always boot the full stack — never the frontend alone.** RSpace is a SPA: the
React UI is useless without the Java backend and database serving its API,
session, and integration data. Even for a pure frontend change, `up` (which
starts db + backend + frontend together) is required — do not stand up only the
Vite server (e.g. host `pnpm run serve`) to test a UI change.

First boot takes several minutes (Maven + pnpm downloads). Watch readiness:

```bash
./docker/dev/rspace-dev logs app          # wait for Jetty "app started"
```

The app URL and ports are printed by `up` and by `ps` (e.g.
`http://localhost:8080`). Logins: `user1a` / `user1234`,
sysadmin `sysadmin1` / `sysWisc23!`.

### 2a. Enable chemistry (Ketcher structure editing)

`up --chemistry` only starts the chemistry microservice and sets
`chemistry.provider=indigo` — necessary but **not** sufficient. The in-editor
chemical-structure tool is gated by three layers that must all be satisfied, in
order:

1. **Deployment (the `--chemistry` flag):** sets `chemistry.provider=indigo`
   and points the app at the microservice. Verify with
   `docker exec <project>-app sh -c 'ps aux | grep -o "chemistry.provider=[a-z]*"'`
   or `RS.chemistryProvider` in the browser console.

2. **System setting (sysadmin, persisted in the DB) — the easy one to miss.**
   Log in as `sysadmin1` → **System → Configuration → System Settings** (the
   `#systemSettingsLink` on `/system/config`; it is under *Configuration*, not
   *Maintenance*). Confirm **`chemistry.available`** is **`ALLOWED`** (options:
   `ALLOWED` / `DENIED_BY_DEFAULT` / `DENIED`). **Check it rather than assume:**
   the seeded value is `ALLOWED` by default. Startup forces it to `DENIED` only
   when the chemistry provider or service URL is missing (any boot without
   `--chemistry`), but does not restore it afterwards. So a stack first booted
   with `--chemistry` is usually already `ALLOWED` (no change needed); but if it
   was ever started without chemistry, the setting stays at `DENIED` and you
   must set it back to `ALLOWED`.
   - **Gotcha:** the row shows the *saved* value as text, with a separate edit
     dropdown that **always defaults to "Allowed"** even when the saved value is
     `DENIED`. Don't trust the dropdown's default — click the value to enter
     edit mode, pick **Allowed**, and click **Save**, then confirm the row's
     displayed value now reads `ALLOWED`.
   - **Authoritative check** (bypasses the UI). Use the `mariadb` client — the dev
     DB is MariaDB and the image has no `mysql` symlink (the launcher's own `db`
     command uses `mariadb`):
     ```bash
     docker exec <project>-db mariadb -urspacedbuser -prspacedbpwd rspace -e \
       "SELECT pd.name, spv.value FROM PropertyDescriptor pd \
        JOIN SystemProperty sp ON sp.descriptor_id = pd.id \
        JOIN SystemPropertyValue spv ON spv.property_id = sp.id \
        WHERE pd.name='chemistry.available';"
     ```

3. **Per-user integration (Apps page).** As the working user, open **Apps**
   (top-right profile menu → Apps). The **Chemistry** card is greyed/disabled
   until step 2 is `ALLOWED`; once it is, open the card and click **ENABLE**.

With all three in place, the document editor's rich-text toolbar gains
**"Ketcher Insert chemical structure"** (plus "Insert reaction table" and
"Insert PubChem Compound"). That button opens the Ketcher editor; drawing a
structure enables Ketcher's **3D Viewer** button, which loads the Miew viewer
(`miew-react`). A fresh login / editor reload may be needed for a
just-enabled integration to appear.

### 3. Apply changes while running

| You changed…             | Command                                         |
| ------------------------ | ----------------------------------------------- |
| Frontend `.ts/.tsx/.css` | nothing — Vite HMR auto-reloads                 |
| JSP                      | nothing — recompiled on next request            |
| Java code (existing bean)| `./docker/dev/rspace-dev reload` (or IDE hot code replace) |
| Spring XML / web.xml / new beans / pom | `./docker/dev/rspace-dev restart`     |
| Liquibase / DB schema    | `./docker/dev/rspace-dev reset-db` (or `up --fresh`) |

See README §"Hot reloading Java" / "What still requires a full restart" for the
full matrix when behaviour looks stale.

### 4. Diagnose

```bash
./docker/dev/rspace-dev logs app          # backend (Jetty/Spring) logs
./docker/dev/rspace-dev logs frontend     # Vite logs
./docker/dev/rspace-dev logs db           # MariaDB logs
./docker/dev/rspace-dev shell [svc]       # shell into a container (default app)
./docker/dev/rspace-dev db                # MariaDB client for this worktree
```

For backend stepping, attach a debugger to this worktree's JDWP port (shown by
`ps`); see README §Debugging.

### 5. Test functionality in the browser

Use the app URL from `ps` and drive the flow with whichever browser-automation
tooling your runtime has connected — point it at the running instance's URL to
exercise flows and capture state. See "Browser automation" below for which of
`preview_*` / Playwright / Chrome DevTools to reach for and the standard loop.
If none are connected, drive it manually and read state from devtools (console,
network, DOM).

### 6. Tear down (always prompt when finished)

When the editing/diagnosis work that needed the stack is done, **always ask the
user whether to tear it down** — do not leave it running silently and do not
destroy it on your own. Offer the two levels:

```bash
./docker/dev/rspace-dev down              # stop, keep DB + caches (reversible)
./docker/dev/rspace-dev nuke              # destroy this worktree's volumes (confirm first)
```

Recommend `down` by default (fast to resume); reserve `nuke` for when the user
wants the local data and volumes gone. Only run either after the user confirms.

## Browser automation

Three browser-driving toolsets can target the running instance. None are built
into a coding agent by default — they come from MCP servers (and matching
skills) in the runtime, so **check what's actually connected** (your tool list /
`ToolSearch`)
before relying on one; in headless/cron runs none may be present, in which case
drive manually via devtools. They share an accessibility-tree model, so the
workflow is the same; pick by the job:

| Use when…                                              | Reach for          |
| ------------------------------------------------------ | ------------------ |
| UI flows, forms, upload, tabs — and `t3-code` is connected | **t3-code** `preview_*` (preferred) |
| Same, but no `t3-code` in your runtime (**default fallback**) | **Playwright** |
| Perf trace / Web Vitals, network or console forensics, throttling, Lighthouse (a11y/SEO) | **Chrome DevTools** |

**Prefer `t3-code` `preview_*` when it's present; otherwise default to
Playwright.** `t3-code` exists only in some runtimes and most developers won't
have it, so don't assume it — but when your tool list does show it, it's the
preferred driver. Playwright is the broadly-available fallback. (Chrome DevTools
is for diagnosis, per the table, regardless of which driver you use for flows.)

**The loop is always: navigate → snapshot → act on a ref → assert.** Snapshot
first (it returns element refs/selectors); act using those refs; re-snapshot or
wait to confirm. Don't act on coordinates from a stale snapshot.

First navigate to the app URL printed by `ps` (e.g. `http://localhost:8080`),
log in (`user1a` / `user1234`), then:

- **Playwright** (`browser_*`, default fallback): `browser_navigate`, `browser_snapshot`,
  `browser_click`, `browser_type`, `browser_fill_form`, `browser_select_option`,
  `browser_press_key`, `browser_file_upload`, `browser_wait_for`, `browser_tabs`,
  `browser_console_messages`, `browser_network_requests` /
  `browser_network_request`, `browser_evaluate`, `browser_take_screenshot`.
- **Chrome DevTools** (`mcp__chrome-devtools__*`): `navigate_page` (url/back/
  forward/reload), `new_page` / `list_pages` / `select_page`, `take_snapshot`
  (a11y tree with `uid`s — act on those), `click`, `fill` / `fill_form`,
  `type_text`, `hover`, `press_key`, `upload_file`, `handle_dialog`, `wait_for`.
  For diagnosis: `performance_start_trace` (set `reload`, then
  `navigate_page` first) / `performance_stop_trace` / `performance_analyze_insight`
  for Web Vitals (LCP/INP/CLS); `list_network_requests` (filter by `resourceTypes`,
  paginated) / `get_network_request`; `list_console_messages` /
  `get_console_message`; `evaluate_script`, `take_heapsnapshot`, `take_screenshot`.
  `emulate` is **one** combined tool — CPU slowdown, network conditions
  (`Slow 3G`…`Fast 4G`/`Offline`), viewport/mobile/touch, dark/light, geolocation,
  extra HTTP headers. `lighthouse_audit` covers a11y/SEO/best-practices and
  **excludes performance** — use `performance_start_trace` for that.
- **t3-code** (`preview_*`, preferred when connected): `preview_navigate`,
  `preview_snapshot` (state + console/network failures + screenshot),
  `preview_click`, `preview_type`, `preview_press`, `preview_scroll`,
  `preview_wait_for`, `preview_evaluate`, `preview_recording_start` /
  `preview_recording_stop`.

If a matching **skill** is installed (Playwright / Chrome DevTools), invoking it
is the higher-level entry point; calling the MCP tools directly (above) is the
lower-level one. Either works — use whichever is available.

### Debugging recipe

A symptom reported in the UI (step 5 reproduces it), then: capture the failing
request with `*_network_request*` and the stack via `logs app`; read browser
errors from the snapshot's console section or `*_console_messages`; for "it's
slow" use Chrome DevTools `performance_start_trace` around the action and
`performance_analyze_insight`. Take the concrete trace/request back to the code.

## Escape hatch

`./docker/dev/rspace-dev compose <args>` passes straight through to
`docker compose` for this stack.
