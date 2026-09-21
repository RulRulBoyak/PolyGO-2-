<?php
// Marketplace Pro: "Follow" seller social proof.
// Actions: follow | unfollow. Always returns follower_count + is_following.
require_once __DIR__ . '/config.php';

$input = input_json();
$viewerId = verify_jwt();

$followedId = (int)($input['followed_id'] ?? 0);
$action = $input['action'] ?? 'follow';

if ($followedId <= 0) {
    respond(false, 'Missing user');
}
if ($followedId === $viewerId) {
    respond(false, 'You cannot follow yourself');
}

// Respect blocks in both directions: no following someone you have
// blocked, and no following someone who has blocked you.
$blockCheck = $pdo->prepare('SELECT COUNT(*) FROM blocked_users
    WHERE (user_id = ? AND blocked_id = ?) OR (user_id = ? AND blocked_id = ?)');
$blockCheck->execute([$viewerId, $followedId, $followedId, $viewerId]);
if ((int)$blockCheck->fetchColumn() > 0) {
    respond(false, 'You cannot follow this user');
}

$targetExists = $pdo->prepare('SELECT id FROM users WHERE id = ?');
$targetExists->execute([$followedId]);
if (!$targetExists->fetch()) {
    respond(false, 'User not found');
}

if ($action === 'unfollow') {
    $delete = $pdo->prepare('DELETE FROM user_follows WHERE follower_id = ? AND followed_id = ?');
    $delete->execute([$viewerId, $followedId]);
} else {
    try {
        $insert = $pdo->prepare('INSERT IGNORE INTO user_follows (follower_id, followed_id) VALUES (?, ?)');
        $insert->execute([$viewerId, $followedId]);
    } catch (Throwable $e) {
        error_log('[polygo-api] follow error: ' . $e->getMessage());
        respond(false, 'Failed to follow user');
    }
}

$countQuery = $pdo->prepare('SELECT COUNT(*) FROM user_follows WHERE followed_id = ?');
$countQuery->execute([$followedId]);
$followerCount = (int)$countQuery->fetchColumn();

$stateQuery = $pdo->prepare('SELECT COUNT(*) FROM user_follows WHERE follower_id = ? AND followed_id = ?');
$stateQuery->execute([$viewerId, $followedId]);
$isFollowing = (int)$stateQuery->fetchColumn() > 0;

respond(true, $isFollowing ? 'Following' : 'Unfollowed', [
    'follower_count' => $followerCount,
    'is_following' => $isFollowing,
]);