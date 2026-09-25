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
    $isPrivate = array_key_exists('is_private', $input)
        ? (filter_var($input['is_private'], FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE) === true ? 1 : 0)
        : null;
    $fcmToken = isset($input['fcm_token']) ? trim((string)$input['fcm_token']) : null;

    if ($userId <= 0) {
        respond(false, 'Unauthorized');
    }

    if ($email !== null && !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        respond(false, 'Invalid email address');
    }
    if ($name !== null && $name === '') {
        respond(false, 'Name cannot be empty');
    }
    if ($name !== null && mb_strlen($name) > 120) {
        respond(false, 'Name is too long (max 120 characters)');
    }
    if ($mobile !== null && mb_strlen($mobile) > 30) {
        respond(false, 'Mobile number is too long');
    }
    if ($bio !== null && mb_strlen($bio) > 255) {
        respond(false, 'Bio is too long (max 255 characters)');
    }
    if ($profilePicUrl !== null && mb_strlen($profilePicUrl) > 500) {
        respond(false, 'Profile image URL is too long');
    }
    if ($profilePicUrl !== null) {
        $canonicalPhoto = canonical_uploaded_image_url($profilePicUrl);
        if ($canonicalPhoto === null) respond(false, 'Please upload a valid profile image');
        $profilePicUrl = $canonicalPhoto;
    }
    if ($fcmToken !== null && mb_strlen($fcmToken) > 500) {
        respond(false, 'Invalid device token');
    }
    if ($email !== null) {
        $currentEmailQuery = $pdo->prepare('SELECT email FROM users WHERE id = ? LIMIT 1');
        $currentEmailQuery->execute([$userId]);
        $currentEmail = strtolower((string)$currentEmailQuery->fetchColumn());
        if ($currentEmail === '' || strtolower($email) !== $currentEmail) {
            respond(false, 'Email changes require verification');
        }
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

    $transferringToken = $fcmToken !== null && $fcmToken !== '';
    if ($transferringToken) $pdo->beginTransaction();
    try {
        if ($transferringToken) {
            // One Firebase installation belongs to the account currently using
            // it. Leaving the same token on old accounts causes duplicate pushes.
            $clear = $pdo->prepare('UPDATE users SET fcm_token = NULL WHERE id <> ? AND fcm_token = ?');
            $clear->execute([$userId, $fcmToken]);
        }
        $query = $pdo->prepare($sql);
        $updated = $query->execute($params);
        if ($transferringToken) $pdo->commit();
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }

    if ($updated) {
        respond(true, 'Profile updated successfully');
    } else {
        respond(false, 'Failed to update database profile');
    }
} catch (Exception $e) {
    error_log('[polygo-api] update_profile error: ' . $e->getMessage());
    respond(false, 'Failed to update profile');
}
