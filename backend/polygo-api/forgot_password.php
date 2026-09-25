<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/Mailer.php';

$input = input_json();
$identifier = trim((string)($input['identifier'] ?? ''));
$action = (string)($input['action'] ?? 'request');
if ($identifier === '') respond(false, 'Enter your student ID or email');

$ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
if (!rate_limit_check($pdo, 'forgot_ip:' . $ip, 5, 600)
        || !rate_limit_check($pdo, 'forgot_id:' . strtolower($identifier), 5, 600)) {
    respond(false, 'Too many reset requests, please try again later');
}

$pdo->exec('CREATE TABLE IF NOT EXISTS password_reset_codes (
    user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');

$query = $pdo->prepare('SELECT id, email FROM users WHERE student_id = ? OR email = ? LIMIT 1');
$query->execute([$identifier, $identifier]);
$user = $query->fetch();
$generic = 'If your account exists, a reset code has been sent to your email';

if ($action === 'request') {
    if ($user) {
        $code = (string)random_int(100000, 999999);
        $store = $pdo->prepare('INSERT INTO password_reset_codes (user_id, code_hash, expires_at)
            VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE code_hash = VALUES(code_hash), expires_at = VALUES(expires_at), created_at = CURRENT_TIMESTAMP');
        $store->execute([(int)$user['id'], password_hash($code, PASSWORD_DEFAULT), date('Y-m-d H:i:s', time() + 600)]);
        $body = "Your PolyGo+ password reset code is: $code\n\nIt expires in 10 minutes.\n\nIf you did not request this, your password has not changed.";
        if (!Mailer::send((string)$user['email'], 'PolyGo+ password reset code', $body)) {
            $pdo->prepare('DELETE FROM password_reset_codes WHERE user_id = ?')->execute([(int)$user['id']]);
            error_log('[polygo-api] password reset email failed for user ' . (int)$user['id']);
        }
        if (defined('APP_ENV') && APP_ENV === 'dev' && defined('DEV_OTP_ECHO') && DEV_OTP_ECHO) {
            respond(true, $generic, ['reset_code' => $code]);
        }
    }
    respond(true, $generic);
}

if ($action === 'reset') {
    $code = trim((string)($input['code'] ?? ''));
    $newPassword = (string)($input['new_password'] ?? '');
    $length = function_exists('mb_strlen') ? mb_strlen($newPassword, 'UTF-8') : strlen($newPassword);
    if (!preg_match('/^\d{6}$/', $code) || $length < 5 || $length > 128) {
        respond(false, 'Enter the six-digit code and a 5 to 128 character password');
    }
    if (!rate_limit_check($pdo, 'forgot_verify:' . strtolower($identifier), 5, 900)) {
        respond(false, 'Too many attempts, please try again later');
    }
    if (!$user) respond(false, 'Invalid or expired reset code');
    $find = $pdo->prepare('SELECT code_hash, expires_at FROM password_reset_codes WHERE user_id = ? LIMIT 1');
    $find->execute([(int)$user['id']]);
    $reset = $find->fetch();
    if (!$reset || strtotime((string)$reset['expires_at']) < time()
            || !password_verify($code, (string)$reset['code_hash'])) {
        respond(false, 'Invalid or expired reset code');
    }
    $pdo->beginTransaction();
    $update = $pdo->prepare('UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?');
    $update->execute([password_hash($newPassword, PASSWORD_DEFAULT), (int)$user['id']]);
    $pdo->prepare('DELETE FROM password_reset_codes WHERE user_id = ?')->execute([(int)$user['id']]);
    $pdo->prepare('DELETE FROM rate_limits WHERE bucket = ?')->execute(['forgot_verify:' . strtolower($identifier)]);
    $pdo->commit();
    respond(true, 'Password reset successfully');
}

respond(false, 'Invalid reset action');
