#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
CONTAINER_PREFIX="rspace-agent-db"
MARIADB_IMAGE="mariadb:10.11"
DB_NAME="rspace"
DB_USER="rspacedbuser"
DB_PASS="rspacedbpwd"
ROOT_PASS="rootpwd"
STATE_FILE=".agent-db.env"
DEPLOY_PROPS="src/main/resources/deployments/dev/deployment.properties"

die()  { echo "$SCRIPT_NAME: $*" >&2; exit 1; }
info() { echo "$SCRIPT_NAME: $*" >&2; }

# --- Worktree detection ------------------------------------------------

is_worktree() {
  local toplevel common
  toplevel=$(git rev-parse --show-toplevel 2>/dev/null) || return 1
  common=$(git rev-parse --git-common-dir 2>/dev/null)  || return 1
  # In the main checkout, --git-common-dir is <toplevel>/.git or just .git
  [[ "$common" != "$toplevel/.git" && "$common" != ".git" ]]
}

worktree_name() {
  basename "$(git rev-parse --show-toplevel)"
}

container_name() {
  echo "${CONTAINER_PREFIX}-$(worktree_name)"
}

# --- State file helpers -------------------------------------------------

state_file_path() {
  echo "$(git rev-parse --show-toplevel)/$STATE_FILE"
}

write_state() {
  local sf
  sf=$(state_file_path)
  cat > "$sf" <<EOF
AGENT_DB_CONTAINER=$1
AGENT_DB_PORT=$2
EOF
  info "state written to $sf"
}

read_state() {
  local sf
  sf=$(state_file_path)
  [[ -f "$sf" ]] || return 1
  # shellcheck disable=SC1090
  source "$sf"
}

# --- GC: remove orphaned containers ------------------------------------

gc() {
  local containers name worktree_dir removed=0
  containers=$(docker ps -a --filter "name=${CONTAINER_PREFIX}-" --format '{{.Names}}' 2>/dev/null) || return 0
  for name in $containers; do
    local suffix="${name#${CONTAINER_PREFIX}-}"
    # Find the worktree directory for this container by checking all git worktrees
    worktree_dir=""
    while IFS= read -r line; do
      if [[ "$line" == /* ]]; then
        local candidate
        candidate="$line"
        if [[ "$(basename "$candidate")" == "$suffix" ]]; then
          worktree_dir="$candidate"
          break
        fi
      fi
    done < <(git worktree list --porcelain 2>/dev/null | grep '^worktree ' | sed 's/^worktree //')

    if [[ -z "$worktree_dir" || ! -d "$worktree_dir" ]]; then
      info "gc: removing orphaned container $name (worktree gone)"
      docker rm -f "$name" >/dev/null 2>&1 || true
      removed=$((removed + 1))
    fi
  done
  [[ $removed -gt 0 ]] && info "gc: removed $removed orphaned container(s)"
  return 0
}

# --- Start --------------------------------------------------------------

cmd_start() {
  is_worktree || die "not in a git worktree — isolated DB is only for worktrees"
  docker info >/dev/null 2>&1 || die "docker is not available"

  local cname port
  cname=$(container_name)

  # If container already running, reuse it
  if docker ps --format '{{.Names}}' | grep -qx "$cname"; then
    if read_state; then
      info "container $cname already running on port $AGENT_DB_PORT"
      return 0
    fi
    # Container exists but no state file — recover port
    port=$(docker port "$cname" 3306 2>/dev/null | sed 's/.*://')
    write_state "$cname" "$port"
    patch_jdbc "$port"
    info "container $cname already running on port $port (recovered)"
    return 0
  fi

  # If container exists but stopped, remove it
  if docker ps -a --format '{{.Names}}' | grep -qx "$cname"; then
    info "removing stopped container $cname"
    docker rm -f "$cname" >/dev/null 2>&1
  fi

  # GC orphans before creating a new container
  gc

  # Pick a free port
  port=$(python3 -c "import socket; s=socket.socket(); s.bind(('',0)); print(s.getsockname()[1]); s.close()")

  info "starting container $cname on port $port"
  docker run -d \
    --name "$cname" \
    -e MYSQL_ROOT_PASSWORD="$ROOT_PASS" \
    -e MYSQL_DATABASE="$DB_NAME" \
    -e MYSQL_USER="$DB_USER" \
    -e MYSQL_PASSWORD="$DB_PASS" \
    -p "127.0.0.1:${port}:3306" \
    "$MARIADB_IMAGE" \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci >/dev/null

  info "waiting for MariaDB to be ready"
  until docker exec "$cname" mariadb-admin ping -u root -p"$ROOT_PASS" --silent 2>/dev/null; do
    sleep 1
  done

  write_state "$cname" "$port"
  patch_jdbc "$port"
  init_schema
  info "ready"
}

# --- JDBC patching ------------------------------------------------------

patch_jdbc() {
  local port=$1
  local toplevel props
  toplevel=$(git rev-parse --show-toplevel)
  props="$toplevel/$DEPLOY_PROPS"

  if [[ ! -f "$props" ]]; then
    mkdir -p "$(dirname "$props")"
    touch "$props"
  fi

  sed -i.bak '/^jdbc\.url=/d' "$props" && rm -f "${props}.bak"
  echo "jdbc.url=jdbc:mysql://127.0.0.1:${port}/${DB_NAME}" >> "$props"
  info "patched $DEPLOY_PROPS → port $port"
}

# --- Schema init --------------------------------------------------------

init_schema() {
  local toplevel port
  toplevel=$(git rev-parse --show-toplevel)
  read_state || die "no .agent-db.env — cannot determine container port"
  port="$AGENT_DB_PORT"
  info "initialising schema (drop-recreate-db) against 127.0.0.1:${port}"
  # sql-maven-plugin's drop-recreate-db phase reads jdbc.url.maven from pom.xml
  # (default jdbc:mysql://localhost:3306). Override it to point at the
  # container so we don't wipe the developer's local DB.
  (cd "$toplevel" && ./mvnw test \
    -Denvironment=drop-recreate-db \
    -Djdbc.url.maven="jdbc:mysql://127.0.0.1:${port}" \
    -Dtest=NoSuchTest \
    -DfailIfNoTests=false \
    -Dfast=false \
    -q) || die "schema initialisation failed"
  info "schema initialised"
}

# --- Stop ---------------------------------------------------------------

cmd_stop() {
  is_worktree || die "not in a git worktree"

  local cname
  cname=$(container_name)

  if docker ps -a --format '{{.Names}}' | grep -qx "$cname"; then
    info "stopping container $cname"
    docker stop "$cname" >/dev/null 2>&1 || true
    docker rm "$cname" >/dev/null 2>&1 || true
  else
    info "no container $cname found"
  fi

  # Remove state file
  local sf
  sf=$(state_file_path)
  rm -f "$sf"

  # Restore deployment.properties — remove the jdbc.url line we added;
  # defaultDeployment.properties provides the fallback automatically
  local toplevel props
  toplevel=$(git rev-parse --show-toplevel)
  props="$toplevel/$DEPLOY_PROPS"
  if [[ -f "$props" ]]; then
    sed -i.bak '/^jdbc\.url=/d' "$props" && rm -f "${props}.bak"
  fi

  info "cleaned up"
}

# --- Status -------------------------------------------------------------

cmd_status() {
  if ! is_worktree; then
    echo "not in a git worktree"
    return 1
  fi

  local cname
  cname=$(container_name)

  if docker ps --format '{{.Names}}' | grep -qx "$cname"; then
    local port
    port=$(docker port "$cname" 3306 2>/dev/null | sed 's/.*://')
    echo "running: $cname on port $port"
  elif docker ps -a --format '{{.Names}}' | grep -qx "$cname"; then
    echo "stopped: $cname"
  else
    echo "no container for worktree $(worktree_name)"
  fi
}

# --- Main ---------------------------------------------------------------

case "${1:-}" in
  start)  cmd_start ;;
  stop)   cmd_stop ;;
  gc)     gc ;;
  status) cmd_status ;;
  *)      die "usage: $SCRIPT_NAME {start|stop|gc|status}" ;;
esac
