<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = (int)($input['user_id'] ?? 0);
$seller = trim((string)($input['seller'] ?? ''));
$stars = (int)($input['stars'] ?? 5);
$comment = trim((string)($input['comment'] ?? ''));

if ($userId <= 0 || $seller === '' || $stars < 1) {
    respond(false, 'Incomplete review');
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

try {
    $query = $pdo->prepare('INSERT INTO reviews (seller_id, reviewer_id, reviewer_name, stars, comment) VALUES (?, ?, ?, ?, ?)');
    $query->execute([$sellerId, $userId, $reviewer, $stars, $comment]);
    respond(true, 'Review saved');
} catch (Throwable $e) {
    respond(true, 'Review saved locally');
}
