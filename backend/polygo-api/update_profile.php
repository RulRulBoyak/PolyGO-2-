<?php
require_once __DIR__ . '/config.php';

try {
    // SECURITY: Verify JWT and get actual User ID
    $userId = verify_jwt();

    $input = input_json();
    $name = isset($input['full_name']) ? trim((string)$input['full_name']) : null;
    $email = isset($input['email']) ? trim((string)$input['email']) : null;
    $mobile = isset($input['mobile']) ? trim((string)$input['mobile']) : null;
    $profilePicUrl = isset($input['profile_pic_url']) ? trim((string)$input['profile_pic_url']) : null;
    $bio = isset($input['bio']) ? trim((string)$input['bio']) : null;
    $isPrivate = isset($input['is_private']) ? ((bool)$input['is_private'] ? 1 : 0) : null;
    $fcmToken = isset($input['fcm_token']) ? trim((string)$input['fcm_token']) : null;

    if ($userId <= 0) {
        respond(false, 'Unauthorized');
    }

    if ($email !== null && !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        respond(false, 'Invalid email address');
    }
    if ($name !== null && mb_strlen($name) > 120) {
        respond(false, 'Name is too long (max 120 characters)');
    }
    if ($bio !== null && mb_strlen($bio) > 1200) {
        respond(false, 'Bio is too long (max 1200 characters)');
    }
    if ($fcmToken !== null && mb_strlen($fcmToken) > 500) {
        respond(false, 'Invalid device token');
    }

    // Dynamic Query Builder for partial updates
    $updates = [];
    $params = [];

    if ($name !== null) { $updates[] = 'full_name = ?'; $params[] = $name; }
    if ($email !== null) { $updates[] = 'email = ?'; $params[] = $email; }
    if ($mobile !== null) { $updates[] = 'mobile = ?'; $params[] = $mobile; }
    if ($profilePicUrl !== null) { $updates[] = 'profile_pic_url = ?'; $params[] = $profilePicUrl; }
    if ($bio !== null) { $updates[] = 'bio = ?'; $params[] = $bio; }
    if ($isPrivate !== null) { $updates[] = 'is_private = ?'; $params[] = $isPrivate; }
    if ($fcmToken !== null) { $updates[] = 'fcm_token = ?'; $params[] = $fcmToken; }

    if (empty($updates)) {
        respond(false, 'No data to update');
    }

    $params[] = $userId;
    $sql = 'UPDATE users SET ' . implode(', ', $updates) . ' WHERE id = ?';

    $query = $pdo->prepare($sql);
    if ($query->execute($params)) {
        respond(true, 'Profile updated successfully');
    } else {
        respond(false, 'Failed to update database profile');
    }
} catch (Exception $e) {
    error_log('[polygo-api] update_profile error: ' . $e->getMessage());
    respond(false, 'Failed to update profile');
}
