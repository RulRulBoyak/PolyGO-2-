<?php
require_once __DIR__ . '/config.php';

try {
    $input = input_json();
    $studentId = trim((string)($input['student_id'] ?? ''));
    $password = (string)($input['password'] ?? '');

    if (empty($studentId) || empty($password)) {
        respond(false, 'Please enter both student ID/Email and password');
    }

    // Rate limit login attempts per IP and per identifier.
    $ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
    if (!rate_limit_check($pdo, 'login_ip:' . $ip, 10, 300) ||
        !rate_limit_check($pdo, 'login_id:' . $studentId, 10, 300)) {
        respond(false, 'Too many login attempts, please try again later');
    }

    // Support login via either Student ID (Matrix No) OR Email
    $query = $pdo->prepare('SELECT id, full_name, student_id, email, mobile, password_hash FROM users WHERE student_id = ? OR email = ? LIMIT 1');
    $query->execute([$studentId, $studentId]);
    $user = $query->fetch();

    if (!$user || !password_verify($password, $user['password_hash'])) {
        respond(false, 'Incorrect student ID or password');
    }

    unset($user['password_hash']);
    $userId = (int)$user['id'];
    $user['id'] = $userId;
    $user['name'] = $user['full_name'];
    $user['studentId'] = $user['student_id'];

    // Generate Security Token
    $token = create_jwt($userId);

    respond(true, 'Login successful', [
        'user' => $user,
        'token' => $token
    ]);

} catch (Exception $e) {
    error_log('[polygo-api] login error: ' . $e->getMessage());
    respond(false, 'Login failed, please try again');
}
