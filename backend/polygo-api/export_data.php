<?php
require_once __DIR__ . '/config.php';

try {
    // PDPA 2010 data portability: any user can download a copy of their data.
    // Identity is taken from the JWT, never from the request body.
    $userId = verify_jwt();

    if ($userId <= 0) {
        respond(false, 'Unauthorized');
    }

    $db = $pdo;

    $data = [
        'generated_at' => gmdate('c'),
        'schema_version' => '1.0',
        'profile' => null,
        'listings' => [],
        'favorites' => [],
        'threads' => [],
        'messages' => [],
        'transactions' => [],
        'reviews_given' => [],
        'reviews_received' => [],
        'notifications' => [],
        'reports_submitted' => [],
        'security_alerts' => [],
        'green_impact' => [],
    ];

    $fetch = static function (string $sql, array $params) use ($db): array {
        $stmt = $db->prepare($sql);
        $stmt->execute($params);
        return $stmt->fetchAll();
    };

    $profileRows = $fetch('SELECT id, full_name, student_id, email, mobile, bio, is_verified, is_private, profile_pic_url, role, created_at, updated_at FROM users WHERE id = ?', [$userId]);
    if (isset($profileRows[0])) {
        $data['profile'] = $profileRows[0];
    }

    $data['listings'] = $fetch('SELECT id, title, category, description, price, image_url, tags, free_slots, major_id, location, is_available, archived_at, created_at FROM listings WHERE owner_id = ?', [$userId]);
    $favorites = $fetch('SELECT f.listing_id, l.title, f.created_at FROM favorites f LEFT JOIN listings l ON l.id = f.listing_id WHERE f.user_id = ?', [$userId]);
    foreach ($favorites as $fav) {
        $data['favorites'][] = ['listing_id' => $fav['listing_id'], 'title' => $fav['title'], 'saved_at' => $fav['created_at']];
    }

    $threads = $fetch('SELECT id, listing_id, buyer_id, seller_id FROM threads WHERE buyer_id = ? OR seller_id = ? ORDER BY id', [$userId, $userId]);
    foreach ($threads as $t) {
        $data['threads'][] = [
            'thread_id' => $t['id'],
            'listing_id' => $t['listing_id'],
            'buyer_id' => $t['buyer_id'],
            'seller_id' => $t['seller_id'],
        ];
        $messages = $fetch('SELECT sender_id, text, is_read, created_at FROM messages WHERE thread_id = ? ORDER BY id ASC', [$t['id']]);
        foreach ($messages as $m) {
            $data['messages'][] = [
                'thread_id' => $t['id'],
                'sender_id' => $m['sender_id'],
                'text' => $m['text'],
                'is_read' => (bool)$m['is_read'],
                'sent_at' => $m['created_at'],
            ];
        }
    }

    $data['transactions'] = $fetch('SELECT id, listing_id, buyer_id, seller_id, amount, status, impact_credited, created_at FROM transactions WHERE buyer_id = ? OR seller_id = ? ORDER BY id', [$userId, $userId]);
    $data['reviews_given'] = $fetch('SELECT seller_id, listing_id, stars, comment, created_at FROM reviews WHERE reviewer_id = ? ORDER BY id', [$userId]);
    $data['reviews_received'] = $fetch('SELECT reviewer_id, reviewer_name, listing_id, stars, comment, created_at FROM reviews WHERE seller_id = ? ORDER BY id', [$userId]);
    $data['notifications'] = $fetch('SELECT id, title, body, is_read, created_at FROM notifications WHERE user_id = ? ORDER BY id', [$userId]);
    $data['reports_submitted'] = $fetch('SELECT target_type, target_id, reason, details, created_at FROM reports WHERE reporter_id = ? ORDER BY id', [$userId]);
    $data['security_alerts'] = $fetch('SELECT thread_id, listing_id, landmark, latitude, longitude, created_at FROM security_logs WHERE user_id = ? ORDER BY id', [$userId]);
    $data['green_impact'] = $fetch('SELECT category_name, co2_kg, water_l, paper_kg, energy_kwh, created_at FROM impact_entries WHERE user_id = ? ORDER BY id', [$userId]);

    $impacts = $fetch('SELECT co2_kg, water_l, paper_kg, energy_kwh, impact_count, tier FROM user_impact WHERE user_id = ?', [$userId]);
    if (isset($impacts[0])) {
        $data['green_impact_summary'] = $impacts[0];
    }

    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'success' => true,
        'message' => 'Data export ready',
        'data' => $data,
    ], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
} catch (Throwable $e) {
    error_log('[polygo-api] export_data error: ' . $e->getMessage());
    respond(false, 'Failed to export data');
}