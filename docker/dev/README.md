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

## Quick start

From anywhere inside your worktree:

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

## How source sync works

The whole worktree is bind-mounted at `/rspace` in the `app` and `frontend`
containers. To keep host (often macOS/arm64) and Linux-container artifacts from
mixing, these paths are masked with named volumes instead of the bind mount:
`target/`, `node_modules/`, the search-index dirs, and the filestore.

| You change…                | What to do                                              |
| -------------------------- | ------------------------------------------------------- |
| Frontend (`.ts/.tsx/.css`) | Nothing — Vite HMR updates the browser automatically.   |
| Java (`.java`)             | `rspace-dev reload` (recompile + hot redeploy), or `restart` for a clean JVM. |
| JSP / templates / config   | `rspace-dev reload` (or `restart`).                     |
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
