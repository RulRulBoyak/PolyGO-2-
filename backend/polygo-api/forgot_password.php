<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$identifier = trim((string)($input['identifier'] ?? ''));
if ($identifier === '') {
    respond(false, 'Enter your student ID or email');
}

$query = $pdo->prepare('SELECT id, email, student_id FROM users WHERE student_id = ? OR email = ? LIMIT 1');
$query->execute([$identifier, $identifier]);
$user = $query->fetch();
if (!$user) {
    respond(false, 'No matching campus account found');
}

$temp = 'PKS' . random_int(1000, 9999);
$hash = password_hash($temp, PASSWORD_DEFAULT);
$update = $pdo->prepare('UPDATE users SET password_hash = ? WHERE id = ?');
$update->execute([$hash, $user['id']]);

respond(true, 'Temporary password issued', ['temporary_password' => $temp]);
