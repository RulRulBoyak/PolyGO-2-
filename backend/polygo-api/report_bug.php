<?php
require_once __DIR__ . '/config.php';

$ownerId = verify_jwt();

$input = input_json();
$description = trim((string)($input['description'] ?? ''));
$deviceModel = trim((string)($input['device_model'] ?? ''));
$osVersion = trim((string)($input['os_version'] ?? ''));
$appVersion = trim((string)($input['app_version'] ?? ''));
$screen = trim((string)($input['screen'] ?? ''));
$screenshotUrl = trim((string)($input['screenshot_url'] ?? ''));

if ($description === '' || mb_strlen($description) > 5000) {
    respond(false, 'Please describe the problem (max 5000 characters)');
}

try {
    $query = $pdo->prepare(
        'INSERT INTO bug_reports (user_id, description, device_model, os_version, app_version, screen, screenshot_url)
         VALUES (?, ?, ?, ?, ?, ?, ?)'
    );
    $query->execute([$ownerId, $description, $deviceModel, $osVersion, $appVersion, $screen, $screenshotUrl]);
    respond(true, 'Bug report sent. Thank you for helping improve PolyGo+.', ['id' => (int)$pdo->lastInsertId()]);
} catch (Throwable $error) {
    error_log('[polygo-api] report_bug error: ' . $error->getMessage());
    respond(false, 'Could not submit the report. Check your connection and try again.');
}