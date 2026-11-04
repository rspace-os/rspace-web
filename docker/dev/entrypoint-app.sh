#!/usr/bin/env bash
# Entrypoint for the RSpace backend dev container.
#
# Builds with Gradle, then runs Jetty directly as PID 1.
#
# Jetty redeploys the webapp for each line on stdin. A FIFO keeps stdin open
# and lets `rspace-dev reload` request a redeploy without restarting the JVM.
set -euo pipefail

RS_FILE_BASE="${RS_FILE_BASE:-/var/rspace/filestore}"
DB_MODE="${RSPACE_DB_MODE:-keepdbintact}"
JDBC_URL="${JDBC_URL:-jdbc:mysql://db:3306/rspace}"
JDBC_SERVER_URL="${JDBC_SERVER_URL:-jdbc:mysql://db:3306}"
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
# Volume-backed search index dirs (outside the bind mount). Under jettyRun the
# default index base is unset, so without these Lucene scatters per-entity index
# directories into the worktree root.
SEARCH_INDEX_DIR="${SEARCH_INDEX_DIR:-/var/rspace/search/hibernate}"
LUCENE_INDEX_DIR="${LUCENE_INDEX_DIR:-/var/rspace/search/lucene}"
FIFO=/tmp/jetty.in

# Refuse both startup and --compile without the isolated volumes. Never fall back
# to root-owned build outputs or caches in the source bind mount.
for dir in /var/rspace-build/{app,buildsrc,gradle-home,project}; do
  if ! awk -v path="${dir}" '$5 == path { found=1 } END { exit !found }' /proc/self/mountinfo; then
    echo "[entrypoint] Refusing to build: ${dir} is not mounted. Run via docker/dev/rspace-dev with its build volumes." >&2
    exit 1
  fi
done
for dir in /var/rspace-build/project/.gradle /var/rspace-build/project/buildSrc/.gradle; do
  if ! awk -v path="${dir}" '$5 == path { found=1 } END { exit !found }' /proc/self/mountinfo; then
    echo "[entrypoint] Refusing to build: ${dir} cache volume is not mounted." >&2
    exit 1
  fi
done
export GRADLE_USER_HOME=/var/rspace-build/gradle-home
# Gradle 9.8 creates buildSrc's cache service before init scripts can change its
# StartParameter. Give it an external project directory, with links to live source.
# No mountpoint or symlink is created inside the host worktree.
#
# Only what the build reads is linked in. Anything else, such as RSLogs.txt or archive/,
# which the app writes relative to its working directory, then stays in this volume
# rather than reaching the worktree as root-owned files. Build scripts are copied, not
# linked, and every sync removes links and copies whose source has gone.
PROJECT=/var/rspace-build/project
PROJECT_LINKS=(src gradle gradlew gradle.properties jetty)
PROJECT_COPIES=(build.gradle settings.gradle)
sync_entry() { # source target copy|link
  if [ ! -e "$1" ]; then
    rm -rf "$2"
  elif [ "$3" = copy ]; then
    rm -rf "$2"
    cp -p "$1" "$2"
  else
    # rm first: ln -sfn onto a real directory would create the link inside it
    [ -L "$2" ] || rm -rf "$2"
    ln -sfn "$1" "$2"
  fi
}
for entry in "${PROJECT}"/* "${PROJECT}"/.[!.]*; do
  [ -L "${entry}" ] || continue
  name="${entry##*/}"
  case " ${PROJECT_LINKS[*]} " in *" ${name} "*) ;; *) rm -f "${entry}" ;; esac
done
for name in "${PROJECT_LINKS[@]}"; do sync_entry "/rspace/${name}" "${PROJECT}/${name}" link; done
for name in "${PROJECT_COPIES[@]}"; do sync_entry "/rspace/${name}" "${PROJECT}/${name}" copy; done
mkdir -p "${PROJECT}/buildSrc"
for entry in "${PROJECT}"/buildSrc/*; do
  name="${entry##*/}"
  [ -e "/rspace/buildSrc/${name}" ] || rm -rf "${entry}"
done
for source in /rspace/buildSrc/*; do
  name="${source##*/}"
  case "${name}" in
    build) continue ;;
    *.gradle) sync_entry "${source}" "${PROJECT}/buildSrc/${name}" copy ;;
    *) sync_entry "${source}" "${PROJECT}/buildSrc/${name}" link ;;
  esac
done
cd "${PROJECT}"
GRADLE_BUILD_ARGS=(--project-dir "${PROJECT}" --no-daemon --console=plain
  --init-script /rspace/docker/dev/gradle-state.init.gradle)

# RSpace fails to start if these directories do not exist.
mkdir -p "${RS_FILE_BASE}" "${SEARCH_INDEX_DIR}" "${LUCENE_INDEX_DIR}"

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

# Optional third-party integration mocks (rspace-dev up --e2e). The mock
# server runs beside Vite in the network namespace shared by both containers,
# so localhost is intentionally correct here.
# Keep this list in sync with the mock block in .github/workflows/e2e.yml.
E2E_MOCK_ARGS=()
if [ "${RSPACE_E2E_MOCKS:-false}" = "true" ]; then
  E2E_MOCK_URL="http://localhost:${E2E_MOCK_PORT:-9099}"
  E2E_MOCK_ARGS=(
    "-Dpubchem.base.url=${E2E_MOCK_URL}"
    "-Dfieldmark.api.url=${E2E_MOCK_URL}"
    "-Dsnapgene.web.url=${E2E_MOCK_URL}"
    "-Dzenodo.url=${E2E_MOCK_URL}"
    "-Dgalaxy.server.config=[{\"alias\":\"mock\",\"url\":\"${E2E_MOCK_URL}\"}]"
    "-Dpyrat.server.config={\"mock\":{\"url\":\"${E2E_MOCK_URL}\",\"token\":\"mock-server-token\"}}"
    "-Domero.api.url=${E2E_MOCK_URL}"
    "-Ddmptool.base.url=${E2E_MOCK_URL}"
    "-Ddmpassistant.base.url=${E2E_MOCK_URL}/dmpassistant"
    "-Ddmponline.base.url=${E2E_MOCK_URL}/dmponline"
    "-Draid.server.config={\"mock\":{\"url\":\"${E2E_MOCK_URL}/raid-api\",\"authUrl\":\"${E2E_MOCK_URL}/raid-auth\",\"servicePointId\":1,\"clientId\":\"mock-client\",\"clientSecret\":\"mock-secret\"}}"
    "-Ddryad.base.url=${E2E_MOCK_URL}/dryad"
    "-Ddryad.client.id=mock-client"
    "-Ddryad.client.secret=mock-secret"
    "-Dclustermarket.api.url=${E2E_MOCK_URL}/calira-api/"
    "-Dclustermarket.web.url=${E2E_MOCK_URL}/calira-web/"
    "-Dclustermarket.client.id=mock-client"
    "-Dclustermarket.secret=mock-secret"
    "-Dargos.url=${E2E_MOCK_URL}/argos-api/public"
    "-Ddcd.base.url=${E2E_MOCK_URL}/dcd"
    "-Ddcd.client.id=mock-client"
    "-Ddcd.client.secret=mock-secret"
    "-Degnyte.client.id=mock-client"
    "-Downcloud.url=${E2E_MOCK_URL}/owncloud"
    "-Downcloud.client.id=mock-client"
    "-Downcloud.secret=mock-secret"
    "-Dnextcloud.url=${E2E_MOCK_URL}/nextcloud"
    "-Dnextcloud.client.id=mock-client"
    "-Dnextcloud.secret=mock-secret"
    "-Dnextcloud.server.name=Nextcloud"
    "-Dprotocolsio.client.id=mock-client"
    "-Dprotocolsio.secret=mock-secret"
    "-Dprotocolsio.oauth.authorize.url=${E2E_MOCK_URL}/protocolsio/oauth/authorize"
    "-Dprotocolsio.oauth.token.url=${E2E_MOCK_URL}/protocolsio/oauth/token"
    "-Dgithub.client.id=mock-client"
    "-Dgithub.secret=mock-secret"
    "-Dgithub.oauth.authorize.url=${E2E_MOCK_URL}/github/oauth/authorize"
    "-Dgithub.oauth.access.token.url=${E2E_MOCK_URL}/github/oauth/token"
    "-Dgithub.api.base.url=${E2E_MOCK_URL}/github-api"
    "-Dorcid.oauth.authorize.url=${E2E_MOCK_URL}/orcid/oauth/authorize"
    "-Dorcid.oauth.token.url=${E2E_MOCK_URL}/orcid/oauth/token"
    "-Dslack.client.id=mock-client"
    "-Dslack.secret=mock-secret"
    "-Dslack.signing.secret=mock-signing-secret"
    "-Dslack.oauth.authorize.url=${E2E_MOCK_URL}/slack/oauth/authorize"
    "-Dslack.api.base.url=${E2E_MOCK_URL}/slack-api"
    "-Dror.api.url=${E2E_MOCK_URL}/ror"
    "-Dpidinst.lookup.datacite.url=${E2E_MOCK_URL}"
    "-Dpidinst.lookup.b2inst.url=${E2E_MOCK_URL}"
    "-Dnetfilestores.s3.global.credentials.accessKey=rspacetest"
    "-Dnetfilestores.s3.global.credentials.secretKey=rspacetestsecret"
  )
  echo "[entrypoint] E2E integration mocks enabled at ${E2E_MOCK_URL}"
fi

# Optional Mailpit SMTP catcher (rspace-dev up --mailpit).
MAILPIT_ARGS=()
if [ "${RSPACE_MAILPIT:-false}" = "true" ]; then
  MAILPIT_ARGS=(
    -Demail.enabled=true
    -Dmail.emailHost=mailpit
    -Dmail.port=1025
    -Dmail.emailAccount=mailpit
    -Dmail.password=mailpit
    -Dmail.ssl.enabled=false
    -Dmail.from=noreply@rspace-e2e.local
  )
  echo "[entrypoint] Mailpit enabled — SMTP at mailpit:1025, UI/API on the published host port"
fi

if [ "${1:-}" = "--compile" ]; then
  # DB_MODE only selects the startup's resource-filter settings here: compiling never
  # touches the database.
  echo "[entrypoint] Compiling RSpace with the ${DB_MODE} startup's build settings (no database changes) ..."
else
  echo "[entrypoint] Starting RSpace (db mode: ${DB_MODE}) ..."
  echo "[entrypoint] App will be reachable on the host at http://localhost:${APP_PUBLIC_PORT}"
fi

# The same build settings must reach resource filtering during reload, otherwise
# `classes` would replace the running app's resources with non-container defaults.
GRADLE_ARGS=(
  "${CHEMISTRY_ARGS[@]}"
  "${E2E_MOCK_ARGS[@]}"
  "${MAILPIT_ARGS[@]}"
  -Denvironment="${DB_MODE}"
  -Dspring.profiles.active=run
  -DreactDevMode=true
  -DviteDevServerOrigin="${VITE_ORIGIN}"
  -Djdbc.url="${JDBC_URL}"
  -Djdbc.url.maven="${JDBC_SERVER_URL}"
  -DRS_FILE_BASE="${RS_FILE_BASE}"
  -Drs.hibernate.searchIndex.folder="${SEARCH_INDEX_DIR}"
  -Drs.attachment.lucene.index.dir="${LUCENE_INDEX_DIR}"
  -DlegacyAssetCacheBustingInDevMode="${LEGACY_ASSET_CACHE_BUSTING}"
  -Drs.indexOnstartup="${INDEX_ON_STARTUP}"
  -Dserver.urls.prefix="http://localhost:${APP_PUBLIC_PORT}"
  -Drs.session.cookie.name="${SESSION_COOKIE_NAME}"
  -DRS.devlogLevel=INFO
  -Dlog4j2.configurationFile=log4j2-dev.xml
)

if [ "${1:-}" = "--compile" ]; then
  exec ./gradlew "${GRADLE_BUILD_ARGS[@]}" classes "${GRADLE_ARGS[@]}"
fi

export ORG_GRADLE_PROJECT_jettyJvmArgs="${ORG_GRADLE_PROJECT_jettyJvmArgs:--Xmx2g}"
if [ "${RSPACE_DEBUG:-true}" != "false" ]; then
  ORG_GRADLE_PROJECT_jettyJvmArgs+=" -agentlib:jdwp=transport=dt_socket,server=y,suspend=${RSPACE_DEBUG_SUSPEND:-n},address=*:5005"
  echo "[entrypoint] JDWP debug listening on container port 5005 (suspend=${RSPACE_DEBUG_SUSPEND:-n})"
fi
if [ "${RSPACE_HOTSWAP:-true}" != "false" ]; then
  ORG_GRADLE_PROJECT_jettyJvmArgs+=" -XX:+AllowEnhancedClassRedefinition -XX:HotswapAgent=external -javaagent:/opt/hotswap/hotswap-agent.jar=propertiesFilePath=/rspace/docker/dev/hotswap-agent.properties"
  echo "[entrypoint] HotswapAgent enabled (JBR enhanced class redefinition)"
fi

# Gradle exits, including its single-use daemon, before the long-lived JVM starts.
./gradlew "${GRADLE_BUILD_ARGS[@]}" jettyLaunchArgs "${GRADLE_ARGS[@]}"
rm -f "${FIFO}"
mkfifo "${FIFO}"
# Open read/write so startup never blocks and stdin stays open between reloads.
exec 3<> "${FIFO}"
exec java @/var/rspace-build/app/jetty/launch.args <&3
