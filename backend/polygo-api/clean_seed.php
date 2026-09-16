<?php
/**
 * One-shot cleanup: deletes ALL demo seed data (8 demo users + their
 * listings, reviews, transactions, messages, threads, notifications,
 * favorites, impact data).  Does NOT re-seed.
 *
 * Run once in a browser: http://localhost/polygo-api/clean_seed.php
 */
$maintenanceClient = $_SERVER['REMOTE_ADDR'] ?? '';
if (!in_array($maintenanceClient, ['127.0.0.1', '::1', '10.0.2.2'], true)) {
    http_response_code(403);
    echo '<h1>Forbidden</h1><p>This cleanup script may only be run locally.</p>';
    exit;
}
require_once __DIR__ . '/config.php';
header('Content-Type: text/html; charset=utf-8');

if (defined('APP_ENV') && APP_ENV === 'prod') {
    http_response_code(403);
    echo '<h1>Forbidden</h1><p>Cleanup is disabled in production.</p>';
    exit;
}

$demoStudentIds = ['A202601', 'D2001', 'D2002', 'D2003', 'D2004', 'D2005', 'D2006', 'D2007'];

try {
    echo "<h1>Clean Seed Data</h1>";

    // Resolve demo user IDs
    $placeholders = implode(',', array_fill(0, count($demoStudentIds), '?'));
    $query = $pdo->prepare("SELECT id, student_id FROM users WHERE student_id IN ($placeholders)");
    $query->execute($demoStudentIds);
    $existing = [];
    while ($row = $query->fetch()) {
        $existing[$row['student_id']] = (int)$row['id'];
    }

    if (count($existing) === 0) {
        echo "<p>No demo users found. Nothing to delete.</p>";
        exit;
    }

    $ids = array_values($existing);
    echo "<p>Found " . count($ids) . " demo users to remove.</p>";

    $deleteStatements = [
        "DELETE FROM impact_entries WHERE user_id IN ($placeholders)",
        "DELETE FROM user_impact WHERE user_id IN ($placeholders)",
        "DELETE FROM transactions WHERE buyer_id IN ($placeholders) OR seller_id IN ($placeholders)",
        "DELETE FROM notifications WHERE user_id IN ($placeholders)",
        "DELETE FROM favorites WHERE user_id IN ($placeholders)",
        "DELETE FROM reviews WHERE seller_id IN ($placeholders) OR reviewer_id IN ($placeholders)",
        "DELETE FROM messages WHERE sender_id IN ($placeholders)",
        "DELETE FROM threads WHERE buyer_id IN ($placeholders) OR seller_id IN ($placeholders)",
        "DELETE FROM listings WHERE owner_id IN ($placeholders)",
        "DELETE FROM users WHERE id IN ($placeholders)",
    ];

    foreach ($deleteStatements as $sql) {
        $stmt = $pdo->prepare($sql);
        $paramCount = substr_count($sql, '?');
        $paramCountId = count($ids);
        // Statements with two IN-clauses need the ID list repeated
        if ($paramCount === $paramCountId * 2) {
            $stmt->execute(array_merge($ids, $ids));
        } else {
            $stmt->execute($ids);
        }
        $deleted = $stmt->rowCount();
        // Extract table name for display
        preg_match('/FROM\s+(\w+)/i', $sql, $m);
        $table = $m[1] ?? 'unknown';
        echo "<p>Deleted <b>{$deleted}</b> row(s) from <code>{$table}</code>.</p>";
    }

    echo "<hr><p><b>Done.</b> All demo seed data has been removed. You can now create your own listings for testing.</p>";
} catch (Throwable $e) {
    http_response_code(500);
    echo "<p>Error: " . htmlspecialchars($e->getMessage()) . "</p>";
}
