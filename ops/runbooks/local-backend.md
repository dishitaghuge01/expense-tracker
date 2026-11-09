# Local backend foundation (partial package 1)

This is **host-only, local-only scaffolding**, not a completed backend or package-1
checkpoint. Android is unchanged. There are no users, business tables, household
features, auth/device leases, capture, billing, website or hosted deployment.
Sales remain closed. Do not provision cloud services or use production data.

## Pinned tools and installation

Supported bootstrap host: Linux x86_64, Bash, curl, sha256sum, unzip, tar, install,
and an existing Docker Engine with the bridge driver. Docker access is privileged;
use a trusted development host. No sudo, global npm install or daemon changes are
needed by these scripts. Other host architectures need separately reviewed
upstream artifacts/checksums; do not silently substitute versions or mirrors.

Exact versions in `ops/tooling/backend-versions.env`:

- Deno **2.9.8**, official stable release archive with SHA-256 verification.
- Supabase CLI **3.0.0**, official stable release archive with SHA-256 verification.
- PostgreSQL major **17**, with local service image versions bundled by that CLI
  (`local.services.default.version = "bundled"`), not a linked hosted project.
- OpenAPI structure: `@apidevtools/swagger-parser` **13.1.0** (supports 3.1).
- Response JSON Schema: Ajv **8.20.0**, draft 2020-12, strict mode and format
  validation, `ajv-formats` **3.0.1**. No coercion, removal or inserted defaults.
- YAML: `js-yaml` **5.5.0** via the local `yaml` import adapter. Its library does
  not read environment variables. The initially considered `yaml` Node entrypoint
  does, so it is deliberately not used; no environment permission is granted.
- npm transitive dependencies and integrity hashes are frozen in `deno.lock`.

From the repository root, use a clean shell with no exported `SUPABASE_*`
variables, dotenv files (except `.env.example`), or linked-project state:

```sh
bash ops/tooling/install-backend.sh
source ops/tooling/backend-env.sh
deno install --frozen --entrypoint ops/tooling/validate-contracts.ts
```

Installation downloads only tooling, never starts Docker, runs project checks or
executes downloaded binaries as verification. It does not grant npm lifecycle
scripts. Sources remain the pinned official GitHub releases; failures never
substitute a version, checksum, provider or mirror. Delivery uses GitHub's
[documented release-asset API](https://docs.github.com/en/rest/releases/assets?apiVersion=2022-11-28#get-a-release-asset)
for those same exact archives:

- Deno: `https://api.github.com/repos/denoland/deno/releases/assets/662551650`
  (`deno-x86_64-unknown-linux-gnu.zip`, release `v2.9.8`).
- Supabase: `https://api.github.com/repos/supabase/cli/releases/assets/701653806`
  (`supabase_3.0.0_linux_amd64.tar.gz`, release `v3.0.0`).

The asset IDs are pinned in `backend-versions.env`, with official tag-metadata
provenance; there is no runtime latest lookup or URL override. Requests explicitly
send `Accept: application/octet-stream` and `X-GitHub-Api-Version: 2022-11-28`,
without authentication. Redirects use normal `--location`, not credential-trusting
redirects or TLS bypass. Full-archive SHA-256 verification remains mandatory;
an HTTP 200 JSON/error body cannot become a cache entry or installed tool.

### Archive cache and bounded retries

Only release archives are reusable bootstrap cache entries, under ignored paths:

- `.tools/backend/downloads/deno-2.9.8-linux-x86_64.zip`
- `.tools/backend/downloads/supabase-3.0.0-linux-amd64.tar.gz`

Every existing entry is SHA-256 checked against `backend-versions.env`, on every
invocation, before extraction/use. Two valid cache hits need **zero network
requests**. Binaries, CLI state, credentials and dependency/user caches are not
this archive cache and are not trusted instead of archive verification.

For each uncached archive, there are at most **three attempts**, one curl process
per attempt, with internal retries disabled (`--retry 0`) and curlrc disabled.
Each attempt uses `--connect-timeout 20 --max-time 180` and
`--fail --location --silent --show-error`, keeping default TLS verification.
Transient HTTP **408, 429 and 5xx** responses retry; transport errors **5, 6, 7,
16, 18, 28, 35, 52, 55, 56 and 92** retry unless a permanent HTTP 4xx response was
received. Backoff is exactly **15 seconds then 45 seconds**, with no sleep after
the last failure. Permanent HTTP errors (including 403/404), certificate trust
failure (curl 60), other unclassified failures and checksum mismatches fail
immediately. Exhausted retries return nonzero. Cold network/backoff budget is
at most **600 seconds per archive / 20 minutes for both**, excluding local
hashing/extraction/filesystem work. This cannot guarantee a successful cold
bootstrap during a sustained upstream outage.

Downloads go to private unique temporary files on the cache filesystem; only
fully downloaded, checksum-verified bytes are atomically renamed into the cache.
Partial/error bodies and installation staging files are cleaned up on ordinary
exit and handled INT/TERM signals (not uncatchable SIGKILL). A verified first
archive remains reusable if the second fails.
Both archives must verify and extract before either installed executable is
replaced. Staged executable files are prepared first, then replaced using
same-directory atomic renames, never in-place truncation.

**A corrupt cache fails closed before any network request**, without deleting
the cache or changing installed binaries. Inspect the checksum failure privately
and compare with the reviewed pins. If deliberate redownload is appropriate,
remove **only the exact corrupt archive path reported**, for example (Deno only):

```sh
rm -- .tools/backend/downloads/deno-2.9.8-linux-x86_64.zip
bash ops/tooling/install-backend.sh
```

Do not remove installed executables to repair an archive cache error, clear the
whole tool directory, bypass verification or change pins to match bad bytes.
Binaries, caches and private local output live in ignored `.tools/` per checkout.
Source `backend-env.sh` in each new shell; do not use an unrelated system Deno.
The wrapper invokes the pinned Supabase binary by absolute path.

## Start, reset, inspect, stop

**One stack at a time across all worktrees.** The fixed project ID is
`expense-tracker-local`; worktrees do not get separate volumes/ports. Avoid other
projects using ports 54320–54322 or inspector port 8083. Do not change the ID to a
production project reference.

```sh
bash ops/tooling/local-backend.sh start
# DESTRUCTIVE: erases only this project's local DB, reapplies migrations + seed.
bash ops/tooling/local-backend.sh reset

# Public endpoint: no Authorization header or API key required.
curl --fail --include http://127.0.0.1:54321/functions/v1/api/v1/release

# Optional foreground hot reload; Ctrl-C ends serving (not the whole stack).
bash ops/tooling/local-backend.sh serve

# Stops this project, preserving its local volumes for later starts.
bash ops/tooling/local-backend.sh stop
```

Reset is intentional/destructive and does not prompt in the wrapper. Never run it
while another worktree is using the stack. The wrapper accepts only the five
listed local actions, no extra flags/remote URLs, and refuses environment/dotenv
or linked-project overrides. It does not remove other projects or Docker volumes.
No remote CLI action, cloud secret or account login is needed.

CLI v3's standard Docker stack publishes ports without a TOML host-bind option.
The wrapper first creates the **project-scoped bridge network** with
`com.docker.network.bridge.host_binding_ipv4=127.0.0.1`, then supplies that network
to the CLI. This makes unspecified published host addresses IPv4 loopback; it
does not change Docker's daemon or global networks. An existing network without
that option causes start to fail closed. Stop this project first, privately
inspect its containers/network, then explicitly remove only the unused
`supabase_network_expense-tracker-local` network if appropriate and start again.
Never prune Docker globally to fix a conflict. Do not bypass the wrapper with a
bare `supabase start`, use host networking, expose a remote Docker daemon, or
forward these ports to a LAN/public interface.

The verifier must confirm actual port bindings on the target Docker host; this
source configuration is not runtime isolation evidence. Auth, Studio, SMTP,
Realtime, Storage, analytics, pooler, image proxy and postgres-meta are disabled
or excluded to reduce resources. DB/PostgREST/gateway/Edge remain local. CLI
telemetry is disabled for wrapper commands. A future auth slice must review and
deliberately re-enable services/providers rather than add credentials here.

Start/status output may contain conventional **local-only** CLI credentials.
Start output is captured in ignored `.tools/local-backend/start.log` (private
umask). Inspect failures privately; never paste keys or that log into reports,
issues or CI artifacts. There are no production credentials in configuration.
`.env*`, `supabase/.temp/`, `supabase/.branches/`, `.supabase/`, `.tools/` and
`node_modules/` are ignored; do not commit generated keys or signing material.

## Checks (independent verifier only during delegated batches)

After installing dependencies and integrating the independent test commits:

```sh
python3 -m unittest discover -s ops/tests -p 'test_backend_bootstrap.py' -v
source ops/tooling/backend-env.sh
deno task check          # typecheck source, contract tooling and independent tests
deno task contracts      # structural OpenAPI + response JSON Schema + fixture alignment
deno task test           # handler + independent contract negatives; local read only
bash ops/tooling/local-backend.sh start
bash ops/tooling/local-backend.sh reset
deno task db:test         # pinned CLI: supabase test db, local by default in v3
bash ops/tooling/local-backend.sh stop
```

`check`, `contracts` and `test` are cached-only/frozen after installation. Tests
have bounded local read permissions only: no environment, network, subprocess,
write or database access. SQL pgTAP runs separately via Docker/CLI. The exported
`loadReleaseValidator()` in `ops/tooling/validate-contracts.ts` performs structural
validation with external reference resolvers disabled and returns the same Ajv
response validator used by the CLI and negative tests. It has no top-level CLI
execution when imported. UUID and URI formats are actively checked. Valid
reserved-domain HTTPS strings in tests demonstrate schema format support, not an
approved deployment URL; the actual local metadata/fixture URLs remain null.

To deliberately update dependency locks, review exact pins/upstream release
notes, then use `deno install --frozen=false --entrypoint
ops/tooling/validate-contracts.ts`, review `deno.lock`, commit and independently
verify again. Do not regenerate locks just to hide failed verification.

`.github/workflows/backend.yml` is separate from Android CI. It runs the offline
Python-stdlib bootstrap fault tests before network bootstrap, restores only the
release archive paths via `actions/cache`, and always runs the installer to
recheck SHA-256 even after an action cache hit. The exact cache key includes
runner OS/architecture, bootstrap archive format `v1` and the version-pin file
hash, with no broad restore keys. No executables, Deno/npm user caches, dotenv
files, credentials or CLI state are cached. Cache presence is not checksum proof.
The job has a finite 45-minute budget (21-minute installer step) to accommodate
the bounded cold downloads plus backend checks; there is no whole-job retry loop.

It installs frozen dependencies, typechecks, validates contracts, runs unit and
contract tests, starts/reset/migrates a local synthetic stack, runs pgTAP and
checks public Edge routing. The always-cleanup guard attempts project-local stop
after successful tooling installation even if later steps fail. It uses no
cloud/deployment secrets and does not publish private CLI output. CI commands are
configured, not claimed to have passed by implementation.

## Security and release boundaries

- Migration `20261109000100_private_foundation.sql` creates `app_private`, owned
  by `postgres`, outside Data API schemas and request search paths. PUBLIC,
  `anon` and `authenticated` have no schema access and no default table,
  sequence or function privileges for objects created by `postgres`.
- Global function defaults revoke PostgreSQL's default PUBLIC EXECUTE; a
  schema-local REVOKE alone cannot undo the global default. Both global and
  schema-local defaults are restricted. These defaults do not cover objects
  created by a different owner: future migrations must review their role/defaults.
- **Defaults do not enable RLS.** Every future business table must explicitly
  enable RLS, keep default-deny client grants and gain independent isolation
  tests. The independent pgTAP tests create synthetic canaries after migration,
  explicitly enable RLS and roll their entire transaction back. A successful
  canary test is not evidence of an implemented ledger/tenant model.
- No `service_role` grants/RPCs are added here. Future narrowly scoped server
  RPCs require explicit reviewed grants, qualified names, transaction-scoped
  authorization and protected server-only credentials. A service role can
  bypass RLS; it is never a browser/mobile credential or substitute for actor
  authorization. This public handler needs no database/client credentials.
- `api` has JWT verification disabled **only because it contains public release
  metadata**. Never add protected routes under this setting without implementing
  and verifying their complete authorization boundary. There is no pretend JWT,
  lease or entitlement check. No browser-auth/CORS credential route exists.
- The function imports immutable scalar defaults from its local JSON. Only exact
  `/v1/release` and `/api/v1/release` GET paths are accepted; query markers are
  rejected, unknown paths are 404, and methods on recognized paths are 405 with
  `Allow: GET`. Errors are fixed messages, UUID envelope and `no-store`; success
  is JSON with `public, max-age=300`. Request headers/body never configure release
  metadata, request IDs or CORS origins.
- Release metadata is the disposable build 3 / `0.0.3-prototype`, Google Play,
  checkout closed and null Play/support URLs. **Null means deployment blocked.**
  No listing, production domain, APK hash/bytes/download link, price or purchase
  API is invented. The OpenAPI contract intentionally covers GET `/release`
  only; it is not the full user/backend or revised commerce contract.

## Remaining gates (not completed by this slice)

Independent local execution now passed typecheck/contracts, 180 handler/contract
tests, 58 pgTAP assertions, reset/migrations, loopback bindings and the recorded
live release/anonymous nonexposure probes. See [verification evidence](../release/backend-foundation-verification.md)
for exact sources, failed-attempt history and limits. Latest observed backend CI
at `25d86d6` failed on an external HTTP 504 tool download; the resilience changes
have not yet established a backend CI pass. Independent verification at
`8c022b7` passed the original 23 offline bootstrap tests, but the real cold
installer exhausted three browser-release-URL attempts with HTTP 504. A separate
single-attempt diagnostic for each official asset API URL returned HTTP 200, but
both transfers timed out after 180 seconds with incomplete, nonmatching hashes;
neither archive was verified, extracted or executed. Separate 1,024-byte range
diagnostics are not full-archive proof. The installer now uses the API route for
the same pinned assets; independent execution of this change, a checksum-verified
cold/warm bootstrap and actual backend CI success remain pending. This is not a
claim of outage recovery. Android CI separately recovered at
`37ba265` ([green run 42570266300](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42570266300)).
Local success is not a backend CI pass. Package 1 still needs staging
prerequisites/provisioning/deployment (separate owner authorization), web tooling,
full backend contracts and applicable reproducibility/signing work. Production
identifiers, Play account/permission/testing gates, Google OAuth, signed-device
leases, household isolation, revised Play commerce contract/verification,
backup/restore and device/pilot tests are not implemented or passed here. No
package-2 progress, phone prompts or spending is authorized by these commands.

## Official references inspected

- [Deno stable release](https://github.com/denoland/deno/releases/tag/v2.9.8),
  [install/cache CLI](https://docs.deno.com/runtime/reference/cli/install/),
  [task runner](https://docs.deno.com/runtime/reference/cli/task/).
- [Supabase CLI stable release and v3 changes](https://github.com/supabase/cli/releases/tag/v3.0.0),
  [configuration](https://supabase.com/docs/guides/local-development/cli/config),
  [v3 config template](https://github.com/supabase/cli/blob/v3.0.0/apps/cli/src/shared/init/testdata/templates/config.toml).
- [Docker bridge host-binding option](https://docs.docker.com/engine/network/drivers/bridge/#default-host-binding-address).
- [Swagger Parser 13.1.0 schema validation](https://github.com/APIDevTools/swagger-parser/blob/v13.1.0/lib/validators/schema.js),
  [Ajv draft 2020-12](https://ajv.js.org/json-schema.html#draft-2020-12-breaking),
  [Ajv formats](https://github.com/ajv-validator/ajv-formats),
  [js-yaml](https://github.com/nodeca/js-yaml/tree/5.5.0).
