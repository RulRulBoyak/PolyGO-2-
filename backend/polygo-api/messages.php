<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$userId = (int)($input['user_id'] ?? 0);
$threadId = (int)($input['thread_id'] ?? 0);
$listingId = (int)($input['listing_id'] ?? 0);
$receiverId = (int)($input['receiver_id'] ?? 0);
$text = $input['text'] ?? '';
$action = $input['action'] ?? 'threads';

if ($userId <= 0) respond(false, 'Unauthorized');

if ($action === 'send') {
    if ($threadId <= 0) {
        // Create new thread
        $query = $pdo->prepare('INSERT INTO threads (listing_id, buyer_id, seller_id) VALUES (?, ?, ?)');
        $query->execute([$listingId, $userId, $receiverId]);
        $threadId = (int)$pdo->lastInsertId();
    }

    $query = $pdo->prepare('INSERT INTO messages (thread_id, sender_id, text) VALUES (?, ?, ?)');
    if ($query->execute([$threadId, $userId, $text])) {
        respond(true, 'Message sent', ['thread_id' => $threadId]);
    } else {
        respond(false, 'Failed to send message');
    }
}

if ($action === 'messages') {
    $query = $pdo->prepare('SELECT m.*, u.full_name AS sender_name FROM messages m INNER JOIN users u ON u.id = m.sender_id WHERE m.thread_id = ? ORDER BY m.created_at ASC');
    $query->execute([$threadId]);
    $messages = [];
    foreach ($query->fetchAll() as $m) {
        $m['id'] = (int)$m['id'];
        $m['thread_id'] = (int)$m['thread_id'];
        $m['sender_id'] = (int)$m['sender_id'];
        $m['mine'] = $m['sender_id'] === $userId;
        $messages[] = $m;
    }
    respond(true, 'Messages loaded', ['messages' => $messages]);
}

// Default: fetch threads
$query = $pdo->prepare('
    SELECT t.id, t.listing_id, l.title AS listing_title,
           IF(t.buyer_id = ?, u2.full_name, u1.full_name) AS other_name,
           (SELECT text FROM messages WHERE thread_id = t.id ORDER BY created_at DESC LIMIT 1) AS last_message,
           (SELECT created_at FROM messages WHERE thread_id = t.id ORDER BY created_at DESC LIMIT 1) AS last_time
    FROM threads t
    INNER JOIN listings l ON l.id = t.listing_id
    INNER JOIN users u1 ON u1.id = t.buyer_id
    INNER JOIN users u2 ON u2.id = t.seller_id
    WHERE t.buyer_id = ? OR t.seller_id = ?
    ORDER BY last_time DESC
');
$query->execute([$userId, $userId, $userId]);
$threads = [];
foreach ($query->fetchAll() as $t) {
    $t['id'] = (int)$t['id'];
    $t['listing_id'] = (int)$t['listing_id'];
    $threads[] = $t;
}
respond(true, 'Threads loaded', ['threads' => $threads]);
