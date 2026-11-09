# Source from Bash; tooling is isolated to this checkout's ignored .tools/ tree.
_backend_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$_backend_root/ops/tooling/backend-versions.env"
export PATH="$_backend_root/.tools/backend/deno-$DENO_VERSION:$_backend_root/.tools/backend/supabase-$SUPABASE_VERSION:$PATH"
export DENO_DIR="$_backend_root/.tools/deno-cache"
unset _backend_root
