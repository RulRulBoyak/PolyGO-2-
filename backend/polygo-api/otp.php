<?php
require_once __DIR__ . '/config.php';

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

        error_log("[polygo-api] OTP for $email: $otp"); // simulator only; use a real email service in production
        respond(true, 'OTP sent successfully', ['otp' => $otp]); // dev-only leak, remove before production
    }

    if ($action === 'verify') {
        $otp = (string)($input['otp'] ?? '');
        if ($otp === '') {
            respond(false, 'Please enter the verification code');
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
        respond(true, 'Email verified successfully');
    }

    respond(false, 'Invalid action');
} catch (Throwable $e) {
    error_log('[polygo-api] otp error: ' . $e->getMessage());
    respond(false, 'Verification failed, please try again');
}