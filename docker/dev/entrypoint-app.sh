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
# Add the ?v= cache-busting token to legacy /scripts & /styles asset URLs even in
# dev mode (default on). Set RSPACE_LEGACY_ASSET_CACHE_BUSTING=false when
# debugging legacy frontend assets so the browser always refetches.
LEGACY_ASSET_CACHE_BUSTING="${RSPACE_LEGACY_ASSET_CACHE_BUSTING:-true}"
# Rebuild the Lucene full-text index on startup (default off — it slows boot and
# is rarely needed in dev). Set RSPACE_INDEX_ON_STARTUP=true to force a reindex.
INDEX_ON_STARTUP="${RSPACE_INDEX_ON_STARTUP:-false}"
# Browsers scope cookies by host only, so concurrent worktree instances on
# different localhost ports would overwrite each other's JSESSIONID and log
# each other out. Each instance therefore gets a session cookie named after
# its (worktree-unique) host port.
SESSION_COOKIE_NAME="${RSPACE_SESSION_COOKIE_NAME:-JSESSIONID_${APP_PUBLIC_PORT}}"
# Volume-backed search index dirs (outside the bind mount). Under jetty:run the
# default index base is unset, so without these Lucene scatters per-entity index
# directories into the worktree root.
SEARCH_INDEX_DIR="${SEARCH_INDEX_DIR:-/var/rspace/search/hibernate}"
LUCENE_INDEX_DIR="${LUCENE_INDEX_DIR:-/var/rspace/search/lucene}"
FIFO=/tmp/jetty.in

# RSpace fails to start if these directories do not exist.
mkdir -p "${RS_FILE_BASE}" "${SEARCH_INDEX_DIR}" "${LUCENE_INDEX_DIR}"

# Put the HotswapAgent config on the webapp classpath (target/classes), so the
# agent reads it for the application's class loader. Copied on every start so it
# survives a fresh target volume.
if [ "${RSPACE_HOTSWAP:-true}" != "false" ] && [ -f /rspace/docker/dev/hotswap-agent.properties ]; then
  mkdir -p /rspace/target/classes
  cp /rspace/docker/dev/hotswap-agent.properties /rspace/target/classes/hotswap-agent.properties
fi

# Set up the redeploy FIFO and hold it open for writing so the reader (Jetty)
# never receives EOF. The background writer blocks until the `exec` below opens
# the read end, at which point both rendezvous and the script continues.
rm -f "${FIFO}"
mkfifo "${FIFO}"
# (A plain loop rather than `sleep infinity`, which is GNU-coreutils-only and
# would break if MAVEN_IMAGE is overridden to a BusyBox/Alpine-based image.)
( exec > "${FIFO}"; while :; do sleep 3600; done ) &

# Remote JVM debugging (JDWP) for IDEs such as IntelliJ's "Remote JVM Debug".
# jetty:run runs in this Maven JVM, so putting the agent on MAVEN_OPTS debugs the
# app. Enabled by default; set RSPACE_DEBUG=false to disable. suspend=n so the
# app starts without waiting for a debugger (set RSPACE_DEBUG_SUSPEND=y to pause
# startup until the IDE attaches). It listens on 5005 in the container, published
# on the host as this worktree's debug port.
if [ "${RSPACE_DEBUG:-true}" != "false" ]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} -agentlib:jdwp=transport=dt_socket,server=y,suspend=${RSPACE_DEBUG_SUSPEND:-n},address=*:5005"
  echo "[entrypoint] JDWP debug listening on container port 5005 (suspend=${RSPACE_DEBUG_SUSPEND:-n})"
fi

# Enhanced class redefinition via JetBrains Runtime (DCEVM) + HotswapAgent. The
# app runs in this Maven JVM (JBR), so the flags go on MAVEN_OPTS. This lets the
# IDE's hot code replace redefine changed classes (incl. structural changes) in
# the running JVM over JDWP. See hotswap-agent.properties for which plugins are
# disabled, and README "Hot reloading Java". Set RSPACE_HOTSWAP=false to disable.
if [ "${RSPACE_HOTSWAP:-true}" != "false" ]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=external -javaagent:/opt/hotswap/hotswap-agent.jar"
  echo "[entrypoint] HotswapAgent enabled (JBR enhanced class redefinition)"
fi

# Optional chemistry microservice (rspace-dev up --chemistry). Points the app's
# chemistry features at the in-network service; with the flag off the chemistry.*
# properties stay at their (empty/disabled) defaults.
CHEMISTRY_ARGS=()
if [ "${RSPACE_CHEMISTRY:-false}" = "true" ]; then
  CHEMISTRY_ARGS=(
    -Dchemistry.provider=indigo
    "-Dchemistry.service.url=${CHEMISTRY_SERVICE_URL:-http://chemistry:8090}"
  )
  echo "[entrypoint] Chemistry service enabled at ${CHEMISTRY_SERVICE_URL:-http://chemistry:8090}"
fi

echo "[entrypoint] Starting RSpace (db mode: ${DB_MODE}) ..."
echo "[entrypoint] App will be reachable on the host at http://localhost:${APP_PUBLIC_PORT}"

# jetty:run executes the Maven lifecycle up to test-compile in this same JVM, so
# the -D system properties below reach Spring at runtime and the Maven build
# plugins (e.g. the drop-recreate-db SQL step) at build time.
exec mvn -B jetty:run \
  "${CHEMISTRY_ARGS[@]}" \
  -Denvironment="${DB_MODE}" \
  -Dspring.profiles.active=run \
  -DreactDevMode=true \
  -DviteDevServerOrigin="${VITE_ORIGIN}" \
  -Djdbc.url="${JDBC_URL}" \
  -Djdbc.url.maven="${JDBC_URL_MAVEN}" \
  -DRS_FILE_BASE="${RS_FILE_BASE}" \
  -Drs.hibernate.searchIndex.folder="${SEARCH_INDEX_DIR}" \
  -Drs.attachment.lucene.index.dir="${LUCENE_INDEX_DIR}" \
  -DlegacyAssetCacheBustingInDevMode="${LEGACY_ASSET_CACHE_BUSTING}" \
  -Drs.indexOnstartup="${INDEX_ON_STARTUP}" \
  -Dserver.urls.prefix="http://localhost:${APP_PUBLIC_PORT}" \
  -Drs.session.cookie.name="${SESSION_COOKIE_NAME}" \
  -DRS.devlogLevel=INFO \
  -Dlog4j2.configurationFile=log4j2-dev.xml \
  < "${FIFO}"
