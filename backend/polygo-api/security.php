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