<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$sellerId = (int)($input['seller_id'] ?? 0);
$sellerName = trim((string)($input['seller_name'] ?? ''));
$action = $input['action'] ?? 'profile';
$viewerId = verify_jwt_optional();

if ($sellerId <= 0 && $sellerName === '') {
    respond(false, 'Missing seller');
}

// Resolve user ID
$userSelect = 'id, full_name, is_verified, bio, is_private, profile_pic_url, created_at';
if ($sellerId > 0) {
    $userQuery = $pdo->prepare("SELECT $userSelect FROM users WHERE id = ? LIMIT 1");
    $userQuery->execute([$sellerId]);
} else {
    $userQuery = $pdo->prepare("SELECT $userSelect FROM users WHERE full_name = ? LIMIT 1");
    $userQuery->execute([$sellerName]);
}
$user = $userQuery->fetch();
if (!$user) {
    if ($action === 'metrics') respond(false, 'User not found');
    respond(true, 'Seller profile (local only)', ['seller' => ['name' => $sellerName, 'verified' => false, 'bio' => '', 'is_private' => false, 'profile_pic_url' => '', 'is_following' => false], 'listings' => []]);
}

$ownerId = (int)$user['id'];

if ($viewerId > 0 && $viewerId !== $ownerId) {
    $block = $pdo->prepare('SELECT 1 FROM blocked_users WHERE (user_id = ? AND blocked_id = ?) OR (user_id = ? AND blocked_id = ?) LIMIT 1');
    $block->execute([$viewerId, $ownerId, $ownerId, $viewerId]);
    if ($block->fetchColumn()) respond(false, 'Seller profile is not available');
}

// Social proof: follower count + whether the (optional) viewer follows them.
$followCount = 0;
$isFollowing = false;
try {
    $fCount = $pdo->prepare('SELECT COUNT(*) FROM user_follows WHERE followed_id = ?');
    $fCount->execute([$ownerId]);
    $followCount = (int)$fCount->fetchColumn();
    if ($viewerId > 0 && $viewerId !== $ownerId) {
        $fCheck = $pdo->prepare('SELECT COUNT(*) FROM user_follows WHERE follower_id = ? AND followed_id = ?');
        $fCheck->execute([$viewerId, $ownerId]);
        $isFollowing = (int)$fCheck->fetchColumn() > 0;
    }
} catch (Throwable $ignored) {
}

// Metrics Action: Returns calculated business data
if ($action === 'metrics') {
    // Earnings and business metrics are private to the account owner.
    $viewerId = verify_jwt();
    if ((int)$viewerId !== $ownerId) {
        respond(false, 'Seller metrics are private');
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
    $rateRow = $rateQuery->fetch(PDO::FETCH_NUM) ?: [null, 0];
    $avgRating = round((float)$rateRow[0], 1);
    $reviewCount = (int)$rateRow[1];

    respond(true, 'Metrics fetched', [
        'earnings' => (string)$earnings,
        'active_listings' => (int)$activeCount,
        'items_sold' => (int)$soldCount,
        'rating' => (float)$avgRating,
        'reviews' => (int)$reviewCount,
        'trust_score' => (float)min(100, ($soldCount * 10) + ($avgRating * 10))
    ]);
}

// Private accounts hide their listings and reviews from other students
if ((bool)$user['is_private'] && $viewerId !== $ownerId) {
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
            'rating' => 0.0,
            'reviews' => 0,
            'joined_at' => $user['created_at'] ?? '',
            'follower_count' => $followCount,
            'is_following' => $isFollowing,
        ],
        'listings' => [],
        'reviews' => [],
    ]);
}

// Default: Profile action
$listQuery = $pdo->prepare('SELECT l.id, l.owner_id, l.title, u.full_name AS seller, l.category, l.description, l.price, l.`condition`, l.original_price, l.image_url, l.location, l.is_available, UNIX_TIMESTAMP(l.created_at)*1000 AS posted_at_ms, u.is_verified FROM listings l INNER JOIN users u ON u.id = l.owner_id WHERE l.owner_id = ? AND l.archived_at IS NULL ORDER BY l.created_at DESC');
$listQuery->execute([$ownerId]);
$listings = [];
$active = 0;
$sold = 0;
foreach ($listQuery->fetchAll() as $item) {
    $item['id'] = (string)$item['id'];
    $item['owner_id'] = (string)$item['owner_id'];
    $item['price'] = (string)$item['price'];
    $item['rating'] = (string)0; // Default
    $item['review_count'] = (string)0; // Default
    $item['is_available'] = (bool)$item['is_available'];
    $item['is_verified'] = (bool)($item['is_verified'] ?? false);
    $item['posted_at_ms'] = (int)($item['posted_at_ms'] ?? 0);
    $item['thumb_url'] = listing_thumbnail_url((string)($item['image_url'] ?? ''));
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

// Aggregate social proof: average rating + review count (mirrors the metrics action).
$rateQuery = $pdo->prepare('SELECT AVG(stars), COUNT(*) FROM reviews WHERE seller_id = ?');
$rateQuery->execute([$ownerId]);
$rateRow = $rateQuery->fetch(PDO::FETCH_NUM) ?: [null, 0];
$avgRating = round((float)$rateRow[0], 1);
$reviewCount = (int)$rateRow[1];

respond(true, 'Seller loaded', [
    'seller' => [
        'id' => (string)$ownerId,
        'name' => $user['full_name'],
        'verified' => (bool)$user['is_verified'],
        'bio' => $user['bio'] ?? '',
        'is_private' => false,
        'profile_pic_url' => $user['profile_pic_url'] ?? '',
        'active' => (int)$active,
        'sold' => (int)$sold,
        'rating' => (float)$avgRating,
        'reviews' => (int)$reviewCount,
        'joined_at' => $user['created_at'] ?? '',
        'follower_count' => (int)$followCount,
        'is_following' => (bool)$isFollowing,
    ],
    'listings' => $listings,
    'reviews' => $reviews,
]);
