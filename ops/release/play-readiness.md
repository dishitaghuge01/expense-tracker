# Google Play readiness — verified policy references and open decisions

Reviewed official English policy/help pages on 8 November 2026. These findings are not app approval, an account registration, or permission to spend. Recheck at submission: policies and account-specific Console requirements can change.

## Verified public requirements

| Area | Finding | Effect on this project |
|---|---|---|
| Restricted SMS | The exception table includes **SMS-based money management** (for example, budget tracking), with READ_SMS and RECEIVE_SMS among eligible permissions. Exceptions are subject to review/approval and require core functionality, lack of a suitable alternative, permission declaration, disclosure and consent. | A relevant potential exception exists; acceptance is not guaranteed. Do not request default SMS role or extra SMS permissions. Describe automatic financial-alert capture as core functionality truthfully. A grant in our USB prototype is not policy approval. |
| Digital purchases | Play-distributed apps selling digital functionality generally must use Play Billing unless a documented policy exception or enrolled programme applies. Financial-management software is explicitly included. Steering users to an unapproved website checkout is not permitted. | Do not implement the old Razorpay/website-unlock design. Billing proposal below needs owner approval first. No alternative billing enrollment is assumed. |
| Personal-account testing | New personal accounts created after 13 November 2023 must run a closed test with at least 12 testers opted in continuously for 14 days, then apply for production access. Google reviews the application and may require more testing. | This is additional to the PRD's accuracy/household-value gates; 10/12 successful installs alone does not satisfy it. Internal testing is useful but does not replace the closed test. |
| Registration | US$25 one-time registration fee; identity verification and the distribution agreement apply. No account exists yet, per owner. | Exact INR/card/tax cost must be confirmed by the owner at checkout. The previous direct-distribution launch budget is not a Play budget. No purchase performed or authorized by this note. |
| Account type | Personal and organization accounts can monetize. Organization accounts are required for listed services including financial products/services. Organization verification requires a D-U-N-S number. | Confirm the appropriate account type for this expense-only product and the owner's publishing status before paying. Do not assume that absence of banking/payment initiation alone settles Google's classification. Do not misrepresent a business as a hobbyist or choose an account type to evade testing. |
| Device verification | New personal accounts require verification using the Play Console mobile app on a non-rooted physical Android 10+ phone. The same phone need not be retained for future verification. | Redmi Android 13 meets the OS criterion; rooted/verification status is unverified. A later eligible replacement phone can be used. This is separate from the app's one-active-device licence rule. |
| Target API | The published requirement for new mobile apps/updates since 31 August 2026 is API 36 or higher. | Keep the documented API 36 baseline. No SDK change is needed from this policy review. |

## Proposed commerce migration — NOT APPROVED OR IMPLEMENTED

Recommend a **free-to-install app with a one-time, non-consumable Play Billing household unlock**, retaining the chosen ₹499 household price and five-adult limit. A paid download would not directly preserve the intended one-purchase/custom-household invite workflow.

Before implementing, obtain owner approval and revise the PRD/build plan together for:

- Purchase, pending/cancelled and recovery flows inside Android; no website payment steering.
- Server-side purchase verification, acknowledgement, durable unique purchase ownership, idempotent household fulfilment, restore and refund/revocation handling. Never activate from a client callback alone.
- Binding a verified purchase to the signed-in app owner, with explicit handling of a different Google Play purchasing account. Restore must not mint a second household licence.
- Existing invite membership, pilot upgrade, owner transfer and phone-replacement semantics. Keep the licence attached to its household after accepted ownership transfer.
- Play provider schema/API/event processing replacing the old Razorpay-specific fields/routes, plus purchase credentials and operational reconciliation.
- Support/refunds consistent with Play capabilities and the promised customer rights; no silent removal of the PRD's seven-day full-refund commitment.
- A separately approved budget reflecting registration, actual applicable Play service fees, tax and domain costs. No unverified fee percentage or proceeds calculation is adopted here.

This is a proposal, not a change to the ₹499 price, licence scope, privacy boundary or approved purchase implementation.

## Next actions in sequence

1. Keep the prototype process-restart regression test pending until the owner tests version 0.0.2. Do not mark the bug device-verified yet.
2. Validate an **unsigned disposable AAB** locally/CI. It is build evidence only, not suitable for public pilot, Console submission or production registration.
3. Resolve account type and get owner approval for billing/budget revisions before commerce work or registration spend.
4. Finalize the owned domain, production application ID and Play App Signing/upload-key setup before a public pilot. Never register the disposable prototype as the production app.
5. Prepare an honest SMS permission declaration, privacy/Data safety/app-content disclosures and reviewer access for the implemented app. Do not submit a compatibility-only prototype as completed money-management functionality.
6. When Console setup and an appropriate implemented test build are ready, use Play testing tracks for device grants, actual eligible alerts and update evidence. Retain all PRD accuracy, privacy, security and household-value gates.

## Official sources reviewed

- [SMS/Call Log permission uses and exceptions](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)
- [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en)
- [Personal developer account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Play Console registration and fee](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en)
- [Choose a developer account type](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en)
- [Device verification and replacement-device FAQ](https://support.google.com/googleplay/android-developer/answer/14316361?hl=en)
- [Target API submission requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
