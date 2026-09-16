<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/Mailer.php';

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
$generic = 'If your account exists, a temporary password has been sent to your email';
if (!$user) {
    // Same message as the success branch to prevent account enumeration.
    respond(true, $generic);
}

// High-entropy temporary password (128 bits of randomness).
$temp = 'PKS' . bin2hex(random_bytes(16));
$hash = password_hash($temp, PASSWORD_DEFAULT);

// Keep the previous hash so we can roll back if the email cannot be sent —
// otherwise the user is silently locked out of their old password.
$oldHashQ = $pdo->prepare('SELECT password_hash FROM users WHERE id = ?');
$oldHashQ->execute([$user['id']]);
$oldHash = (string)$oldHashQ->fetchColumn();

$update = $pdo->prepare('UPDATE users SET password_hash = ? WHERE id = ?');
$update->execute([$hash, $user['id']]);

$body = "Your PolyGo+ temporary password is: $temp\n\n"
    . 'Please sign in and change it as soon as possible.';
if (!Mailer::send((string)$user['email'], 'PolyGo+ temporary password', $body)) {
    if ($oldHash !== '') {
        $update->execute([$oldHash, $user['id']]);
    }
    // Keep it generic — never confirm account existence in a failure message.
    respond(false, 'Could not send the email right now, please try again later');
}

respond(true, $generic);
