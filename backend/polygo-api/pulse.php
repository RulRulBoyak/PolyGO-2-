<?php
require_once __DIR__ . '/config.php';

$input   = input_json();
$action  = $input['action'] ?? 'list';

// Public read feed: only approved, newest first.
if ($action === 'list') {
    $query = $pdo->prepare(
        'SELECT id, user_id, user_name, tag, title, body, UNIX_TIMESTAMP(created_at) AS created_at
           FROM campus_alerts
          WHERE status = ?
          ORDER BY id DESC
          LIMIT 50'
    );
    $query->execute(['approved']);
    respond(true, 'OK', ['alerts' => $query->fetchAll()]);
}

// Authenticated post.
if ($action === 'post') {
    $userId = verify_jwt();

    $tag   = strtoupper(trim((string)($input['tag'] ?? '')));
    $title = trim((string)($input['title'] ?? ''));
    $body  = trim((string)($input['body'] ?? ''));

    $allowedTags = ['ANNOUNCEMENT', 'REQUEST', 'FLASH SALE', 'EVENT'];
    if (!in_array($tag, $allowedTags, true) || $title === '' || mb_strlen($title) > 150
        || $body === '' || mb_strlen($body) > 2000) {
        respond(false, 'Please provide a valid tag, title and message');
    }

    // Resolve the display name server-side so posters cannot spoof it.
    $nameQuery = $pdo->prepare('SELECT full_name FROM users WHERE id = ?');
    $nameQuery->execute([$userId]);
    $name = (string)$nameQuery->fetchColumn();
    if ($name === '') {
        $name = 'PolyGo member';
    }

    $insert = $pdo->prepare(
        'INSERT INTO campus_alerts (user_id, user_name, tag, title, body, status) VALUES (?, ?, ?, ?, ?, ?)'
    );
    $insert->execute([$userId, $name, $tag, $title, $body, 'approved']);

    respond(true, 'Pulse posted');
}

respond(false, 'Unknown action');