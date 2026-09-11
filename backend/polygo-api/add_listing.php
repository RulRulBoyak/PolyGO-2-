<?php
require_once __DIR__ . '/config.php';

try {
    // SECURITY: Verify JWT and get actual User ID
    $ownerId = verify_jwt();

    // Only verified students may post to the marketplace.
    $verifyQuery = $pdo->prepare('SELECT verification_status FROM users WHERE id = ?');
    $verifyQuery->execute([$ownerId]);
    $verifyStatus = (string)$verifyQuery->fetchColumn();
    if ($verifyStatus !== 'approved') {
        respond(false, 'Verify your Matrix Card in Profile > Verification before posting');
    }

    // Bot wall: keep per-user and per-IP posting within sane limits.
    $clientIp = $_SERVER['REMOTE_ADDR'] ?? '';
    if (!rate_limit_check($pdo, 'listing_uid:' . $ownerId, 5, 60) ||
        !rate_limit_check($pdo, 'listing_ip:' . $clientIp, 10, 60)) {
        respond(false, 'Too many posts. Try again in a minute.');
    }

    $input = input_json();
    $title = $input['title'] ?? '';
    $category = $input['category'] ?? '';
    $description = $input['description'] ?? '';
    $price = (float)($input['price'] ?? 0);
    $imageUrl = $input['image_url'] ?? '';
    $location = $input['location'] ?? 'Near campus';
    $tags = $input['tags'] ?? '';
    $freeSlots = $input['free_slots'] ?? '';
    $majorId = (int)($input['major_id'] ?? 0);
    $majorId = $majorId > 0 ? $majorId : null;

    if ($ownerId <= 0 || empty($title)) {
        respond(false, 'Missing required listing information');
    }

    $query = $pdo->prepare('INSERT INTO listings (owner_id, title, category, description, price, image_url, tags, free_slots, major_id, location) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    if ($query->execute([$ownerId, $title, $category, $description, $price, $imageUrl, $tags, $freeSlots, $majorId, $location])) {
        respond(true, 'Listing added successfully', ['id' => $pdo->lastInsertId()]);
    } else {
        respond(false, 'Failed to add listing');
    }
} catch (Exception $e) {
    error_log('[polygo-api] add_listing error: ' . $e->getMessage());
    respond(false, 'Failed to add listing');
}
