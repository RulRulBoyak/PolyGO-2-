<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$name = trim((string)($input['full_name'] ?? ''));
$studentId = trim((string)($input['student_id'] ?? ''));
$email = strtolower(trim((string)($input['email'] ?? '')));
$password = (string)($input['password'] ?? '');
// Strict boolean: only a literal JSON true counts as consent. The string
// "false" previously cast to true, silently bypassing PDPA consent.
$consentAgreed = ($input['consent_agreed'] ?? false) === true;

if ($name === '' || $studentId === '' || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($password) < 6) {
    respond(false, 'Please provide valid registration details');
}

// PDPA 2010 requires an auditable record of when the user agreed to our terms.
if (!$consentAgreed) {
    respond(false, 'You must accept our Terms & Privacy Policy to register');
}

// Bot wall: cap signups per IP so a single client cannot mass-create accounts.
$clientIp = $_SERVER['REMOTE_ADDR'] ?? '';
if (!rate_limit_check($pdo, 'register_ip:' . $clientIp, 5, 3600)) {
    respond(false, 'Too many accounts created from this network. Try again later.');
}

try {
    $pdo->beginTransaction();
    $verified = $pdo->prepare('SELECT code_hash, expires_at FROM verification_codes
        WHERE email = ? FOR UPDATE');
    $verified->execute([$email]);
    $proof = $verified->fetch();
    if (!$proof || !hash_equals('verified', (string)$proof['code_hash'])
            || strtotime((string)$proof['expires_at']) < time()) {
        $pdo->rollBack();
        respond(false, 'Please verify your email before creating the account');
    }

    $query = $pdo->prepare('INSERT INTO users (full_name, student_id, email, password_hash, consent_agreed_at) VALUES (?, ?, ?, ?, NOW())');
    $query->execute([$name, $studentId, $email, password_hash($password, PASSWORD_DEFAULT)]);
    $id = (int)$pdo->lastInsertId();

    $consume = $pdo->prepare('DELETE FROM verification_codes WHERE email = ?');
    $consume->execute([$email]);
    $pdo->commit();

    $token = create_jwt($id);

    respond(true, 'Account created', [
        'token' => $token,
        'user' => ['id' => $id, 'name' => $name, 'studentId' => $studentId, 'email' => $email, 'mobile' => '']
    ]);
} catch (PDOException $error) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    if ($error->getCode() === '23000') respond(false, 'Student ID or email already exists');
    respond(false, 'Could not create the account');
}
