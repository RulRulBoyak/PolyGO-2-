<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$name = trim((string)($input['full_name'] ?? ''));
$studentId = trim((string)($input['student_id'] ?? ''));
$email = strtolower(trim((string)($input['email'] ?? '')));
$password = (string)($input['password'] ?? '');

if ($name === '' || $studentId === '' || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($password) < 6) {
    respond(false, 'Please provide valid registration details');
}

try {
    $query = $pdo->prepare('INSERT INTO users (full_name, student_id, email, password_hash) VALUES (?, ?, ?, ?)');
    $query->execute([$name, $studentId, $email, password_hash($password, PASSWORD_DEFAULT)]);
    $id = (int)$pdo->lastInsertId();

    $token = create_jwt($id);

    respond(true, 'Account created', [
        'token' => $token,
        'user' => ['id' => $id, 'name' => $name, 'studentId' => $studentId, 'email' => $email, 'mobile' => '']
    ]);
} catch (PDOException $error) {
    if ($error->getCode() === '23000') respond(false, 'Student ID or email already exists');
    respond(false, 'Could not create the account');
}
