<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = verify_jwt();
$seller = trim((string)($input['seller'] ?? ''));
$listingId = (int)($input['listing_id'] ?? 0);
$stars = (int)($input['stars'] ?? 5);
$comment = trim((string)($input['comment'] ?? ''));

if ($userId <= 0 || $seller === '' || $stars < 1) {
    respond(false, 'Incomplete review');
}

if (mb_strlen($comment) > 2000) {
    respond(false, 'Comment is too long (max 2000 characters)');
}

$stars = max(1, min(5, $stars));
$reviewer = 'Buyer';
$nameQuery = $pdo->prepare('SELECT full_name FROM users WHERE id = ? LIMIT 1');
$nameQuery->execute([$userId]);
$row = $nameQuery->fetch();
if ($row) $reviewer = $row['full_name'];

$sellerId = 0;
$sellerQuery = $pdo->prepare('SELECT id FROM users WHERE full_name = ? LIMIT 1');
$sellerQuery->execute([$seller]);
$sellerRow = $sellerQuery->fetch();
if ($sellerRow) $sellerId = (int)$sellerRow['id'];

$txQuery = $pdo->prepare('SELECT id FROM transactions WHERE buyer_id = ? AND seller_id = ? AND buyer_id <> seller_id AND status = "completed" LIMIT 1');
$txQuery->execute([$userId, $sellerId]);
$hasPurchase = (bool)$txQuery->fetch();
if ($sellerId <= 0 || !$hasPurchase) {
    respond(false, 'You must complete a transaction before leaving a review.');
}

try {
    $query = $pdo->prepare('INSERT INTO reviews (seller_id, listing_id, reviewer_id, reviewer_name, stars, comment) VALUES (?, ?, ?, ?, ?, ?)');
    $query->execute([$sellerId, $listingId, $userId, $reviewer, $stars, $comment]);
    respond(true, 'Review saved');
} catch (Throwable $e) {
    error_log('[polygo-api] review save failed: ' . $e->getMessage());
    respond(false, 'Could not save review');
}
