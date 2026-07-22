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
if [ "${RSPACE_E2E_MOCKS:-false}" != "true" ]; then
  exec pnpm run serve
fi

echo "[entrypoint] Starting E2E mock server on ${E2E_MOCK_HOST:-0.0.0.0}:${E2E_MOCK_PORT:-9099} ..."
node src/main/webapp/ui/src/__tests__/e2e/mockServer.ts "${E2E_MOCK_PORT:-9099}" &
mock_pid=$!
pnpm run serve &
vite_pid=$!

stop_children() {
  kill "${mock_pid}" "${vite_pid}" 2>/dev/null || true
  wait "${mock_pid}" "${vite_pid}" 2>/dev/null || true
}
trap stop_children EXIT INT TERM

# The mock server and Vite are both required in E2E mode. If either exits, stop
# the other and let the container's restart policy bring the pair back together.
set +e
wait -n "${mock_pid}" "${vite_pid}"
status=$?
set -e
exit "${status}"
