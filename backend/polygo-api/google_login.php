<?php
require_once __DIR__ . '/config.php';

// The OAuth 2.0 Web Client ID for your Firebase/Google Cloud project.
// Provide it locally via backend/polygo-api/secrets/google_web_client_id.php
// (gitignored) or the GOOGLE_WEB_CLIENT_ID environment variable. While empty,
// the token audience check is skipped (dev mode).
$webClientId = '';
$secretFile = __DIR__ . '/secrets/google_web_client_id.php';
if (is_file($secretFile)) {
    $secrets = @include $secretFile;
    if (is_array($secrets) && !empty($secrets['google_web_client_id'])) {
        $webClientId = trim((string)$secrets['google_web_client_id']);
    }
}
if ($webClientId === '') {
    $webClientId = trim((string)(getenv('GOOGLE_WEB_CLIENT_ID') ?: ''));
}

$input = input_json();
$idToken = trim((string)($input['id_token'] ?? ''));

if ($idToken === '') {
    respond(false, 'Google ID token required');
}

try {
    // Google's tokeninfo endpoint verifies the token signature + expiry for us.
    $certsUrl = 'https://oauth2.googleapis.com/tokeninfo?id_token=' . urlencode($idToken);
    $ctx = stream_context_create(['http' => ['timeout' => 10, 'ignore_errors' => true]]);
    $response = @file_get_contents($certsUrl, false, $ctx);
    if ($response === false) {
        respond(false, 'Could not reach Google to verify sign-in');
    }

    $info = json_decode($response, true);
    if (!is_array($info) || empty($info['email'])) {
        respond(false, 'Invalid or expired Google ID token');
    }

    // Validate the token was issued for THIS app (skipped until a client ID is configured).
    if ($webClientId !== '') {
        $aud = (string)($info['aud'] ?? '');
        $azp = (string)($info['azp'] ?? '');
        if ($aud !== $webClientId && $azp !== $webClientId) {
            respond(false, 'Google token was not issued for this app');
        }
    }

    $email = strtolower(trim((string)($info['email'] ?? '')));
    $name = trim((string)($info['name'] ?? ''));
    if ($name === '') {
        $local = explode('@', $email)[0] ?? 'campus user';
        $name = ucwords(str_replace(['.', '_', '-'], ' ', $local));
    }

    // Find an existing user by email, otherwise create one.
    $query = $pdo->prepare('SELECT id, full_name, student_id, email, mobile, role FROM users WHERE email = ? LIMIT 1');
    $query->execute([$email]);
    $user = $query->fetch();

    if (!$user) {
        $localPart = explode('@', $email)[0] ?? '';
        $studentId = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', $localPart));
        $randomHash = password_hash(bin2hex(random_bytes(8)), PASSWORD_DEFAULT);
        $insert = $pdo->prepare('INSERT INTO users (full_name, student_id, email, password_hash) VALUES (?, ?, ?, ?)');
        $insert->execute([$name, $studentId, $email, $randomHash]);
        $userId = (int)$pdo->lastInsertId();
        $user = ['id' => $userId, 'full_name' => $name, 'student_id' => $studentId, 'email' => $email, 'mobile' => '', 'role' => 'Student'];
    }

    $userId = (int)$user['id'];
    $user['id'] = $userId;
    $user['name'] = $user['full_name'];
    $user['studentId'] = $user['student_id'];
    unset($user['full_name'], $user['student_id']);

    $token = create_jwt($userId);

    respond(true, 'Google sign-in successful', [
        'user' => $user,
        'token' => $token
    ]);

} catch (Throwable $e) {
    respond(false, 'Could not complete Google sign-in');
}