<?php
require_once __DIR__ . '/config.php';

// The OAuth 2.0 Web Client ID for your Firebase/Google Cloud project.
// Provide it locally via backend/polygo-api/secrets/google_web_client_id.php
// (gitignored) or the GOOGLE_WEB_CLIENT_ID environment variable. While empty,
// the token audience check is skipped (dev mode).
$webClientId = defined('GOOGLE_WEB_CLIENT_ID') ? trim((string)GOOGLE_WEB_CLIENT_ID) : '';
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

// The audience check is mandatory outside the local dev host. Skipping it would
// let any Google account sign in — never acceptable in production.
if ($webClientId === '' && !is_dev_request()) {
    respond(false, 'Google sign-in is not configured on this server');
}

$input = input_json();
$idToken = trim((string)($input['id_token'] ?? ''));
$consentAgreed = filter_var($input['consent_agreed'] ?? false, FILTER_VALIDATE_BOOLEAN);
$requestedName = trim((string)($input['full_name'] ?? ''));
$requestedStudentId = trim((string)($input['student_id'] ?? ''));
$requestedPassword = (string)($input['password'] ?? '');

if ($idToken === '') {
    respond(false, 'Google ID token required');
}

// Slow down abuse of the token endpoint itself.
$ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
if (!rate_limit_check($pdo, 'google_login_ip:' . $ip, 20, 900)) {
    respond(false, 'Too many attempts, please try again later');
}

try {
    // Google's tokeninfo endpoint verifies the token signature + expiry for us.
    // curl with full TLS peer/host validation — never silently disable it.
    $certsUrl = 'https://oauth2.googleapis.com/tokeninfo?id_token=' . urlencode($idToken);
    $ch = curl_init($certsUrl);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 10,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
    ]);
    $caInfo = (string)ini_get('curl.cainfo');
    if ($caInfo !== '') {
        curl_setopt($ch, CURLOPT_CAINFO, $caInfo);
    }
    $response = curl_exec($ch);
    $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $curlError = curl_error($ch);
    curl_close($ch);

    if ($response === false || $httpCode !== 200) {
        error_log('[polygo-api] Google tokeninfo failed: HTTP ' . $httpCode . ' ' . $curlError);
        respond(false, 'Could not verify your Google sign-in, please try again');
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

    // Only accept accounts with a verified email address.
    if (!filter_var($info['email_verified'] ?? false, FILTER_VALIDATE_BOOLEAN)) {
        respond(false, 'Your Google account email is not verified');
    }

    $email = strtolower(trim((string)($info['email'] ?? '')));
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        respond(false, 'Google did not provide a valid email address');
    }
    $name = trim((string)($info['name'] ?? ''));
    if ($name === '') {
        $local = explode('@', $email)[0] ?? 'campus user';
        $name = ucwords(str_replace(['.', '_', '-'], ' ', $local));
    }
    $name = mb_substr($name, 0, 120);

    // Per-account rate limit: an attacker may not loop sign-ins for one email.
    if (!rate_limit_check($pdo, 'google_login_email:' . $email, 10, 3600)) {
        respond(false, 'Too many attempts for this account, please try again later');
    }

    // Existing users sign in immediately. New users must finish the profile
    // form first; authenticating with Google must never silently create one.
    $query = $pdo->prepare('SELECT id, full_name, student_id, email, mobile, role, profile_pic_url, bio, is_verified, verification_status, is_private, is_banned FROM users WHERE email = ? LIMIT 1');
    $query->execute([$email]);
    $user = $query->fetch();

    if (!$user) {
        if ($requestedName === '' && $requestedStudentId === '' && $requestedPassword === '') {
            respond(true, 'Complete your profile to create your account', [
                'requires_profile' => true,
                'google_email' => $email,
                'google_name' => $name,
            ]);
        }
        if (!$consentAgreed) {
            respond(false, 'Please agree to the Terms of Service and Privacy Policy before creating an account');
        }
        $passwordLength = function_exists('mb_strlen')
            ? mb_strlen($requestedPassword, 'UTF-8') : strlen($requestedPassword);
        if ($requestedName === '' || $requestedStudentId === ''
                || mb_strlen($requestedName) > 120 || mb_strlen($requestedStudentId) > 50
                || $passwordLength < 5 || $passwordLength > 128) {
            respond(false, 'Please provide valid registration details');
        }
        try {
            $insert = $pdo->prepare('INSERT INTO users (full_name, student_id, email, password_hash, consent_agreed_at) VALUES (?, ?, ?, ?, NOW())');
            $insert->execute([$requestedName, $requestedStudentId, $email,
                password_hash($requestedPassword, PASSWORD_DEFAULT)]);
        } catch (PDOException $e) {
            if ($e->getCode() === '23000') respond(false, 'Student ID or email already exists');
            error_log('[polygo-api] google profile creation failed: ' . $e->getMessage());
            respond(false, 'Could not create the account');
        }
        $userId = (int)$pdo->lastInsertId();
        $user = [
            'id' => $userId, 'full_name' => $requestedName,
            'student_id' => $requestedStudentId,
            'email' => $email, 'mobile' => '', 'role' => 'Student',
            'profile_pic_url' => '', 'bio' => '', 'is_verified' => 0,
            'verification_status' => 'unverified', 'is_private' => 0, 'is_banned' => 0,
        ];
    }

    $userId = (int)$user['id'];

    // Reject suspended accounts before minting a fresh JWT (is_banned set by the
    // admin panel; verify_jwt() blocks their existing tokens server-side too).
    if ((int)($user['is_banned'] ?? 0) === 1) {
        respond(false, 'Unauthorized: Your account has been suspended by an administrator.');
    }

    $user['id'] = $userId;
    $user['name'] = $user['full_name'];
    $user['studentId'] = $user['student_id'];

    // Fix: Cast numbers to booleans to prevent Android Gson crash
    $user['is_verified'] = (bool)($user['is_verified'] ?? 0);
    $user['is_banned'] = (bool)($user['is_banned'] ?? 0);
    $user['is_private'] = (bool)($user['is_private'] ?? 0);

    unset($user['full_name'], $user['student_id']);

    $token = create_jwt($userId);

    respond(true, 'Google sign-in successful', [
        'user' => $user,
        'token' => $token,
        'requires_profile' => false
    ]);

} catch (Throwable $e) {
    error_log('[polygo-api] google_login error: ' . $e->getMessage());
    respond(false, 'Could not complete Google sign-in');
}
