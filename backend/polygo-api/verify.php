<?php
require_once __DIR__ . '/config.php';

$input  = input_json();
$action = $input['action'] ?? 'status';

// Lightweight status read used to reflect server-side verification state.
if ($action === 'status') {
    $userId = verify_jwt_optional();
    if ($userId > 0) {
        $query = $pdo->prepare('SELECT verification_photo, verification_status FROM users WHERE id = ?');
        $query->execute([$userId]);
        $row = $query->fetch();
        if ($row) {
            respond(true, 'OK', [
                'verification_status' => $row['verification_status'] ?: 'unverified',
                'verification_photo'  => $row['verification_photo'] ?: ''
            ]);
        }
    }
    respond(true, 'OK', ['verification_status' => 'unverified', 'verification_photo' => '']);
}

// ADMIN ACTIONS: require the admin token from secrets.php (ADMIN_PASSWORD).
// These keep the Web admin page and manual SQL approvals working side by side.
if (in_array($action, ['admin_pending', 'approve', 'reject'], true)) {
    $adminToken = (string)($input['admin_token'] ?? '');
    $headerToken = $_SERVER['HTTP_X_ADMIN_TOKEN'] ?? '';
    if ($adminToken === '' && $headerToken !== '') {
        $adminToken = $headerToken;
    }
    if (!defined('ADMIN_PASSWORD') || ADMIN_PASSWORD === '' || !hash_equals(ADMIN_PASSWORD, $adminToken)) {
        respond(false, 'Unauthorized admin token');
    }

    if ($action === 'admin_pending') {
        $pending = $pdo->prepare(
            'SELECT id, full_name, student_id, email, verification_photo, updated_at
             FROM users WHERE verification_status = "pending" ORDER BY updated_at DESC'
        );
        $pending->execute();
        $rows = $pending->fetchAll();
        respond(true, 'OK', ['pending' => $rows]);
    }

    $targetId = (int)($input['user_id'] ?? 0);
    if ($targetId <= 0) {
        respond(false, 'Missing user_id');
    }
    $newStatus = $action === 'approve' ? 'approved' : 'rejected';
    $update = $pdo->prepare('UPDATE users SET verification_status = ?, updated_at = NOW() WHERE id = ?');
    $update->execute([$newStatus, $targetId]);
    $request = $pdo->prepare('UPDATE verification_requests SET status = ? WHERE user_id = ? AND status = "pending"');
    $request->execute([$newStatus, $targetId]);
    respond(true, $action === 'approve' ? 'User approved' : 'User rejected');
}

// Authenticated submission of the Matrix Card upload URL.
if ($action === 'submit') {
    $userId   = verify_jwt();
    $photoUrl = trim((string)($input['verification_photo'] ?? ''));

    if ($photoUrl === '') {
        respond(false, 'Please upload your Matrix Card first');
    }

    $update = $pdo->prepare(
        'UPDATE users SET verification_photo = ?, verification_status = ?, updated_at = NOW() WHERE id = ?'
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