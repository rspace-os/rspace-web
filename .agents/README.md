# Agent Infrastructure

This directory contains tooling and configuration for AI coding agents
(Claude Code, GitHub Copilot, Codex CLI, Cursor, etc.) working on the
RSpace codebase. Agent _instructions_ live in `AGENTS.md` at the repo root;
this directory holds the supporting scripts and skills those instructions
reference.

## Directory layout

```
.agents/
  scripts/
    agent-db.sh    # Isolated MariaDB container lifecycle
  skills/
    rspace-empty-integration/   # Scaffold a new RSpace integration
```

## Isolated database (`scripts/agent-db.sh`)

When an agent works in a **git worktree** and needs a database (Spring
integration tests, IT tests, or running the app), it uses this script to
spin up a disposable MariaDB container instead of touching the developer's
local database.

### How it works

| Step | What happens |
|------|-------------|
| Worktree detection | Compares `git rev-parse --show-toplevel` with `--git-common-dir` — if they differ, you're in a worktree. |
| Container naming | `rspace-agent-db-<worktree-basename>` — deterministic and recomputable. |
| Port allocation | A random free port, stored in `.agent-db.env` in the worktree root. |
| JDBC patching | The script writes a `jdbc.url` line into `deployment.properties` (the worktree's own copy). |
| Schema init | Runs `mvn test -Denvironment=drop-recreate-db` with a no-op test to trigger Liquibase. |
| Garbage collection | On every `start`, the script removes containers whose worktree directory no longer exists. |

### Lifecycle

Containers are scoped **per worktree**, not per session or test run. A
second session in the same worktree reuses the existing container. The
container persists until explicitly stopped or until garbage collection
detects its worktree has been deleted.

### Commands

```bash
.agents/scripts/agent-db.sh start    # Idempotent — creates or reuses
.agents/scripts/agent-db.sh stop     # Stops container, restores config
.agents/scripts/agent-db.sh status   # Shows current state
.agents/scripts/agent-db.sh gc       # Removes orphaned containers
```

### When does this NOT apply?

- **Main checkout** — agents use the developer's local MariaDB as-is.
- **Pure unit tests** (`-Dfast=true`) — no database involved.
- **Docker unavailable** — the script exits with an error; agents fall back
  to the local database defaults in `defaultDeployment.properties`.

### Troubleshooting

**Orphaned containers after a crash:**
```bash
# From any worktree or the main checkout:
docker ps -a --filter "name=rspace-agent-db-" --format '{{.Names}}'

# Remove all orphans:
.agents/scripts/agent-db.sh gc

# Or manually:
docker rm -f <container-name>
```

**Port conflict / stale state:**
Delete `.agent-db.env` from the worktree root and run `start` again — it
will detect the running container and recover the port, or create a fresh
one.

## Skills (`skills/`)

Reusable agent playbooks. Each skill is a directory with a `SKILL.md`
(YAML frontmatter: `name` + `description`) and optional `REFERENCE.md`.

| Skill | Purpose |
|-------|---------|
| `rspace-empty-integration` | Scaffold a new RSpace integration (Liquibase, toggles, Apps card, TinyMCE icon). |

See `AGENTS.md` section "Repo-Local Agent Skills" for discovery and usage
conventions.
