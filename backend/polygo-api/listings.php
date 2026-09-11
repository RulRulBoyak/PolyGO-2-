<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$id = (int)($input['id'] ?? 0);
$viewerId = verify_jwt_optional();

// Blocked-user filter (UGC): hide listings where the viewer has blocked the
// owner, or where the owner has blocked the viewer, in both directions.
$blockedClause = '';
if ($viewerId > 0) {
    $blockedClause = ' l.owner_id NOT IN (SELECT blocked_id FROM blocked_users WHERE user_id = ' . $viewerId . ')
        AND l.owner_id NOT IN (SELECT user_id FROM blocked_users WHERE blocked_id = ' . $viewerId . ')';
}

// Mark listing as sold (owner only)
if (($input['action'] ?? '') === 'mark_sold') {
    $ownerId = verify_jwt();
    if ($id <= 0) respond(false, 'Missing listing id');

    $ownerCheck = $pdo->prepare('SELECT owner_id, is_available FROM listings WHERE id = ?');
    $ownerCheck->execute([$id]);
    $listing = $ownerCheck->fetch();
    if (!$listing) respond(false, 'Listing not found');
    if ((int)$listing['owner_id'] !== $ownerId) respond(false, 'Not authorized');
    if ((int)$listing['is_available'] === 0) respond(true, 'Listing already marked as sold');

    $marker = $pdo->prepare('UPDATE listings SET is_available = 0 WHERE id = ?');
    if ($marker->execute([$id])) {
        respond(true, 'Listing marked as sold');
    }
    respond(false, 'Failed to mark listing as sold');
}

// Reactivate an archived listing (owner only)
if (($input['action'] ?? '') === 'relist') {
    $ownerId = verify_jwt();
    if ($id <= 0) respond(false, 'Missing listing id');

    $ownerCheck = $pdo->prepare('SELECT owner_id FROM listings WHERE id = ?');
    $ownerCheck->execute([$id]);
    $listing = $ownerCheck->fetch();
    if (!$listing) respond(false, 'Listing not found');
    if ((int)$listing['owner_id'] !== $ownerId) respond(false, 'Not authorized');

    $relist = $pdo->prepare('UPDATE listings SET archived_at = NULL, is_available = 1 WHERE id = ?');
    if ($relist->execute([$id])) {
        respond(true, 'Listing relisted');
    }
    respond(false, 'Failed to relist listing');
}

// Single Listing retrieval
if ($id > 0) {
    $query = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.tags, l.free_slots, l.major_id, m.name AS major_name, l.location, l.is_available, l.created_at, l.archived_at,
        COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
        COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
        FROM listings l LEFT JOIN majors m ON m.id = l.major_id INNER JOIN users u ON u.id = l.owner_id WHERE l.id = ?');
    if ($blockedClause !== '') {
        $query = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.tags, l.free_slots, l.major_id, m.name AS major_name, l.location, l.is_available, l.created_at, l.archived_at,
            COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
            COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
            FROM listings l LEFT JOIN majors m ON m.id = l.major_id INNER JOIN users u ON u.id = l.owner_id WHERE l.id = ? AND (' . $blockedClause . ')');
    }
    $query->execute([$id]);
    $item = $query->fetch();
    if ($item) {
        $item['id'] = (int)$item['id'];
        $item['owner_id'] = (int)$item['owner_id'];
        $item['is_available'] = (bool)$item['is_available'];
        $item['archived'] = $item['archived_at'] !== null;
        $item['major_id'] = $item['major_id'] !== null ? (int)$item['major_id'] : null;
        respond(true, 'Listing loaded', ['listings' => [$item]]);
    } else {
        respond(false, 'Listing not found');
    }
}

// Lazily auto-archive stale listings (available for 90+ days without a sale)
// and notify each owner once. Runs under the list / my-listings paths only.
$stale = $pdo->prepare('SELECT id, owner_id, title FROM listings WHERE archived_at IS NULL AND is_available = 1 AND created_at < ? LIMIT 100');
$staleCutoff = date('Y-m-d H:i:s', time() - (90 * 24 * 60 * 60));
$stale->execute([$staleCutoff]);
$staleRows = $stale->fetchAll();
if (!empty($staleRows)) {
    $touch = $pdo->prepare('UPDATE listings SET archived_at = NOW() WHERE id = ?');
    $note = $pdo->prepare('INSERT INTO notifications (user_id, title, body) VALUES (?, ?, ?)');
    foreach ($staleRows as $row) {
        $touch->execute([$row['id']]);
        $note->execute([
            $row['owner_id'],
            'Listing auto-archived',
            'Your listing "' . $row['title'] . '" was auto-archived after 90 days without a sale. Relist it in My Listings to reactivate it.'
        ]);
    }
}

// Owner's own listings (including archived state) for "My Listings"
if (($input['action'] ?? '') === 'mylistings') {
    $ownerId = verify_jwt();
    $myQuery = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.tags, l.free_slots, l.major_id, m.name AS major_name, l.location, l.is_available, l.created_at, l.archived_at,
        COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
        COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
        FROM listings l LEFT JOIN majors m ON m.id = l.major_id INNER JOIN users u ON u.id = l.owner_id WHERE l.owner_id = ? ORDER BY l.created_at DESC LIMIT 200');
    $myQuery->execute([$ownerId]);
    $mine = [];
    foreach ($myQuery->fetchAll() as $item) {
        $item['id'] = (int)$item['id'];
        $item['owner_id'] = (int)$item['owner_id'];
        $item['is_available'] = (bool)$item['is_available'];
        $item['archived'] = $item['archived_at'] !== null;
        $item['major_id'] = $item['major_id'] !== null ? (int)$item['major_id'] : null;
        $mine[] = $item;
    }
    respond(true, 'My listings loaded', ['listings' => $mine]);
}

// Pagination & Sorting Logic
$limit = (int)($input['limit'] ?? 20);
$offset = (int)($input['offset'] ?? 0);
$sort = $input['sort'] ?? 'newest';
$search = $input['query'] ?? '';
$majorId = (int)($input['major'] ?? 0);

// Determine ORDER BY clause
$orderBy = 'l.created_at DESC'; // Default: Newest
if ($sort === 'price_low') $orderBy = 'CAST(l.price AS DECIMAL(10,2)) ASC';
else if ($sort === 'price_high') $orderBy = 'CAST(l.price AS DECIMAL(10,2)) DESC';
else if ($sort === 'oldest') $orderBy = 'l.created_at ASC';
else if ($sort === 'top_rated') $orderBy = '(SELECT COALESCE(AVG(r.stars), 0) FROM reviews r WHERE r.seller_id = l.owner_id) DESC, l.created_at DESC';

// Fetch items with optional search
$sql = 'SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.tags, l.free_slots, l.major_id, m.name AS major_name, l.location, l.is_available, l.created_at,
        COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
        COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
        FROM listings l
        LEFT JOIN majors m ON m.id = l.major_id
        INNER JOIN users u ON u.id = l.owner_id';

$params = [];
if (!empty($search)) {
    $sql .= ' WHERE (l.title LIKE ? OR l.description LIKE ? OR l.category LIKE ? OR l.tags LIKE ?) AND l.archived_at IS NULL';
    $like = '%' . addcslashes($search, '%_') . '%';
    $params = [$like, $like, $like, $like];
    if ($blockedClause !== '') {
        $sql .= ' AND (' . $blockedClause . ')';
    }
} else {
    $sql .= ' WHERE l.archived_at IS NULL';
    if ($blockedClause !== '') {
        $sql .= ' AND (' . $blockedClause . ')';
    }
}

if ($majorId > 0) {
    $sql .= ' AND l.major_id = ?';
    $params[] = $majorId;
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
    $item['major_id'] = $item['major_id'] !== null ? (int)$item['major_id'] : null;
    $item['thumb_url'] = ($item['image_url'] ?? '') !== ''
        ? preg_replace('#/uploads/([^/]+)$#', '/uploads/thumbs/' . pathinfo($item['image_url'], PATHINFO_FILENAME) . '.thumb.jpg', $item['image_url'])
        : '';
    $items[] = $item;
}

// Check for next page
$countWhere = ' WHERE l.archived_at IS NULL';
$countParams = [];
if (!empty($search)) {
    $countWhere = ' WHERE (l.title LIKE ? OR l.description LIKE ? OR l.category LIKE ? OR l.tags LIKE ?) AND l.archived_at IS NULL';
    $countParams = [$like, $like, $like, $like];
}
if ($blockedClause !== '') {
    $countWhere .= ' AND (' . $blockedClause . ')';
}
if ($majorId > 0) {
    $countWhere .= ' AND l.major_id = ?';
    $countParams[] = $majorId;
}
$totalQuery = $pdo->prepare('SELECT COUNT(*) FROM listings l' . $countWhere);
$totalQuery->execute($countParams);
$total = (int)$totalQuery->fetchColumn();
$hasNextPage = ($offset + $limit) < $total;

respond(true, 'Listings loaded', [
    'listings' => $items,
    'has_next' => $hasNextPage,
    'total' => $total
]);
