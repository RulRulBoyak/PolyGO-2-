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
if (!in_array($targetType, ['listing', 'user'], true)
    || !ctype_digit($targetId) || (int)$targetId <= 0) {
    respond(false, 'Invalid report target');
}
if (mb_strlen($reason) > 120 || mb_strlen($details) > 4000) {
    respond(false, 'Report details are too long');
}
if (!rate_limit_check($pdo, 'report_uid:' . $userId, 10, 3600)) {
    respond(false, 'Too many reports. Please try again later.');
}

$targetTable = $targetType === 'listing' ? 'listings' : 'users';
$ownerColumn = $targetType === 'listing' ? 'owner_id' : 'id';
$targetExists = $pdo->prepare("SELECT id, {$ownerColumn} AS owner_id FROM {$targetTable} WHERE id = ? LIMIT 1");
$targetExists->execute([(int)$targetId]);
$target = $targetExists->fetch();
if (!$target) {
    respond(false, 'Report target not found');
}
if ((int)$target['owner_id'] === $userId) {
    respond(false, $targetType === 'listing' ? 'You cannot report your own listing' : 'You cannot report yourself');
}

$duplicate = $pdo->prepare('SELECT 1 FROM reports
    WHERE reporter_id = ? AND target_type = ? AND target_id = ?
      AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) LIMIT 1');
$duplicate->execute([$userId, $targetType, $targetId]);
if ($duplicate->fetchColumn()) {
    respond(false, 'You already reported this recently');
}

try {
    $query = $pdo->prepare('INSERT INTO reports (reporter_id, target_type, target_id, reason, details) VALUES (?, ?, ?, ?, ?)');
    $query->execute([$userId, $targetType, $targetId, $reason, $details]);
    respond(true, 'Report saved');
} catch (Throwable $e) {
    error_log('[polygo-api] report save failed: ' . $e->getMessage());
    respond(false, 'Could not save report');
}
