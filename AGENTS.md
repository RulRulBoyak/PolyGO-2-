# PolyGo+ Agent Guide

## Project Structure
- Single-module Android app (`app/`), Java 11, MVVM, ViewBinding (no Compose). Package/namespace: `com.poliku.polygoplus`.
- PHP backend in `backend/polygo-api/`, served from `C:\xampp\htdocs\polygo-api\` (XAMPP Apache + MariaDB, port **3306**, DB `polygo`, user `root`, no password).
- `backend/polygo.sql` is the master schema. Schema changes must also ship as an incremental `backend/migration_*.sql` script (applied manually) — existing DBs are not re-imported.
- `PolyGoReact/` is empty and unused.
- Dependency versions live in the version catalog `gradle/libs.versions.toml`, referenced as `libs.*` in `app/build.gradle.kts`.

## Build & Verify (Windows)
```bash
.\gradlew.bat assembleDebug     # compile check
.\gradlew.bat checkstyle         # custom task over src/main/java only
```
- `php` is NOT on PATH. Lint PHP with: `& "C:\xampp\php\php.exe" -l backend/polygo-api/<file>.php`
- Unit tests (`.\gradlew.bat test`) contain only the boilerplate `ExampleUnitTest` — not meaningful. Instrumented tests need a device/emulator: `.\gradlew.bat connectedAndroidTest`.

## Backend conventions
- One endpoint = one `.php` file; actions dispatch on a JSON `action` field (e.g. `block.php`: block/unblock/list, `pulse.php`: list/post).
- `config.php` provides `input_json()`, `respond($ok,$msg,$extra)` (JSON + exit), `verify_jwt()`, `verify_jwt_optional()` — reuse them; never echo raw JSON.
- JWT secret: `backend/polygo-api/secrets.php` (gitignored) may `define('JWT_SECRET', …)`; otherwise a dev fallback is used.
- `secrets.php` also holds (all gitignored): `GEMINI_API_KEY` (server-side AI proxy — never in the APK or `local.properties`), `ADMIN_PASSWORD` (Matrix-verification web approvals via `admin_verify.php`), and optional `APP_CHECK_ENFORCE` + `FIREBASE_PROJECT_NUMBER` (App Check).
- Verification: `verify.php` handles photo upload/submit; `admin_verify.php` (web, guarded by `ADMIN_PASSWORD`) approve/rejects. `config.php::verify_jwt()` auto-rejects posts from unverified accounts; the app mirrors this with `ui/VerificationGate.java`.
- Consent: `register.php` rejects signup unless `consent_agreed=true` and records `consent_agreed_at`; the Android client sends it from `RegisterActivity`'s terms checkbox. Do not remove the check.
- Bug reporting: `report_bug.php` inserts into `bug_reports` (description + device/OS/app version, optional screenshot via `upload_image.php`).
- `service-account.json` (gitignored) is only needed by `NotificationManager.php` for FCM push — its absence breaks push, not the REST API.

## Critical Setup
- `local.properties` (gitignored) feeds `GOOGLE_MAPS_API_KEY`, `GOOGLE_WEB_CLIENT_ID` into the build (`app/build.gradle.kts` → `BuildConfig` + `@string/google_maps_api_key` resValue). Never hardcode keys in Java/XML. The Gemini key is **not** a build key — it lives in backend `secrets.php` (`GEMINI_API_KEY`) and is only used by `ai_suggest.php`.
- `app/google-services.json` required for Firebase (gitignored).
- Backend base URL is in `di/NetworkModule.java`: debug → `http://10.0.2.2/polygo-api/`, release → `https://polygo.pks.edu.my/polygo-api/`. The emulator works as-is; a **physical device in debug requires editing that base URL** (there is no `NetworkApi.java`).
- Cleartext HTTP is allowed only for `10.0.2.2`/`localhost` via `res/xml/network_security_config.xml`; everything else requires HTTPS.

## Architecture (navigating the code)
- **Activities live in the ROOT package** `com.poliku.polygoplus/` (~40 files, e.g. `BugReportActivity`, `VerificationActivity`, `CampusPulseActivity`); only `fragments/`, `viewmodel/`, `api/`, `network/`, `data/`, `di/`, `ui/` are subpackages.
- Hilt DI in `di/` (`NetworkModule` provides Retrofit/PolyGoApi + OkHttp interceptors).
- **Layouts are split by feature**, not flat: `app/src/main/res/layouts/{auth,home,explore,profile,messaging,marketplace,common}/layout/` are wired in `app/build.gradle.kts` sourceSets. Find/edit layouts inside the matching subfolder, never `res/layout/`.
- UI strings are duplicated: `res/values/strings.xml` (EN) + `res/values-ms/strings.xml` (BM) — keep both in sync for any new text.
- Data layer: Room (`data/local/`) via `PolyGoRepository`, plus the legacy static `data/AppDataStore` (EncryptedSharedPreferences-backed local cache) still used by many screens.
- Networking quirks: an OkHttp interceptor in `NetworkModule` funnels `IOException` (server unreachable/timeout) into `NetworkErrorHandler`, launching `ErrorStateActivity` — expected when testing offline. `network/BookLookup` calls the public keyless Google Books API.

## Conventions
- Commit format: `feat(ui): …` / `fix(network): …`.
- Activity transitions: `R.anim.fade_in` / `R.anim.fade_out`.
- Checkstyle enforced before push: 4-space indent, no star imports, no unused imports.

# Project Rules

This is a student development project.

## File Access
- Work only inside this project directory.
- Do not access files outside the project.
- Do not read .env files.
- Do not search my home directory.
- Do not access personal files.

## Security
- Never expose passwords, API keys, tokens, or credentials.
- Ask before running shell commands.
- Ask before modifying files.
- Explain major changes before applying them.

## Development
- Explain important changes.
- Do not delete existing code unless necessary.
- Do not install dependencies without asking first.

## Backend workflow (XAMPP)
- Backend runs from `C:\xampp\htdocs\polygo-api\` (Apache + MariaDB on port **3306**).
- `php` is NOT on PATH. Always use the full path when linting PHP files: `& "C:\xampp\php\php.exe" -l <file>`.
- To query the live database use: `& "C:\xampp\mysql\bin\mysql.exe" -u root -e "<query>" polygo`.
- **Do NOT use shell input redirection** (`<`) with the PowerShell `mysql.exe` tool — the `<` character is treated as a reserved operator and the query will fail. Always pipe with `Get-Content <file> | & "C:\xampp\mysql\bin\mysql.exe" -u root polygo` or use `-e`.
- DB credentials: host `localhost`, user `root`, password (empty), database `polygo`.
- One endpoint = one `.php` file; actions dispatch on a JSON `action` field (e.g. `listings.php` supports `mylistings` action, `categories.php` supports `list_majors`).
- `config.php` provides `input_json()`, `respond($ok,$msg,$extra)`, `verify_jwt()`, `verify_jwt_optional()`. Reuse them; never echo raw JSON.
- Never commit or expose `secrets.php`, `service-account.json`, or DB credentials.
- Schema changes ship as both an **incremental migration** (`backend/migration_*.sql` — applied manually once against live DB) and a mirror update to the master schema (`backend/polygo.sql` — used for fresh installs).

## App Check (write endpoints)
- The Android app attaches `X-Firebase-AppCheck` to non-GET requests via an OkHttp interceptor (`di/NetworkModule.java`); providers: debug = `DebugAppCheckProviderFactory` (`firebase-appcheck-debug`), release = Play Integrity (`PolyGoApplication.installAppCheck()`).
- `config.php::verify_jwt(true)` gates `verify_app_check()` for authenticated write endpoints. Enforcement is OFF by default.
- To enable: add to `backend/polygo-api/secrets.php` (gitignored) `define('APP_CHECK_ENFORCE', true);` and `define('FIREBASE_PROJECT_NUMBER', '<project number>');` (aud check). Firebase console must have App Check enabled and the app's debug/provider token registered — otherwise default-enabled providers never launch for debug builds.
- `verify_app_check()` verifies RS256 via **bcmath only** (`app_check_verify_signature`), no openssl dependency — XAMPP's openssl needs an `OPENSSL_CONF` env at process start and is unreliable otherwise. JWKS (`https://firebaseappcheck.googleapis.com/v1/jwks`) cached 1h in `sys_get_temp_dir()`; fails OPEN (logs) if Firebase is unreachable, fails CLOSED on signature/claim errors.
- Do NOT put App Check on read endpoints; `verify_jwt(false)`/`verify_jwt_optional()` stays App-Check-free.