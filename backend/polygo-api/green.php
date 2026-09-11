<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/ImpactEngine.php';

try {
    // SECURITY: every Green Impact call is JWT-authenticated
    $userId = verify_jwt();
    if ($userId <= 0) {
        respond(false, 'Unauthorized access');
    }

    $input = input_json();
    $action = $input['action'] ?? 'metrics';

    if ($action === 'metrics') {
        respond(true, 'Impact metrics loaded', [
            'metrics' => ImpactEngine::metrics($pdo, $userId),
            'breakdown' => ImpactEngine::breakdown($pdo, $userId)
        ]);
    }

    if ($action === 'leaderboard') {
        $limit = min(50, max(1, (int)($input['limit'] ?? 20)));
        respond(true, 'Green leaderboard loaded', [
            'leaderboard' => ImpactEngine::leaderboard($pdo, $limit),
            'me' => ImpactEngine::metrics($pdo, $userId)
        ]);
    }

    respond(false, 'Unknown action');

} catch (Exception $e) {
    respond(false, 'Could not load sustainability data');
}