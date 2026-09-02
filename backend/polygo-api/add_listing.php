<?php
require_once __DIR__ . '/config.php';

try {
    // SECURITY: Verify JWT and get actual User ID
    $ownerId = verify_jwt();

    $input = input_json();
    $title = $input['title'] ?? '';
    $category = $input['category'] ?? '';
    $description = $input['description'] ?? '';
    $price = (float)($input['price'] ?? 0);
    $imageUrl = $input['image_url'] ?? '';
    $location = $input['location'] ?? 'Near campus';

    if ($ownerId <= 0 || empty($title)) {
        respond(false, 'Missing required listing information');
    }

    $query = $pdo->prepare('INSERT INTO listings (owner_id, title, category, description, price, image_url, location) VALUES (?, ?, ?, ?, ?, ?, ?)');
    if ($query->execute([$ownerId, $title, $category, $description, $price, $imageUrl, $location])) {
        respond(true, 'Listing added successfully', ['id' => $pdo->lastInsertId()]);
    } else {
        respond(false, 'Failed to add listing');
    }
} catch (Exception $e) {
    respond(false, 'Database error: ' . $e->getMessage());
}
