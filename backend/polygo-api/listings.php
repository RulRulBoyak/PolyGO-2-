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

    // A listing that already sold (completed deal) cannot be relisted — the
    // buyer paid for it and the seller earned green impact on it.
    $soldCheck = $pdo->prepare('SELECT COUNT(*) FROM transactions WHERE listing_id = ? AND status = "completed"');
    $soldCheck->execute([$id]);
    if ((int)$soldCheck->fetchColumn() > 0) {
        respond(false, 'This listing was already sold and cannot be relisted');
    }

    $relist = $pdo->prepare('UPDATE listings SET archived_at = NULL, is_available = 1 WHERE id = ?');
    if ($relist->execute([$id])) {
        respond(true, 'Listing relisted');
    }
    respond(false, 'Failed to relist listing');
}

// Similar listings: same category, excluding the current listing.
if (($input['action'] ?? '') === 'similar') {
    if ($id <= 0) respond(false, 'Missing listing id');
    $catQuery = $pdo->prepare('SELECT category FROM listings WHERE id = ?');
    $catQuery->execute([$id]);
    $category = $catQuery->fetchColumn();
    if ($category === false) respond(false, 'Listing not found');

    $similar = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.`condition`, l.original_price, l.image_url, l.tags,
        l.free_slots, l.major_id, m.name AS major_name, l.location, l.auto_reply, l.hide_from_friends, l.is_available, l.created_at, l.archived_at, l.views, u.is_verified, UNIX_TIMESTAMP(l.created_at)*1000 AS posted_at_ms,
        COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
        COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
        FROM listings l LEFT JOIN majors m ON m.id = l.major_id INNER JOIN users u ON u.id = l.owner_id
        WHERE l.category = ? AND l.id <> ? AND l.archived_at IS NULL AND l.is_available = 1 AND l.hide_from_friends = 0
        AND (' . ($blockedClause !== '' ? $blockedClause : '1=1') . ')
        ORDER BY l.created_at DESC LIMIT 10');
    $similar->execute([$category, $id]);
    $rows = [];
    foreach ($similar->fetchAll() as $item) {
        $item['id'] = (string)$item['id'];
        $item['owner_id'] = (string)$item['owner_id'];
        $item['price'] = (string)$item['price'];
        $item['rating'] = (string)($item['rating'] ?? 0);
        $item['review_count'] = (string)($item['review_count'] ?? 0);
        $item['original_price'] = (string)($item['original_price'] ?? 0);
        $item['rating'] = (float)($item['rating'] ?? 0);
        $item['review_count'] = (int)($item['review_count'] ?? 0);
        $item['is_available'] = (bool)$item['is_available'];
        $item['archived'] = $item['archived_at'] !== null;
        $item['major_id'] = $item['major_id'] !== null ? (int)$item['major_id'] : null;
        $item['views'] = (int)($item['views'] ?? 0);
        $item['is_verified'] = (bool)($item['is_verified'] ?? false);
        $item['auto_reply'] = (bool)($item['auto_reply'] ?? 0);
        $item['hide_from_friends'] = (bool)($item['hide_from_friends'] ?? 0);
        $item['posted_at_ms'] = (int)($item['posted_at_ms'] ?? 0);
        $item['thumb_url'] = listing_thumbnail_url((string)($item['image_url'] ?? ''));
        $rows[] = $item;
    }
    respond(true, 'Similar listings loaded', ['listings' => $rows]);
}

// Single Listing retrieval
if ($id > 0) {
    $query = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.`condition`, l.original_price, l.image_url, l.tags,
        l.free_slots, l.major_id, m.name AS major_name, l.location, l.auto_reply, l.hide_from_friends, l.is_available, l.created_at, l.archived_at, l.views, u.is_verified, UNIX_TIMESTAMP(l.created_at)*1000 AS posted_at_ms,
        COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
        COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
        FROM listings l LEFT JOIN majors m ON m.id = l.major_id INNER JOIN users u ON u.id = l.owner_id WHERE l.id = ?');
    if ($blockedClause !== '') {
        $query = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.`condition`, l.original_price, l.image_url, l.tags,
        l.free_slots, l.major_id, m.name AS major_name, l.location, l.auto_reply, l.hide_from_friends, l.is_available, l.created_at, l.archived_at, l.views, u.is_verified, UNIX_TIMESTAMP(l.created_at)*1000 AS posted_at_ms,
            COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
            COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
            FROM listings l LEFT JOIN majors m ON m.id = l.major_id INNER JOIN users u ON u.id = l.owner_id WHERE l.id = ? AND (' . $blockedClause . ')');
    }
    $query->execute([$id]);
    $item = $query->fetch();
    if ($item) {
        $item['id'] = (string)$item['id'];
        $item['owner_id'] = (string)$item['owner_id'];
        $item['price'] = (string)$item['price'];
        $item['rating'] = (string)($item['rating'] ?? 0);
        $item['review_count'] = (string)($item['review_count'] ?? 0);
        $item['original_price'] = (string)($item['original_price'] ?? 0);
        $item['rating'] = (float)($item['rating'] ?? 0);
        $item['review_count'] = (int)($item['review_count'] ?? 0);
        $item['is_available'] = (bool)$item['is_available'];
        $item['archived'] = $item['archived_at'] !== null;
        $item['major_id'] = $item['major_id'] !== null ? (int)$item['major_id'] : null;
        $item['views'] = (int)($item['views'] ?? 0);
        $item['is_verified'] = (bool)($item['is_verified'] ?? false);
        $item['auto_reply'] = (bool)($item['auto_reply'] ?? 0);
        $item['hide_from_friends'] = (bool)($item['hide_from_friends'] ?? 0);
        $item['posted_at_ms'] = (int)($item['posted_at_ms'] ?? 0);
        // Increment view count (owner views excluded)
        $viewKey = 'listing_view:' . $id . ':' . ($_SERVER['REMOTE_ADDR'] ?? 'unknown');
        if (($viewerId <= 0 || $viewerId !== (int)$item['owner_id'])
                && rate_limit_check($pdo, $viewKey, 1, 3600)) {
            $inc = $pdo->prepare('UPDATE listings SET views = views + 1 WHERE id = ?');
            $inc->execute([$id]);
            $item['views'] = $item['views'] + 1;
        }
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
    $touch = $pdo->prepare('UPDATE listings SET archived_at = NOW() WHERE id = ? AND archived_at IS NULL');
    $note = $pdo->prepare('INSERT INTO notifications (user_id, title, body) VALUES (?, ?, ?)');
    foreach ($staleRows as $row) {
        $touch->execute([$row['id']]);
        if ($touch->rowCount() === 0) continue;
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
    $myQuery = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.`condition`, l.original_price, l.image_url, l.tags,
        l.free_slots, l.major_id, m.name AS major_name, l.location, l.auto_reply, l.hide_from_friends, l.is_available, l.created_at, l.archived_at, l.views, u.is_verified, UNIX_TIMESTAMP(l.created_at)*1000 AS posted_at_ms,
        COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
        COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
        FROM listings l LEFT JOIN majors m ON m.id = l.major_id INNER JOIN users u ON u.id = l.owner_id WHERE l.owner_id = ? ORDER BY l.created_at DESC LIMIT 200');
    $myQuery->execute([$ownerId]);
    $mine = [];
    foreach ($myQuery->fetchAll() as $item) {
        $item['id'] = (string)$item['id'];
        $item['owner_id'] = (string)$item['owner_id'];
        $item['price'] = (string)$item['price'];
        $item['rating'] = (string)($item['rating'] ?? 0);
        $item['review_count'] = (string)($item['review_count'] ?? 0);
        $item['original_price'] = (string)($item['original_price'] ?? 0);
        $item['rating'] = (float)($item['rating'] ?? 0);
        $item['review_count'] = (int)($item['review_count'] ?? 0);
        $item['is_available'] = (bool)$item['is_available'];
        $item['archived'] = $item['archived_at'] !== null;
        $item['major_id'] = $item['major_id'] !== null ? (int)$item['major_id'] : null;
        $item['views'] = (int)($item['views'] ?? 0);
        $item['is_verified'] = (bool)($item['is_verified'] ?? false);
        $item['auto_reply'] = (bool)($item['auto_reply'] ?? 0);
        $item['hide_from_friends'] = (bool)($item['hide_from_friends'] ?? 0);
        $item['posted_at_ms'] = (int)($item['posted_at_ms'] ?? 0);
        $mine[] = $item;
    }
    respond(true, 'My listings loaded', ['listings' => $mine]);
}

// Pagination & Sorting Logic
$limit = min(50, max(1, (int)($input['limit'] ?? 20)));
$offset = min(10000, max(0, (int)($input['offset'] ?? 0)));
$sort = $input['sort'] ?? 'newest';
$search = mb_substr(trim((string)($input['query'] ?? '')), 0, 100);
$majorId = (int)($input['major'] ?? 0);
$ownerId = (int)($input['owner_id'] ?? 0);

// Determine ORDER BY clause
$orderBy = 'l.created_at DESC'; // Default: Newest
if ($sort === 'price_low') $orderBy = 'CAST(l.price AS DECIMAL(10,2)) ASC';
else if ($sort === 'price_high') $orderBy = 'CAST(l.price AS DECIMAL(10,2)) DESC';
else if ($sort === 'oldest') $orderBy = 'l.created_at ASC';
else if ($sort === 'top_rated') $orderBy = '(SELECT COALESCE(AVG(r.stars), 0) FROM reviews r WHERE r.seller_id = l.owner_id) DESC, l.created_at DESC';

// Fetch items with optional search
$sql = 'SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.`condition`, l.original_price, l.image_url, l.tags, l.free_slots, l.major_id, m.name AS major_name, l.location, l.auto_reply, l.hide_from_friends, l.is_available, l.created_at, l.views, u.is_verified, UNIX_TIMESTAMP(l.created_at)*1000 AS posted_at_ms,
        COALESCE((SELECT ROUND(AVG(r.stars), 1) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS rating,
        COALESCE((SELECT COUNT(r.id) FROM reviews r WHERE r.seller_id = l.owner_id), 0) AS review_count
        FROM listings l
        LEFT JOIN majors m ON m.id = l.major_id
        INNER JOIN users u ON u.id = l.owner_id';

$params = [];
if (!empty($search)) {
    $sql .= ' WHERE (l.title LIKE ? OR l.description LIKE ? OR l.category LIKE ? OR l.tags LIKE ?) AND l.archived_at IS NULL AND l.is_available = 1 AND l.hide_from_friends = 0';
    $like = '%' . addcslashes($search, '%_') . '%';
    $params = [$like, $like, $like, $like];
    if ($blockedClause !== '') {
        $sql .= ' AND (' . $blockedClause . ')';
    }
} else {
    $sql .= ' WHERE l.archived_at IS NULL AND l.is_available = 1 AND l.hide_from_friends = 0';
    if ($blockedClause !== '') {
        $sql .= ' AND (' . $blockedClause . ')';
    }
}

if ($ownerId > 0) {
    $sql .= ' AND l.owner_id = ?';
    $params[] = $ownerId;
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
    $item['id'] = (string)$item['id'];
    $item['owner_id'] = (string)$item['owner_id'];
    $item['price'] = (string)$item['price'];
    $item['rating'] = (float)($item['rating'] ?? 0);
    $item['review_count'] = (int)($item['review_count'] ?? 0);
    $item['original_price'] = (string)($item['original_price'] ?? 0);
    $item['is_available'] = (bool)$item['is_available'];
    $item['major_id'] = $item['major_id'] !== null ? (int)$item['major_id'] : null;
    $item['views'] = (int)($item['views'] ?? 0);
    $item['is_verified'] = (bool)($item['is_verified'] ?? false);
    $item['auto_reply'] = (bool)($item['auto_reply'] ?? 0);
    $item['hide_from_friends'] = (bool)($item['hide_from_friends'] ?? 0);
    $item['posted_at_ms'] = (int)($item['posted_at_ms'] ?? 0);
    $item['thumb_url'] = listing_thumbnail_url((string)($item['image_url'] ?? ''));
    $items[] = $item;
}

// Check for next page
$countWhere = ' WHERE l.archived_at IS NULL AND l.is_available = 1 AND l.hide_from_friends = 0';
$countParams = [];
if (!empty($search)) {
    $countWhere = ' WHERE (l.title LIKE ? OR l.description LIKE ? OR l.category LIKE ? OR l.tags LIKE ?) AND l.archived_at IS NULL AND l.is_available = 1 AND l.hide_from_friends = 0';
    $countParams = [$like, $like, $like, $like];
}
if ($blockedClause !== '') {
    $countWhere .= ' AND (' . $blockedClause . ')';
}
if ($majorId > 0) {
    $countWhere .= ' AND l.major_id = ?';
    $countParams[] = $majorId;
}
if ($ownerId > 0) {
    $countWhere .= ' AND l.owner_id = ?';
    $countParams[] = $ownerId;
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
