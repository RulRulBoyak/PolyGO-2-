<?php
require_once __DIR__ . '/config.php';

// SECURITY: Only a logged-in, authenticated user may change their own password.
$userId = verify_jwt();

$input = input_json();
$newPassword = (string)($input['new_password'] ?? '');

if (strlen($newPassword) < 6) {
    respond(false, 'New password must be at least 6 characters');
}

try {
    $update = $pdo->prepare('UPDATE users SET password_hash = ? WHERE id = ?');
    $update->execute([password_hash($newPassword, PASSWORD_DEFAULT), $userId]);
    respond(true, 'Password updated successfully');
} catch (Throwable $e) {
    error_log('[polygo-api] update_password error: ' . $e->getMessage());
    respond(false, 'Failed to update password');
}