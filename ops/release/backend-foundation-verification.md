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
- **Live Data API nonexposure remains unverified:** all three groups stopped before HTTP because the harness expected the legacy status field `ANON_KEY`, unavailable in this local CLI response. An independent test-author review of pinned CLI 3's public-key interface is underway. This is not evidence that the private schema is exposed.
- Cleanup stopped only this local project; no project containers remained. Source and locks were unchanged by verification.

CI at `1161d5e` is **not green**: backend tooling downloads failed with HTTP 504 (including one identical retry); Android CI failed fetching the pinned Kotlin compiler with HTTP 504. These are external bootstrap failures, not passing test results. A minimal workflow cleanup guard was authored separately so a failed tooling installation does not cause a misleading second cleanup failure. Independent static review confirmed cleanup still runs after startup/later failure when tools installed; this is not a CI pass.

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

## Remaining gates

Staging prerequisites/provisioning/deployment, web tooling, full non-billing backend contracts, applicable clean-build reproducibility/signing, production identifiers, Play eligibility/track tests and the Play-specific commerce contract remain incomplete. No new package-2 feature was advanced. See [local runbook](../runbooks/local-backend.md) and [device verification](device-storage-verification.md) for separate evidence and boundaries.
