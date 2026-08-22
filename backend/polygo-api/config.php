<?php
declare(strict_types=1);

// Prevent PHP from outputting HTML errors
ini_set('display_errors', '0');
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Content-Type');

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

// Global Exception Handler to always return JSON
set_exception_handler(function($e) {
    echo json_encode(['success' => false, 'message' => 'PHP Error: ' . $e->getMessage()]);
    exit;
});
