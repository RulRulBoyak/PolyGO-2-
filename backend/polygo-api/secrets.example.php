<?php
declare(strict_types=1);

// Copy to secrets.php on the server and replace every placeholder. Never
// commit the real file. Use a restricted MySQL account, not root.
define('APP_ENV', 'prod');
define('PUBLIC_API_BASE_URL', 'https://your-domain.example/polygo-api');

define('DB_HOST', '127.0.0.1');
define('DB_PORT', 3306);
define('DB_NAME', 'polygo');
define('DB_USER', 'polygo_app');
define('DB_PASS', 'replace-with-a-long-random-database-password');

define('JWT_SECRET', 'replace-with-at-least-32-random-bytes');
define('ADMIN_PASSWORD', 'replace-with-a-long-unique-admin-password');

define('SMTP_HOST', 'smtp-relay.brevo.com');
define('SMTP_PORT', 587);
define('SMTP_USER', 'replace-with-brevo-smtp-login');
define('SMTP_PASS', 'replace-with-brevo-smtp-key');
define('SMTP_FROM', 'replace-with-verified-sender@example.com');

define('GEMINI_API_KEY', 'replace-with-gemini-api-key');
define('GEMINI_MODEL', 'gemini-3.6-flash');
define('GOOGLE_WEB_CLIENT_ID', 'replace-with-google-oauth-web-client-id');

define('FIREBASE_PROJECT_NUMBER', 'replace-with-firebase-project-number');
define('APP_CHECK_ENFORCE', true);

