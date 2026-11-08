# Three-agent development workflow

Requested by the owner. Product scope remains governed by `prd.md` and `buildplan.md`; delegation does not authorize alternate architecture, account purchases, deployment, live capture or deferred phone tests.

## Roles

1. **Implementation agent:** changes production code/build setup and migrations only. No test edits or claims of test execution. Makes small scoped commits; does not push.
2. **Test-writing agent:** independently writes tests and schema checks against the agreed requirements/API contract. Does not change production code or weaken expectations to conceal failures. Does not run the test suite or push.
3. **Verification agent:** runs integrated tests, lint, builds and static checks, then reports the exact source SHA, commands, exit statuses, counts and remaining gaps. Does not fix production code or tests. Generated schema/lock artifacts may result from builds and must be reviewed before committing.

The coordinating assistant reviews diffs, resolves handoffs, integrates scoped commits and pushes only after reviewing verification results. Failures go back to the responsible implementation/test-writing role; verification is rerun after changes. Baseline results are explicitly separate from results for newly integrated code.

## Isolation and evidence

- Run separate Pi processes/contexts in separate git worktrees; implementation/test branches share a known starting commit.
- Implementation and test writing can run in parallel; final verification runs on their integrated source, not a moving worktree.
- Reuse the approved model/toolchain. No new packages, providers or external services are implied by the agent workflow.
- Keep role prompts, sessions and raw execution logs under ignored `ops/private/`; do not publish credentials, phone identifiers, real alerts or statements.
- Separate worktrees and role instructions prevent routine edit collisions; they are not an operating-system security sandbox.
- Do not equate compiled Android tests with executed device tests, a passing local build with CI success, or USB prototype checks with Google Play qualification.
