<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/NotificationManager.php';

$input  = input_json();
$action = $input['action'] ?? 'status';

// Lightweight status read used to reflect server-side verification state.
// Strict auth: a suspended (banned) account gets HTTP 401 "suspended" here so
// the Android client's 401 handler can route to the banned screen, and any
// expired/missing token is bounced to login instead of a silent guest blob.
if ($action === 'status') {
    $userId = verify_jwt();
    if ($userId > 0) {
        $query = $pdo->prepare('SELECT verification_photo, verification_status, is_banned FROM users WHERE id = ?');
        $query->execute([$userId]);
        $row = $query->fetch();
        if ($row) {
            respond(true, 'OK', [
                'verification_status' => $row['verification_status'] ?: 'unverified',
                'verification_photo'  => $row['verification_photo'] ?: '',
                'is_banned'           => (int)($row['is_banned'] ?? 0) === 1
            ]);
        }
    }
    respond(true, 'OK', ['verification_status' => 'unverified', 'verification_photo' => '', 'is_banned' => false]);
}

// Moderation happens only in the CSRF-protected admin panel. Keeping a second
// public password-based admin route here would create an unnecessary bypass.
if (in_array($action, ['admin_pending', 'approve', 'reject'], true)) {
    respond(false, 'Use the authenticated admin panel for moderation');
}

// Authenticated submission of the Matrix Card upload URL.
if ($action === 'submit') {
    $userId   = verify_jwt();
    $photoUrl = trim((string)($input['verification_photo'] ?? ''));

    $canonicalPhoto = canonical_uploaded_image_url($photoUrl);
    if ($canonicalPhoto === null || $canonicalPhoto === '') {
        respond(false, 'Please upload your Matrix Card first');
    }
    $photoUrl = $canonicalPhoto;

    $update = $pdo->prepare(
        'UPDATE users SET verification_photo = ?, verification_status = ?, is_verified = 0, updated_at = NOW() WHERE id = ?'
    );
    $update->execute([$photoUrl, 'pending', $userId]);

    // Keep the existing request trail populated for future moderator tooling.
    $emailQuery = $pdo->prepare('SELECT email FROM users WHERE id = ?');
    $emailQuery->execute([$userId]);
    $email = (string)$emailQuery->fetchColumn();
    if ($email !== '') {
        $request = $pdo->prepare(
            'INSERT INTO verification_requests (user_id, verification_email, status) VALUES (?, ?, ?)'
        );
        $request->execute([$userId, $email, 'pending']);
    }

    respond(true, 'Verification submitted for review');
}

respond(false, 'Unknown action');
