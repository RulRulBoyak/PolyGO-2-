<?php
// Marketplace Pro: per-listing "Alert" watches (price-drop / availability).
// Actions: toggle | list. Toggle flips the watch on/off for the caller.
require_once __DIR__ . '/config.php';

$input = input_json();
$viewerId = verify_jwt();

$action = $input['action'] ?? 'list';
$listingId = (int)($input['listing_id'] ?? 0);

if ($action === 'list') {
    $query = $pdo->prepare('SELECT listing_id FROM listing_alerts WHERE user_id = ?');
    $query->execute([$viewerId]);
    $ids = [];
    foreach ($query->fetchAll() as $row) {
        $ids[] = (int)$row['listing_id'];
    }
    respond(true, 'Alerts loaded', ['listing_ids' => $ids]);
    return;
}

if ($action === 'toggle') {
    if ($listingId <= 0) {
        respond(false, 'Missing listing');
    }
    $listingCheck = $pdo->prepare('SELECT id FROM listings WHERE id = ?');
    $listingCheck->execute([$listingId]);
    if (!$listingCheck->fetch()) {
        respond(false, 'Listing not found');
    }

    $existing = $pdo->prepare('SELECT id FROM listing_alerts WHERE user_id = ? AND listing_id = ?');
    $existing->execute([$viewerId, $listingId]);
    if ($existing->fetch()) {
        $delete = $pdo->prepare('DELETE FROM listing_alerts WHERE user_id = ? AND listing_id = ?');
        $delete->execute([$viewerId, $listingId]);
        respond(true, 'Alert removed', ['watching' => false]);
    }

    try {
        $insert = $pdo->prepare('INSERT IGNORE INTO listing_alerts (user_id, listing_id) VALUES (?, ?)');
        $insert->execute([$viewerId, $listingId]);
        respond(true, 'Alert set - we will notify you if anything changes', ['watching' => true]);
    } catch (Throwable $e) {
        error_log('[polygo-api] alert error: ' . $e->getMessage());
        respond(false, 'Failed to set alert');
    }
}

respond(false, 'Unknown alert action');