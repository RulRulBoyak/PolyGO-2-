<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$id = (int)($input['id'] ?? 0);

if ($id > 0) {
    $query = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.location, l.is_available, l.created_at FROM listings l INNER JOIN users u ON u.id = l.owner_id WHERE l.id = ?');
    $query->execute([$id]);
    $item = $query->fetch();
    if ($item) {
        $item['id'] = (int)$item['id'];
        $item['owner_id'] = (int)$item['owner_id'];
        $item['price'] = (float)$item['price'];
        $item['is_available'] = (bool)$item['is_available'];
        respond(true, 'Listing loaded', ['listing' => $item]);
    } else {
        respond(false, 'Listing not found');
    }
}

$query = $pdo->query('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.location, l.is_available, l.created_at FROM listings l INNER JOIN users u ON u.id = l.owner_id ORDER BY l.created_at DESC');
$items = [];
foreach ($query->fetchAll() as $item) {
    $item['id'] = (int)$item['id'];
    $item['owner_id'] = (int)$item['owner_id'];
    $item['price'] = (float)$item['price'];
    $item['is_available'] = (bool)$item['is_available'];
    $items[] = $item;
}
respond(true, 'Listings loaded', ['listings' => $items]);
