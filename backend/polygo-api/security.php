<?php
require_once __DIR__ . '/config.php';

try {
    // SECURITY: Verify JWT and get actual User ID
    $userId = verify_jwt();

    $input = input_json();
    if ($userId <= 0) {
        respond(false, 'Unauthorized');
    }

    $threadId = (int)($input['thread_id'] ?? 0);
    $listingId = (int)($input['listing_id'] ?? 0);
    $landmark = trim((string)($input['landmark'] ?? ''));
    $latitude = isset($input['latitude']) ? (float)$input['latitude'] : null;
    $longitude = isset($input['longitude']) ? (float)$input['longitude'] : null;

    if (!rate_limit_check($pdo, 'security_uid:' . $userId, 20, 3600)) {
        respond(false, 'Too many safety check-ins. Please try again later.');
    }
    if (mb_strlen($landmark) > 150) respond(false, 'Landmark is too long');
    if (($latitude === null) !== ($longitude === null)
            || ($latitude !== null && ($latitude < -90 || $latitude > 90
                || $longitude < -180 || $longitude > 180))) {
        respond(false, 'Invalid location coordinates');
    }
    if ($threadId > 0) {
        $thread = $pdo->prepare('SELECT 1 FROM threads WHERE id = ? AND (buyer_id = ? OR seller_id = ?) LIMIT 1');
        $thread->execute([$threadId, $userId, $userId]);
        if (!$thread->fetchColumn()) respond(false, 'Conversation not found');
    }
    if ($listingId > 0) {
        $listing = $pdo->prepare('SELECT 1 FROM listings WHERE id = ? LIMIT 1');
        $listing->execute([$listingId]);
        if (!$listing->fetchColumn()) respond(false, 'Listing not found');
    }

    $query = $pdo->prepare('INSERT INTO security_logs (user_id, thread_id, listing_id, landmark, latitude, longitude) VALUES (?, ?, ?, ?, ?, ?)');
    $query->execute([
        $userId,
        $threadId > 0 ? $threadId : null,
        $listingId > 0 ? $listingId : null,
        $landmark !== '' ? $landmark : null,
        $latitude,
        $longitude
    ]);

    respond(true, 'Security alert logged');
} catch (Throwable $e) {
    respond(false, 'Failed to log security alert');
}
