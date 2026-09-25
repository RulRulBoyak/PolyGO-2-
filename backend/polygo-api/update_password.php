<?php
require_once __DIR__ . '/config.php';

// SECURITY: Only a logged-in, authenticated user may change their own password.
$userId = verify_jwt();

$input = input_json();
$currentPassword = (string)($input['current_password'] ?? '');
$newPassword = (string)($input['new_password'] ?? '');
$passwordLength = function_exists('mb_strlen') ? mb_strlen($newPassword, 'UTF-8') : strlen($newPassword);

if ($passwordLength < 5 || $passwordLength > 128) {
    respond(false, 'New password must be 5 to 128 characters');
}

try {
    $current = $pdo->prepare('SELECT password_hash FROM users WHERE id = ? LIMIT 1');
    $current->execute([$userId]);
    $hash = $current->fetchColumn();
    if (!$hash || !password_verify($currentPassword, (string)$hash)) {
        respond(false, 'Current password is incorrect');
    }
    $update = $pdo->prepare('UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?');
    $update->execute([password_hash($newPassword, PASSWORD_DEFAULT), $userId]);
    respond(true, 'Password updated successfully', ['token' => create_jwt($userId)]);
} catch (Throwable $e) {
    error_log('[polygo-api] update_password error: ' . $e->getMessage());
    respond(false, 'Failed to update password');
}
