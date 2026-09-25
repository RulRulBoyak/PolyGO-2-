<?php
/**
 * Dev diagnostic: sends a test push to the requesting user's own stored token
 * and returns the raw FCM HTTP code + response body so delivery failures
 * (HTTP v1 API not enabled, wrong IAM role, bad service-account, …) surface
 * immediately instead of failing silently.
 *
 * POST debug_push.php  { "action": "test_push" }
 * Requires: Authorization: Bearer <jwt>
 * Guard: local requests only (127.0.0.1/::1/10.0.2.2) + APP_ENV !== 'prod'
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/NotificationManager.php';

$remoteAddr = $_SERVER['REMOTE_ADDR'] ?? '';
if (!is_dev_request() || !in_array($remoteAddr, ['127.0.0.1', '::1', '10.0.2.2'], true)) {
    http_response_code(403);
    echo json_encode(['success' => false, 'message' => 'debug_push is for local dev only']);
    exit;
}

if (defined('APP_ENV') && APP_ENV === 'prod') {
    http_response_code(403);
    echo json_encode(['success' => false, 'message' => 'debug_push is disabled in production']);
    exit;
}

$userId = verify_jwt();
if ($userId <= 0) {
    respond(false, 'Unauthorized');
}

$input = input_json();
$action = $input['action'] ?? 'test_push';

if ($action !== 'test_push') {
    respond(false, 'Unknown action.  Supported: test_push');
}

$title = 'PolyGo+ Test Push';
$body = 'This is a diagnostic push sent by debug_push.php.  If you can see this, notifications are working!';
$result = NotificationManager::sendDiagnostic(
    $pdo,
    $userId,
    $title,
    $body,
    ['type' => 'test_push']
);

respond(
    $result['ok'],
    $result['ok'] ? 'Push delivered' : 'Push failed — see body for details',
    [
        'had_token' => $result['had_token'],
        'http_code' => $result['http_code'],
        'fcm_body'  => $result['body'],
        'hint'      => $result['ok']
            ? null
            : 'Common causes: (1) Cloud Messaging API (HTTP v1) not enabled in Firebase/Google Cloud — '
              . 'open https://console.developers.google.com/apis/api/fcm.googleapis.com/overview and enable it.  '
              . '(2) Service account lacks the "Firebase Cloud Messaging API Admin" role — go to IAM & Admin > IAM '
              . 'and grant it.  (3) service-account.json belongs to a different project than the app.'
    ]
);
