#!/usr/bin/env bash
# Installs tooling only. Never starts services or runs project checks/binaries.
set -euo pipefail
umask 077
if (( $# != 0 )); then
  echo 'Usage: bash ops/tooling/install-backend.sh (no arguments)' >&2
  exit 1
fi
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$root/ops/tooling/backend-versions.env"
if [[ "$(uname -s)/$(uname -m)" != Linux/x86_64 ]]; then
  echo 'Only Linux x86_64 is checksum-pinned. Do not substitute another build.' >&2
  exit 1
fi
for tool in curl sha256sum unzip tar install mktemp sleep; do
  command -v "$tool" >/dev/null || { echo "Missing prerequisite: $tool" >&2; exit 1; }
done
cache="$root/.tools/backend/downloads"
mkdir -p "$cache"
tmp="$(mktemp -d "$root/.tools/backend/install.XXXXXX")"
download_tmp=''
staged_deno=''
staged_supabase=''
cleanup() {
  [[ -z "$download_tmp" ]] || rm -f -- "$download_tmp"
  [[ -z "$staged_deno" ]] || rm -f -- "$staged_deno"
  [[ -z "$staged_supabase" ]] || rm -f -- "$staged_supabase"
  rm -rf -- "$tmp"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

deno_archive="$cache/deno-${DENO_VERSION}-linux-x86_64.zip"
supabase_archive="$cache/supabase-${SUPABASE_VERSION}-linux-amd64.tar.gz"

verify_archive() {
  printf '%s  %s\n' "$2" "$1" | sha256sum --check --strict --status
}

check_existing_cache() {
  local archive="$1" expected="$2"
  if [[ -e "$archive" || -L "$archive" ]]; then
    if ! verify_archive "$archive" "$expected"; then
      printf 'Cached archive checksum failed: %s\n' "$archive" >&2
      echo 'Stopped without network fallback. Review and deliberately remove only that corrupt cache file before retrying.' >&2
      return 1
    fi
  fi
}

ensure_archive() {
  local archive="$1" expected="$2" url="$3" label="$4"
  local attempt status result retry delay
  if [[ -e "$archive" || -L "$archive" ]]; then
    check_existing_cache "$archive" "$expected"
    printf 'Verified cached %s archive (SHA-256): %s\n' "$label" "$archive"
    return 0
  fi
  for attempt in 1 2 3; do
    download_tmp="$(mktemp "$cache/.download.XXXXXX")"
    result=0
    # Disable curlrc and internal retries: one bounded invocation per attempt.
    if status="$(curl --disable --fail --location --silent --show-error \
      --connect-timeout 20 --max-time 180 --retry 0 --write-out '%{http_code}' \
      "$url" -o "$download_tmp")"; then
      result=0
    else
      result=$?
    fi
    if [[ ! "$status" =~ ^[0-9]{3}$ ]]; then
      echo "Download failed for $label: invalid HTTP status metadata." >&2
      (( result != 0 )) || result=1
      return "$result"
    fi
    if (( result == 0 )) && [[ "$status" == 2?? ]]; then
      if ! verify_archive "$download_tmp" "$expected"; then
        echo "Downloaded $label archive checksum failed; not cached or retried." >&2
        return 1
      fi
      mv -fT -- "$download_tmp" "$archive"
      download_tmp=''
      printf 'Downloaded and SHA-256 verified %s archive: %s\n' "$label" "$archive"
      return 0
    fi
    rm -f -- "$download_tmp"
    download_tmp=''
    retry=false
    case "$result" in
      0|22)
        case "$status" in 408|429|5??) retry=true ;; esac
        ;;
      5|6|7|16|18|28|35|52|55|56|92)
        # Never retry a known permanent HTTP response, even with a transport error.
        case "$status" in 4??) [[ "$status" != 408 && "$status" != 429 ]] || retry=true ;;
          *) retry=true ;;
        esac
        ;;
    esac
    printf 'Download failed for %s (attempt %s/3, curl %s, HTTP %s).\n' \
      "$label" "$attempt" "$result" "$status" >&2
    if [[ "$retry" != true ]] || (( attempt == 3 )); then
      (( result != 0 )) || result=1
      return "$result"
    fi
    if (( attempt == 1 )); then delay=15; else delay=45; fi
    printf 'Retrying %s from the same official URL in %s seconds.\n' "$label" "$delay"
    sleep "$delay"
  done
}

# Preflight both cache entries so any known corruption fails before any network.
check_existing_cache "$deno_archive" "$DENO_LINUX_X64_SHA256"
check_existing_cache "$supabase_archive" "$SUPABASE_LINUX_X64_SHA256"
ensure_archive "$deno_archive" "$DENO_LINUX_X64_SHA256" \
  "https://github.com/denoland/deno/releases/download/v$DENO_VERSION/deno-x86_64-unknown-linux-gnu.zip" Deno
ensure_archive "$supabase_archive" "$SUPABASE_LINUX_X64_SHA256" \
  "https://github.com/supabase/cli/releases/download/v$SUPABASE_VERSION/supabase_${SUPABASE_VERSION}_linux_amd64.tar.gz" Supabase

# Neither installed binary is touched until both archives verify and extract.
mkdir "$tmp/deno" "$tmp/supabase"
unzip -q "$deno_archive" -d "$tmp/deno"
tar -xzf "$supabase_archive" -C "$tmp/supabase" supabase
for binary in "$tmp/deno/deno" "$tmp/supabase/supabase"; do
  if [[ ! -f "$binary" || -L "$binary" ]]; then
    echo 'Archive did not contain the expected regular executable; installation stopped.' >&2
    exit 1
  fi
done
deno_dir="$root/.tools/backend/deno-$DENO_VERSION"
supabase_dir="$root/.tools/backend/supabase-$SUPABASE_VERSION"
mkdir -p "$deno_dir" "$supabase_dir"
staged_deno="$(mktemp "$deno_dir/.deno.XXXXXX")"
staged_supabase="$(mktemp "$supabase_dir/.supabase.XXXXXX")"
install -m 0755 "$tmp/deno/deno" "$staged_deno"
install -m 0755 "$tmp/supabase/supabase" "$staged_supabase"
# Same-directory renames avoid exposing truncated executables.
mv -fT -- "$staged_deno" "$deno_dir/deno"
staged_deno=''
mv -fT -- "$staged_supabase" "$supabase_dir/supabase"
staged_supabase=''
printf 'Installed checksum-verified Deno %s and Supabase CLI %s.\n' "$DENO_VERSION" "$SUPABASE_VERSION"
printf 'Source ops/tooling/backend-env.sh before using Deno.\n'
