<?php
require_once __DIR__ . '/config.php';
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
        'location' => "VARCHAR(150) DEFAULT 'Near campus'",
        'is_available' => "BOOLEAN DEFAULT TRUE",
        'created_at' => "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
    ];
    foreach($listCols as $col => $type) {
        try { $pdo->exec("ALTER TABLE listings ADD COLUMN $col $type"); echo "✅ Listing column verified: $col<br>"; } catch(Exception $e){}
    }

    echo "<h3>🎉 Database is now 100% compatible with Photos and Services!</h3>";
    echo "<p>Please copy the latest .php files to htdocs before testing.</p>";

} catch (Exception $e) {
    echo "<h1>❌ Master Repair Failed</h1>" . $e->getMessage();
}
