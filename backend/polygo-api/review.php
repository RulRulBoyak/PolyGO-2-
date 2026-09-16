<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/NotificationManager.php';

$input = input_json();
$userId = verify_jwt();
$listingId = (int)($input['listing_id'] ?? 0);
$stars = (int)($input['stars'] ?? 5);
$comment = trim((string)($input['comment'] ?? ''));

if ($userId <= 0 || $listingId <= 0) {
    respond(false, 'Incomplete review');
}

if (mb_strlen($comment) > 2000) {
    respond(false, 'Comment is too long (max 2000 characters)');
}

$stars = max(1, min(5, $stars));

// The seller is the listing owner, resolved server-side — never trusted from
// a client-supplied name.
$ownerQuery = $pdo->prepare('SELECT owner_id FROM listings WHERE id = ? LIMIT 1');
$ownerQuery->execute([$listingId]);
$sellerId = (int)$ownerQuery->fetchColumn();
if ($sellerId <= 0) {
    respond(false, 'Listing not found');
}

// Only a buyer who actually completed a deal on THIS listing may review it.
$txQuery = $pdo->prepare('SELECT id FROM transactions WHERE buyer_id = ? AND seller_id = ? AND listing_id = ? AND status = "completed" LIMIT 1');
$txQuery->execute([$userId, $sellerId, $listingId]);
if (!$txQuery->fetch()) {
    respond(false, 'You must complete a transaction before leaving a review.');
}

$reviewer = '';
$nameQuery = $pdo->prepare('SELECT full_name FROM users WHERE id = ? LIMIT 1');
$nameQuery->execute([$userId]);
$reviewer = (string)$nameQuery->fetchColumn();

try {
    $query = $pdo->prepare('INSERT INTO reviews (seller_id, listing_id, reviewer_id, reviewer_name, stars, comment) VALUES (?, ?, ?, ?, ?, ?)');
    $query->execute([$sellerId, $listingId, $userId, $reviewer, $stars, $comment]);

    NotificationManager::sendToUser($pdo, $sellerId, 'New Review',
        $reviewer . ' left a ' . $stars . '-star review on your listing', [
            'type' => 'review',
            'listing_id' => (string)$listingId
        ]);

    respond(true, 'Review saved');
} catch (Throwable $e) {
    if ($e->getCode() === '23000') {
        respond(false, 'You have already reviewed this listing');
    }
    error_log('[polygo-api] review save failed: ' . $e->getMessage());
    respond(false, 'Could not save review');
}
