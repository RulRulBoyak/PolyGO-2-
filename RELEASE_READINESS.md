# PolyGo+ release readiness

Last reviewed: 2026-09-24

## Current build state

- Debug and minified release builds, API-contract unit tests, Checkstyle, Android lint, and release lint pass with zero lint errors. Lint now fails the build on future errors.
- Both APKs verify with APK Signature Scheme v2. The release package is `com.poliku.polygoplus` v1.0 (version code 1), min SDK 24, target SDK 36.
- The local XAMPP API responds successfully to health, campus-event, authenticated transaction-list, authenticated message-list, and Gemini photo-to-listing smoke tests. AI authentication and invalid-photo failures are handled without losing the user's draft.
- Fresh-schema installation, password-reset/token-revocation, duplicate/self-report rejection, exclusive-offer acceptance, account deletion, and transaction lifecycle tests pass.
- Campus Pulse likes and replies are persisted by the API, duplicate likes are idempotent, comment posting is rate-limited, and sharing uses the Android Sharesheet.
- The live database has no QA users or listings.
- A read-only API smoke suite covers status, categories, listings, events, Campus Pulse, and unauthorized Pulse writes (`scripts/local-api-smoke.ps1`).
- Local backup and restore verification are automated (`scripts/backup-local.ps1`, `scripts/verify-backup.ps1`). A verified backup restored all 32 tables successfully on 23 September 2026.
- All 89 deployable backend files match the live XAMPP copy; all 146 PHP files pass syntax lint and all database tables pass `mysqlcheck`.
- Production Android traffic is HTTPS-only. Development HTTP exceptions live only in the debug source set.
- The app targets API 36, matching Google Play's Android 16 requirement for new apps and updates from 31 August 2026.
- OTP verification, registration, password login, password reset and login with the new password passed an end-to-end API smoke test on 24 September 2026; temporary test data was removed.
- SMTP delivery succeeds, Firebase service-account OAuth succeeds, and a live FCM diagnostic push returned HTTP 200.
- A signed, minified release AAB builds successfully. Unit tests, Checkstyle, debug/release lint, debug APK assembly and release bundle assembly pass.

## Required before giving the app to real users

1. Deploy `backend/polygo-api/` and the MySQL database to a public HTTPS host. The default production host, `polygo.pks.edu.my`, does not currently resolve.
2. Build against the final URL with `scripts/release-preflight.ps1 -ApiBaseUrl https://your-host.example/polygo-api/`. The URL must use HTTPS and end with `/`; the script refuses to build unless `status.php` is reachable and healthy.
3. On the server, copy `backend/polygo-api/secrets.example.php` to the gitignored `secrets.php`, replace every placeholder, and use a restricted MySQL account. Configure a permanent random `JWT_SECRET`, `GEMINI_API_KEY`, `APP_ENV=prod`, `PUBLIC_API_BASE_URL`, SMTP credentials, Google web client ID, and the Firebase project number. Optionally pin `GEMINI_MODEL`; the tested default is `gemini-3.6-flash`. Keep `service-account.json` private and outside downloads.
   Apply `backend/polygo-api/sql/20260922_add_token_version.sql`, `backend/polygo-api/sql/20260922_password_reset_codes.sql`, and `backend/polygo-api/sql/20260922_add_pulse_social.sql` before switching traffic.
4. Configure Firebase App Check for the intended distribution channel. For a directly shared/sideloaded APK, change the Play Integrity App Check settings for distribution outside Google Play before enabling `APP_CHECK_ENFORCE`.
   The current Firebase configuration does not register either the debug or release APK signing SHA-1, so Google sign-in remains blocked until the certificate is added in Firebase/Google Cloud and `app/google-services.json` is refreshed. Release preflight now rejects this automatically.
5. Test the release build on at least one real phone and one tablet: register/login/OTP, image upload, AI listing generation, listing creation, chat and unread state, offer/accept/complete/review, notifications, password reset, export, and account deletion.
6. Back up the database and uploaded images, enable HTTPS renewal and server monitoring, and verify restore procedures before launch.
7. Rotate both the Firebase service-account private key and Gemini API key before public launch. The local Apache tree previously served `service-account.json` directly, and the Gemini key was visible during the local audit. The new root `.htaccess` blocks credential, backup, SQL, log, and dependency-metadata downloads, but exposed credentials must never be trusted again.
8. Complete the Play Console Data safety form for Analytics, Crashlytics, FCM, App Check/Play Integrity, location, camera, and user-generated marketplace data. Publish and verify `/privacy.php`, `/terms.php`, and `/account_deletion.php` on the final HTTPS host.
9. After rollout, enable Play Console alerts and watch Android vitals for crashes, ANRs, wake locks, memory, and slow rendering. Start with an internal test track, then a staged production rollout.

## Build and verify

```powershell
.\scripts\release-preflight.ps1 -ApiBaseUrl https://your-host.example/polygo-api/
```

Release artifact: `app/build/outputs/apk/release/app-release.apk`

Play Console artifact: `app/build/outputs/bundle/release/app-release.aab`

- Release AAB: 20,046,352 bytes; SHA-256 `285A3100A27A43CE9E807ABA3A6BFC930DAE8943ADE8B705C1493762ECB15978`

- Release APK: 15,763,784 bytes; SHA-256 `DC84129ABA08A6365FA0AF9B556BAAA3ED1F33C351BDDD5EEE17956852CD3A1A`
- Debug APK: 28,951,810 bytes; SHA-256 `0083A84593F961802630671463E2BF0A4A587563AFF41F47C7078F46D4BFA050`

Keep the release keystore backed up securely. All later APK updates must use the same signing identity.

## Known maintenance items

- The custom nested Android resource source-set structure is accepted today but must be flattened before an Android Gradle Plugin 9 upgrade.
- Android lint has no errors, but still reports non-blocking localization, unused-resource, overdraw, and accessibility cleanup work.
- Device instrumentation could not be rerun during the final pass because the previously connected phone disconnected. Reconnect it and run `./gradlew connectedDebugAndroidTest` before distribution.
- The shared-password admin panel is suitable for one trusted operator, but a multi-admin deployment should add named admin accounts and MFA for individual accountability.

## Public-host options

- Preferred: deploy PHP, MySQL, uploads, SMTP, and Firebase credentials to managed hosting with a stable domain and HTTPS.
- If the XAMPP computer must remain the origin, a named Cloudflare Tunnel can publish `http://localhost` behind a stable HTTPS hostname, but the computer and tunnel service must stay online and the database/uploads still need backups.
- A random TryCloudflare/Quick Tunnel is suitable only for temporary testing, not for an APK given to real users.
