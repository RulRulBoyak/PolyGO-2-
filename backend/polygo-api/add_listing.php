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
    $title = trim((string)($input['title'] ?? ''));
    $category = trim((string)($input['category'] ?? ''));
    $description = trim((string)($input['description'] ?? ''));
    $price = (float)($input['price'] ?? 0);
    $imageUrl = trim((string)($input['image_url'] ?? ''));
    $location = trim((string)($input['location'] ?? 'Near campus'));
    $tags = trim((string)($input['tags'] ?? ''));
    $freeSlots = trim((string)($input['free_slots'] ?? ''));
    $majorId = (int)($input['major_id'] ?? 0);
    $majorId = $majorId > 0 ? $majorId : null;

    // Marketplace Pro attributes.
    $knownConditions = ['New', 'Used - Like New', 'Used - Good', 'Used - Fair'];
    $conditionRaw = (string)($input['condition'] ?? 'New');
    $condition = in_array($conditionRaw, $knownConditions, true) ? $conditionRaw : 'New';
    $originalPrice = max(0, (float)($input['original_price'] ?? 0));
    // Strike-through only makes sense when the original price is above the ask.
    $originalPrice = $originalPrice > $price ? $originalPrice : 0;
    $autoReply = !empty($input['auto_reply']) ? 1 : 0;
    $hideFromFriends = !empty($input['hide_from_friends']) ? 1 : 0;

    if ($ownerId <= 0 || empty($title)) {
        respond(false, 'Missing required listing information');
    }

    // Bound the inputs the same way the schema + marketplace expect.
    if (mb_strlen($title) > 150) respond(false, 'Title is too long (max 150 characters)');
    if (mb_strlen($description) > 2000) respond(false, 'Description is too long (max 2000 characters)');
    if ($category === '' || mb_strlen($category) > 80) respond(false, 'Please choose a valid category');
    if ($price <= 0 || $price > 100000) respond(false, 'Please enter a valid price');
    if (mb_strlen($tags) > 255 || mb_strlen($freeSlots) > 500 || mb_strlen($location) > 150) {
        respond(false, 'Listing details are too long');
    }
    $categoryCheck = $pdo->prepare('SELECT 1 FROM categories WHERE name = ? AND is_published = 1 LIMIT 1');
    $categoryCheck->execute([$category]);
    if (!$categoryCheck->fetchColumn()) respond(false, 'Please choose a published category');
    if ($imageUrl !== '') {
        $canonicalImage = canonical_uploaded_image_url($imageUrl);
        if ($canonicalImage === null) respond(false, 'Please upload a valid listing image');
        $imageUrl = $canonicalImage;
    }
    if ($majorId !== null) {
        $majorCheck = $pdo->prepare('SELECT id FROM majors WHERE id = ? LIMIT 1');
        $majorCheck->execute([$majorId]);
        if (!$majorCheck->fetch()) respond(false, 'Invalid major selected');
    }

    $action = strtolower((string)($input['action'] ?? ''));
    if ($action === 'edit') {
        $listingId = (string)($input['listing_id'] ?? '');
        if ($listingId === '') respond(false, 'Missing listing id');

        $check = $pdo->prepare('SELECT owner_id, is_available, archived_at FROM listings WHERE id = ? LIMIT 1');
        $check->execute([$listingId]);
        $row = $check->fetch();
        if (!$row) respond(false, 'Listing not found');
        if ((int)$row['owner_id'] !== (int)$ownerId) respond(false, 'You can only edit your own listings');
        if ((int)$row['is_available'] === 0 || $row['archived_at'] !== null) {
            respond(false, 'Sold or removed listings can no longer be edited');
        }

        $originalPriceDb = $originalPrice > 0 ? $originalPrice : null;
        $update = $pdo->prepare('UPDATE listings SET title = ?, category = ?, description = ?, price = ?, `condition` = ?, original_price = ?, image_url = ?, tags = ?, free_slots = ?, major_id = ?, location = ?, auto_reply = ?, hide_from_friends = ? WHERE id = ? AND owner_id = ?');
        if ($update->execute([$title, $category, $description, $price, $condition, $originalPriceDb, $imageUrl, $tags, $freeSlots, $majorId, $location, $autoReply, $hideFromFriends, $listingId, $ownerId])) {
            respond(true, 'Listing updated successfully', ['id' => (string)$listingId]);
        }
        respond(false, 'Failed to update listing');
    }

    $query = $pdo->prepare('INSERT INTO listings (owner_id, title, category, description, price, `condition`, original_price, image_url, tags, free_slots, major_id, location, auto_reply, hide_from_friends) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $originalPriceDb = $originalPrice > 0 ? $originalPrice : null;
    if ($query->execute([$ownerId, $title, $category, $description, $price, $condition, $originalPriceDb, $imageUrl, $tags, $freeSlots, $majorId, $location, $autoReply, $hideFromFriends])) {
        respond(true, 'Listing added successfully', ['id' => $pdo->lastInsertId()]);
    } else {
        respond(false, 'Failed to add listing');
    }
} catch (Exception $e) {
    error_log('[polygo-api] add_listing error: ' . $e->getMessage());
    respond(false, 'Failed to add listing');
}
