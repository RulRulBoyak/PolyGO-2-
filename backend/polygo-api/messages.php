<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/NotificationManager.php';

$input = input_json();
$userId = verify_jwt();
$threadId = (int)($input['thread_id'] ?? 0);
$listingId = (int)($input['listing_id'] ?? 0);
$receiverId = (int)($input['receiver_id'] ?? 0);
$text = trim((string)($input['text'] ?? ''));
$action = $input['action'] ?? 'threads';

if ($userId <= 0) respond(false, 'Unauthorized');

// Return true when $a and $b have a block relation in either direction.
function is_blocked(PDO $pdo, int $a, int $b): bool {
    $stmt = $pdo->prepare('SELECT COUNT(*) FROM blocked_users WHERE (user_id = ? AND blocked_id = ?) OR (user_id = ? AND blocked_id = ?)');
    $stmt->execute([$a, $b, $b, $a]);
    return (int)$stmt->fetchColumn() > 0;
}

if ($action === 'send') {
    if ($text === '') respond(false, 'Write a message first');
    if (mb_strlen($text) > 2000) respond(false, 'Message is too long (max 2000 characters)');
    if (!rate_limit_check($pdo, 'message_uid:' . $userId, 60, 60)) {
        respond(false, 'Too many messages. Please wait a moment.');
    }
    if ($threadId <= 0) {
        // Create new thread — refuse when either side has blocked the other.
        if ($receiverId <= 0) respond(false, 'Missing receiver');
        if ($receiverId === $userId) respond(false, 'You cannot message yourself');
        if (is_blocked($pdo, $userId, $receiverId)) respond(false, 'You cannot message this user');
        // The receiver must be the actual listing owner — never trust the caller.
        $ownerCheck = $pdo->prepare('SELECT owner_id FROM listings WHERE id = ? AND is_available = 1 AND archived_at IS NULL LIMIT 1');
        $ownerCheck->execute([$listingId]);
        $ownerId = (int)$ownerCheck->fetchColumn();
        if ($ownerId <= 0) respond(false, 'Listing not found');
        if ($ownerId !== $receiverId) respond(false, 'You can only chat with the seller of this listing');
        // Reuse an existing thread for the same listing pair (either direction)
        // instead of silently creating a duplicate conversation per message.
        $find = $pdo->prepare('SELECT id FROM threads WHERE listing_id = ?
            AND ((buyer_id = ? AND seller_id = ?) OR (buyer_id = ? AND seller_id = ?))
            ORDER BY id LIMIT 1');
        $find->execute([$listingId, $userId, $receiverId, $receiverId, $userId]);
        $existing = $find->fetchColumn();
        if ($existing) {
            $threadId = (int)$existing;
        } else {
            $query = $pdo->prepare('INSERT INTO threads (listing_id, buyer_id, seller_id) VALUES (?, ?, ?)');
            $query->execute([$listingId, $userId, $receiverId]);
            $threadId = (int)$pdo->lastInsertId();
        }
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

        // Marketplace Pro auto-reply: when the seller enabled auto_reply and a
        // BUYER asks whether the item is still available, the seller instantly
        // answers (canned) so the deal moves 5x faster.
        try {
            $auto = $pdo->prepare('SELECT t.listing_id, t.buyer_id, t.seller_id, l.auto_reply FROM threads t INNER JOIN listings l ON l.id = t.listing_id WHERE t.id = ?');
            $auto->execute([$threadId]);
            $autoRow = $auto->fetch();
            if ($autoRow
                && (int)$autoRow['auto_reply'] === 1
                && (int)$autoRow['buyer_id'] === $userId
                && preg_match('/(still available|available\?|available now|masih ada|cantik\?|available)/i', $text)
            ) {
                $reply = 'Yes, it is still available. Would you like to arrange a meetup on campus?';
                $alreadyReplied = $pdo->prepare('SELECT 1 FROM messages WHERE thread_id = ? AND sender_id = ? AND text = ? LIMIT 1');
                $alreadyReplied->execute([$threadId, (int)$autoRow['seller_id'], $reply]);
                if (!$alreadyReplied->fetchColumn()) {
                    $autoInsert = $pdo->prepare('INSERT INTO messages (thread_id, sender_id, text) VALUES (?, ?, ?)');
                    $autoInsert->execute([$threadId, (int)$autoRow['seller_id'], $reply]);
                    NotificationManager::sendToUser($pdo, $userId, 'Instant reply', $reply, [
                        'type' => 'chat',
                        'thread_id' => (string)$threadId
                    ]);
                }
            }
        } catch (Throwable $ignored) {
        }

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

    $query = $pdo->prepare('SELECT m.id, m.thread_id, m.sender_id, u.full_name AS sender, m.text, UNIX_TIMESTAMP(m.created_at) * 1000 AS time FROM messages m INNER JOIN users u ON u.id = m.sender_id WHERE m.thread_id = ? AND (m.sender_id = ? OR EXISTS (SELECT 1 FROM threads t WHERE t.id = m.thread_id AND (t.buyer_id = ? OR t.seller_id = ?))) ORDER BY m.created_at DESC LIMIT 500');
    $query->execute([$threadId, $userId, $userId, $userId]);
    $messages = [];
    foreach (array_reverse($query->fetchAll()) as $m) {
        $m['id'] = (int)$m['id'];
        $m['thread_id'] = (int)$m['thread_id'];
        $m['mine'] = (int)$m['sender_id'] === $userId;
        unset($m['sender_id']);
        $messages[] = $m;
    }
    $markRead = $pdo->prepare('UPDATE messages SET is_read = 1 WHERE thread_id = ? AND sender_id <> ?');
    $markRead->execute([$threadId, $userId]);
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
           EXISTS(SELECT 1 FROM messages unread_messages
                  WHERE unread_messages.thread_id = t.id
                    AND unread_messages.sender_id <> ?
                    AND unread_messages.is_read = 0) AS unread
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
    ORDER BY lastMessageTime DESC LIMIT 200
');
$query->execute([$userId, $userId, $userId, $userId, $userId, $userId]);
$threads = [];
foreach ($query->fetchAll() as $t) {
    $t['id'] = (int)$t['id'];
    $t['listingId'] = (int)$t['listingId'];
    $t['unread'] = (bool)$t['unread'];
    $threads[] = $t;
}
respond(true, 'Threads loaded', ['threads' => $threads]);
