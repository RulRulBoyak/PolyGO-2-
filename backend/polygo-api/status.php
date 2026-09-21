<?php
require_once __DIR__ . '/config.php';

// Maintenance Mode kill-switch (flipped from the Admin panel -> app_settings).
// The Android app checks this at launch (SplashActivity), on Home resume and
// while retrying ErrorStateActivity; when `maintenance` is true the app shows
// the maintenance screen and locks the main flow until the flag is cleared.
$maintenance = false;
$message = '';
$homeMessages = [];
$homeInterval = 8;

try {
    global $pdo;
    $stmt = $pdo->prepare("SELECT setting_value FROM app_settings WHERE setting_key = 'maintenance'");
    $stmt->execute();
    $maintenance = ((string)$stmt->fetchColumn()) === '1';

    $stmt = $pdo->prepare("SELECT setting_value FROM app_settings WHERE setting_key = 'maintenance_message'");
    $stmt->execute();
    $message = (string)$stmt->fetchColumn();

    $stmt = $pdo->prepare("SELECT setting_value FROM app_settings WHERE setting_key = 'home_messages'");
    $stmt->execute();
    $storedMessages = preg_split('/\R/', (string)$stmt->fetchColumn());
    $homeMessages = array_values(array_filter(array_map('trim', $storedMessages ?: [])));

    $stmt = $pdo->prepare("SELECT setting_value FROM app_settings WHERE setting_key = 'home_message_interval_seconds'");
    $stmt->execute();
    $homeInterval = max(5, min(60, (int)$stmt->fetchColumn() ?: 8));
} catch (Throwable $e) {
    error_log('[polygo-api] status.php could not read app_settings: ' . $e->getMessage());
}

respond(true, 'OK', [
    'maintenance'         => (bool)$maintenance,
    'maintenance_message' => $message !== '' ? $message : 'PolyGo+ is under maintenance while the campus database is updated. Please try again shortly.',
    'home_messages' => $homeMessages,
    'home_message_interval_seconds' => $homeInterval,
    'app'                 => 'PolyGo+',
]);
