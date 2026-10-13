#!/usr/bin/env bash
# Starts local S3 (MinIO), SFTP, Samba and iRODS servers for the mock-mode filestore e2e specs
# (specs/system/config/filestores/), seeds each with the same test folder, and prints the
# E2E_* variables those specs read. Reuses containers that are already running.
#
#   ./start-filestore-servers.sh                       # print the variables (paste into ui/.env)
#   ./start-filestore-servers.sh >> "$GITHUB_ENV"      # CI
#   FILESTORE_DOCKER_NETWORK=<project>_default FILESTORE_CONTAINER_PREFIX=<project>-filestore ./start-filestore-servers.sh
#
# RSpace must also be started with the MinIO credentials below as
#   -Dnetfilestores.s3.global.credentials.accessKey / .secretKey
# These are throwaway credentials for local containers, never real keys.
#
# Fixed host ports: SFTP uses 2222 because hosted runners hold 22, and SmbjClient always uses 445.
# The SFTP URL is "localhost:2222" because SftpClient can't combine "sftp://" with a port.
# iRODS needs a PostgreSQL catalogue next to it; the image is large (about 5 GB) and pulled once.
set -euo pipefail

USER_NAME=rspacetest
USER_PASSWORD=rspacetestpass
MINIO_SECRET=rspacetestsecret
BUCKET=rspace-test
PREFIX=${FILESTORE_CONTAINER_PREFIX:-rspace-e2e}
NETWORK=${FILESTORE_DOCKER_NETWORK:-}
IRODS_IMAGE=ghcr.io/bihealth/irods-docker:4.3.5-3
IRODS_ZONE=tempZone

log() { echo "start-filestore-servers: $*" >&2; }

IRODS_NETWORK_ARGS=()
if [ -n "$NETWORK" ]; then
  docker network inspect "$NETWORK" >/dev/null
  MINIO_HOST="$PREFIX-minio"
  SFTP_ADDRESS="$PREFIX-sftp:22"
  SAMBA_HOST="$PREFIX-samba"
  IRODS_HOST="$PREFIX-irods"
  IRODS_NETWORK="$NETWORK"
  # iRODS must recognise its advertised catalog address as its own hostname.
  IRODS_NETWORK_ARGS=(--hostname "$IRODS_HOST")
else
  MINIO_HOST=localhost
  SFTP_ADDRESS=localhost:2222
  SAMBA_HOST=localhost
  IRODS_HOST=localhost
  IRODS_NETWORK="$PREFIX-irods-net"
  IRODS_NETWORK_ARGS=(-p 127.0.0.1:1247:1247)
fi

start() {
  local name=$1
  shift
  if [ "$(docker inspect --format '{{.State.Running}}' "$name" 2>/dev/null)" = "true" ]; then
    local previous_network
    previous_network=$(docker inspect --format '{{ index .Config.Labels "org.rspace.filestore.network" }}' "$name")
    if [ "$previous_network" != "${NETWORK:-host}" ]; then
      log "$name uses different or unrecorded networking; use a new FILESTORE_CONTAINER_PREFIX"
      return 1
    fi
    log "$name already running"
  else
    docker rm -f "$name" >/dev/null 2>&1 || true
    docker run -d --name "$name" --label "org.rspace.filestore.network=${NETWORK:-host}" "$@" >/dev/null
    log "started $name"
  fi
}

# Host-based RSpace uses loopback publications; Docker RSpace uses the shared network.
start_filestore() {
  local name=$1 host_port=$2 container_port=$3
  shift 3
  if [ -n "$NETWORK" ]; then
    start "$name" --network "$NETWORK" "$@"
  else
    start "$name" -p "127.0.0.1:$host_port:$container_port" "$@"
  fi
}

# Usage: wait_for <what> <seconds> <command...>
wait_for() {
  local what=$1 seconds=$2
  shift 2
  for _ in $(seq 1 "$seconds"); do
    if "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  log "timed out waiting for $what"
  return 1
}

# Same tree everywhere: playwright-test/{hello.txt,clip.flv,sub/nested.txt}.
SEED='mkdir -p "$1/playwright-test/sub" && echo hello > "$1/playwright-test/hello.txt" && echo video > "$1/playwright-test/clip.flv" && echo nested > "$1/playwright-test/sub/nested.txt"'

# Community build of MinIO RELEASE.2025-10-15T17-29-55Z, including mc and the shell used below.
# Build source: https://github.com/coollabsio/minio
start_filestore "$PREFIX-minio" 9000 9000 \
  -e MINIO_ROOT_USER="$USER_NAME" -e MINIO_ROOT_PASSWORD="$MINIO_SECRET" \
  ghcr.io/coollabsio/minio@sha256:69b55a1c1c5dc285ce04db96689f5b2102317fc77a50680a1874ca6efd1c87f9 server /data
start_filestore "$PREFIX-sftp" 2222 22 \
  atmoz/sftp "$USER_NAME:$USER_PASSWORD:1001:1001:upload"
start_filestore "$PREFIX-samba" 445 445 \
  dperson/samba -u "$USER_NAME;$USER_PASSWORD" -s "share;/share;yes;no;no;$USER_NAME"
docker network inspect "$IRODS_NETWORK" >/dev/null 2>&1 || docker network create "$IRODS_NETWORK" >/dev/null
# No POSTGRES_DB: the iRODS image creates its ICAT database itself and stops if it already exists.
start "$PREFIX-irods-db" --network "$IRODS_NETWORK" \
  -e POSTGRES_USER=irods -e POSTGRES_PASSWORD=irods postgres:16
wait_for "iRODS catalogue database" 60 docker exec "$PREFIX-irods-db" pg_isready -U irods
start "$PREFIX-irods" --network "$IRODS_NETWORK" "${IRODS_NETWORK_ARGS[@]}" \
  -e IRODS_ICAT_DBSERVER="$PREFIX-irods-db" -e IRODS_ZONE_NAME="$IRODS_ZONE" -e IRODS_HOST_NAME="$IRODS_HOST" \
  "$IRODS_IMAGE"

wait_for "MinIO" 60 docker exec "$PREFIX-minio" mc alias set local http://localhost:9000 "$USER_NAME" "$MINIO_SECRET"
docker exec "$PREFIX-minio" sh -c "mc mb -p local/$BUCKET && sh -c '$SEED' _ /tmp/seed && mc cp --recursive --quiet /tmp/seed/ local/$BUCKET/" >/dev/null

wait_for "SFTP host key" 60 docker exec "$PREFIX-sftp" test -s /etc/ssh/ssh_host_ed25519_key.pub
docker exec "$PREFIX-sftp" sh -c "sh -c '$SEED' _ /home/$USER_NAME/upload && chown -R 1001:1001 /home/$USER_NAME/upload"
SFTP_HOST_KEY=$(docker exec "$PREFIX-sftp" cut -d' ' -f2 /etc/ssh/ssh_host_ed25519_key.pub)

wait_for "Samba share" 60 docker exec "$PREFIX-samba" test -d /share
docker exec "$PREFIX-samba" sh -c "sh -c '$SEED' _ /share && chmod -R a+rwX /share"

# Setup briefly starts then stops iRODS. Wait for the entrypoint's final startup before seeding.
irods_ready() {
  docker logs "$PREFIX-irods" 2>&1 | grep -F 'iRODS is ready' >/dev/null &&
    docker exec "$PREFIX-irods" su - irods -c "ils /$IRODS_ZONE/home/rods"
}
wait_for "iRODS server" 300 irods_ready
# The admin creates the test user, uploads the seed into its home, then hands the files over to it.
docker exec "$PREFIX-irods" su - irods -c "
  iadmin lu $USER_NAME | grep -q $USER_NAME || iadmin mkuser $USER_NAME rodsuser
  iadmin moduser $USER_NAME password $USER_PASSWORD
  ichmod -M own rods /$IRODS_ZONE/home/$USER_NAME
  sh -c '$SEED' _ /tmp/seed
  iput -rf /tmp/seed/playwright-test /$IRODS_ZONE/home/$USER_NAME/
  ichmod -r -M own $USER_NAME /$IRODS_ZONE/home/$USER_NAME/playwright-test" >/dev/null

log "seeded all four; variables follow"
cat <<EOF
E2E_S3_FILESTORE_URL=http://$MINIO_HOST:9000
E2E_S3_FILESTORE_BUCKET=$BUCKET
E2E_SFTP_FILESTORE_URL=$SFTP_ADDRESS
E2E_SFTP_FILESTORE_HOST_KEY=$SFTP_HOST_KEY
E2E_SAMBA_FILESTORE_URL=smb://$SAMBA_HOST
E2E_IRODS_FILESTORE_URL=$IRODS_HOST
E2E_FILESTORE_USERNAME=$USER_NAME
E2E_FILESTORE_PASSWORD=$USER_PASSWORD
EOF
