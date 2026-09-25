<?php
require __DIR__ . '/../backend/polygo-api/config.php';

$duplicates = (int)$pdo->query(
    "SELECT COUNT(*) FROM (
        SELECT fcm_token FROM users
        WHERE fcm_token IS NOT NULL AND fcm_token <> ''
        GROUP BY fcm_token HAVING COUNT(*) > 1
    ) duplicate_tokens"
)->fetchColumn();
if ($duplicates !== 0) {
    throw new RuntimeException("Duplicate FCM tokens remain: $duplicates");
}

$latestGlobal = $pdo->query(
    'SELECT id FROM campus_alerts WHERE is_global = 1 ORDER BY id DESC LIMIT 1'
)->fetchColumn();
if ($latestGlobal === false) {
    throw new RuntimeException('No global announcement is available for the history check');
}

echo "Announcement state check passed\n";
