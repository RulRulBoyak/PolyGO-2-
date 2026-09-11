<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = verify_jwt();
$listingId = (int)($input['listing_id'] ?? 0);
$action = $input['action'] ?? 'get';

if ($userId <= 0) respond(false, 'Unauthorized');

if ($action === 'toggle') {
    if ($listingId <= 0) respond(false, 'Invalid listing');

    $check = $pdo->prepare('SELECT 1 FROM favorites WHERE user_id = ? AND listing_id = ?');
    $check->execute([$userId, $listingId]);
    if ($check->fetch()) {
        $pdo->prepare('DELETE FROM favorites WHERE user_id = ? AND listing_id = ?')->execute([$userId, $listingId]);
        respond(true, 'Removed from favorites', ['favorite' => false]);
    } else {
        $pdo->prepare('INSERT INTO favorites (user_id, listing_id) VALUES (?, ?)')->execute([$userId, $listingId]);
        respond(true, 'Added to favorites', ['favorite' => true]);
    }
}

$query = $pdo->prepare('SELECT l.*, u.full_name AS seller,
    COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
    COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
    FROM listings l INNER JOIN favorites f ON f.listing_id = l.id INNER JOIN users u ON u.id = l.owner_id WHERE f.user_id = ?');
$query->execute([$userId]);
$items = [];
foreach ($query->fetchAll() as $item) {
    $item['id'] = (int)$item['id'];
    $item['owner_id'] = (int)$item['owner_id'];
    $item['price'] = (float)$item['price'];
    $item['is_available'] = (bool)$item['is_available'];
    $items[] = $item;
}
respond(true, 'Favorites loaded', ['listings' => $items]);
