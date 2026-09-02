<?php
declare(strict_types=1);

// Prevent PHP from outputting HTML errors
ini_set('display_errors', '0');
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// JWT Configuration
define('JWT_SECRET', 'polygo_pks_secure_key_2026'); // In real production, this would be in an environment variable
define('TOKEN_EXPIRY', 2592000); // 30 days in seconds

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
    echo json_encode(['success' => false, 'message' => 'Database connection failed: ' . $error->getMessage()]);
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
function verify_jwt(): int {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';

    if (!preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        respond(false, 'Unauthorized: Missing or malformed token');
    }

    $jwt = $matches[1];
    $tokenParts = explode('.', $jwt);
    if (count($tokenParts) !== 3) respond(false, 'Unauthorized: Invalid token format');

    $header = $tokenParts[0];
    $payload = $tokenParts[1];
    $signature = $tokenParts[2];

    // Verify Signature
    $validSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode(hash_hmac('sha256', $header . "." . $payload, JWT_SECRET, true)));
    if ($signature !== $validSignature) respond(false, 'Unauthorized: Invalid token signature');

    $data = json_decode(base64_decode(str_replace(['-', '_'], ['+', '/'], $payload)), true);
    if (($data['exp'] ?? 0) < time()) respond(false, 'Unauthorized: Token expired. Please login again.');

    return (int)$data['user_id'];
}

// Global Exception Handler to always return JSON
set_exception_handler(function($e) {
    echo json_encode(['success' => false, 'message' => 'PHP Error: ' . $e->getMessage()]);
    exit;
});
