<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = (int)($input['user_id'] ?? 0);
$action = $input['action'] ?? 'get';

if ($userId <= 0) respond(false, 'Unauthorized');

if ($action === 'read') {
    $pdo->prepare('UPDATE notifications SET is_read = 1 WHERE user_id = ?')->execute([$userId]);
    respond(true, 'Notifications marked as read');
}

$query = $pdo->prepare('SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC');
$query->execute([$userId]);
$items = [];
foreach ($query->fetchAll() as $item) {
    $item['id'] = (int)$item['id'];
    $item['is_read'] = (bool)$item['is_read'];
    $items[] = $item;
}
respond(true, 'Notifications loaded', ['notifications' => $items]);
