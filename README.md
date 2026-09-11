# PolyGo+ 🎓

PolyGo+ is a modern, student-centric marketplace designed exclusively for the **Politeknik Kuching Sarawak (PKS)** campus. It enables students and staff to buy, sell, and offer services (like printing, delivery, or repairs) safely within the campus ecosystem.

## 🚀 Key Features

- **Campus-Only Marketplace**: Restricted to verified PKS emails.
- **Service Portfolio**: Students can offer and browse technical services.
- **Matrix Card Verification**: Students verify their identity by submitting a photo of their Matrix card; an admin approves/rejects it from a web panel. Publishing is gated until the account is approved.
- **SafeMeetup**: In-app deal coordination with location-sharing disclosure so transactions happen safely on campus.
- **AI Discovery**: Server-side Google Gemini proxy (`ai_suggest.php`) for smart listing suggestions and categorization.
- **Real-time Chat**: In-app messaging for safe deal negotiations, with block/report controls.
- **Campus Pulse**: Campus-wide announcements and updates feed.
- **Sustainability Dashboard**: Track your environmental impact (CO₂, water, paper, energy) by recycling second-hand goods on campus.
- **Push Notifications**: FCM (HTTP v1) via a server-side NotificationManager.
- **PDPA Consent Log**: Explicit GDPR/PDPA-style Terms & Privacy agreement captured at registration with a server-side timestamp (`consent_agreed_at`).
- **Offline Publishing**: Listings created offline are queued via WorkManager and uploaded once connectivity returns.
- **Bug Reporting**: In-app "Report a Bug" flow submits device/OS/app-version metadata with optional screenshot.
- **App Check + JWT**: Every write endpoint verifies a Firebase App Check token (Play Integrity in release, debug provider in debug) plus a signed JWT.

---

## 📁 Project Structure

```
PolyGo/
├── app/                          # Android app (single module, Java 11, ViewBinding)
│   ├── src/main/java/com/poliku/polygoplus/
│   │   ├── *.java                # ~38-40 Activities (root package)
│   │   ├── fragments/            # Home, Explore, Messages, Profile
│   │   ├── viewmodel/            # ViewModels (Auth, Home, Explore…)
│   │   ├── api/  network/        # Retrofit + DTOs; FCM, connectivity, error handler
│   │   ├── data/                 # Room DAOs, PolyGoRepository, legacy AppDataStore
│   │   ├── di/                   # Hilt modules (Network, Database, App)
│   │   └── ui/                   # Base classes, gates, adapters, formatters
│   └── src/main/res/layouts/     # Feature-split layout source sets
│       ├── auth/ home/ explore/  # (see app/build.gradle.kts sourceSets)
│       ├── profile/ messaging/ marketplace/ common/
├── backend/
│   ├── polygo.sql                # Master MySQL schema (fresh installs)
│   ├── migration_*.sql           # Incremental migrations (apply to live DBs)
│   └── polygo-api/               # PHP REST API (~30 endpoints)
├── config/checkstyle/            # Checkstyle rules (4-space indent)
├── PolyGoReact/                  # Unused (empty)
└── gradle/libs.versions.toml     # Version catalog
```

---

## 🛠️ Tech Stack

| Layer | Technologies |
|-------|--------------|
| **Android** | Java 11, MVVM, Hilt, Room, Retrofit/OkHttp/Gson, Material3, ViewBinding, Glide, Shimmer, Lottie, Konfetti, CameraX + ML Kit, WorkManager, EncryptedSharedPreferences, Biometric, ViewPager2 |
| **Firebase** | App Check (Play Integrity, BOM), Auth, Messaging (FCM), Crashlytics |
| **AI** | Google Gemini (1.5 Flash) — **server-side only** via `ai_suggest.php`; key stored in backend, never in the APK |
| **Security** | HS256 JWT (BCMath, custom), Firebase App Check verification (RS256 via bcmath-only), rate limiting, prepared statements |
| **Backend** | PHP 8 (strict_types), PDO + prepared statements, MySQL, custom JWT, FCM HTTP v1 notifications |

---

## 🏗️ Architecture Notes

- **MVVM + Hilt**: ViewModels injected via `@HiltViewModel`; DI modules in `di/` (`NetworkModule`, `DatabaseModule`, `AppModule`).
- **Activities live in the root package** `com.poliku.polygoplus/` (a project quirk) — all 38+ of them.
- **Layouts are split by feature** in `res/layouts/{auth,home,explore,profile,messaging,marketplace,common}/layout/`, wired via `sourceSets` in `app/build.gradle.kts` — never put layouts in a flat `res/layout/`.
- **Data**: Room entities/DAOs via `PolyGoRepository`; the legacy static `AppDataStore` (EncryptedSharedPreferences-backed) is still used by several screens.
- **Networking quirks**: an OkHttp interceptor funnels server errors into `ErrorStateActivity`; a second interceptor attaches `X-Firebase-AppCheck` to non-GET requests.
- **Posting gate**: `VerificationGate` blocks publishing until the account's Matrix verification is `approved`.
- **Strings are bilingual**: `res/values/strings.xml` (EN) + `res/values-ms/strings.xml` (BM) — keep both in sync.

---

## 🛠️ Project Setup for Developers

### 1. Prerequisites
- **Android Studio** (Koala or newer).
- **XAMPP** (Apache & MariaDB on port **3306**) for the local backend.
- **Git** for version control.

### 2. Backend Configuration (XAMPP)
1. Copy `backend/polygo-api` to `C:\xampp\htdocs\`.
2. Start Apache and MySQL in XAMPP Control Panel.
3. **Fresh install**: import `backend/polygo.sql`:
   ```powershell
   Get-Content backend\polygo.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root
   ```
4. **Existing database**: apply each incremental migration once, in order:
   ```powershell
   Get-Content backend\migration_hardening.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root polygo
   Get-Content backend\migration_bug_reports.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root polygo
   ```
5. **DB credentials**: `config.php` defaults to host `localhost`, user `root`, empty password, database `polygo`.
6. **Admin panel**: set a password for Matrix-verification approvals by editing `backend/polygo-api/secrets.php` (gitignored):
   ```php
   define('ADMIN_PASSWORD', '<your-password>');
   ```

### 3. API Keys & Security
- **Google Gemini AI**: the key now lives **server-side** in `backend/polygo-api/secrets.php` (gitignored):
  ```php
  define('GEMINI_API_KEY', 'your_actual_key_here');
  ```
  The Android app never receives the key — it calls `ai_suggest.php` which proxies to Gemini. Do **not** put `GEMINI_API_KEY` in `local.properties`.
- **Firebase**: place `google-services.json` in `app/` (gitignored, never commit).
- **Map keys** (`GOOGLE_MAPS_API_KEY`, `GOOGLE_WEB_CLIENT_ID`, and the debug App Check provider token): still fed via `local.properties` into `BuildConfig` (see `app/build.gradle.kts`). Never hardcode keys in Java/XML.
- **App Check enforcement** (optional, OFF by default): add to `backend/polygo-api/secrets.php`:
  ```php
  define('APP_CHECK_ENFORCE', true);
  define('FIREBASE_PROJECT_NUMBER', '<project-number>');
  ```
  The Firebase console must have App Check enabled and the debug provider token registered, otherwise debug builds' App Check token never launches.
- **Emulator access**: the debug build talks to `http://10.0.2.2/polygo-api/`. A physical device requires editing the base URL in `di/NetworkModule.java` (debug block) and HTTPS/cleartext rules in `res/xml/network_security_config.xml`.

### 4. Backend test commands (Windows)
```powershell
& "C:\xampp\php\php.exe" -l backend/polygo-api/login.php       # lint any endpoint
Get-Content backend\polygo.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root polygo
```

---

## 🔒 Security Model

| Layer | Mechanism |
|-------|-----------|
| **Authentication** | Email/OTP + Google sign-in; JWT returned and verified on every request |
| **Write endpoints** | JWT **and** Firebase App Check token (non-GET requests send `X-Firebase-AppCheck`) |
| **App Check** | Debug provider (`firebase-appcheck-debug`) in debug; Play Integrity in release; server verifies RS256 JWKS signatures via **bcmath only** (no openssl dependency) |
| **Identity** | Matrix card photo → admin approves → publishing gate unlocks |
| **Abuse** | Rate limiting on `add_listing.php` (5/min per user, 10/min per IP); JWT secret from `secrets.php` |
| **Passwords** | `password_hash()`/`password_verify()`, never stored in plain text |
| **Import hygiene** | `secrets.php`, `service-account.json`, `google-services.json`, `uploads/` are gitignored |

Never commit `secrets.php`, `service-account.json`, or DB credentials.

---

## 🗄️ Database Migrations

- `backend/polygo.sql` is the **master schema** for fresh installs (import once).
- Any schema change ships TWO deliverables:
  1. an **incremental** `backend/migration_*.sql` (idempotent, applied manually to live DBs), and
  2. a **mirror update** to `backend/polygo.sql`.
- Existing databases are **never re-imported** from `polygo.sql`.

---

## 🤝 Teamwork & Contribution Rules

### Git Branching Strategy
- `main`: Production-ready code only.
- `develop`: Integration branch for features.
- `feature/feature-name`: Individual work branches (e.g., `feature/login-validation`).
- `hotfix/issue-name`: Urgent bug fixes.

### Commit Message Format
Prefix with scope:
```
feat(ui): add new sustainability card
fix(network): handle timeout errors
```

### Coding Standards
- **Architecture**: Follow MVVM pattern.
- **Transitions**: Use `R.anim.fade_in` / `R.anim.fade_out` for activity navigation.
- **Dependency Injection**: Use **Hilt** (in use) for service instances.
- **Linting**: Run `.\gradlew.bat checkstyle` before pushing.
- **No comments** unless the code genuinely needs an explanation.

---

## 📞 Support

For technical issues or backend questions, contact the lead developer or open an issue on the repository.

*Built with ❤️ by the PolyGo+ Team at PKS.*