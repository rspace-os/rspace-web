# Dockerized RSpace dev stack (per worktree)

> **Experimental.** A newer convenience workflow, not yet the officially
> supported way to run RSpace from source. If you hit problems, fall back to the
> manual [Gradle/Jetty workflow](/DevDocs/DeveloperNotes/Gradle.md#jetty).

Boot a **complete** RSpace instance — database, Java/Jetty backend, and Vite
frontend — for the git worktree you are currently in, with one command. Your
worktree source is bind-mounted into the containers, so edits on your machine
are live: the frontend hot-reloads automatically (HMR), and the backend can be
hot-redeployed or restarted without rebuilding any image.

> This is for **local development only**. It uses throwaway credentials and is
> not hardened. For running RSpace as a real deployment, use
> [`rspace-docker`](https://github.com/rspace-os/rspace-docker).

## Why per-worktree

If you keep several git worktrees (e.g. one per branch/PR), each can run its own
fully isolated RSpace at the same time:

- a Compose **project name** derived from the worktree directory, so containers,
  networks, and volumes never collide;
- its **own database, build artifacts and Gradle caches** in named volumes;
- its **own host ports**, auto-allocated to be free and recorded in
  `docker/dev/.env` (git-ignored);
- its **own session cookie** (`JSESSIONID_<app port>`), so logging into one
  instance does not log you out of the others — browsers scope cookies by host
  only, and every instance lives on `localhost`.

Only the content-addressed pnpm store is shared. Each stack has its own Gradle
user home, project cache and outputs. Writable Gradle homes cannot be shared
across container network namespaces because Gradle coordinates cache locks over
loopback UDP. Each new stack downloads Gradle and Java dependencies on first boot;
later boots reuse its volumes. Host and container Gradle builds can run together.

On first run `rspace-dev` also creates the git-ignored
`src/main/resources/deployments/dev/deployment.properties` from the checked-in
`deployment.properties.example`, since Spring will not start without it. Edit the copy to add your
local credentials.

## Prerequisites

- Docker Engine + Docker Compose v2 (Docker Desktop on macOS/Windows works).
- Allow 8 GB free RAM per stack during compilation and several GB of disk per
  stack for Gradle dependencies and outputs. Steady-state usage is lower because
  Gradle exits before Jetty starts; reload temporarily adds a build JVM.
- No local Java, Gradle, Node, or MariaDB needed — it all runs in containers.
- **Windows: run this inside WSL2** — see [Windows (WSL2)](#windows-wsl2) below.
  The launcher is a bash script and will not run from PowerShell or `cmd`.

## Windows (WSL2)

This stack is driven by a bash launcher and runs Linux containers, so on Windows
use **WSL2** (the Windows Subsystem for Linux). Do not run `rspace-dev` from
PowerShell or `cmd`.

1. **Install WSL2 and a distro** (e.g. Ubuntu): in an admin PowerShell run
   `wsl --install`, then reboot and finish the Ubuntu first-run setup.
2. **Install Docker Desktop** and enable the WSL2 backend: Settings → General →
   *Use the WSL 2 based engine*, and Settings → Resources → WSL Integration →
   enable integration for your distro. This makes `docker` / `docker compose`
   available inside WSL2.
3. **Keep the repo on the Linux filesystem, not the Windows drive.** Clone (and
   create any git worktrees) under your WSL2 home, e.g. `~/code/rspace-web` —
   reachable from Windows as `\\wsl$\Ubuntu\home\<you>\code`. Working from a
   `/mnt/c/...` path is the most common WSL2 mistake: bind-mount file I/O there
   is very slow and filesystem change events are not delivered, so Gradle, the
   database, and Vite hot reload will all crawl or silently stop updating.
4. **Run everything from the WSL2 shell:**

   ```bash
   cd ~/code/rspace-web          # a path on the Linux filesystem
   ./docker/dev/rspace-dev up
   ```

5. **Open the app in your Windows browser** at the URL the launcher prints (e.g.
   `http://localhost:8111`). Docker Desktop forwards `localhost` from Windows
   into the WSL2 VM, so the published app/Vite ports and HMR work normally.

Notes:
- A `docker/dev/.gitattributes` forces LF line endings, so the scripts work even
  if the repo is cloned with Windows defaults — but cloning *inside* WSL2 is
  still recommended (it also avoids the `/mnt/c` performance trap above).
- File-watch polling defaults by platform: on in macOS/Windows shells, off on
  Linux (WSL2 counts as Linux — with the repo on the WSL2 filesystem, native
  file events reach the container, and polling would only burn CPU). If HMR
  misses changes, set `VITE_USE_POLLING=true` in `docker/dev/.env`.
- Give the WSL2 VM enough memory (Docker Desktop → Resources, or a `.wslconfig`
  with e.g. `memory=8GB`); the backend defaults to a 2 GB JVM heap.

## Quick start

From anywhere inside your worktree (on Windows, a WSL2 shell — see above):

```bash
./docker/dev/rspace-dev up
```

The first boot:

1. builds the backend and frontend images,
2. starts MariaDB and waits until it is healthy,
3. creates the schema and sample data (`drop-recreate-db`),
4. compiles and starts the backend, and installs frontend deps + starts Vite.

This takes several minutes the first time (Gradle and pnpm download
dependencies). Watch progress with:

```bash
./docker/dev/rspace-dev logs app
```

When you see Jetty report the app has started, open the URL printed by
`rspace-dev up` (e.g. `http://localhost:8080`). Log in with `user1a` /
`user1234`, or `sysadmin1` / `sysWisc23!`.

Subsequent `up`s reuse the existing database and are much faster.

To run the Playwright suite against local third-party integration mocks, enable
the stack's E2E mode instead:

```bash
./docker/dev/rspace-dev up --e2e
```

This starts the mock server in the frontend/backend shared network namespace
and points integrations including PubChem, Fieldmark, Zenodo, Galaxy, PyRAT,
OMERO, ROR, and the PIDINST registry lookups (DataCite and B2INST) at it. The
mock port is unique per worktree and published on host loopback, so the Java
backend and host Playwright process both use `http://localhost:<mock-port>`.
The choice persists in `docker/dev/.env`; use `up --no-e2e` to return to real
integration endpoints.

To run the API project inside the stack, install Chromium for its admin-page
fixtures and create the ignored auth-state directory. No host Node is needed:

```bash
./docker/dev/rspace-dev compose exec -T frontend pnpm exec playwright install --with-deps --only-shell chromium
./docker/dev/rspace-dev compose exec -T frontend mkdir -p /rspace/src/main/webapp/ui/playwright/.auth
./docker/dev/rspace-dev compose exec -T -e RSPACE_BASE_URL=http://localhost:8080 -e E2E_BROWSER=api frontend pnpm run test-e2e:api --reporter=list
```

The container uses port 8080 in the shared network namespace and inherits this
worktree's mock port. Browser installation lasts until the frontend container is
recreated.

E2E mode also supplies the local MinIO credentials. To run the filestore tests, start
their containers on this worktree's Docker network using the
[filestore setup instructions](../../src/main/webapp/ui/src/__tests__/e2e/README.md#filestore-and-ror-specs).
The default filestore script mode publishes host loopback ports, which the Docker backend cannot reach.

## Everyday commands

```bash
./docker/dev/rspace-dev up [--fresh]   # start (--fresh recreates the DB)
./docker/dev/rspace-dev up --chemistry # also start the chemistry microservice
./docker/dev/rspace-dev up --e2e       # start and use third-party integration mocks
./docker/dev/rspace-dev up --no-e2e    # stop using integration mocks
./docker/dev/rspace-dev up --mailpit   # also start the Mailpit SMTP catcher
./docker/dev/rspace-dev up --no-mailpit # stop using Mailpit
./docker/dev/rspace-dev logs [svc]     # follow logs: app | frontend | db | chemistry | mailpit
./docker/dev/rspace-dev ps             # status + URLs/ports for this worktree
./docker/dev/rspace-dev reload         # recompile Java + hot-redeploy webapp
./docker/dev/rspace-dev restart        # full backend JVM restart
./docker/dev/rspace-dev reset-db       # rebuild DB from scratch (destroys data)
./docker/dev/rspace-dev shell [svc]    # shell into a container (default: app)
./docker/dev/rspace-dev db             # MariaDB client for this worktree
./docker/dev/rspace-dev down           # stop containers (keeps DB + caches)
./docker/dev/rspace-dev nuke           # stop + delete this worktree's volumes
```

`rspace-dev compose <args>` passes anything straight through to
`docker compose` for this stack if you need an escape hatch.

## Optional: chemistry microservice

RSpace's ELN chemistry features (structure drawing, rendering, and chemical
search) are backed by a separate microservice
([`rspaceops/oss-chemistry`](https://hub.docker.com/r/rspaceops/oss-chemistry)).
It is **off by default**; enable it per worktree with:

```bash
./docker/dev/rspace-dev up --chemistry     # start it + point the app at it
./docker/dev/rspace-dev up --no-chemistry  # turn it off again
```

The choice persists in `docker/dev/.env` (`RSPACE_CHEMISTRY=true`), so plain
`up`/`down` keep honouring it. When enabled, the app is started with
`chemistry.provider=indigo` and `chemistry.service.url=http://chemistry:8090`;
the service is only reachable on the project's internal Docker network (no host
port). Its working data lives in a per-worktree `chemistry-files` volume,
removed by `nuke` like the others. Override the image with `CHEMISTRY_IMAGE` in
`.env`.

Note: toggling the flag changes the app container's environment, so the next
`up` recreates the backend (expect the usual startup wait).

## Optional: Mailpit SMTP catcher

RSpace sends real email for flows like password reset, forgotten username,
signup verification, and sharing notifications. Locally this is disabled by
default (`email.enabled=false` in `deployments/dev/deployment.properties`), enable [Mailpit](https://mailpit.axllent.org/), a lightweight
local SMTP catcher with a web UI and REST API:

```bash
./docker/dev/rspace-dev up --mailpit     # start it + point the app at it
./docker/dev/rspace-dev up --no-mailpit  # turn it off again
```

The choice persists in `docker/dev/.env` (`RSPACE_MAILPIT=true`). When
enabled, the app is started with `email.enabled=true` and `mail.emailHost`
pointed at Mailpit's SMTP listener (`mailpit:1025`, Mailpit is started with
`MP_SMTP_AUTH_ACCEPT_ANY=1`/`MP_SMTP_AUTH_ALLOW_INSECURE=1` so it accepts any
placeholder credentials. The web UI/API
is published at the per-worktree URL printed by `rspace-dev ps` (default
`http://localhost:8025`); open it in a browser to read captured mail, or point
the e2e suite's `MAILPIT_HTTP_URL` at it. Messages are not persisted — they
live only for the container's lifetime. Override the image with
`MAILPIT_IMAGE` in `.env`.

Note: toggling the flag changes the app container's environment, so the next
`up` recreates the backend (expect the usual startup wait).

## Destroying a worktree's instance

Two levels, both scoped to the worktree you run them from (they never touch
another worktree's instance):

```bash
# Stop it (reversible): remove the containers, KEEP this worktree's database,
# node_modules, Gradle caches and build outputs.
# `up` later resumes fast.
./docker/dev/rspace-dev down

# Destroy it: remove the containers AND this worktree's volumes — database (all
# local data), node_modules, search indices, filestore, Gradle home and outputs.
# The next `up` downloads dependencies and re-initialises the database.
./docker/dev/rspace-dev nuke
```

`nuke` runs `docker compose down --volumes` and removes the legacy
`<project>_app-target` volume if present. It only removes this project's volumes.
The shared pnpm store survives. Host build directories are never deleted.

The launcher reports an existing Maven-era `rspace-dev-m2-repo` volume but never
deletes it. Older branches may still need it. Once those stacks are retired,
remove it with `docker volume rm rspace-dev-m2-repo`. The old shared
`rspace-dev-gradle-cache` is also unused by this stack; only remove it once no
older Gradle stacks need it.

Verify it is gone:

```bash
./docker/dev/rspace-dev compose ps          # should list no containers
docker volume ls | grep "$(basename "$(git rev-parse --show-toplevel)")"   # none after nuke
```

Going further (rarely needed):

```bash
# Remove the locally-built dev images too (next up rebuilds them):
docker image rm rspace-dev-app:local rspace-dev-frontend:local

# Remove the SHARED pnpm store only when no stacks need it:
docker volume rm rspace-dev-pnpm-store
```

> After `nuke`, the generated `docker/dev/.env` (ports for this worktree) is
> left in place so the instance keeps the same ports if you `up` again. Delete
> it if you also want the ports re-allocated.

## How source sync works

The whole worktree is bind-mounted at `/rspace` in the `app` and `frontend`
containers. To keep host (often macOS/arm64) and Linux-container artifacts from
mixing for frontend dependencies, `node_modules/` uses a named volume. Search
indices and the filestore also use volumes. All Gradle state lives in named volumes outside `/rspace`, under
`/var/rspace-build`. The entrypoint creates a project view there with links to
live source and copies of the Gradle settings and build scripts. This keeps
buildSrc's default `.gradle` directory outside the host worktree too.
The Gradle user home is also per-stack. Container builds leave host outputs and
locks alone, so host/IDE builds can run concurrently. HotswapAgent watches the
container's volume-backed class and resource directories. Existing frontend
writes such as `ui/dist` and application log files still use the bind mount.

| You change…                | What to do                                              |
| -------------------------- | ------------------------------------------------------- |
| Frontend (`.ts/.tsx/.css`) | Nothing — Vite HMR updates the browser automatically.   |
| Java (`.java`)             | Per-class: your IDE's hot code replace (live, via JBR). Otherwise `rspace-dev reload`; `restart` for a clean JVM. See [Hot reloading Java](#hot-reloading-java-jbr--hotswapagent). |
| JSP                        | Nothing — Jetty recompiles changed JSPs on the next request. |
| Spring XML / web.xml       | `rspace-dev reload` (or `restart`).                     |
| Liquibase / DB schema      | `rspace-dev reset-db`, or `up --fresh`.                 |
| `package.json` deps        | `rspace-dev restart` the `frontend` (re-runs install).  |

> **HMR.** `vite.config.ts` reads `VITE_HMR_HOST`, `VITE_HMR_CLIENT_PORT`, and
> `VITE_USE_POLLING` (all env-gated, no effect on plain local dev). The compose
> file sets them so the browser's HMR socket targets the published Vite port and
> file changes are picked up via polling over the bind mount, giving true hot
> reload inside the container.

`reload` works because the backend runs Jetty directly with manual
redeploy mode; the entrypoint feeds Jetty's stdin from a FIFO, and `reload`
writes to it to trigger a webapp redeploy after recompiling.

## Debugging

### Backend (Java remote debug)

The backend JVM exposes a standard JDWP remote-debug port, **on by default**
(`server=y,suspend=n`, so it never blocks startup). Find this worktree's debug
port with `rspace-dev ps` (also printed by `up`):

```
Debug      127.0.0.1:5005  (JDWP — attach IntelliJ "Remote JVM Debug")
```

Each worktree gets its own auto-allocated debug port, so you can debug several
instances at the same time. In both IDEs below, **open this worktree's directory
as the project** — the container runs the very same files via the bind mount, so
breakpoints and stepping line up with no path mapping needed. Substitute your
worktree's port for `5005` everywhere below.

#### IntelliJ IDEA

1. Open the repo as a project and let it import the Gradle model from `build.gradle`.
2. **Run → Edit Configurations… → `+` → Remote JVM Debug.**
3. Fill in:
   - **Name:** `RSpace (Docker)`
   - **Debugger mode:** `Attach to remote JVM`
   - **Host:** `localhost`
   - **Port:** your debug port (e.g. `5005`)
   - **Use module classpath:** `rspace-web`
   - Leave the command-line args field at its default — it should read
     `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`,
     which is exactly what the container uses.
4. Click **OK**, then **Debug 'RSpace (Docker)'** (the bug icon). The console
   shows "Connected to the target VM". Set breakpoints and exercise the app.

To debug application startup, set `RSPACE_DEBUG_SUSPEND=y` (see knobs below) and
attach quickly after `restart` — the JVM waits for you before initialising.

#### VS Code

1. Install the **Extension Pack for Java** (includes *Language Support for Java*
   and *Debugger for Java*).
2. Open the repo folder; let the Java extension import the Gradle project (the
   status bar shows progress the first time).
3. Create `.vscode/launch.json` at the repo root (this path is git-ignored, so
   it stays a personal setting) with:

   ```jsonc
   {
     "version": "0.2.0",
     "configurations": [
       {
         "type": "java",
         "name": "Attach to RSpace (Docker)",
         "request": "attach",
         "hostName": "localhost",
         "port": "${input:rspaceDebugPort}",
         "projectName": "rspace-web"
       }
     ],
     "inputs": [
       {
         "id": "rspaceDebugPort",
         "type": "promptString",
         "description": "RSpace debug port (run: ./docker/dev/rspace-dev ps)",
         "default": "5005"
       }
     ]
   }
   ```

   The `${input:...}` prompts for the port on each attach, which keeps one config
   working across worktrees. If you only use one worktree, replace
   `"${input:rspaceDebugPort}"` with your fixed port (e.g. `5005`) and drop the
   `inputs` block.
4. Open **Run and Debug** (Ctrl/Cmd+Shift+D), pick **Attach to RSpace (Docker)**,
   press **F5**, and enter the port if prompted. Set breakpoints and go.

#### Debug knobs

Set in `docker/dev/.env`, then `rspace-dev restart`:

- `RSPACE_DEBUG=false` — turn the debug agent off entirely.
- `RSPACE_DEBUG_SUSPEND=y` — pause JVM startup until a debugger attaches (for
  debugging Spring startup / early initialisation). Default `n`.
- `RSPACE_HOTSWAP=false` — disable enhanced class redefinition (below).

### Frontend

Production sourcemaps are enabled by the dev server, so debug in the browser's
devtools (Sources panel) against the original `.ts`/`.tsx`. No port to expose —
it is served through the app origin you already browse.

## Hot reloading Java (JBR + HotswapAgent)

The backend runs on the **JetBrains Runtime (JBR)**, whose DCEVM-derived
*enhanced class redefinition* lets you redefine a loaded class with structural
changes (new/removed methods and fields), not just method bodies. RSpace is plain
Spring Framework (not Spring Boot), so this replaces what Boot devtools would do.

**Drive it from your IDE's debugger (hot code replace).** This is the supported
path: the IDE compiles the single class you changed and pushes the new bytecode
into the running JVM over the debug connection (JDWP).

1. Attach the debugger (see "Backend (Java remote debug)" above).
2. Enable reload-on-compile:
   - **IntelliJ:** Settings → Build, Execution, Deployment → Debugger → HotSwap →
     *Reload classes after compilation = Always*; tick *Build project before
     reloading*. Edit, then **Build → Recompile** (Ctrl/Cmd+Shift+F9).
   - **VS Code:** `"java.debug.settings.hotCodeReplace": "auto"`; save the file.
3. The change is live in the running app; the bean instance keeps its state.

**Reloads live this way:** method-body changes, and adding/removing/changing
methods and fields on existing classes (JBR enhanced redefinition, beyond plain
HotSpot HotSwap). The existing bean runs the new code; it is not re-created.

> **Two deliberate constraints, both because of RSpace's legacy stack:**
> - HotswapAgent's **Spring, Hibernate, and Proxy plugins are disabled** (see
>   `hotswap-agent.properties`). On RSpace's large XML/AOP context they break:
>   the Hibernate plugin breaks transaction binding at startup, the Spring plugin
>   throws mid-reload, and the Proxy plugin hits a Java 17 module error. So
>   **code** in existing beans reloads, but **bean definitions/wiring** do not.
> - File-watch auto-reload is off. A clean build or cache restore can replace
>   many class files at once. Use IDE hot code replace for individual classes,
>   or `reload` to compile and rebuild the context.

`reload` runs `classes` with the same container settings as startup, using a
single-use Gradle daemon and the same project cache as startup. Gradle has
already exited, so no build locks remain held while Jetty runs. Incremental
compilation reuses the startup state and preserves the startup environment,
including `drop-recreate-db`, and all resource-filter flags. `classes` does not
depend on `recreateDatabase`, so reload never resets the database. Unchanged
resources and classes stay present; incremental compilation may briefly remove
changed classes. Changed resource inputs can still cause `processResources` to
replace its outputs.
Dependency resolution stays online so newly added dependencies can be fetched.

### What still requires a full restart

HotswapAgent cannot apply everything. These need `rspace-dev reload` (rebuilds
the Spring context in the same JVM) or `rspace-dev restart` (new JVM):

- **New or re-wired Spring beans** — a new `@Component`/`@Service`/`@Repository`/
  `@Controller`/`@Bean`, changed `@Autowired`/`@Value` injection, new or changed
  `@RequestMapping`s, or `@Configuration` changes. The bean-reload plugin is
  disabled (it breaks RSpace's context), so the registry is fixed at startup. The
  *code* of an existing controller/service reloads live; its *registration* does
  not. → `reload` (often enough) or `restart`.
- **Spring XML config** (`applicationContext-*.xml`). RSpace wires most of its
  context — including the `*Manager` **AOP transaction proxies** — in XML, which
  HotswapAgent does not reload. Editing those needs `reload`/`restart`.
- **Infrastructure / framework beans**: the `DataSource`, Hibernate
  `SessionFactory`, transaction manager, the Liquibase bean, caches, the task
  executors — anything created once at context startup. → `restart`.
- **Hibernate entity mapping / Envers / Hibernate Search** changes (new or
  remapped `@Entity`, audited fields, index mappings). → `restart` (and possibly
  a `reset-db` if the schema changed).
- **`web.xml`, Jetty config, servlets/filters/listeners** (e.g. the MIME and
  decorator setup). → `restart`.
- **Liquibase changesets / DB schema.** → `rspace-dev reset-db`.
- **`build.gradle` / `gradle/` / dependency changes** (new jars on the classpath). → `restart`.
- **Compile-time constants and enums** used in `switch` (the value is inlined at
  call sites), and **`static` initialisers** / already-run `@PostConstruct` side
  effects on existing singletons. → `restart` if behaviour looks stale.
- **JVM / agent args** (`ORG_GRADLE_PROJECT_jettyJvmArgs`, `RSPACE_*` env, `hotswap-agent.properties`).
  → `restart` (a container recreate, since those are set at JVM launch).

Rule of thumb: Java *code* changes reload live; **configuration, schema, and
framework-wiring** changes do not. When in doubt, `reload`; if that still looks
wrong, `restart`.

> `rspace-dev reload` keeps the same JVM, so an attached debugger stays
> connected; `rspace-dev restart` is a new JVM, so re-attach afterward.
> `RSPACE_HOTSWAP=false` disables the agent (e.g. to rule it out while diagnosing
> a startup issue). JBR + HotswapAgent are pinned third-party components — see
> `Dockerfile.app` for versions.

## Troubleshooting

Symptom → fix. The remedies are detailed elsewhere in this README; this table
is the quick lookup.

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| First `up` takes several minutes | Gradle + pnpm downloading all deps into cold caches | Normal — watch `rspace-dev logs app`; later boots reuse the caches and are fast. |
| `java.net.BindException: Address already in use` | A stale process or another tool holds this worktree's port | Ports auto-allocate to free `127.0.0.1` ports stored in `docker/dev/.env`; delete `.env` to re-allocate, then `rspace-dev up`. Confirm with `rspace-dev ps`. |
| Backend OOMs / `OutOfMemoryError` in the app log | JVM heap too small for your workload | Raise `ORG_GRADLE_PROJECT_jettyJvmArgs` (default `-Xmx2g`) in `.env` and `rspace-dev restart`; give Docker Desktop a generous memory limit. |
| App won't start after a schema change; DB looks corrupted or out of sync | Old data/schema left from a previous migration | `rspace-dev reset-db` (or `up --fresh`) to rebuild the DB from scratch. |
| The `db` container keeps restarting | Corrupt data volume or interrupted init | `rspace-dev reset-db`; if it persists, `rspace-dev nuke` (destroys this worktree's volumes) then `up`. |
| Frontend edits don't appear in the browser | HMR not receiving file events (common on macOS/Windows or `/mnt/c` on WSL2) | Set `VITE_USE_POLLING=true` in `.env`, then recreate the frontend container so it re-reads the var: `rspace-dev compose up -d --force-recreate frontend` (`rspace-dev restart` only recreates the backend `app`). On WSL2 keep the repo on the Linux filesystem. |
| Java edits don't take effect after `reload` | Change needs a context rebuild or new JVM (see "What still requires a full restart") | `rspace-dev reload`, and if still stale `rspace-dev restart`. |
| E2E integration calls reach live services or fail DNS | The stack was started without its mock mode | Run `rspace-dev up --e2e`; use the mock port printed by `rspace-dev ps` when running Playwright. |
| Password reset / signup / notification emails never arrive anywhere | Mailpit not enabled — `email.enabled=false` by default locally, so mail is only logged | Run `rspace-dev up --mailpit`; read captured mail at the Mailpit URL printed by `rspace-dev ps`. |
| Docker Desktop using too much RAM/disk | Multiple stacks running, or large caches | `rspace-dev down` worktrees you aren't using; reclaim disk per "Destroying a worktree's instance". |

## Notes & gotchas

- **Ports**: chosen once per worktree and stored in `docker/dev/.env`. Delete
  that file to re-allocate (e.g. after freeing a port). All ports bind to
  `127.0.0.1` only.
- **E2E integration mocks**: `up --e2e` starts them beside Vite and persists
  `RSPACE_E2E_MOCKS=true`. `up --no-e2e` disables them. `rspace-dev ps` prints
  the per-worktree mock URL.
- **Mailpit**: `up --mailpit` starts it and persists `RSPACE_MAILPIT=true`.
  `up --no-mailpit` disables it. `rspace-dev ps` prints the per-worktree
  Mailpit URL.
- **Formatting**: `jettyRun` and `classes` do not run Spotless. Run
  `./gradlew spotlessApply` explicitly when needed.
- **Locale and JVM**: Gradle and Jetty run on the image's JBR under `C.UTF-8`.
  Do not set `LC_ALL=C` or select another Java toolchain for this stack.
- **JVM arguments**: `ORG_GRADLE_PROJECT_jettyJvmArgs` configures the separate
  Jetty JVM, including heap, JDWP and HotswapAgent. Arguments are separated by
  whitespace. Gradle's own heap is configured by `org.gradle.jvmargs`.
- **Memory**: the backend defaults to `-Xmx2g` (`ORG_GRADLE_PROJECT_jettyJvmArgs` in `.env`), and
  MariaDB uses a smaller InnoDB buffer pool than `rspace-docker` for local use
  (see `db/db-config.cnf`). Vite uses a large Node heap; give Docker Desktop a
  generous memory limit.
- **Image/tool overrides**: `DB_IMAGE`, `APP_BASE_IMAGE`, `NODE_IMAGE`, and
  `ORG_GRADLE_PROJECT_jettyJvmArgs` can be set in `.env` (see `.env.example`).
- **Session cookies**: each instance names its session cookie
  `JSESSIONID_<app port>` (via the `rs.session.cookie.name` system property),
  so concurrent localhost logins don't evict each other. Override with
  `RSPACE_SESSION_COOKIE_NAME` in `.env`. The same property works for a
  plain (non-Docker) `./gradlew jettyRun` if you run several checkouts at once:
  `-Drs.session.cookie.name=JSESSIONID_8081`.

## What this maps to

The stack reproduces the documented local workflow, just containerized:

- backend: `./gradlew --no-daemon jettyLaunchArgs` then `exec java @/var/rspace-build/app/jetty/launch.args`
  with the DB pointed at the `db` service and the Vite dev-server proxy pointed
  at `localhost:5173`. The app container shares the `frontend` container's
  network namespace, so "localhost" reaches Vite; this is also why Vite accepts
  the proxied requests (its host check trusts localhost) without a config change;
- frontend: `pnpm run serve` (from the repo root, where the deps live);
- database: MariaDB 11.8 by default, configured with the database settings from
  `rspace-docker` except for the smaller InnoDB buffer pool. CI also tests
  MariaDB 10.11. Set `DB_IMAGE=mariadb:10.11` in `docker/dev/.env` before the
  first `up` to run the dev stack against that version. Use a separate worktree
  for each version; do not reuse a database volume across MariaDB major versions.

The launch argfile uses the same options, properties, launcher classpath and
program arguments as `jettyRun`. Jetty is PID 1 and receives SIGTERM directly;
its shutdown hook stops Jetty and the Spring context. Compose allows 120 seconds
before forcing termination. No Gradle daemon remains resident after startup.

An existing `MAVEN_OPTS` entry in `.env` triggers a warning. Move its heap flags
and application `-D` properties, including PyRAT configuration, to
`ORG_GRADLE_PROJECT_jettyJvmArgs`. Gradle's build heap uses `org.gradle.jvmargs`.
The launcher never prints the old variable's value.

SIGTERM during an in-flight redeploy waits for Spring to finish starting before
Jetty stops it. The 120-second grace period allows slower shared machines to
finish; Docker kills the process if it exceeds that limit. Manual redeploy
invalidates logged-in sessions, matching Maven's redirect to login.
