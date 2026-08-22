<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = (int)($input['user_id'] ?? 0);
$name = $input['full_name'] ?? '';
$email = $input['email'] ?? '';
$mobile = $input['mobile'] ?? '';

if ($userId <= 0 || empty($name) || empty($email)) {
    respond(false, 'Invalid data provided');
}

$query = $pdo->prepare('UPDATE users SET full_name = ?, email = ?, mobile = ? WHERE id = ?');
if ($query->execute([$name, $email, $mobile, $userId])) {
    respond(true, 'Profile updated successfully');
} else {
    respond(false, 'Failed to update profile');
}
