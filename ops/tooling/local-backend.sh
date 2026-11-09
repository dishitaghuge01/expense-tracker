#!/usr/bin/env bash
# Fixed local-only commands; no remote/deploy/link options or argument forwarding.
set -euo pipefail
umask 077
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$root/ops/tooling/backend-versions.env"
cli="$root/.tools/backend/supabase-$SUPABASE_VERSION/supabase"
network=supabase_network_expense-tracker-local
if [[ $# != 1 || ! "$1" =~ ^(start|reset|test|stop|serve)$ ]]; then
  echo 'Usage: bash ops/tooling/local-backend.sh {start|reset|test|stop|serve}' >&2
  exit 1
fi
[[ -x "$cli" ]] || { echo 'Run bash ops/tooling/install-backend.sh first.' >&2; exit 1; }
# v3 environment overrides beat TOML. Refuse them rather than silently changing
# the project, binding, service set or credentials. No names/values are logged.
if [[ -n "$(compgen -e | grep '^SUPABASE_' || true)" ]]; then
  echo 'Use a clean shell with no exported SUPABASE_* overrides.' >&2
  exit 1
fi
shopt -s nullglob
for file in "$root"/.env* "$root/supabase"/.env*; do
  [[ "$file" == *.example ]] && continue
  echo 'Local foundation refuses dotenv overrides. Use a clean checkout/shell.' >&2
  exit 1
done
[[ ! -f "$root/supabase/.temp/project-ref" ]] || {
  echo 'Refusing linked-project state; use an unlinked local checkout.' >&2; exit 1;
}
export SUPABASE_TELEMETRY_DISABLED=1 DO_NOT_TRACK=1
cd "$root"
mkdir -p .tools/local-backend
case "$1" in
  start)
    # CLI v3 publishes without a host IP and has no TOML bind-address setting.
    # A project-scoped bridge default binds *all* its published ports to IPv4
    # loopback. Never change the daemon/global Docker network configuration.
    if ! docker network inspect "$network" >/dev/null 2>&1; then
      docker network create --driver bridge \
        --label com.supabase.cli.project=expense-tracker-local \
        --label com.docker.compose.project=expense-tracker-local \
        --opt com.docker.network.bridge.host_binding_ipv4=127.0.0.1 \
        "$network" >/dev/null
    fi
    binding="$(docker network inspect --format '{{index .Options "com.docker.network.bridge.host_binding_ipv4"}}' "$network")"
    [[ "$binding" == 127.0.0.1 ]] || {
      echo 'Existing project network is not loopback-only; refusing start. See runbook.' >&2; exit 1;
    }
    # CLI start/status output includes conventional local keys. Keep it private.
    "$cli" start --workdir "$root" --network-id "$network" \
      --exclude gotrue,realtime,storage-api,imgproxy,studio,mailpit,postgres-meta,logflare,vector,supavisor \
      > .tools/local-backend/start.log 2>&1 || {
        echo 'Start failed; inspect ignored .tools/local-backend/start.log privately.' >&2; exit 1;
      }
    echo 'Local stack started. Public API: http://127.0.0.1:54321/functions/v1/api/v1/release'
    ;;
  reset)
    echo 'Resetting only expense-tracker-local: all of its local DB data will be lost.' >&2
    "$cli" db reset --workdir "$root"
    ;;
  test) "$cli" test db --workdir "$root" ;;
  stop) "$cli" stop --workdir "$root" ;;
  serve) "$cli" functions serve api --workdir "$root" --network-id "$network" ;;
esac
