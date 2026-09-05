<?php
require_once __DIR__ . '/config.php';

try {
    // SECURITY: Verify JWT and get actual User ID
    $userId = verify_jwt();

    $input = input_json();
    $name = $input['full_name'] ?? null;
    $email = $input['email'] ?? null;
    $mobile = $input['mobile'] ?? null;
    $profilePicUrl = $input['profile_pic_url'] ?? null;
    $fcmToken = $input['fcm_token'] ?? null;

    if ($userId <= 0) {
        respond(false, 'Unauthorized');
    }

    // Dynamic Query Builder for partial updates
    $updates = [];
    $params = [];

    if ($name !== null) { $updates[] = 'full_name = ?'; $params[] = $name; }
    if ($email !== null) { $updates[] = 'email = ?'; $params[] = $email; }
    if ($mobile !== null) { $updates[] = 'mobile = ?'; $params[] = $mobile; }
    if ($profilePicUrl !== null) { $updates[] = 'profile_pic_url = ?'; $params[] = $profilePicUrl; }
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
    respond(false, 'Database error: ' . $e->getMessage());
}
