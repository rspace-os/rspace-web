#!/usr/bin/env bash
# Entrypoint for the RSpace frontend (Vite) dev container.
#
# All frontend dependencies are declared in the repo-root package.json, so we
# install and serve from /rspace (the bind-mounted worktree root). Dependencies
# land in the `node_modules` named volume; the pnpm store is a shared volume.
set -euo pipefail

cd /rspace

echo "[entrypoint] Installing frontend dependencies (pnpm) ..."
corepack enable >/dev/null 2>&1 || true
# --frozen-lockfile keeps installs deterministic and fast once the node_modules
# volume is warm. The root `prepare` script (scripts/install-git-hooks.mjs)
# detects there is no accessible git repo here and skips the lefthook hook
# install, so a normal install succeeds inside the container.
pnpm install --frozen-lockfile

echo "[entrypoint] Starting Vite dev server on 0.0.0.0:5173 (HMR client port ${VITE_HMR_CLIENT_PORT:-5173}) ..."
exec pnpm run serve
