<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$action = $input['action'] ?? '';
$email = strtolower(trim((string)($input['email'] ?? '')));

if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
    respond(false, 'Please provide a valid email address');
}

// In a real production app, we would send a real email here.
// For development at PKS, we simulate the success and log the OTP locally.
if ($action === 'send') {
    // Check if user already exists
    $check = $pdo->prepare('SELECT id FROM users WHERE email = ?');
    $check->execute([$email]);
    if ($check->fetch()) {
        respond(false, 'An account with this email already exists');
    }

    $otp = "123456"; // Fixed OTP for dev/simulator

    // Logic to store/send OTP would go here.
    respond(true, 'OTP sent successfully to your campus email');
}

if ($action === 'verify') {
    $otp = (string)($input['otp'] ?? '');

    if ($otp === '123456') {
        respond(true, 'Email verified successfully');
    } else {
        respond(false, 'Invalid verification code');
    }
}

respond(false, 'Invalid action');
