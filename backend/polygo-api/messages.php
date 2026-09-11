<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/NotificationManager.php';

$input = input_json();
$userId = verify_jwt();
$threadId = (int)($input['thread_id'] ?? 0);
$listingId = (int)($input['listing_id'] ?? 0);
$receiverId = (int)($input['receiver_id'] ?? 0);
$text = $input['text'] ?? '';
$action = $input['action'] ?? 'threads';

if ($userId <= 0) respond(false, 'Unauthorized');

// Return true when $a and $b have a block relation in either direction.
function is_blocked(PDO $pdo, int $a, int $b): bool {
    $stmt = $pdo->prepare('SELECT COUNT(*) FROM blocked_users WHERE (user_id = ? AND blocked_id = ?) OR (user_id = ? AND blocked_id = ?)');
    $stmt->execute([$a, $b, $b, $a]);
    return (int)$stmt->fetchColumn() > 0;
}

if ($action === 'send') {
    if ($threadId <= 0) {
        // Create new thread — refuse when either side has blocked the other.
        if ($receiverId <= 0) respond(false, 'Missing receiver');
        if (is_blocked($pdo, $userId, $receiverId)) respond(false, 'You cannot message this user');
        $query = $pdo->prepare('INSERT INTO threads (listing_id, buyer_id, seller_id) VALUES (?, ?, ?)');
        $query->execute([$listingId, $userId, $receiverId]);
        $threadId = (int)$pdo->lastInsertId();
    } else {
        // Existing thread — verify membership and block status.
        $tCheck = $pdo->prepare('SELECT buyer_id, seller_id FROM threads WHERE id = ?');
        $tCheck->execute([$threadId]);
        $tRow = $tCheck->fetch();
        if (!$tRow) respond(false, 'Thread not found');
        $otherId = ((int)$tRow['buyer_id'] === $userId) ? (int)$tRow['seller_id'] : (int)$tRow['buyer_id'];
        if ((int)$tRow['buyer_id'] !== $userId && (int)$tRow['seller_id'] !== $userId) respond(false, 'Not authorized');
        if (is_blocked($pdo, $userId, $otherId)) respond(false, 'You cannot message this user');
    }

    $query = $pdo->prepare('INSERT INTO messages (thread_id, sender_id, text) VALUES (?, ?, ?)');
    if ($query->execute([$threadId, $userId, $text])) {

        // Find recipient to send notification
        $tQuery = $pdo->prepare('SELECT buyer_id, seller_id FROM threads WHERE id = ?');
        $tQuery->execute([$threadId]);
        $thread = $tQuery->fetch();
        if ($thread) {
            $recipientId = ($thread['buyer_id'] == $userId) ? $thread['seller_id'] : $thread['buyer_id'];

            // Get sender name
            $sQuery = $pdo->prepare('SELECT full_name FROM users WHERE id = ?');
            $sQuery->execute([$userId]);
            $senderName = $sQuery->fetchColumn();

            NotificationManager::sendToUser($pdo, (int)$recipientId, "New message from " . $senderName, $text, [
                'type' => 'chat',
                'thread_id' => (string)$threadId
            ]);
        }

        respond(true, 'Message sent', ['thread_id' => $threadId]);
    } else {
        respond(false, 'Failed to send message');
    }
}

if ($action === 'messages') {
    // Verify thread membership and block status.
    $tCheck = $pdo->prepare('SELECT buyer_id, seller_id FROM threads WHERE id = ?');
    $tCheck->execute([$threadId]);
    $tRow = $tCheck->fetch();
    if (!$tRow) respond(false, 'Thread not found');
    if ((int)$tRow['buyer_id'] !== $userId && (int)$tRow['seller_id'] !== $userId) respond(false, 'Not authorized');
    $otherId = ((int)$tRow['buyer_id'] === $userId) ? (int)$tRow['seller_id'] : (int)$tRow['buyer_id'];
    if (is_blocked($pdo, $userId, $otherId)) {
        respond(true, 'Messages loaded', ['messages' => []]);
    }

    $query = $pdo->prepare('SELECT m.id, m.thread_id, m.sender_id, u.full_name AS sender, m.text, UNIX_TIMESTAMP(m.created_at) * 1000 AS time FROM messages m INNER JOIN users u ON u.id = m.sender_id WHERE m.thread_id = ? AND (m.sender_id = ? OR EXISTS (SELECT 1 FROM threads t WHERE t.id = m.thread_id AND (t.buyer_id = ? OR t.seller_id = ?))) ORDER BY m.created_at ASC');
    $query->execute([$threadId, $userId, $userId, $userId]);
    $messages = [];
    foreach ($query->fetchAll() as $m) {
        $m['id'] = (int)$m['id'];
        $m['thread_id'] = (int)$m['thread_id'];
        $m['mine'] = (int)$m['sender_id'] === $userId;
        unset($m['sender_id']);
        $messages[] = $m;
    }
    respond(true, 'Messages loaded', ['messages' => $messages]);
}

// Default: fetch threads
$query = $pdo->prepare('
    SELECT t.id AS id,
           t.listing_id AS listingId,
           l.title AS listing_title,
           IF(t.buyer_id = ?, u2.full_name, u1.full_name) AS name,
           (SELECT text FROM messages WHERE thread_id = t.id ORDER BY created_at DESC LIMIT 1) AS last_message,
           (SELECT UNIX_TIMESTAMP(created_at) * 1000 FROM messages WHERE thread_id = t.id ORDER BY created_at DESC LIMIT 1) AS lastMessageTime,
           false AS unread
    FROM threads t
    INNER JOIN listings l ON l.id = t.listing_id
    INNER JOIN users u1 ON u1.id = t.buyer_id
    INNER JOIN users u2 ON u2.id = t.seller_id
    WHERE (t.buyer_id = ? OR t.seller_id = ?)
      AND NOT EXISTS (
          SELECT 1 FROM blocked_users b
          WHERE b.user_id = ? AND b.blocked_id IN (t.buyer_id, t.seller_id)
      )
      AND NOT EXISTS (
          SELECT 1 FROM blocked_users b2
          WHERE b2.blocked_id = ? AND b2.user_id IN (t.buyer_id, t.seller_id)
      )
    ORDER BY lastMessageTime DESC
');
$query->execute([$userId, $userId, $userId, $userId]);
$threads = [];
foreach ($query->fetchAll() as $t) {
    $t['id'] = (int)$t['id'];
    $t['listingId'] = (int)$t['listingId'];
    $t['unread'] = (bool)$t['unread'];
    $threads[] = $t;
}
respond(true, 'Threads loaded', ['threads' => $threads]);
