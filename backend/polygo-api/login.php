<?php
require_once __DIR__ . '/config.php';

try {
    $input = input_json();
    $studentId = trim((string)($input['student_id'] ?? ''));
    $password = (string)($input['password'] ?? '');

    if (empty($studentId) || empty($password)) {
        respond(false, 'Please enter both student ID and password');
    }

    $query = $pdo->prepare('SELECT id, full_name, student_id, email, mobile, password_hash FROM users WHERE student_id = ? LIMIT 1');
    $query->execute([$studentId]);
    $user = $query->fetch();

    if (!$user || !password_verify($password, $user['password_hash'])) {
        respond(false, 'Incorrect student ID or password');
    }

    unset($user['password_hash']);
    $user['id'] = (int)$user['id'];
    $user['name'] = $user['full_name'];
    $user['studentId'] = $user['student_id'];

    respond(true, 'Login successful', ['user' => $user]);

} catch (Exception $e) {
    respond(false, 'Database error: ' . $e->getMessage());
}
