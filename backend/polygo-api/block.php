<?php
require_once __DIR__ . '/config.php';

try {
    $userId = verify_jwt();
    if ($userId <= 0) {
        respond(false, 'Unauthorized');
    }

    $input = input_json();
    $action = trim((string)($input['action'] ?? 'list'));
    $blockedId = (int)($input['blocked_id'] ?? 0);

    if ($action === 'list') {
        $query = $pdo->prepare('SELECT blocked_id FROM blocked_users WHERE user_id = ? ORDER BY created_at DESC');
        $query->execute([$userId]);
        $ids = [];
        foreach ($query->fetchAll() as $row) {
            $ids[] = (int)$row['blocked_id'];
        }
        respond(true, 'Blocked users loaded', ['blocked_ids' => $ids]);
    }

    if ($blockedId <= 0) {
        respond(false, 'Missing target user id');
    }
    if ($blockedId === $userId) {
        respond(false, 'You cannot block yourself');
    }

    // Confirm the target actually exists.
    $exists = $pdo->prepare('SELECT COUNT(*) FROM users WHERE id = ?');
    $exists->execute([$blockedId]);
    if ((int)$exists->fetchColumn() === 0) {
        respond(false, 'User not found');
    }

    if ($action === 'block') {
        $query = $pdo->prepare('INSERT INTO blocked_users (user_id, blocked_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE blocked_id = VALUES(blocked_id)');
        $query->execute([$userId, $blockedId]);

        // Blocking is mutual: remove any reciprocal messages/rows is enough — the
        // pair is now excluded on both sides by the same two row checks.
        respond(true, 'User blocked');
    }

    if ($action === 'unblock') {
        $query = $pdo->prepare('DELETE FROM blocked_users WHERE user_id = ? AND blocked_id = ?');
        $query->execute([$userId, $blockedId]);
        respond(true, 'User unblocked');
    }

    respond(false, 'Unknown action');
} catch (Throwable $e) {
    error_log('[polygo-api] block error: ' . $e->getMessage());
    respond(false, 'Failed to update block list');
}