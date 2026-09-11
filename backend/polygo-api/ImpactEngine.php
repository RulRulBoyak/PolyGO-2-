<?php
require_once __DIR__ . '/config.php';

/**
 * Impact Engine — Green savings (CO2 / water / paper / energy) for re-used items.
 *
 * Methodology: each completed second-hand transaction displaces the manufacture
 * of one new item (displacement rate = 1), consistent with the Adevinta
 * "Second Hand Effect" (2024) and IVL Swedish Environmental Research Institute
 * reuse studies (laptop reuse avoids roughly 270-280 kg CO2e). Coefficients below
 * are deliberately conservative, per-category, and degrade gracefully to the
 * "Other" row (or zeros) when a listing has no usable category.
 */
final class ImpactEngine {

    private const TIER_EMERALD = 500.0;
    private const TIER_GOLD = 200.0;
    private const TIER_SILVER = 50.0;

    /** Map a total CO2 figure (kg) to a tier. */
    public static function tier(float $co2Kg): string {
        if ($co2Kg >= self::TIER_EMERALD) {
            return 'emerald';
        }
        if ($co2Kg >= self::TIER_GOLD) {
            return 'gold';
        }
        if ($co2Kg >= self::TIER_SILVER) {
            return 'silver';
        }
        return 'bronze';
    }

    /** Look up savings coefficients for a listing category. Falls back to Other, then zeros. */
    public static function coefficient(PDO $pdo, string $category): array {
        static $cache = [];
        $key = strtolower(trim($category));
        if ($key === '') {
            $key = 'other';
        }
        if (isset($cache[$key])) {
            return $cache[$key];
        }

        $query = $pdo->prepare('SELECT co2_kg, water_l, paper_kg, energy_kwh FROM sustainability_coefficients WHERE LOWER(category_name) = ? LIMIT 1');
        $query->execute([$key]);
        $row = $query->fetch();

        if ($row === false && $key !== 'other') {
            $query->execute(['other']);
            $row = $query->fetch();
        }

        $cache[$key] = $row ? [
            (float)$row['co2_kg'],
            (float)$row['water_l'],
            (float)$row['paper_kg'],
            (float)$row['energy_kwh']
        ] : [0.0, 0.0, 0.0, 0.0];
        return $cache[$key];
    }

    /**
     * Credit impact for a completed transaction to BOTH buyer and seller.
     * Idempotent: impact_entries has a UNIQUE(user_id, transaction_id) key, so a
     * re-credit for an already-processed pair is silently skipped and never
     * double-counts the user totals.
     */
    public static function creditTransaction(PDO $pdo, int $transactionId): void {
        $query = $pdo->prepare('SELECT t.buyer_id, t.seller_id, COALESCE(l.category, "Other") AS category FROM transactions t LEFT JOIN listings l ON l.id = t.listing_id WHERE t.id = ?');
        $query->execute([$transactionId]);
        $transaction = $query->fetch();
        if (!$transaction) {
            return;
        }

        [$co2, $water, $paper, $energy] = self::coefficient($pdo, (string)$transaction['category']);
        $category = trim((string)$transaction['category']);
        if ($category === '') {
            $category = 'Other';
        }

        $buyerId = (int)$transaction['buyer_id'];
        $sellerId = (int)$transaction['seller_id'];

        foreach ([$buyerId, $sellerId] as $userId) {
            if ($userId <= 0) {
                continue;
            }
            $entry = $pdo->prepare('INSERT IGNORE INTO impact_entries (user_id, transaction_id, category_name, co2_kg, water_l, paper_kg, energy_kwh) VALUES (?, ?, ?, ?, ?, ?, ?)');
            $entry->execute([$userId, $transactionId, $category, $co2, $water, $paper, $energy]);

            // Do not double-count totals when this pair was already credited.
            if ($entry->rowCount() > 0) {
                $upsert = $pdo->prepare('INSERT INTO user_impact (user_id, co2_kg, water_l, paper_kg, energy_kwh, impact_count)
                    VALUES (?, ?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                        co2_kg = co2_kg + VALUES(co2_kg),
                        water_l = water_l + VALUES(water_l),
                        paper_kg = paper_kg + VALUES(paper_kg),
                        energy_kwh = energy_kwh + VALUES(energy_kwh),
                        impact_count = impact_count + 1,
                        updated_at = CURRENT_TIMESTAMP');
                $upsert->execute([$userId, $co2, $water, $paper, $energy]);
            }
        }

        self::refreshTiers($pdo, $buyerId, $sellerId);
    }

    /** Recompute tier from the current stored CO2 totals. */
    public static function refreshTiers(PDO $pdo, int ...$userIds): void {
        $ids = implode(',', array_map('intval', $userIds));
        if ($ids === '') {
            return;
        }
        $pdo->exec("UPDATE user_impact SET tier = CASE
            WHEN co2_kg >= 500 THEN 'emerald'
            WHEN co2_kg >= 200 THEN 'gold'
            WHEN co2_kg >= 50 THEN 'silver'
            ELSE 'bronze' END
            WHERE user_id IN ($ids)");
    }

    /** Aggregated metrics + rank for a single user. */
    public static function metrics(PDO $pdo, int $userId): array {
        $query = $pdo->prepare('SELECT * FROM user_impact WHERE user_id = ?');
        $query->execute([$userId]);
        $row = $query->fetch();

        $metrics = [
            'co2' => 0,
            'water' => 0,
            'paper' => 0,
            'energy' => 0,
            'count' => 0,
            'tier' => 'bronze',
            'rank' => 0
        ];

        if ($row) {
            $metrics['co2'] = round((float)$row['co2_kg'], 2);
            $metrics['water'] = round((float)$row['water_l'], 0);
            $metrics['paper'] = round((float)$row['paper_kg'], 2);
            $metrics['energy'] = round((float)$row['energy_kwh'], 1);
            $metrics['count'] = (int)$row['impact_count'];
            $metrics['tier'] = !empty($row['tier']) ? $row['tier'] : self::tier((float)$row['co2_kg']);

            $rank = $pdo->prepare('SELECT COUNT(*) + 1 FROM user_impact WHERE co2_kg > ?');
            $rank->execute([(float)$row['co2_kg']]);
            $metrics['rank'] = (int)$rank->fetchColumn();
        }
        return $metrics;
    }

    /** Per-category breakdown for a user, most impactful first. */
    public static function breakdown(PDO $pdo, int $userId): array {
        $query = $pdo->prepare('SELECT category_name AS category, COUNT(*) AS items,
            ROUND(SUM(co2_kg), 2) AS co2, ROUND(SUM(water_l), 0) AS water,
            ROUND(SUM(paper_kg), 2) AS paper, ROUND(SUM(energy_kwh), 1) AS energy
            FROM impact_entries WHERE user_id = ? GROUP BY category_name ORDER BY co2 DESC');
        $query->execute([$userId]);
        return $query->fetchAll();
    }

    /** Top users by impact. Single query, no N+1. */
    public static function leaderboard(PDO $pdo, int $limit = 20): array {
        $query = $pdo->prepare('SELECT u.id AS user_id, u.full_name AS name,
            ui.co2_kg AS co2, ui.water_l AS water, ui.paper_kg AS paper, ui.energy_kwh AS energy,
            ui.impact_count AS count, ui.tier
            FROM user_impact ui
            JOIN users u ON u.id = ui.user_id
            WHERE ui.co2_kg > 0
            ORDER BY ui.co2_kg DESC, ui.impact_count DESC
            LIMIT ?');
        $query->execute([$limit]);
        $rows = $query->fetchAll();

        foreach ($rows as &$row) {
            $row['co2'] = round((float)$row['co2'], 2);
            $row['water'] = round((float)$row['water'], 0);
            $row['paper'] = round((float)$row['paper'], 2);
            $row['energy'] = round((float)$row['energy'], 1);
            $row['count'] = (int)$row['count'];
        }
        return $rows;
    }
}