# Independent local backend foundation verification

This is a partial package-1 slice, not staging or full backend completion.

## Source and scope

Latest verification source: `ef02547` (the delta from `f677cb5` is documentation and the CI cleanup guard, not backend runtime/tests). Separate implementation and independent test-writing agents authored the slice; an independent verifier executed it. No cloud resources, production credentials, users, business/commerce tables, auth/household features, website deployment or paid services were introduced.

Added:
- Local Supabase configuration, no-op synthetic seed and a migration creating server-only `app_private` with restricted schema/default privileges. Future tables still need explicit RLS; an empty schema is not a working ledger.
- Checksum-pinned Deno 2.9.8/Supabase CLI 3.0.0, frozen dependency lock and local-only Docker wrapper.
- Immutable public release metadata, a partial OpenAPI 3.1 contract and synthetic fixtures. Play/support URLs are null and checkout closed; no production identity or billing contract is invented.
- Separate backend CI plus independent handler, contract-negative and transactional pgTAP tests.

## Executed checks

The first integrated run at `741143b` stopped on two validator TypeScript errors. Implementation repaired CommonJS plugin typing and the validated-envelope type; the verifier did not repair source or weaken tests.

At `ef02547` (rerunning the earlier checks):
- Official tooling archive checksums and exact versions verified.
- Frozen dependency installation and backend typechecking passed.
- OpenAPI structure, strict JSON Schema/formats and fixture/metadata alignment passed.
- **180 tests passed**: 51 handler and 129 contract tests; zero failures.
- The first startup hit a **900-second limit during Docker image pulls**; one final same-pin retry succeeded within its 600-second bound. No unresolved local image-download blocker remains.
- Actual Docker bindings passed: four project containers, with only database/API ports published, both on `127.0.0.1`; no wildcard/IPv6 publication or host networking observed.
- Synthetic local reset/migration/seed passed. **All 58 pgTAP assertions passed** in one rollback-only SQL test file.
- After independent review corrected an overstrict private CORS probe, **all nine live Edge checks passed**: public GET, immutable metadata, two unreflected hostile origins, query/path errors, POST/OPTIONS/HEAD and final unchanged defaults. Gateway CORS was fixed `*` without credentials, accepted only for this local public metadata surface; production origin configuration remains gated.
- Fixture consistency and the post-pgTAP migration/rollback audit passed.
- **Three separately authored keyless anonymous Data API checks passed:** public OpenAPI returned 200 without `app_private`; selecting `app_private` on the root or canary path returned 406 / `PGRST106`. All three requests and responses were observed, without Authorization, API keys or cookies. Runtime anonymous role and loopback bindings were checked.
- The earlier key-dependent harness remains a recorded failed prerequisite: pinned CLI 3 deliberately omits public-key fields while auth is disabled. Source review established a separate keyless anonymous test strategy; no auth service, permissions or source configuration was changed and no privileged key was retrieved. This proves local anonymous nonexposure, **not authenticated/API-key/tenant isolation**. SQL role-privilege tests are separate evidence.
- Cleanup stopped only this local project; no project containers remained. Source and locks were unchanged by verification.

**Later CI repair verified at `77247e8`: backend cold/warm-cache workflows and Android CI all passed.** See [CI download verification](ci-download-verification.md) for run links and the independently tested official same-asset API route, checksum cache and bounded retry policy. It adds 24 bootstrap fault-test methods while preserving tool versions/hashes and existing backend tests. Complete GitHub cold downloads and verified cache reuse are now execution evidence, not an implementation-only claim.

Earlier external HTTP 504 failures at `1161d5e` and `25d86d6` remain historical failures ([backend](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42568771882), [Android](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42568770792)); Android first recovered at `37ba265`. Local API partial-transfer timeouts are not relabelled as successes. The cleanup guard still runs after later failure when tools installed and skips a missing-tool cleanup after installation failure.

Commands (independent verifier; see the runbook for prerequisites):

```sh
bash ops/tooling/install-backend.sh
source ops/tooling/backend-env.sh
deno install --frozen --entrypoint supabase/functions/api/*.ts contracts/*.ts ops/tooling/*.ts
deno task check
deno task contracts
deno task test
bash ops/tooling/local-backend.sh start
bash ops/tooling/local-backend.sh reset
deno task db:test
bash ops/tooling/local-backend.sh stop
```

Private execution reports/logs are ignored under `ops/private/agent-team/package1-foundation/`. Never publish CLI start/status logs: even local tooling can print conventional development credentials.

## Result and remaining gates

The scoped **local foundation checks passed** across the independently recorded runs on unchanged runtime/tests: typechecking/contracts, 180 handler/contract tests, 58 pgTAP assertions, nine live Edge probes, three anonymous Data API probes, loopback bindings and migration/rollback audit. Source/locks stayed unchanged; each local stack was stopped with no project containers remaining. Earlier failed attempts are preserved, not rewritten as successes. No phone was needed for backend verification.

Staging prerequisites/provisioning/deployment, web tooling, full non-billing backend contracts, applicable clean-build reproducibility/signing, production identifiers, Play eligibility/track tests and the Play-specific commerce contract remain incomplete. No new package-2 feature was advanced. See [local runbook](../runbooks/local-backend.md) and [device verification](device-storage-verification.md) for separate evidence and boundaries.
