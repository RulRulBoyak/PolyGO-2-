<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$id = (int)($input['id'] ?? 0);

// Single Listing retrieval
if ($id > 0) {
    $query = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.location, l.is_available, l.created_at FROM listings l INNER JOIN users u ON u.id = l.owner_id WHERE l.id = ?');
    $query->execute([$id]);
    $item = $query->fetch();
    if ($item) {
        $item['id'] = (int)$item['id'];
        $item['owner_id'] = (int)$item['owner_id'];
        $item['is_available'] = (bool)$item['is_available'];
        respond(true, 'Listing loaded', ['listing' => $item]);
    } else {
        respond(false, 'Listing not found');
    }
}

// Pagination & Sorting Logic
$limit = (int)($input['limit'] ?? 20);
$offset = (int)($input['offset'] ?? 0);
$sort = $input['sort'] ?? 'newest';
$search = $input['query'] ?? '';

// Determine ORDER BY clause
$orderBy = 'l.created_at DESC'; // Default: Newest
if ($sort === 'price_low') $orderBy = 'CAST(l.price AS DECIMAL(10,2)) ASC';
else if ($sort === 'price_high') $orderBy = 'CAST(l.price AS DECIMAL(10,2)) DESC';
else if ($sort === 'oldest') $orderBy = 'l.created_at ASC';

// Fetch items with optional search
$sql = 'SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.location, l.is_available, l.created_at
        FROM listings l
        INNER JOIN users u ON u.id = l.owner_id';

$params = [];
if (!empty($search)) {
    $sql .= ' WHERE l.title LIKE ? OR l.description LIKE ? OR l.category LIKE ?';
    $like = "%$search%";
    $params = [$like, $like, $like];
}

$sql .= " ORDER BY $orderBy LIMIT ? OFFSET ?";
$params[] = $limit;
$params[] = $offset;

$query = $pdo->prepare($sql);
$query->execute($params);

$items = [];
foreach ($query->fetchAll() as $item) {
    $item['id'] = (int)$item['id'];
    $item['owner_id'] = (int)$item['owner_id'];
    $item['is_available'] = (bool)$item['is_available'];
    $items[] = $item;
}

// Check for next page
$countSql = 'SELECT COUNT(*) FROM listings l';
if (!empty($search)) $countSql .= ' WHERE l.title LIKE ? OR l.description LIKE ? OR l.category LIKE ?';
$totalQuery = $pdo->prepare($countSql);
$totalQuery->execute(!empty($search) ? ["%$search%", "%$search%", "%$search%"] : []);
$total = (int)$totalQuery->fetchColumn();
$hasNextPage = ($offset + $limit) < $total;

respond(true, 'Listings loaded', [
    'listings' => $items,
    'has_next' => $hasNextPage,
    'total' => $total
]);
