# Independent local backend foundation verification

This is a partial package-1 slice, not staging or full backend completion.

## Source and scope

Latest verification source: `f677cb5` (the subsequent documentation changes do not alter runtime or tests). Separate implementation and independent test-writing agents authored the slice; an independent verifier executed it. No cloud resources, production credentials, users, business/commerce tables, auth/household features, website deployment or paid services were introduced.

Added:
- Local Supabase configuration, no-op synthetic seed and a migration creating server-only `app_private` with restricted schema/default privileges. Future tables still need explicit RLS; an empty schema is not a working ledger.
- Checksum-pinned Deno 2.9.8/Supabase CLI 3.0.0, frozen dependency lock and local-only Docker wrapper.
- Immutable public release metadata, a partial OpenAPI 3.1 contract and synthetic fixtures. Play/support URLs are null and checkout closed; no production identity or billing contract is invented.
- Separate backend CI plus independent handler, contract-negative and transactional pgTAP tests.

## Executed checks

The first integrated run at `741143b` stopped on two validator TypeScript errors. Implementation repaired CommonJS plugin typing and the validated-envelope type; the verifier did not repair source or weaken tests.

At `f677cb5`:
- Official tooling archive checksums and exact versions verified.
- Frozen dependency installation and backend typechecking passed.
- OpenAPI structure, strict JSON Schema/formats and fixture/metadata alignment passed.
- **180 tests passed**: 51 handler and 129 contract tests; zero failures.
- Local Supabase startup hit the verifier's **900-second limit during initial Docker image pulls**. No project services started in that attempt. The sanitized progress summary showed image pulls but no specific network error; the precise cause of the delay is not established. The verifier is making one final bounded 600-second attempt with the same pins and partially cached images; no success is claimed here.
- **No reset/migration, pgTAP, live Edge routing or actual port-binding checks executed.** These remain unverified. Cleanup stopped only this local project; no project containers remained. No source or locks were changed by verification. Do not infer these missing results from unit/contract success.

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
