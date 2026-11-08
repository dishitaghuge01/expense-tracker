# Work package 0 — distribution validation

Sources: build plan §§1, 4, 9–10; PRD §§3, 5. All checks below are pending. A checklist is not evidence that a gate passed.

## Google Play selected

Owner selected Google Play. The browser procedure below is retained as historical website-option reference only; it is not the launch gate. Prepare tooling and synthetic/consent tests now; finalize identifiers, Play App Signing, SMS permission eligibility and owner-approved payment-policy/budget revisions before a public pilot. Domain purchase can wait during local development. A disposable prototype application ID is owner-approved.

### Active Play gate additions

- [ ] Verify Play Console account type, identity/device verification and current testing requirements; no account purchase authorized here.
- [ ] Verify SMS permission eligibility and required declaration/review; do not assume the expense-tracking use case is automatically approved.
- [ ] Verify current target-API submission requirement; retain API 36 until any change is approved.
- [ ] Configure production package, Play App Signing, separate upload key and OAuth app-signing fingerprints.
- [ ] Submit an AAB to the appropriate Play testing track and test installs/updates through Play, not ADB or a direct APK link.
- [ ] Repeat consent, both grants, activated real-alert and update-preservation checks on the PRD device/household matrix.
- [x] Owner approved free installation plus a one-time non-consumable Play Billing household unlock, initially ₹499 with later Play Console price changes allowed; existing purchasers keep their licence without another charge.
- [ ] Finalize the Play purchase/refund/entitlement provider contract and obtain approval for revised launch fees/spend; do not implement the superseded Razorpay design.

Official verification references: [SMS/Call Log permissions](https://support.google.com/googleplay/android-developer/answer/10208820), [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738), [App signing](https://developer.android.com/studio/publish/app-signing), [Personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465). These are verification tasks, not claims of completed approval.

Available short-term device: Redmi Note 11T 5G (21091116AI), Android 13, build TP1A.220624.014. No tests performed. A replacement phone requires its own device-specific checks; no automatic private-ledger migration is promised.

## Historical website-option preconditions (superseded)

- [ ] Confirm owner-controlled domain and derived application ID.
- [ ] Establish dedicated, offline-kept production signing key; record public fingerprint only.
- [ ] Use that same production package/key for public pilot and production updates.
- [ ] Provision Java 17 and stable Android build tools, min API 29, compile/target API 36.
- [ ] Build minimal signed prototype with a synthetic demo and separate consent-only permission probe.
- [ ] Publish APK at an official-style HTTPS test link with version, checksum and provenance.
- [ ] Begin individual Razorpay digital-licence eligibility and domain/Google approval checks; no paid launch or spending implied.

## Prototype boundaries

The locked app may request/check permissions only after explicit consent. It must not query SMS, scan history, or process notification contents before activation and subsequent capture opt-in. Do not request SEND_SMS, contacts, accessibility, or default SMS role. Raw alert contents must never be persisted, uploaded, or logged.

Live-bank-SMS testing requires a separately activated, consented private test path. A permission grant alone does not authorize capture. Synthetic tests do not establish real bank-format coverage.

## Historical browser install procedure (superseded)

For each test combination, record only non-identifying device/manufacturer, Android version, browser version, APK version/hash, date, result and bounded reason codes. Keep participant/contact details and real financial data out of this repository.

1. Download the signed APK through Chrome from the HTTPS test page; do not use ADB or IDE installation as gate evidence.
2. Verify Android installer completion and that the app starts locked.
3. Run synthetic demo; verify no SMS query or notification processing occurs.
4. Explain and explicitly consent to the compatibility probe. Test READ_SMS and RECEIVE_SMS separately; record actual granted/denied/unavailable/blocked status.
5. Verify the probe never reads alert contents, even if permissions are granted.
6. Only after activation and separate capture consent, observe an eligible real bank alert. Keep contents on the device and record only pass/fail evidence.
7. Revoke a permission in Android settings. Verify accurate source state and that the revoked source stops capture.
8. Download a newer APK through the same HTTPS channel. Verify Android accepts its signature and preserves app data on update.
9. Record failures and exclude unverified combinations from advertised support. If core public-install capture fails, keep checkout closed and testing private.

## Pilot installation gate (Google Play; not satisfied by one successful test)

- 12 volunteers, ideally six real households.
- At least three manufacturers; Android 13–16 where available.
- At least 10 of 12 complete Play installation and live SMS activation without developer tools.
- No unresolved failure in any advertised combination.
- Verify update data preservation and permission-revoke status.

Survey bank/issuer coverage, Android versions and actual household pairing without collecting alert text, account numbers or statements. Accuracy, household value, security and commerce gates remain separate requirements in PRD §5.

## Evidence table

No evidence collected yet. Add only non-identifying results after actual tests.

| Date | Manufacturer / model | Android | Chrome | APK version / SHA-256 | Browser install | READ_SMS | RECEIVE_SMS | Probe no-read | Activated live alert | Revoke | Update / preserved data | Result / reason |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
