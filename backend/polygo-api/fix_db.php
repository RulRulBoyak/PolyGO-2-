<?php
// Maintenance script: only runnable from the local machine (or the Android emulator host bridge).
$maintenanceClient = $_SERVER['REMOTE_ADDR'] ?? '';
if (!in_array($maintenanceClient, ['127.0.0.1', '::1', '10.0.2.2'], true)) {
    http_response_code(403);
    echo '<h1>Forbidden</h1><p>This maintenance script may only be run locally.</p>';
    exit;
}
require_once __DIR__ . '/config.php';
header('Content-Type: text/html; charset=utf-8');
try {
    echo "<h1>🚀 PolyGo+ Database Master Repair</h1>";

    // 1. Repair Users Table (Add profile_pic_url)
    $pdo->exec("CREATE TABLE IF NOT EXISTS users (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY)");
    $userCols = [
        'full_name' => "VARCHAR(120)",
        'student_id' => "VARCHAR(50) UNIQUE",
        'email' => "VARCHAR(160) UNIQUE",
        'mobile' => "VARCHAR(30)",
        'password_hash' => "VARCHAR(255)",
        'role' => "VARCHAR(20) DEFAULT 'Student'",
        'profile_pic_url' => "VARCHAR(500)",
        'fcm_token' => "VARCHAR(500)", // Added for Phase 2: Push Notifications
        'is_verified' => "BOOLEAN DEFAULT FALSE",
        'created_at' => "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
    ];
    foreach($userCols as $col => $type) {
        try { $pdo->exec("ALTER TABLE users ADD COLUMN $col $type"); echo "✅ User column verified: $col<br>"; } catch(Exception $e){}
    }

    // 2. Repair Listings Table (Ensure image_url exists for products & services)
    $pdo->exec("CREATE TABLE IF NOT EXISTS listings (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY)");
    $listCols = [
        'owner_id' => "BIGINT UNSIGNED NOT NULL",
        'title' => "VARCHAR(150) NOT NULL",
        'category' => "VARCHAR(80) NOT NULL",
        'description' => "TEXT NOT NULL",
        'price' => "VARCHAR(50) NOT NULL", // Changed to VARCHAR to support "Starts at RM 50"
        'image_url' => "VARCHAR(500)",
        'tags' => "VARCHAR(255) NOT NULL DEFAULT ''",
        'location' => "VARCHAR(150) DEFAULT 'Near campus'",
        'is_available' => "BOOLEAN DEFAULT TRUE",
        'created_at' => "TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
        'updated_at' => "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
    ];
    foreach($listCols as $col => $type) {
        try { $pdo->exec("ALTER TABLE listings ADD COLUMN $col $type"); echo "✅ Listing column verified: $col<br>"; } catch(Exception $e){}
    }

    // 2b. Repair Reviews Table (Add listing_id for per-listing reviews)
    $pdo->exec("CREATE TABLE IF NOT EXISTS reviews (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY)");
    $reviewCols = [
        'seller_id' => "BIGINT UNSIGNED NOT NULL DEFAULT 0",
        'listing_id' => "BIGINT UNSIGNED NOT NULL DEFAULT 0",
        'reviewer_id' => "BIGINT UNSIGNED NOT NULL",
        'reviewer_name' => "VARCHAR(120) NOT NULL",
        'stars' => "TINYINT UNSIGNED NOT NULL",
        'comment' => "TEXT",
        'created_at' => "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
    ];
    foreach($reviewCols as $col => $type) {
        try { $pdo->exec("ALTER TABLE reviews ADD COLUMN $col $type"); echo "✅ Review column verified: $col<br>"; } catch(Exception $e){}
    }

    // 3. Repair Green Impact Tables
    $pdo->exec("CREATE TABLE IF NOT EXISTS transactions (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY)");
    try { $pdo->exec("ALTER TABLE transactions ADD COLUMN impact_credited BOOLEAN NOT NULL DEFAULT FALSE"); echo "✅ Transactions column verified: impact_credited<br>"; } catch(Exception $e){}

    $pdo->exec("CREATE TABLE IF NOT EXISTS sustainability_coefficients (
        id INT AUTO_INCREMENT PRIMARY KEY,
        category_name VARCHAR(50) NOT NULL UNIQUE,
        co2_kg DECIMAL(8,2) NOT NULL DEFAULT 0,
        water_l DECIMAL(10,2) NOT NULL DEFAULT 0,
        paper_kg DECIMAL(8,2) NOT NULL DEFAULT 0,
        energy_kwh DECIMAL(8,2) NOT NULL DEFAULT 0
    )");
    echo "✅ Table verified: sustainability_coefficients<br>";

    $pdo->exec("CREATE TABLE IF NOT EXISTS user_impact (
        user_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
        co2_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
        water_l DECIMAL(12,2) NOT NULL DEFAULT 0,
        paper_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
        energy_kwh DECIMAL(10,2) NOT NULL DEFAULT 0,
        impact_count INT UNSIGNED NOT NULL DEFAULT 0,
        tier ENUM('bronze','silver','gold','emerald') NOT NULL DEFAULT 'bronze',
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    )");
    echo "✅ Table verified: user_impact<br>";

    $pdo->exec("CREATE TABLE IF NOT EXISTS impact_entries (
        id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT UNSIGNED NOT NULL,
        transaction_id BIGINT UNSIGNED NOT NULL,
        category_name VARCHAR(50) NOT NULL,
        co2_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
        water_l DECIMAL(12,2) NOT NULL DEFAULT 0,
        paper_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
        energy_kwh DECIMAL(10,2) NOT NULL DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_user_tx (user_id, transaction_id)
    )");
    echo "✅ Table verified: impact_entries<br>";
    // Idempotency guard for already-created tables.
    try { $pdo->exec("ALTER TABLE impact_entries ADD UNIQUE KEY uq_user_tx (user_id, transaction_id)"); } catch (Exception $ignored) {}

    // Rate limiter backing table + OTP verification storage.
    $pdo->exec("CREATE TABLE IF NOT EXISTS rate_limits (
        bucket VARBINARY(255) NOT NULL PRIMARY KEY,
        attempts INT UNSIGNED NOT NULL DEFAULT 0,
        window_start INT UNSIGNED NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    echo "✅ Table verified: rate_limits<br>";

    $pdo->exec("CREATE TABLE IF NOT EXISTS verification_codes (
        email VARCHAR(160) NOT NULL PRIMARY KEY,
        code_hash VARCHAR(255) NOT NULL,
        expires_at DATETIME NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    echo "✅ Table verified: verification_codes<br>";

    $seed = $pdo->prepare("INSERT INTO sustainability_coefficients (category_name, co2_kg, water_l, paper_kg, energy_kwh) VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE co2_kg = VALUES(co2_kg), water_l = VALUES(water_l), paper_kg = VALUES(paper_kg), energy_kwh = VALUES(energy_kwh)");
    $coefficients = [
        ['Food', 3.0, 120, 0.2, 4],
        ['Drink', 2.0, 90, 0.1, 3],
        ['Tech', 45.0, 280, 1.5, 75],
        ['Electronics', 55.0, 350, 1.5, 80],
        ['Fashion', 14.0, 1400, 0.1, 20],
        ['Books', 6.0, 30, 3.0, 8],
        ['Repair', 8.0, 60, 0.3, 12],
        ['Home', 20.0, 180, 2.5, 35],
        ['Laundry', 4.0, 150, 0.1, 5],
        ['Delivery', 3.0, 40, 0.3, 5],
        ['Services', 0, 0, 0, 0],
        ['Other', 5.0, 80, 0.5, 8]
    ];
    foreach ($coefficients as $coef) {
        $seed->execute($coef);
    }
    echo "✅ Seed: sustainability_coefficients (" . count($coefficients) . " rows)<br>";

    echo "<h3>🎉 Database is now 100% compatible with Photos and Services!</h3>";
    echo "<p>Please copy the latest .php files to htdocs before testing.</p>";

    // Restore legacy categories table (dropped during 2026-09 rebuild; needed by categories.php & search spinner)
    $pdo->exec("CREATE TABLE IF NOT EXISTS categories (
        id INT AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(50) NOT NULL UNIQUE,
        icon_res VARCHAR(50) DEFAULT 'ic_category_default',
        is_published TINYINT(1) DEFAULT 1,
        proposed_by BIGINT UNSIGNED,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )");
    $cat = $pdo->prepare("INSERT IGNORE INTO categories (name, icon_res, is_published) VALUES (?, ?, 1)");
    $defaults = [
        ['Food','ic_category_food'],['Drink','ic_category_drink'],['Tech','ic_category_tech'],
        ['Electronics','ic_category_electronics'],['Fashion','ic_category_fashion'],['Books','ic_category_books'],
        ['Repair','ic_category_repair'],['Home','ic_category_home'],['Laundry','ic_category_laundry'],
        ['Delivery','ic_category_delivery'],['Services','ic_category_services']
    ];
    foreach ($defaults as $row) $cat->execute($row);
    echo "<br>✅ Restored: categories (" . count($defaults) . " rows)";

} catch (Exception $e) {
    echo "<h1>❌ Master Repair Failed</h1>" . htmlspecialchars((string)$e->getMessage());
}
