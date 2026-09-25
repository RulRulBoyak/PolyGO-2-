# PolyGo+ Play Store release copy

## Store listing

**App name:** PolyGo+

**Short description (80 characters maximum):**

Buy, sell and connect safely within the PKS campus community.

**Full description:**

PolyGo+ is a campus marketplace and community hub built for Politeknik Kuching Sarawak students and staff.

Discover textbooks, electronics, food, services and other useful campus listings. Create listings with photos, receive AI-assisted draft details, chat with buyers or sellers, make offers and arrange safe campus meetups.

Stay connected through Campus Pulse, where students can read official announcements, share campus updates, like posts and join discussions. Built-in campus tools include events, timetable planning, a CA calculator and sustainability impact tracking.

Key features:

- Campus-only marketplace listings and search
- Buyer and seller messaging, offers and transaction tracking
- AI-assisted listing drafts from photos
- Campus Pulse posts, announcements, likes, comments and sharing
- Push notifications for important activity
- Profile privacy, reporting and account moderation tools
- Data export and in-app account deletion
- English and Bahasa Melayu support

PolyGo+ does not process real-money payments. Buyers and sellers arrange payment and meetups directly. Always inspect items and meet in a safe public campus location.

## Release notes (version 1.0)

Welcome to the first PolyGo+ release. Buy and sell on campus, create AI-assisted listings, chat and manage offers, follow Campus Pulse announcements and discussions, discover campus events, and track your sustainability impact. This release also includes account security, email verification, password recovery, push notifications, reporting, privacy controls and account deletion.

## Required Play Console links

- Privacy policy: `https://YOUR-DOMAIN/polygo-api/privacy.php`
- Terms: `https://YOUR-DOMAIN/polygo-api/terms.php`
- Account deletion: `https://YOUR-DOMAIN/polygo-api/account_deletion.php`
- Support email: `support@poliku.com`

Replace `YOUR-DOMAIN` only after all three pages load publicly over HTTPS.

## Data safety answers to verify in Play Console

The app handles account identifiers, profile information, photos, messages, marketplace content, approximate/precise location when the user chooses location features, app interactions, crash logs and device identifiers. Data is used for app functionality, authentication, safety/moderation, analytics, fraud prevention and developer communications. Transport must be HTTPS in production. Users can request deletion in the app and through the public account-deletion page.

Review the final Play Console form against the enabled Firebase products and production retention policy before submission; the console declarations are a legal/product-owner responsibility.

## Release checklist

- Deploy the API and MySQL database to a stable HTTPS host.
- Set `APP_ENV=prod`, a permanent `JWT_SECRET`, `PUBLIC_API_BASE_URL`, SMTP, Gemini and Firebase settings on the server.
- Add the release SHA-1 and SHA-256 from `gradlew signingReport` to Firebase, then download a refreshed `google-services.json`.
- Restrict the Maps key to package `com.poliku.polygoplus` and the release SHA-1.
- Enable Firebase App Check for the chosen distribution channel before enforcing it on the server.
- Upload `app/build/outputs/bundle/release/app-release.aab` to an internal test track first.
- Complete content rating, target audience, ads, app access and Data safety declarations.
- Test registration/OTP, Google sign-in, password reset, listings, AI, chat, offers, notifications and account deletion on a release build installed from Play.

