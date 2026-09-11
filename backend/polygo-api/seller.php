<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$sellerId = (int)($input['seller_id'] ?? 0);
$sellerName = trim((string)($input['seller_name'] ?? ''));
$action = $input['action'] ?? 'profile';

if ($sellerId <= 0 && $sellerName === '') {
    respond(false, 'Missing seller');
}

// Resolve user ID
if ($sellerId > 0) {
    $userQuery = $pdo->prepare('SELECT id, full_name, is_verified, bio, is_private, profile_pic_url FROM users WHERE id = ? LIMIT 1');
    $userQuery->execute([$sellerId]);
} else {
    $userQuery = $pdo->prepare('SELECT id, full_name, is_verified, bio, is_private, profile_pic_url FROM users WHERE full_name = ? LIMIT 1');
    $userQuery->execute([$sellerName]);
}
$user = $userQuery->fetch();
if (!$user) {
    if ($action === 'metrics') respond(false, 'User not found');
    respond(true, 'Seller profile (local only)', ['seller' => ['name' => $sellerName, 'verified' => false, 'bio' => '', 'is_private' => false, 'profile_pic_url' => ''], 'listings' => []]);
}

$ownerId = (int)$user['id'];

// Metrics Action: Returns calculated business data
if ($action === 'metrics') {
    // Private accounts only expose metrics to the account owner.
    if ((bool)$user['is_private']) {
        $viewerId = verify_jwt(); // exits with 401 if no valid token
        if ((int)$viewerId !== $ownerId) {
            respond(false, 'This account is private');
        }
    }
    // 1. Total Earnings from COMPLETED transactions
    $earnQuery = $pdo->prepare('SELECT SUM(amount) FROM transactions WHERE seller_id = ? AND status = "completed"');
    $earnQuery->execute([$ownerId]);
    $earnings = (float)$earnQuery->fetchColumn();

    // 2. Active Listings
    $activeQuery = $pdo->prepare('SELECT COUNT(*) FROM listings WHERE owner_id = ? AND is_available = 1 AND archived_at IS NULL');
    $activeQuery->execute([$ownerId]);
    $activeCount = (int)$activeQuery->fetchColumn();

    // 3. Total Items Sold (Successful Transactions)
    $soldQuery = $pdo->prepare('SELECT COUNT(*) FROM transactions WHERE seller_id = ? AND status = "completed"');
    $soldQuery->execute([$ownerId]);
    $soldCount = (int)$soldQuery->fetchColumn();

    // 4. Rating Statistics
    $rateQuery = $pdo->prepare('SELECT AVG(stars), COUNT(*) FROM reviews WHERE seller_id = ?');
    $rateQuery->execute([$ownerId]);
    $rateRow = $rateQuery->fetch();
    $avgRating = round((float)$rateRow[0], 1);
    $reviewCount = (int)$rateRow[1];

    respond(true, 'Metrics fetched', [
        'earnings' => $earnings,
        'active_listings' => $activeCount,
        'items_sold' => $soldCount,
        'rating' => $avgRating,
        'reviews' => $reviewCount,
        'trust_score' => min(100, ($soldCount * 10) + ($avgRating * 10)) // Simple formula for demo
    ]);
}

// Private accounts hide their listings and reviews from other students
if ((bool)$user['is_private']) {
    respond(true, 'Seller profile (private)', [
        'seller' => [
            'id' => $ownerId,
            'name' => $user['full_name'],
            'verified' => (bool)$user['is_verified'],
            'bio' => $user['bio'] ?? '',
            'is_private' => true,
            'profile_pic_url' => $user['profile_pic_url'] ?? '',
            'active' => 0,
            'sold' => 0,
        ],
        'listings' => [],
        'reviews' => [],
    ]);
}

// Default: Profile action
$listQuery = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.image_url, l.location, l.is_available FROM listings l INNER JOIN users u ON u.id = l.owner_id WHERE l.owner_id = ? AND l.archived_at IS NULL ORDER BY l.created_at DESC');
$listQuery->execute([$ownerId]);
$listings = [];
$active = 0;
$sold = 0;
foreach ($listQuery->fetchAll() as $item) {
    $item['id'] = (int)$item['id'];
    $item['owner_id'] = (int)$item['owner_id'];
    $item['price'] = (float)$item['price'];
    $item['is_available'] = (bool)$item['is_available'];
    $item['thumb_url'] = ($item['image_url'] ?? '') !== ''
        ? preg_replace('#/uploads/([^/]+)$#', '/uploads/thumbs/' . pathinfo($item['image_url'], PATHINFO_FILENAME) . '.thumb.jpg', $item['image_url'])
        : '';
    if ($item['is_available']) $active++;
    else $sold++;
    $listings[] = $item;
}

$reviewQuery = $pdo->prepare('SELECT reviewer_name, stars, comment, listing_id, created_at FROM reviews WHERE seller_id = ? ORDER BY created_at DESC LIMIT 20');
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
        'bio' => $user['bio'] ?? '',
        'is_private' => false,
        'profile_pic_url' => $user['profile_pic_url'] ?? '',
        'active' => $active,
        'sold' => $sold,
    ],
    'listings' => $listings,
    'reviews' => $reviews,
]);
