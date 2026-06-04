#!/usr/bin/env bash
# Entrypoint for the RSpace backend dev container.
#
# Launches `mvn jetty:run` against the bind-mounted worktree at /rspace.
#
# Jetty's Maven plugin is configured (in pom.xml) with <scan>0</scan>, which
# means "redeploy the webapp when a line is read on stdin". We keep a FIFO open
# as Jetty's stdin so that:
#   * Jetty never sees EOF (which would otherwise spin or disable redeploys), and
#   * `rspace-dev reload` can trigger a fast webapp redeploy by writing a line
#     to the FIFO, without a full JVM/container restart.
set -euo pipefail

RS_FILE_BASE="${RS_FILE_BASE:-/var/rspace/filestore}"
DB_MODE="${RSPACE_DB_MODE:-keepdbintact}"
JDBC_URL="${JDBC_URL:-jdbc:mysql://db:3306/rspace}"
JDBC_URL_MAVEN="${JDBC_URL_MAVEN:-jdbc:mysql://db:3306}"
VITE_ORIGIN="${VITE_ORIGIN:-http://localhost:5173}"
APP_PUBLIC_PORT="${APP_PUBLIC_PORT:-8080}"
FIFO=/tmp/jetty.in

# RSpace fails to start if the filestore base directory does not exist.
mkdir -p "${RS_FILE_BASE}"

# Set up the redeploy FIFO and hold it open for writing so the reader (Jetty)
# never receives EOF. The background writer blocks until the `exec` below opens
# the read end, at which point both rendezvous and the script continues.
rm -f "${FIFO}"
mkfifo "${FIFO}"
( exec sleep infinity > "${FIFO}" ) &

echo "[entrypoint] Starting RSpace (db mode: ${DB_MODE}) ..."
echo "[entrypoint] App will be reachable on the host at http://localhost:${APP_PUBLIC_PORT}"

# jetty:run executes the Maven lifecycle up to test-compile in this same JVM, so
# the -D system properties below reach Spring at runtime and the Maven build
# plugins (e.g. the drop-recreate-db SQL step) at build time.
exec mvn -B jetty:run \
  -Denvironment="${DB_MODE}" \
  -Dspring.profiles.active=run \
  -DreactDevMode=true \
  -DviteDevServerOrigin="${VITE_ORIGIN}" \
  -Djdbc.url="${JDBC_URL}" \
  -Djdbc.url.maven="${JDBC_URL_MAVEN}" \
  -DRS_FILE_BASE="${RS_FILE_BASE}" \
  -Dserver.urls.prefix="http://localhost:${APP_PUBLIC_PORT}" \
  -DRS.devlogLevel=INFO \
  -Dlog4j2.configurationFile=log4j2-dev.xml \
  < "${FIFO}"
