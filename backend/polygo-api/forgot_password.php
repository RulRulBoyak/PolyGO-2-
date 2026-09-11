<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$identifier = trim((string)($input['identifier'] ?? ''));
if ($identifier === '') {
    respond(false, 'Enter your student ID or email');
}

// Rate limit reset requests per IP and per identifier.
$ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
if (!rate_limit_check($pdo, 'forgot_ip:' . $ip, 5, 600) ||
    !rate_limit_check($pdo, 'forgot_id:' . $identifier, 5, 600)) {
    respond(false, 'Too many reset requests, please try again later');
}

$query = $pdo->prepare('SELECT id, email, student_id FROM users WHERE student_id = ? OR email = ? LIMIT 1');
$query->execute([$identifier, $identifier]);
$user = $query->fetch();
if (!$user) {
    respond(false, 'No matching campus account found');
}

// High-entropy temporary password (128 bits of randomness).
$temp = 'PKS' . bin2hex(random_bytes(16));
$hash = password_hash($temp, PASSWORD_DEFAULT);
$update = $pdo->prepare('UPDATE users SET password_hash = ? WHERE id = ?');
$update->execute([$hash, $user['id']]);

respond(true, 'Temporary password issued', ['temporary_password' => $temp]);
