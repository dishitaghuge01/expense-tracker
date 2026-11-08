# Pure Kotlin prototype foundation

Implements the channel-independent rules in build plan §§2/6 and PRD accounting requirements. No Android APIs, network, storage, alert access or activation. This is partial local-engine groundwork, not completion of the distribution gate or work package 2.

## Implemented

- `Inr`: positive integer-paise bounds (1–100,000,000,000), exact normalized statement amount parsing, known-template amount-digit parsing with valid grouping, Indian display grouping and checked signed summary accumulation. No floating point or rounding. The alert helper does not inspect free-text SMS, select the payment amount or identify a supported bank template.
- `ReportingDates`: strict ISO dates/months, UTC RFC 3339 instants, Asia/Kolkata reporting dates, inclusive statement coverage with future-date rejection and Monday-start weeks. Date-only rows remain dates; no event instant is invented.
- `CaptureAccess`: separate live/history/notification opt-ins, explicit permission/source states and fail-closed activation/lease/pause gates. Live inbox recovery additionally requires READ_SMS. This consumes an already-verified lease status; it is not a signed-token verifier or capture implementation.
- `Accounting`: deterministic monthly gross/refunds/net, category totals, separate withdrawals and review IDs. Filters confirmed/included records; repayments, transfers, top-ups, incoming, pending/failed events and reversals do not add spending. Reversed purchases no longer contribute in the original month.
- Refunds require a confirmed included original by the same payer/category, including originals outside the reporting month. Over-limit partial-refund groups are excluded and flagged for review rather than selecting an arbitrary winner. Only an explicitly supplied server retention marker permits an expired-original shared refund; missing data never implies expiry.
- Exact duplicate record replay counts once; conflicting payloads under one ID fail. Different IDs are not merged by amount/date similarity.
- Personal and household ledgers are separate keys; mixed scopes or different owner/household IDs fail. Callers remain responsible for authorization and obtaining a complete, consistent snapshot. This reducer is not a cloud permission boundary or an SMS deduplication engine.

## Tests and limits

40 JUnit tests currently cover these rules plus the original permission-state/synthetic-demo checks. Run `:core-model:test` from `android/` using the documented Java 17 toolchain.

Error messages contain bounded reason codes, not financial input. Summaries use checked signed 64-bit arithmetic. Invalid refund issues are returned without editing records, auto-sharing or silently approving corrections.

Room/SQLCipher, capture/parsers, linking/reversal actions, CSV streaming, budget alerts, sync, screens and production billing are not implemented here. The existing Compose prototype is still permission-only; these functions are not connected to a live ledger. The owner reported that the 0.0.2 Redmi revocation/reopen retest now reports the expected non-granted state.
