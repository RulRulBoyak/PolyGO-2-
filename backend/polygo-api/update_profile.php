<?php
require_once __DIR__ . '/config.php';

try {
    // SECURITY: Verify JWT and get actual User ID
    $userId = verify_jwt();

    $input = input_json();
    $name = $input['full_name'] ?? '';
    $email = $input['email'] ?? '';
    $mobile = $input['mobile'] ?? '';
    $profilePicUrl = $input['profile_pic_url'] ?? '';

    if ($userId <= 0 || empty($name) || empty($email)) {
        respond(false, 'Invalid profile data provided');
    }

    $query = $pdo->prepare('UPDATE users SET full_name = ?, email = ?, mobile = ?, profile_pic_url = ? WHERE id = ?');
    if ($query->execute([$name, $email, $mobile, $profilePicUrl, $userId])) {
        respond(true, 'Profile updated successfully');
    } else {
        respond(false, 'Failed to update database profile');
    }
} catch (Exception $e) {
    respond(false, 'Database error: ' . $e->getMessage());
}
