<?php
/**
 * PolyGo+ Admin database bridge + auth/CSRF helpers.
 *
 * Connects to the SAME live `polygo` database used by the API (../config.php):
 *   host 127.0.0.1, port 3306, user root, empty password (XAMPP defaults).
 * Adjust DB_* constants below if your local MySQL differs.
 *
 * NOTE: This file is gitignored on purpose (it may hold credentials). If you
 * clone the repo, recreate it from this template. All admin pages depend on
 * the helpers defined here.
 */

if (session_status() === PHP_SESSION_NONE) {
    ini_set('session.use_strict_mode', '1');
    session_name('polygo_admin');
    $secureCookie = !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off';
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => '/polygo-api/admin',
        'secure' => $secureCookie,
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    session_start();
}

// Load the shared API credentials first so the admin panel uses the same
// production database instead of falling back to XAMPP's local defaults.
@include_once __DIR__ . '/../../secrets.php';

if (!defined('DB_HOST')) {
    define('DB_HOST', '127.0.0.1');
    define('DB_PORT', 3306);
    define('DB_NAME', 'polygo');
    define('DB_USER', 'root');
    define('DB_PASS', '');
}

// The unified Control Center logs in with the API's shared ADMIN_PASSWORD
// (../secrets.php). Keep that file OUT of the repo; if it is missing the panel
// simply refuses to authenticate until it is restored.

function getConnection(): PDO {
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }
    $dsn = 'mysql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME . ';charset=utf8mb4';
    $pdo = new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ]);
    // Moderation state for listings-reports lives OUTSIDE the app's `reports`
    // table so the Android API is never coupled to admin-only columns.
    $pdo->exec("CREATE TABLE IF NOT EXISTS admin_report_actions (
        report_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
        status ENUM('pending','resolved','dismissed') NOT NULL DEFAULT 'pending',
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $pdo->exec("CREATE TABLE IF NOT EXISTS admin_audit_log (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
        action_name VARCHAR(80) NOT NULL,
        entity_type VARCHAR(40) NOT NULL,
        entity_id BIGINT UNSIGNED NULL,
        details VARCHAR(500) NULL,
        actor_label VARCHAR(120) NOT NULL DEFAULT 'shared-admin',
        ip_address VARCHAR(45) NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_admin_audit_created (created_at),
        INDEX idx_admin_audit_entity (entity_type, entity_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    return $pdo;
}

function isAdminLoggedIn(): bool {
    $authenticated = isset($_SESSION['admin_auth']) && $_SESSION['admin_auth'] === true;
    // Legacy DB-session shape (kept transiently for bookmarks/sessions in flight).
    $authenticated = $authenticated || (isset($_SESSION['user_id'], $_SESSION['role'])
        && strtolower((string) $_SESSION['role']) === 'admin');
    if (!$authenticated) return false;
    $now = time();
    if (isset($_SESSION['last_activity']) && $now - (int)$_SESSION['last_activity'] > 1800) {
        $_SESSION = [];
        session_destroy();
        return false;
    }
    $_SESSION['last_activity'] = $now;
    return true;
}

/**
 * Admin panel password (shared Admin control center credential from secrets.php).
 * Returns null when secrets.php / ADMIN_PASSWORD is unavailable.
 */
function adminPassword(): ?string {
    return defined('ADMIN_PASSWORD') && ADMIN_PASSWORD !== '' ? (string) ADMIN_PASSWORD : null;
}

/**
 * Simple per-bucket attempt limiter backed by the API's `rate_limits` table.
 * Returns true when the bucket is over budget (request should be blocked).
 */
function adminRateLimit(PDO $pdo, string $bucket, int $maxTries = 5, int $windowSec = 300): bool {
    $stmt = $pdo->prepare(
        'INSERT INTO rate_limits (bucket, attempts, window_start) VALUES (?, 1, ?)
         ON DUPLICATE KEY UPDATE
           attempts = IF(window_start + ? < ?, 1, attempts + 1),
           window_start = IF(window_start + ? < ?, ?, window_start)'
    );
    $now = time();
    $stmt->execute([$bucket, $now, $windowSec, $now, $windowSec, $now, $now]);
    $stmt = $pdo->prepare('SELECT attempts FROM rate_limits WHERE bucket = ?');
    $stmt->execute([$bucket]);
    $attempts = (int) ($stmt->fetchColumn() ?: 0);
    return $attempts > $maxTries;
}

function csrfToken(): string {
    global $_SESSION;
    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf_token'];
}

function verifyCsrf(): bool {
    return isset($_POST['csrf_token'], $_SESSION['csrf_token'])
        && hash_equals((string) $_SESSION['csrf_token'], (string) $_POST['csrf_token']);
}

/** Records administrative decisions without storing passwords or request bodies. */
function auditAdminAction(PDO $pdo, string $action, string $entityType, ?int $entityId, string $details = ''): void {
    try {
        $actor = (string) ($_SESSION['full_name'] ?? $_SESSION['email'] ?? 'shared-admin');
        $ip = (string) ($_SERVER['REMOTE_ADDR'] ?? '');
        $stmt = $pdo->prepare('INSERT INTO admin_audit_log (action_name, entity_type, entity_id, details, actor_label, ip_address) VALUES (?, ?, ?, ?, ?, ?)');
        $stmt->execute([$action, $entityType, $entityId, mb_substr($details, 0, 500), mb_substr($actor, 0, 120), mb_substr($ip, 0, 45)]);
    } catch (Throwable $ignored) {
        // A logging outage must not accidentally grant or deny a separate action.
        error_log('[polygo-admin] audit write failed');
    }
}

function requireAdmin(): void {
    if (!isAdminLoggedIn()) {
        header('Location: login.php');
        exit();
    }
}
