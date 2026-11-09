#!/usr/bin/env bash
# Installs tooling only. Never starts services or runs project checks.
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$root/ops/tooling/backend-versions.env"
if [[ "$(uname -s)/$(uname -m)" != Linux/x86_64 ]]; then
  echo 'Only Linux x86_64 is checksum-pinned. Do not substitute another build.' >&2
  exit 1
fi
for tool in curl sha256sum unzip tar; do
  command -v "$tool" >/dev/null || { echo "Missing prerequisite: $tool" >&2; exit 1; }
done
mkdir -p "$root/.tools/backend"
tmp="$(mktemp -d "$root/.tools/backend/install.XXXXXX")"
trap 'rm -rf -- "$tmp"' EXIT
curl --fail --location --silent --show-error --connect-timeout 20 --max-time 600 --retry 2 \
  "https://github.com/denoland/deno/releases/download/v$DENO_VERSION/deno-x86_64-unknown-linux-gnu.zip" \
  -o "$tmp/deno.zip"
curl --fail --location --silent --show-error --connect-timeout 20 --max-time 600 --retry 2 \
  "https://github.com/supabase/cli/releases/download/v$SUPABASE_VERSION/supabase_${SUPABASE_VERSION}_linux_amd64.tar.gz" \
  -o "$tmp/supabase.tar.gz"
(
  cd "$tmp"
  printf '%s  deno.zip\n%s  supabase.tar.gz\n' \
    "$DENO_LINUX_X64_SHA256" "$SUPABASE_LINUX_X64_SHA256" | sha256sum --check --strict
  mkdir deno supabase
  unzip -q deno.zip -d deno
  tar -xzf supabase.tar.gz -C supabase supabase
)
mkdir -p "$root/.tools/backend/deno-$DENO_VERSION" "$root/.tools/backend/supabase-$SUPABASE_VERSION"
install -m 0755 "$tmp/deno/deno" "$root/.tools/backend/deno-$DENO_VERSION/deno"
install -m 0755 "$tmp/supabase/supabase" "$root/.tools/backend/supabase-$SUPABASE_VERSION/supabase"
printf 'Installed checksum-verified Deno %s and Supabase CLI %s.\n' "$DENO_VERSION" "$SUPABASE_VERSION"
printf 'Source ops/tooling/backend-env.sh before using Deno.\n'
