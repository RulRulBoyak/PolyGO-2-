<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$sellerId = (int)($input['seller_id'] ?? 0);
$sellerName = trim((string)($input['seller_name'] ?? ''));

if ($sellerId <= 0 && $sellerName === '') {
    respond(false, 'Missing seller');
}

if ($sellerId > 0) {
    $userQuery = $pdo->prepare('SELECT id, full_name, is_verified FROM users WHERE id = ? LIMIT 1');
    $userQuery->execute([$sellerId]);
} else {
    $userQuery = $pdo->prepare('SELECT id, full_name, is_verified FROM users WHERE full_name = ? LIMIT 1');
    $userQuery->execute([$sellerName]);
}
$user = $userQuery->fetch();
if (!$user) {
    respond(true, 'Seller profile (local only)', ['seller' => ['name' => $sellerName, 'verified' => false], 'listings' => []]);
}

$ownerId = (int)$user['id'];
$listQuery = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.location, l.is_available FROM listings l INNER JOIN users u ON u.id = l.owner_id WHERE l.owner_id = ? ORDER BY l.created_at DESC');
$listQuery->execute([$ownerId]);
$listings = [];
$active = 0;
$sold = 0;
foreach ($listQuery->fetchAll() as $item) {
    $item['id'] = (int)$item['id'];
    $item['owner_id'] = (int)$item['owner_id'];
    $item['price'] = (float)$item['price'];
    $item['is_available'] = (bool)$item['is_available'];
    if ($item['is_available']) $active++;
    else $sold++;
    $listings[] = $item;
}

$reviewQuery = $pdo->prepare('SELECT reviewer_name, stars, comment, created_at FROM reviews WHERE seller_id = ? ORDER BY created_at DESC LIMIT 20');
$reviews = [];
try {
    $reviewQuery->execute([$ownerId]);
    $reviews = $reviewQuery->fetchAll();
} catch (Throwable $ignored) {
}

respond(true, 'Seller loaded', [
    'seller' => [
        'id' => $ownerId,
        'name' => $user['full_name'],
        'verified' => (bool)$user['is_verified'],
        'active' => $active,
        'sold' => $sold,
    ],
    'listings' => $listings,
    'reviews' => $reviews,
]);
