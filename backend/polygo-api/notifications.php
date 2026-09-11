<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = verify_jwt();
$action = $input['action'] ?? 'get';

if ($userId <= 0) respond(false, 'Unauthorized');

if ($action === 'read') {
    $pdo->prepare('UPDATE notifications SET is_read = 1 WHERE user_id = ?')->execute([$userId]);
    respond(true, 'Notifications marked as read');
}

$query = $pdo->prepare('SELECT id, title, body, UNIX_TIMESTAMP(created_at) * 1000 AS time, is_read AS `read` FROM notifications WHERE user_id = ? ORDER BY created_at DESC');
$query->execute([$userId]);
$items = [];
foreach ($query->fetchAll() as $item) {
    $item['id'] = (int)$item['id'];
    $item['time'] = (int)$item['time'];
    $item['read'] = (bool)$item['read'];
    $items[] = $item;
}
respond(true, 'Notifications loaded', ['notifications' => $items]);
