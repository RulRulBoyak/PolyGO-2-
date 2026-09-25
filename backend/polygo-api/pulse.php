<?php
require_once __DIR__ . '/config.php';

$input   = input_json();
$action  = $input['action'] ?? 'list';

// Public read feed: only approved, newest first.
if ($action === 'list') {
    $viewerId = verify_jwt_optional();
    $query = $pdo->prepare(
        'SELECT a.id, a.user_id, a.user_name, a.tag, a.title, a.body, a.is_global,
                COALESCE(u.profile_pic_url, "") AS profile_pic_url,
                UNIX_TIMESTAMP(a.created_at) AS created_at,
                (SELECT COUNT(*) FROM pulse_likes l JOIN users lu ON lu.id = l.user_id AND lu.is_banned = 0
                  WHERE l.pulse_id = a.id) AS like_count,
                (SELECT COUNT(*) FROM pulse_comments c JOIN users cu ON cu.id = c.user_id AND cu.is_banned = 0
                  WHERE c.pulse_id = a.id AND c.is_deleted = 0) AS comment_count,
                EXISTS(SELECT 1 FROM pulse_likes mine WHERE mine.pulse_id = a.id AND mine.user_id = ?) AS liked_by_me
           FROM campus_alerts a
           LEFT JOIN users u ON u.id = a.user_id
          WHERE a.status = ?
          ORDER BY a.id DESC
          LIMIT 50'
    );
    $query->execute([$viewerId, 'approved']);
    $announcements = [];
    $threads = [];
    foreach ($query->fetchAll() as $item) {
        $item['is_global'] = (bool)($item['is_global'] ?? 0);
        $item['liked_by_me'] = (bool)($item['liked_by_me'] ?? 0);
        $item['like_count'] = (int)($item['like_count'] ?? 0);
        $item['comment_count'] = (int)($item['comment_count'] ?? 0);
        if ($item['is_global']) $announcements[] = $item;
        else $threads[] = $item;
    }
    respond(true, 'OK', ['announcements' => $announcements, 'alerts' => $threads]);
}

if (in_array($action, ['like', 'comments', 'comment'], true)) {
    $pulseId = filter_var($input['pulse_id'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
    if (!$pulseId) respond(false, 'Invalid Campus Pulse thread');
    $post = $pdo->prepare('SELECT id FROM campus_alerts WHERE id = ? AND status = ?');
    $post->execute([$pulseId, 'approved']);
    if (!$post->fetchColumn()) respond(false, 'This Campus Pulse thread is no longer available');

    if ($action === 'comments') {
        $viewerId = verify_jwt_optional();
        $comments = $pdo->prepare(
            'SELECT c.id, c.pulse_id, c.user_id, c.body, u.full_name AS user_name,
                    COALESCE(u.profile_pic_url, "") AS profile_pic_url,
                    UNIX_TIMESTAMP(c.created_at) AS created_at,
                    (c.user_id = ?) AS is_mine
               FROM pulse_comments c
               JOIN users u ON u.id = c.user_id AND u.is_banned = 0
              WHERE c.pulse_id = ? AND c.is_deleted = 0
              ORDER BY c.id ASC LIMIT 200'
        );
        $comments->execute([$viewerId, $pulseId]);
        $items = $comments->fetchAll();
        foreach ($items as &$item) {
            $item['is_mine'] = (bool)($item['is_mine'] ?? false);
        }
        unset($item);
        respond(true, 'OK', ['comments' => $items]);
    }

    $userId = verify_jwt();
    if ($action === 'like') {
        $liked = filter_var($input['liked'] ?? false, FILTER_VALIDATE_BOOLEAN);
        $statement = $liked
            ? $pdo->prepare('INSERT IGNORE INTO pulse_likes (pulse_id, user_id) VALUES (?, ?)')
            : $pdo->prepare('DELETE FROM pulse_likes WHERE pulse_id = ? AND user_id = ?');
        $statement->execute([$pulseId, $userId]);
        $count = $pdo->prepare(
            'SELECT COUNT(*) FROM pulse_likes l JOIN users u ON u.id = l.user_id AND u.is_banned = 0 WHERE l.pulse_id = ?'
        );
        $count->execute([$pulseId]);
        respond(true, 'OK', ['liked' => $liked, 'like_count' => (int)$count->fetchColumn()]);
    }

    $comment = trim((string)($input['comment'] ?? ''));
    if ($comment === '' || mb_strlen($comment) > 500) respond(false, 'Comments must be between 1 and 500 characters');
    $clientIp = $_SERVER['REMOTE_ADDR'] ?? '';
    if (!rate_limit_check($pdo, 'pulse_comment_uid:' . $userId, 30, 600) ||
        !rate_limit_check($pdo, 'pulse_comment_ip:' . $clientIp, 60, 600)) {
        respond(false, 'You are commenting too fast. Please wait a moment.');
    }
    $insert = $pdo->prepare('INSERT INTO pulse_comments (pulse_id, user_id, body) VALUES (?, ?, ?)');
    $insert->execute([$pulseId, $userId, $comment]);
    $commentId = (int)$pdo->lastInsertId();
    $created = $pdo->prepare(
        'SELECT c.id, c.pulse_id, c.user_id, c.body, u.full_name AS user_name,
                COALESCE(u.profile_pic_url, "") AS profile_pic_url,
                UNIX_TIMESTAMP(c.created_at) AS created_at, 1 AS is_mine
           FROM pulse_comments c JOIN users u ON u.id = c.user_id WHERE c.id = ?'
    );
    $created->execute([$commentId]);
    $createdComment = $created->fetch();
    $createdComment['is_mine'] = true;
    $count = $pdo->prepare(
        'SELECT COUNT(*) FROM pulse_comments c JOIN users u ON u.id = c.user_id AND u.is_banned = 0
          WHERE c.pulse_id = ? AND c.is_deleted = 0'
    );
    $count->execute([$pulseId]);
    respond(true, 'Comment posted', ['comment' => $createdComment, 'comment_count' => (int)$count->fetchColumn()]);
}

// Authenticated post.
if ($action === 'post') {
    $userId = verify_jwt();

    // UGC wall — per-user + per-IP so the campus feed cannot be flooded.
    $clientIp = $_SERVER['REMOTE_ADDR'] ?? '';
    if (!rate_limit_check($pdo, 'pulse_uid:' . $userId, 10, 600) ||
        !rate_limit_check($pdo, 'pulse_ip:' . $clientIp, 20, 600)) {
        respond(false, 'You are posting too fast, please try again later');
    }

    $tag   = strtoupper(trim((string)($input['tag'] ?? '')));
    $title = trim((string)($input['title'] ?? ''));
    $body  = trim((string)($input['body'] ?? ''));

    $allowedTags = ['REQUEST', 'FLASH SALE', 'EVENT'];
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
    $insert->execute([$userId, $name, $tag, $title, $body, 'pending']);

    respond(true, 'Pulse submitted for moderator review');
}

respond(false, 'Unknown action');
