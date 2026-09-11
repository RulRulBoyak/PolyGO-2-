<?php
declare(strict_types=1);

// Prevent PHP from outputting HTML errors
ini_set('display_errors', '0');
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');

// CORS: allow known dev origins; Android native app sends no Origin header.
$allowedOrigins = ['http://localhost', 'http://127.0.0.1', 'http://10.0.2.2'];
$requestOrigin = $_SERVER['HTTP_ORIGIN'] ?? '';
if ($requestOrigin === '' || in_array($requestOrigin, $allowedOrigins, true)) {
    header('Access-Control-Allow-Origin: ' . ($requestOrigin === '' ? '*' : $requestOrigin));
}

// JWT Configuration — prefer gitignored secrets.php, fall back to a dev-only value.
$secretsFile = __DIR__ . '/secrets.php';
if (is_file($secretsFile)) {
    require_once $secretsFile;
}
if (!defined('JWT_SECRET')) {
    define('JWT_SECRET', 'polygo_dev_fallback_change_me_before_production');
}
define('TOKEN_EXPIRY', 604800); // 7 days in seconds

$host = '127.0.0.1';
$port = 3306;
$database = 'polygo';
$username = 'root';
$password = '';

try {
    $pdo = new PDO(
        "mysql:host=$host;port=$port;dbname=$database;charset=utf8mb4",
        $username,
        $password,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false
        ]
    );
} catch (Throwable $error) {
    error_log('[polygo-api] DB connection failed: ' . $error->getMessage());
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Database connection failed']);
    exit;
}

function input_json(): array {
    $body = json_decode(file_get_contents('php://input'), true);
    return is_array($body) ? $body : [];
}

function respond(bool $success, string $message, array $extra = []): void {
    echo json_encode(array_merge(['success' => $success, 'message' => $message], $extra));
    exit;
}

/**
 * Lightweight JWT-style Token Generator
 */
function create_jwt(int $userId): string {
    $header = json_encode(['alg' => 'HS256', 'typ' => 'JWT']);
    $payload = json_encode([
        'user_id' => $userId,
        'exp' => time() + TOKEN_EXPIRY
    ]);

    $base64UrlHeader = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($header));
    $base64UrlPayload = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($payload));

    $signature = hash_hmac('sha256', $base64UrlHeader . "." . $base64UrlPayload, JWT_SECRET, true);
    $base64UrlSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));

    return $base64UrlHeader . "." . $base64UrlPayload . "." . $base64UrlSignature;
}

/**
 * Professional Token Verification
 */
function verify_jwt(bool $exitOnFailure = true): int {
    // App Check gate for authenticated write endpoints. Enforcement is opt-in
    // via APP_CHECK_ENFORCE (see secrets.php); defaults to pass-through for dev.
    if ($exitOnFailure && defined('APP_CHECK_ENFORCE') && APP_CHECK_ENFORCE) {
        verify_app_check(true);
    }

    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';

    if (!preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        if ($exitOnFailure) respond(false, 'Unauthorized: Missing or malformed token');
        return 0;
    }

    $jwt = $matches[1];
    $tokenParts = explode('.', $jwt);
    if (count($tokenParts) !== 3) {
        if ($exitOnFailure) respond(false, 'Unauthorized: Invalid token format');
        return 0;
    }

    $header = $tokenParts[0];
    $payload = $tokenParts[1];
    $signature = $tokenParts[2];

    // Verify Signature (timing-safe comparison)
    $validSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode(hash_hmac('sha256', $header . "." . $payload, JWT_SECRET, true)));
    if (!hash_equals($validSignature, $signature)) {
        if ($exitOnFailure) respond(false, 'Unauthorized: Invalid token signature');
        return 0;
    }

    $data = json_decode(base64_decode(str_replace(['-', '_'], ['+', '/'], $payload)), true);
    if (($data['exp'] ?? 0) < time()) {
        if ($exitOnFailure) respond(false, 'Unauthorized: Token expired. Please login again.');
        return 0;
    }

    return (int)$data['user_id'];
}

/**
 * Optional token verification for public endpoints. Returns the user id when a
 * valid bearer token is present, otherwise 0 (treat as guest). Never exits.
 */
function verify_jwt_optional(): int {
    return verify_jwt(false);
}

/**
 * Firebase App Check verification for the X-Firebase-AppCheck JWT sent on write
 * requests. Verifies the RS256 signature against Firebase's public JWKS, plus
 * header/issuer/expiry/audience claims. Enforcement is opt-in via the
 * APP_CHECK_ENFORCE constant (secrets.php); when disabled the helper logs and
 * passes through so local curl/phpMyAdmin workflows keep working.
 */
function verify_app_check(bool $enforce): bool
{
    $headers = getallheaders();
    $token = $headers['X-Firebase-AppCheck'] ?? $headers['x-firebase-appcheck'] ?? '';

    if ($token === '') {
        error_log('[polygo-api] App Check: missing token');
        if ($enforce) respond(false, 'Unauthorized: Missing App Check token');
        return false;
    }

    $parts = explode('.', $token);
    if (count($parts) !== 3) {
        error_log('[polygo-api] App Check: malformed token');
        if ($enforce) respond(false, 'Unauthorized: Invalid App Check token');
        return false;
    }

    $header = json_decode(base64url_decode($parts[0]), true);
    $payload = json_decode(base64url_decode($parts[1]), true);
    if (($header['alg'] ?? '') !== 'RS256' || ($header['typ'] ?? '') !== 'JWT') {
        error_log('[polygo-api] App Check: bad header');
        if ($enforce) respond(false, 'Unauthorized: Invalid App Check token header');
        return false;
    }

    if (isset($payload['exp']) && (int)$payload['exp'] < time()) {
        error_log('[polygo-api] App Check: expired token');
        if ($enforce) respond(false, 'Unauthorized: App Check token expired');
        return false;
    }
    if (isset($payload['nbf']) && (int)$payload['nbf'] > time()) {
        error_log('[polygo-api] App Check: token not yet valid');
        if ($enforce) respond(false, 'Unauthorized: App Check token not yet valid');
        return false;
    }

    if (!isset($payload['iss']) || strpos((string)$payload['iss'], 'https://firebaseappcheck.googleapis.com/') !== 0) {
        error_log('[polygo-api] App Check: invalid issuer');
        if ($enforce) respond(false, 'Unauthorized: Invalid App Check issuer');
        return false;
    }

    if (defined('FIREBASE_PROJECT_NUMBER') && FIREBASE_PROJECT_NUMBER !== ''
        && (int)($payload['aud'] ?? 0) !== (int)FIREBASE_PROJECT_NUMBER) {
        error_log('[polygo-api] App Check: audience mismatch');
        if ($enforce) respond(false, 'Unauthorized: App Check audience mismatch');
        return false;
    }

    $jwks = app_check_jwks();
    if ($jwks === null) {
        // Firebase JWKS unreachable from this host — fail open to keep the API
        // usable when the XAMPP machine has no network path to Google.
        error_log('[polygo-api] App Check: JWKS unavailable; skipping signature check');
        return true;
    }

    $matchedKey = null;
    foreach (($jwks['keys'] ?? []) as $key) {
        if (($key['kid'] ?? '') === ($header['kid'] ?? '') && ($key['kty'] ?? '') === 'RSA') {
            $matchedKey = $key;
            break;
        }
    }
    if ($matchedKey === null) {
        error_log('[polygo-api] App Check: no JWK matches token kid');
        if ($enforce) respond(false, 'Unauthorized: Invalid App Check token key');
        return false;
    }

    if (!app_check_verify_signature($matchedKey, $parts[0] . '.' . $parts[1], base64url_decode($parts[2]))) {
        error_log('[polygo-api] App Check: bad signature');
        if ($enforce) respond(false, 'Unauthorized: Invalid App Check token signature');
        return false;
    }

    return true;
}

function app_check_jwks(): ?array
{
    $cacheFile = sys_get_temp_dir() . '/polygo_appcheck_jwks.json';
    $jwks = null;
    if (is_file($cacheFile) && (time() - (int)filemtime($cacheFile)) < 3600) {
        $jwks = json_decode((string)file_get_contents($cacheFile), true);
    }
    if ($jwks === null) {
        $ch = curl_init('https://firebaseappcheck.googleapis.com/v1/jwks');
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 8,
            CURLOPT_SSL_VERIFYPEER => false,
        ]);
        $raw = curl_exec($ch);
        curl_close($ch);
        if ($raw !== false) {
            $jwks = json_decode($raw, true);
            if ($jwks !== null) {
                @file_put_contents($cacheFile, $raw);
            }
        }
    }
    return is_array($jwks) ? $jwks : null;
}

/**
 * RS256 signature verification implemented with bcmath only — no openssl
 * dependency, so it runs unchanged on XAMPP regardless of OpenSSL config.
 * Implements EMSA-PKCS1-v1_5 with SHA-256: m = s^e mod n, then the padded
 * DigestInfo must match the message digest.
 */
function app_check_verify_signature(array $jwk, string $message, string $signature): bool
{
    $modulus = ltrim(base64url_decode((string)$jwk['n']), "\x00");
    $exponent = ltrim(base64url_decode((string)$jwk['e']), "\x00");
    if ($modulus === '' || $exponent === '') {
        return false;
    }

    $modulusInt = bc_from_bytes($modulus);
    $exponentInt = bc_from_bytes($exponent);

    $messageRep = bc_to_bytes(bcpowmod(bc_from_bytes($signature), $exponentInt, $modulusInt), strlen($modulus));

    $digestInfo = hex2bin('3031300d060960864801650304020105000420');
    $encoded = $digestInfo . hash('sha256', $message, true);
    $paddingLen = strlen($modulus) - strlen($encoded) - 3;
    if ($paddingLen < 8) {
        return false;
    }
    $expected = "\x00\x01" . str_repeat("\xff", $paddingLen) . "\x00" . $encoded;
    return hash_equals($expected, $messageRep);
}

function base64url_decode(string $data): string
{
    return base64_decode(strtr($data, '-_', '+/'), true) ?: '';
}

function bc_from_bytes(string $bytes): string
{
    $result = '0';
    for ($i = 0, $len = strlen($bytes); $i < $len; $i++) {
        $result = bcadd(bcmul($result, '256'), (string)ord($bytes[$i]));
    }
    return $result;
}

function bc_to_bytes(string $decimal, int $length): string
{
    $bytes = '';
    while (bccomp($decimal, '0') > 0) {
        $bytes .= chr((int)bcmod($decimal, '256'));
        $decimal = bcdiv($decimal, '256');
    }
    return str_pad(strrev($bytes), $length, "\x00", STR_PAD_LEFT);
}

// Global Exception Handler to always return JSON (never leak internals to clients)
set_exception_handler(function($e) {
    error_log('[polygo-api] ' . $e->getMessage());
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Server error']);
    exit;
});

/**
 * Simple sliding-window rate limiter. Returns true when the limit is NOT exceeded
 * and records the attempt; returns false when the caller has exceeded the window.
 */
function rate_limit_check(PDO $pdo, string $bucket, int $maxAttempts, int $windowSeconds): bool {
    try {
        return do_rate_limit_check($pdo, $bucket, $maxAttempts, $windowSeconds);
    } catch (PDOException $error) {
        // Self-heal if the rate_limits table does not exist yet (pre-migration).
        if ($error->getCode() === '42S02') {
            $pdo->exec('CREATE TABLE IF NOT EXISTS rate_limits (
                bucket VARBINARY(255) NOT NULL PRIMARY KEY,
                attempts INT UNSIGNED NOT NULL DEFAULT 0,
                window_start INT UNSIGNED NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
            try {
                return do_rate_limit_check($pdo, $bucket, $maxAttempts, $windowSeconds);
            } catch (PDOException $retryError) {
                error_log('[polygo-api] rate_limit_check failed: ' . $retryError->getMessage());
                return true;
            }
        }
        error_log('[polygo-api] rate_limit_check failed: ' . $error->getMessage());
        return true;
    }
}

function do_rate_limit_check(PDO $pdo, string $bucket, int $maxAttempts, int $windowSeconds): bool {
    $query = $pdo->prepare('SELECT attempts, window_start FROM rate_limits WHERE bucket = ?');
    $query->execute([$bucket]);
    $row = $query->fetch();
    $now = time();

    if (!$row || (int)$row['window_start'] + $windowSeconds < $now) {
        $upsert = $pdo->prepare('INSERT INTO rate_limits (bucket, attempts, window_start) VALUES (?, 1, ?)
            ON DUPLICATE KEY UPDATE attempts = 1, window_start = VALUES(window_start)');
        $upsert->execute([$bucket, $now]);
        return true;
    }

    if ((int)$row['attempts'] >= $maxAttempts) {
        return false;
    }

    $update = $pdo->prepare('UPDATE rate_limits SET attempts = attempts + 1 WHERE bucket = ?');
    $update->execute([$bucket]);
    return true;
}

/**
 * Create a cover-cropped JPEG thumbnail from an image on disk (GD required).
 * Returns true on success, false when GD is missing or the image cannot load.
 * The source MIME is used to pick the correct GD decoder ($mime: image/jpeg|png|gif).
 */
function makeThumbnail(string $sourcePath, string $targetPath, string $mime, int $size): bool {
    if (!function_exists('imagecreatetruecolor')) {
        return false;
    }

    try {
        $source = null;
        if ($mime === 'image/png') {
            $source = @imagecreatefrompng($sourcePath);
        } elseif ($mime === 'image/gif') {
            $source = @imagecreatefromgif($sourcePath);
        } else {
            $source = @imagecreatefromjpeg($sourcePath);
        }
        if ($source === false) {
            return false;
        }

        $width = imagesx($source);
        $height = imagesy($source);
        if ($width <= 0 || $height <= 0) {
            imagedestroy($source);
            return false;
        }

        // Center-crop the larger dimension so the thumb is exactly $size x $size.
        $sourceRatio = $width / $height;
        if ($sourceRatio >= 1.0) {
            $cropSize = $height;
            $srcX = (int)(($width - $cropSize) / 2);
            $srcY = 0;
        } else {
            $cropSize = $width;
            $srcX = 0;
            $srcY = (int)(($height - $cropSize) / 2);
        }

        $thumb = imagecreatetruecolor($size, $size);
        imagecopyresampled($thumb, $source, 0, 0, $srcX, $srcY, $size, $size, $cropSize, $cropSize);
        $targetDir = dirname($targetPath);
        if (!is_dir($targetDir)) {
            mkdir($targetDir, 0777, true);
        }
        $ok = imagejpeg($thumb, $targetPath, 82);

        imagedestroy($thumb);
        imagedestroy($source);
        return $ok;
    } catch (Throwable $error) {
        error_log('[polygo-api] thumbnail failed: ' . $error->getMessage());
        return false;
    }
}
