#!/usr/bin/env bash
# Starts local S3 (MinIO), SFTP, Samba and iRODS servers for the mock-mode filestore e2e specs
# (specs/system/config/filestores/), seeds each with the same test folder, and prints the
# E2E_* variables those specs read. Reuses containers that are already running.
#
#   ./start-filestore-servers.sh                       # print the variables (paste into ui/.env)
#   ./start-filestore-servers.sh >> "$GITHUB_ENV"      # CI
#
# RSpace must also be started with the MinIO credentials below as
#   -Dnetfilestores.s3.global.credentials.accessKey / .secretKey
# These are throwaway credentials for local containers, never real keys.
#
# Fixed host ports: SftpClient can't combine "sftp://" with a port, and SmbjClient always uses 445.
# iRODS needs a PostgreSQL catalogue next to it; the image is large (about 5 GB) and pulled once.
set -euo pipefail

USER_NAME=rspacetest
USER_PASSWORD=rspacetestpass
MINIO_SECRET=rspacetestsecret
BUCKET=rspace-test
PREFIX=${FILESTORE_CONTAINER_PREFIX:-rspace-e2e}
IRODS_IMAGE=ghcr.io/bihealth/irods-docker:4.3.5-3
IRODS_ZONE=tempZone

log() { echo "start-filestore-servers: $*" >&2; }

start() {
  local name=$1
  shift
  if [ "$(docker inspect --format '{{.State.Running}}' "$name" 2>/dev/null)" = "true" ]; then
    log "$name already running"
  else
    docker rm -f "$name" >/dev/null 2>&1 || true
    docker run -d --name "$name" "$@" >/dev/null
    log "started $name"
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

start "$PREFIX-minio" -p 127.0.0.1:9000:9000 \
  -e MINIO_ROOT_USER="$USER_NAME" -e MINIO_ROOT_PASSWORD="$MINIO_SECRET" \
  minio/minio server /data
start "$PREFIX-sftp" -p 127.0.0.1:22:22 \
  atmoz/sftp "$USER_NAME:$USER_PASSWORD:1001:1001:upload"
start "$PREFIX-samba" -p 127.0.0.1:445:445 \
  dperson/samba -u "$USER_NAME;$USER_PASSWORD" -s "share;/share;yes;no;no;$USER_NAME"
docker network inspect "$PREFIX-irods-net" >/dev/null 2>&1 || docker network create "$PREFIX-irods-net" >/dev/null
# No POSTGRES_DB: the iRODS image creates its ICAT database itself and stops if it already exists.
start "$PREFIX-irods-db" --network "$PREFIX-irods-net" \
  -e POSTGRES_USER=irods -e POSTGRES_PASSWORD=irods postgres:16
wait_for "iRODS catalogue database" 60 docker exec "$PREFIX-irods-db" pg_isready -U irods
start "$PREFIX-irods" --network "$PREFIX-irods-net" -p 127.0.0.1:1247:1247 \
  -e IRODS_ICAT_DBSERVER="$PREFIX-irods-db" -e IRODS_ZONE_NAME="$IRODS_ZONE" -e IRODS_HOST_NAME=localhost \
  "$IRODS_IMAGE"

wait_for "MinIO" 60 docker exec "$PREFIX-minio" mc alias set local http://localhost:9000 "$USER_NAME" "$MINIO_SECRET"
docker exec "$PREFIX-minio" sh -c "mc mb -p local/$BUCKET && sh -c '$SEED' _ /tmp/seed && mc cp --recursive --quiet /tmp/seed/ local/$BUCKET/" >/dev/null

wait_for "SFTP host key" 60 docker exec "$PREFIX-sftp" test -s /etc/ssh/ssh_host_ed25519_key.pub
docker exec "$PREFIX-sftp" sh -c "sh -c '$SEED' _ /home/$USER_NAME/upload && chown -R 1001:1001 /home/$USER_NAME/upload"
SFTP_HOST_KEY=$(docker exec "$PREFIX-sftp" cut -d' ' -f2 /etc/ssh/ssh_host_ed25519_key.pub)

wait_for "Samba share" 60 docker exec "$PREFIX-samba" test -d /share
docker exec "$PREFIX-samba" sh -c "sh -c '$SEED' _ /share && chmod -R a+rwX /share"

# A fresh runner boots iRODS cold, and building its catalogue can take minutes.
wait_for "iRODS server" 300 docker exec "$PREFIX-irods" su - irods -c "ils /$IRODS_ZONE/home/rods"
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
E2E_S3_FILESTORE_URL=http://localhost:9000
E2E_S3_FILESTORE_BUCKET=$BUCKET
E2E_SFTP_FILESTORE_URL=sftp://localhost
E2E_SFTP_FILESTORE_HOST_KEY=$SFTP_HOST_KEY
E2E_SAMBA_FILESTORE_URL=smb://localhost
E2E_IRODS_FILESTORE_URL=localhost
E2E_FILESTORE_USERNAME=$USER_NAME
E2E_FILESTORE_PASSWORD=$USER_PASSWORD
EOF
