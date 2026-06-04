# Dockerized RSpace dev stack (per worktree)

> **Experimental.** A newer convenience workflow, not yet the officially
> supported way to run RSpace from source. If you hit problems, fall back to the
> manual Maven/Jetty workflow in the
> [Getting Started guide](/DevDocs/DeveloperNotes/GettingStarted/GettingStarted.md).

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
- its **own database and build artifacts** (separate volumes);
- its **own host ports**, auto-allocated to be free and recorded in
  `docker/dev/.env` (git-ignored).

Heavy, safe-to-share caches (the Maven `~/.m2` repository and the pnpm store)
are reused across worktrees so you only download dependencies once.

## Prerequisites

- Docker Engine + Docker Compose v2 (Docker Desktop on macOS/Windows works).
- ~6 GB free RAM for the stack and a few GB of disk for caches.
- No local Java, Maven, Node, or MariaDB needed — it all runs in containers.
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
   is very slow and filesystem change events are not delivered, so Maven, the
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
- File watching already uses polling (`VITE_USE_POLLING=true`), which is what
  makes HMR reliable across the WSL2/Windows boundary.
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

This takes several minutes the first time (Maven and pnpm download
dependencies). Watch progress with:

```bash
./docker/dev/rspace-dev logs app
```

When you see Jetty report the app has started, open the URL printed by
`rspace-dev up` (e.g. `http://localhost:8080`). Log in with `user1a` /
`user1234`, or `sysadmin1` / `sysWisc23!`.

Subsequent `up`s reuse the existing database and are much faster.

## Everyday commands

```bash
./docker/dev/rspace-dev up [--fresh]   # start (──fresh recreates the DB)
./docker/dev/rspace-dev logs [svc]     # follow logs: app | frontend | db
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

## Destroying a worktree's instance

Two levels, both scoped to the worktree you run them from (they never touch
another worktree's instance):

```bash
# Stop it (reversible): remove the containers, KEEP this worktree's database,
# node_modules, build output, and the shared caches. `up` later resumes fast.
./docker/dev/rspace-dev down

# Destroy it: remove the containers AND this worktree's volumes — database (all
# local data), node_modules, build output, search indices, and filestore. The
# next `up` re-initialises the database from scratch.
./docker/dev/rspace-dev nuke
```

`nuke` is `docker compose down --volumes` under the hood. It removes only the
per-worktree volumes (named `rspace-<worktree>_*`). The **shared** Maven (`.m2`)
and pnpm caches (`rspace-dev-m2-repo`, `rspace-dev-pnpm-store`) are declared
`external`, so `nuke` never deletes them and other worktrees are unaffected.

Verify it is gone:

```bash
./docker/dev/rspace-dev compose ps          # should list no containers
docker volume ls | grep "$(basename "$(git rev-parse --show-toplevel)")"   # none after nuke
```

Going further (rarely needed):

```bash
# Remove the locally-built dev images too (next up rebuilds them):
docker image rm rspace-dev-app:local rspace-dev-frontend:local

# Remove the SHARED caches — affects EVERY worktree (they will re-download
# Maven/npm deps on next up). Only do this to reclaim disk or force a clean cache:
docker volume rm rspace-dev-m2-repo rspace-dev-pnpm-store
```

> After `nuke`, the generated `docker/dev/.env` (ports for this worktree) is
> left in place so the instance keeps the same ports if you `up` again. Delete
> it if you also want the ports re-allocated.

## How source sync works

The whole worktree is bind-mounted at `/rspace` in the `app` and `frontend`
containers. To keep host (often macOS/arm64) and Linux-container artifacts from
mixing, these paths are masked with named volumes instead of the bind mount:
`target/`, `node_modules/`, the search-index dirs, and the filestore.

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

`reload` works because the backend runs `mvn jetty:run` with Jetty's manual
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

1. Open the repo as a project and let it import the Maven model (this is the
   `rspace-web` Maven module).
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
2. Open the repo folder; let the Java extension import the Maven project (the
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
into the running JVM over the debug connection (JDWP) — it never touches the
container filesystem, so it sidesteps the Maven/bind-mount issue below.

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
> - There is **no in-container "recompile" command**: Maven's incremental build
>   does not work over the bind mount (a one-line edit recompiles ~2000 classes),
>   so file-watch auto-reload is off. Use the IDE path above, or `reload`.

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
- **`pom.xml` / dependency changes** (new jars on the classpath). → `restart`.
- **Compile-time constants and enums** used in `switch` (the value is inlined at
  call sites), and **`static` initialisers** / already-run `@PostConstruct` side
  effects on existing singletons. → `restart` if behaviour looks stale.
- **JVM / agent args** (`MAVEN_OPTS`, `RSPACE_*` env, `hotswap-agent.properties`).
  → `restart` (a container recreate, since those are set at JVM launch).

Rule of thumb: Java *code* changes reload live; **configuration, schema, and
framework-wiring** changes do not. When in doubt, `reload`; if that still looks
wrong, `restart`.

> `rspace-dev reload` keeps the same JVM, so an attached debugger stays
> connected; `rspace-dev restart` is a new JVM, so re-attach afterward.
> `RSPACE_HOTSWAP=false` disables the agent (e.g. to rule it out while diagnosing
> a startup issue). JBR + HotswapAgent are pinned third-party components — see
> `Dockerfile.app` for versions.

## Notes & gotchas

- **Ports**: chosen once per worktree and stored in `docker/dev/.env`. Delete
  that file to re-allocate (e.g. after freeing a port). All ports bind to
  `127.0.0.1` only.
- **Spotless**: `jetty:run` runs `spotless:apply` during compile, exactly as a
  local Maven run does. If you have unformatted Java, it may reformat files in
  your worktree.
- **First concurrent boots**: if you `up` two brand-new worktrees at the exact
  same time they briefly contend on the shared Maven cache while it warms. Boot
  one first, or just retry — it is harmless.
- **Memory**: the backend defaults to `-Xmx2g` (`MAVEN_OPTS` in `.env`). Vite
  uses a large Node heap; give Docker Desktop a generous memory limit.
- **Image/tool overrides**: `DB_IMAGE`, `MAVEN_IMAGE`, `NODE_IMAGE`, and
  `MAVEN_OPTS` can be set in `.env` (see `.env.example`).

## What this maps to

The stack reproduces the documented local workflow, just containerized:

- backend: `mvn jetty:run -Dspring.profiles.active=run -DreactDevMode=true`
  with the DB pointed at the `db` service and the Vite dev-server proxy pointed
  at `localhost:5173`. The app container shares the `frontend` container's
  network namespace, so "localhost" reaches Vite; this is also why Vite accepts
  the proxied requests (its host check trusts localhost) without a config change;
- frontend: `pnpm run serve` (from the repo root, where the deps live);
- database: MariaDB configured like production (`utf8mb4`, strict `sql-mode`).
