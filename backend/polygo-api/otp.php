<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/Mailer.php';

// Self-heal: ensure the OTP storage table exists even before the migration runs.
try {
    $pdo->exec('CREATE TABLE IF NOT EXISTS verification_codes (
        email VARCHAR(160) NOT NULL PRIMARY KEY,
        code_hash VARCHAR(255) NOT NULL,
        expires_at DATETIME NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
} catch (Throwable $ignored) {}

$input = input_json();
$action = (string)($input['action'] ?? '');
$email = strtolower(trim((string)($input['email'] ?? '')));

if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
    respond(false, 'Please provide a valid email address');
}

// Rate limit per IP to slow down brute force / spam.
$ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
if (!rate_limit_check($pdo, 'otp:' . $ip, 10, 300)) {
    respond(false, 'Too many attempts, please try again later');
}

try {
    if ($action === 'send') {
        // Only allow a few codes per email in a short window.
        if (!rate_limit_check($pdo, 'otp_send:' . $email, 5, 600)) {
            respond(false, 'Too many codes requested, please try again later');
        }

        $check = $pdo->prepare('SELECT id FROM users WHERE email = ?');
        $check->execute([$email]);
        if ($check->fetch()) {
            respond(false, 'An account with this email already exists');
        }

        // True random OTP; never hardcoded. Stored hashed with a short expiry.
        $otp = (string)random_int(100000, 999999);
        $store = $pdo->prepare('INSERT INTO verification_codes (email, code_hash, expires_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE code_hash = VALUES(code_hash), expires_at = VALUES(expires_at)');
        $store->execute([$email, password_hash($otp, PASSWORD_DEFAULT), date('Y-m-d H:i:s', time() + 600)]);

        if (defined('APP_ENV') && APP_ENV === 'dev') {
            // Log + echo only in the emulator workflow; never in production.
            error_log("[polygo-api] OTP for $email: $otp");
            if (defined('DEV_OTP_ECHO') && DEV_OTP_ECHO) {
                respond(true, 'OTP sent successfully', ['otp' => $otp]);
            }
        }

        $body = 'Your PolyGo+ verification code is: ' . $otp
            . "\n\nIt expires in 10 minutes." . "\n\n"
            . 'If you did not request this, you can safely ignore this email.';
        // Never report success when the email failed to actually send — the
        // dev/log fallback returns true, so only a real SMTP failure surfaces.
        if (!Mailer::send($email, 'PolyGo+ verification code', $body)) {
            respond(false, 'Unable to send the verification email, please try again');
        }
        respond(true, 'OTP sent successfully');
    }

    if ($action === 'verify') {
        $otp = (string)($input['otp'] ?? '');
        if ($otp === '') {
            respond(false, 'Please enter the verification code');
        }

        // Brute-force wall: a handful of wrong codes per email locks the flow
        // for 15 minutes, independent of the per-IP limit above.
        if (!rate_limit_check($pdo, 'otp_verify:' . $email, 5, 900)) {
            respond(false, 'Too many attempts, please try again later');
        }

        $find = $pdo->prepare('SELECT code_hash, expires_at FROM verification_codes WHERE email = ? LIMIT 1');
        $find->execute([$email]);
        $row = $find->fetch();

        if (!$row || strtotime((string)$row['expires_at']) < time()) {
            respond(false, 'Code expired, please request a new one');
        }
        if (!password_verify($otp, (string)$row['code_hash'])) {
            respond(false, 'Invalid verification code');
        }

        $delete = $pdo->prepare('DELETE FROM verification_codes WHERE email = ?');
        $delete->execute([$email]);
        // A successful verify resets the attempt counter for this email.
        $reset = $pdo->prepare('DELETE FROM rate_limits WHERE bucket = ?');
        $reset->execute(['otp_verify:' . $email]);
        respond(true, 'Email verified successfully');
    }

    respond(false, 'Invalid action');
} catch (Throwable $e) {
    error_log('[polygo-api] otp error: ' . $e->getMessage());
    respond(false, 'Verification failed, please try again');
}