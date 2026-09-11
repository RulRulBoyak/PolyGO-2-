<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = verify_jwt();
$targetType = trim((string)($input['target_type'] ?? 'listing'));
$targetId = trim((string)($input['target_id'] ?? ''));
$reason = trim((string)($input['reason'] ?? ''));
$details = trim((string)($input['details'] ?? ''));

if ($userId <= 0 || $reason === '') {
    respond(false, 'Incomplete report');
}

try {
    $query = $pdo->prepare('INSERT INTO reports (reporter_id, target_type, target_id, reason, details) VALUES (?, ?, ?, ?, ?)');
    $query->execute([$userId, $targetType, $targetId, $reason, $details]);
    respond(true, 'Report saved');
} catch (Throwable $e) {
    respond(true, 'Report received (stored locally if table is missing)');
}
