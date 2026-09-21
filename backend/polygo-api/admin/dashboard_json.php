<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
header('Content-Type: application/json; charset=utf-8');

function dbInt2(PDO $pdo, string $sql): int {
    try {
        return (int) $pdo->query($sql)->fetchColumn();
    } catch (Throwable $e) {
        return 0;
    }
}

// Build a zero-filled 14-day series from a "SELECT DATE(...) d, COUNT/SUM(...) c ..." query.
function dailySeries(PDO $pdo, string $sql, array $dayMap, int $n): array {
    $out = array_fill(0, $n, 0);
    try {
        foreach ($pdo->query($sql)->fetchAll() as $row) {
            if (isset($dayMap[$row['d']])) {
                $out[$dayMap[$row['d']]] = (float) $row['c'];
            }
        }
    } catch (Throwable $e) {
        // Missing table (pre-migration) -> zeroes.
    }
    return $out;
}

// --- Stat totals (mirror index.php cards) ---
// Each completed deal credits BOTH counterparties (ImpactEngine), so impact_entries has
// 2 rows per transaction. Green totals/series dedupe to one row per transaction (MAX =
// the credited values) so a deal is never double-counted platform-wide.
$greenTotalsSql = "SELECT MAX(co2_kg) AS co2_kg, MAX(water_l) AS water_l FROM impact_entries GROUP BY transaction_id";

$stats = [
    'total_users'          => dbInt2($pdo, "SELECT COUNT(*) FROM users WHERE role != 'admin'"),
    'banned_users'         => dbInt2($pdo, "SELECT COUNT(*) FROM users WHERE role != 'admin' AND is_banned = 1"),
    'pending_verification' => dbInt2($pdo, "SELECT COUNT(*) FROM users WHERE role != 'admin' AND verification_status = 'pending'"),
    'total_listings'       => dbInt2($pdo, "SELECT COUNT(*) FROM listings WHERE archived_at IS NULL"),
    'active_listings'      => dbInt2($pdo, "SELECT COUNT(*) FROM listings WHERE is_available = 1 AND archived_at IS NULL"),
    'sold_listings'        => dbInt2($pdo, "SELECT COUNT(*) FROM listings WHERE is_available = 0 AND archived_at IS NULL"),
    'removed_listings'     => dbInt2($pdo, "SELECT COUNT(*) FROM listings WHERE archived_at IS NOT NULL"),
    'pending_reports'      => dbInt2($pdo, "SELECT COUNT(*) FROM reports r LEFT JOIN admin_report_actions a ON a.report_id = r.id WHERE r.target_type = 'listing' AND COALESCE(a.status, 'pending') = 'pending'"),
    'pending_pulse'        => dbInt2($pdo, "SELECT COUNT(*) FROM campus_alerts WHERE status = 'pending'"),
    'co2_saved'            => dbInt2($pdo, "SELECT ROUND(COALESCE(SUM(co2_kg), 0)) FROM ($greenTotalsSql) g"),
    'water_saved'          => dbInt2($pdo, "SELECT ROUND(COALESCE(SUM(water_l), 0)) FROM ($greenTotalsSql) g"),
];

// --- 14-day activity series ---
$days = [];
$dayMap = [];
for ($i = 13; $i >= 0; $i--) {
    $d = date('Y-m-d', strtotime("-$i days"));
    $days[] = $d;
    $dayMap[$d] = 13 - $i;
}
$since = date('Y-m-d', strtotime('-13 days'));

$activity = [
    'days'     => $days,
    'users'    => dailySeries($pdo, "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM users WHERE role != 'admin' AND created_at >= '$since' GROUP BY DATE(created_at)", $dayMap, 14),
    'listings' => dailySeries($pdo, "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM listings WHERE created_at >= '$since' GROUP BY DATE(created_at)", $dayMap, 14),
    'deals'    => dailySeries($pdo, "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM transactions WHERE status = 'completed' AND created_at >= '$since' GROUP BY DATE(created_at)", $dayMap, 14),
    'co2'      => dailySeries($pdo, "SELECT DATE(mc) AS d, COALESCE(SUM(co2_kg), 0) AS c FROM (SELECT MAX(co2_kg) AS co2_kg, MIN(created_at) AS mc FROM impact_entries WHERE created_at >= '$since' GROUP BY transaction_id) g GROUP BY DATE(mc)", $dayMap, 14),
];

// --- Verification pipeline (non-admin users) ---
$verification = ['unverified' => 0, 'pending' => 0, 'approved' => 0, 'rejected' => 0];
try {
    $stmt = $pdo->query("SELECT verification_status, COUNT(*) AS c FROM users WHERE role != 'admin' GROUP BY verification_status");
    foreach ($stmt->fetchAll() as $row) {
        $vs = (string) ($row['verification_status'] ?? '');
        if ($vs === '' || !array_key_exists($vs, $verification)) {
            $vs = 'unverified';
        }
        $verification[$vs] = $verification[$vs] + (int) $row['c'];
    }
} catch (Throwable $e) {
    // Pre-migration -> all zero.
}

// --- Green impact by category (top 5) ---
$impactByCategory = [];
try {
    $stmt = $pdo->query("SELECT category_name, ROUND(SUM(co2_kg), 2) AS co2, ROUND(SUM(water_l), 2) AS water
        FROM (SELECT category_name, MAX(co2_kg) AS co2_kg, MAX(water_l) AS water_l
              FROM impact_entries GROUP BY transaction_id, category_name) g
        GROUP BY category_name ORDER BY co2 DESC, category_name ASC LIMIT 5");
    foreach ($stmt->fetchAll() as $row) {
        $impactByCategory[] = [
            'category' => (string) $row['category_name'],
            'co2'      => (float) $row['co2'],
            'water'    => (float) $row['water'],
        ];
    }
} catch (Throwable $e) {
    // Pre-migration -> empty.
}

echo json_encode([
    'fetched_at'        => time(),
    'stats'             => $stats,
    'activity'          => $activity,
    'listing_status'    => [
        'active'  => $stats['active_listings'],
        'sold'    => $stats['sold_listings'],
        'removed' => $stats['removed_listings'],
    ],
    'verification'      => $verification,
    'impact_by_category' => $impactByCategory,
]);