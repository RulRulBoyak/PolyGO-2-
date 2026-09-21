<?php
/**
 * Phase 2: Notification Manager (Secure HTTP v1 Version)
 * Handles sending Push Notifications to Android devices via Firebase Cloud Messaging.
 *
 * Every push is ALSO persisted to the notifications table so the in-app inbox
 * matches the device tray. Dead/rotated FCM tokens are cleared automatically so
 * repeated deliveries never land on a stale token.
 */
final class NotificationManager {

    /**
     * Sends a notification to a specific user by their ID. Persists an inbox
     * row first (delivery never depends on the push succeeding), then sends the
     * FCM push and cleans up any token FCM rejects.
     */
    public static function sendToUser(PDO $pdo, int $userId, string $title, string $body, array $data = []): bool {
        try {
            // Suspended users (admin ban) get no inbox rows or pushes; keep their
            // token intact so unbanning restores delivery without a fresh login.
            $banCheck = $pdo->prepare('SELECT is_banned FROM users WHERE id = ?');
            $banCheck->execute([$userId]);
            if ((int)$banCheck->fetchColumn() === 1) {
                return false;
            }

            $stmt = $pdo->prepare('INSERT INTO notifications (user_id, title, body) VALUES (?, ?, ?)');
            $stmt->execute([$userId, $title, $body]);
        } catch (Throwable $e) {
            error_log('[polygo-api] notifications persist failed: ' . $e->getMessage());
        }

        $query = $pdo->prepare('SELECT fcm_token FROM users WHERE id = ?');
        $query->execute([$userId]);
        $token = (string)$query->fetchColumn();

        if ($token === '') {
            error_log('[polygo-api] FCM push skipped for user ' . $userId . ': no fcm_token registered');
            return false;
        }

        $out = self::sendPush($token, $title, $body, $data);
        $httpCode = $out['code'];

        if ($httpCode === 404 || $httpCode === 400) {
            // FCM rejects these tokens (unregistered / unmapped). Drop it so the
            // next app login registers a fresh one instead of piling up errors.
            $clear = $pdo->prepare('UPDATE users SET fcm_token = NULL WHERE id = ? AND fcm_token = ?');
            $clear->execute([$userId, $token]);
        }

        if ($httpCode >= 200 && $httpCode < 300) {
            error_log('[polygo-api] FCM push delivered to user ' . $userId
                . ' (HTTP ' . $httpCode . ', type=' . ($data['type'] ?? 'general') . ')');
        }

        return $httpCode >= 200 && $httpCode < 300;
    }

    /**
     * Dev-only diagnostic for debug_push.php. Sends a test push and returns the
     * exact HTTP status + FCM response body so console/enablement mistakes
     * (HTTP v1 API disabled, IAM role missing, SENDER_ID_MISMATCH, …) surface
     * instead of failing silently. Never clears the stored token.
     */
    public static function sendDiagnostic(PDO $pdo, int $userId, string $title, string $body, array $data = []): array {
        $query = $pdo->prepare('SELECT fcm_token FROM users WHERE id = ?');
        $query->execute([$userId]);
        $token = (string)$query->fetchColumn();

        if ($token === '') {
            return ['had_token' => false, 'ok' => false, 'http_code' => 0, 'body' => 'user has no registered fcm_token'];
        }

        $accessToken = self::getAccessToken();
        if ($accessToken === null) {
            return ['had_token' => true, 'ok' => false, 'http_code' => 0,
                    'body' => 'could not obtain OAuth access token (JWT signing or token exchange failed)'];
        }

        $out = self::sendPush($token, $title, $body, $data);
        return [
            'had_token' => true,
            'ok' => $out['code'] >= 200 && $out['code'] < 300,
            'http_code' => $out['code'],
            'body' => $out['body']
        ];
    }

    /**
     * Sends FCM request using the secure HTTP v1 API.
     * Returns ['code' => int, 'body' => string] (code 0 when connection failed).
     */
    private static function sendPush(string $targetToken, string $title, string $body, array $data): array {
        $accessToken = self::getAccessToken();
        if ($accessToken === null) {
            return ['code' => 0, 'body' => 'OAuth token exchange failed'];
        }

        $projectId = self::fcmProjectId();

        // Data-only message: title/body travel in 'data' so the app can render
        // its own notification (unique id, custom icon) instead of the system default.
        $data['type'] = (string)($data['type'] ?? 'campus_alert');
        $messageId = $data['message_id'] ?? bin2hex(random_bytes(6));
        $data['message_id'] = $messageId;
        $data['title'] = $title;
        $data['body'] = $body;

        // Per-type collapse key so distinct notifications (chat vs. offer vs.
        // review) never silently collapse into one.  FCM restricts collapse_key
        // to ≤ 32 chars.  Omit when no clear grouping is available.
        $collapseKey = null;
        $type = $data['type'];
        if ($type === 'chat' && !empty($data['thread_id'])) {
            $collapseKey = 'c_' . substr($data['thread_id'], 0, 12);
        } elseif ($type !== '' && in_array($type, ['offer', 'review', 'verification', 'transactions'], true)) {
            $collapseKey = $type;
        }

        $url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send";

        $highPriority = in_array($type, ['chat', 'message', 'live_alert'], true);
        $message = [
            'token' => $targetToken,
            'data'  => $data,
            'android' => [
                'priority' => $highPriority ? 'high' : 'normal'
            ]
        ];
        if ($collapseKey !== null) {
            $message['android']['collapse_key'] = $collapseKey;
        }

        $payload = ['message' => $message];

        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_HTTPHEADER, [
            'Authorization: Bearer ' . $accessToken,
            'Content-Type: application/json'
        ]);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt_array($ch, self::sslOpts());
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload));

        $result = curl_exec($ch);
        $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($httpCode < 200 || $httpCode >= 300) {
            error_log('[polygo-api] FCM send failed: HTTP ' . $httpCode . ' ' . $result);
        }

        return ['code' => $httpCode, 'body' => (string)$result];
    }

    /**
     * Outbound TLS options that verify the peer certificate and hostname. Uses
     * php.ini's curl.cainfo when configured; otherwise the platform default CA
     * bundle is used by libcurl. Verification is never silently disabled.
     */
    private static function sslOpts(): array {
        $opts = [
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
        ];
        $caInfo = (string)ini_get('curl.cainfo');
        if ($caInfo !== '') {
            $opts[CURLOPT_CAINFO] = $caInfo;
        }
        return $opts;
    }

    /**
     * Firebase project id. Prefer the value shipped inside service-account.json;
     * fall back to the known project id so nothing breaks on older keys.
     */
    private static function fcmProjectId(): string {
        $raw = @file_get_contents(__DIR__ . '/service-account.json');
        if ($raw !== false) {
            $key = json_decode($raw, true);
            if (is_array($key) && !empty($key['project_id'])) {
                return (string)$key['project_id'];
            }
        }
        return 'polygo-143cf';
    }

    /**
     * Generates an OAuth2 access token using the Service Account JSON.
     */
    private static function getAccessToken(): ?string {
        $raw = @file_get_contents(__DIR__ . '/service-account.json');
        $jsonKey = $raw ? json_decode($raw, true) : null;

        if (!$jsonKey || !isset($jsonKey['client_email'], $jsonKey['private_key'])) {
            error_log('NotificationManager: service-account.json missing or invalid');
            return null;
        }

        $header = base64url_encode(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
        $now = time();
        $payload = base64url_encode(json_encode([
            'iss' => $jsonKey['client_email'],
            'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
            'aud' => 'https://oauth2.googleapis.com/token',
            'exp' => $now + 3600,
            'iat' => $now
        ]));

        $signature = self::signJwtAssertion("$header.$payload", (string)$jsonKey['private_key']);
        if ($signature === null) {
            error_log('NotificationManager: JWT signing failed (openssl unavailable and no bcmath fallback)');
            return null;
        }
        $jwt = "$header.$payload." . base64url_encode($signature);

        $ch = curl_init('https://oauth2.googleapis.com/token');
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt_array($ch, self::sslOpts());
        curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query([
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $jwt
        ]));

        $response = curl_exec($ch);
        $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($httpCode < 200 || $httpCode >= 300 || $response === false) {
            error_log('NotificationManager: token exchange failed: HTTP ' . $httpCode . ' ' . $response);
            return null;
        }

        $parsed = json_decode($response, true);
        return $parsed['access_token'] ?? null;
    }

    /**
     * RS256 signing without openssl. XAMPP's OpenSSL extension needs an
     * OPENSSL_CONF file at process start and can silently fail, so push delivery
     * must not depend on it. Uses openssl when available, otherwise a pure-PHP
     * PKCS#1 v1.5 SHA-256 signature computed with bcmath (same dependency the
     * App Check verifier in config.php already relies on).
     */
    private static function signJwtAssertion(string $data, string $privateKeyPem): ?string {
        $signature = '';
        if (function_exists('openssl_sign')
            && @openssl_sign($data, $signature, $privateKeyPem, 'SHA256')
            && $signature !== '') {
            return $signature;
        }

        if (!function_exists('bcmod')) {
            return null;
        }

        $fields = self::parseRsaPrivateKey($privateKeyPem);
        if ($fields === null) {
            return null;
        }

        $modulusBytes = ltrim($fields[1]['value'], "\x00");
        $privateExponentBytes = ltrim($fields[3]['value'], "\x00");
        if ($modulusBytes === '' || $privateExponentBytes === '') {
            return null;
        }

        // EMSA-PKCS1-v1_5 (SHA-256): 00 01 FF..FF 00 <DigestInfo<SHA256(message)>>
        $digestInfo = hex2bin('3031300d060960864801650304020105000420') . hash('sha256', $data, true);
        $padding = strlen($modulusBytes) - strlen($digestInfo) - 3;
        if ($padding < 8) {
            return null;
        }
        $encoded = "\x00\x01" . str_repeat("\xff", $padding) . "\x00" . $digestInfo;

        $modulusInt = bc_from_bytes($modulusBytes);
        $privateExponentInt = bc_from_bytes($privateExponentBytes);

        return bc_to_bytes(bcpowmod(bc_from_bytes($encoded), $privateExponentInt, $modulusInt), strlen($modulusBytes));
    }

    /**
     * Extracts the SEQUENCE of RSAPrivateKey INTEGERs (version, modulus, public
     * exponent, private exponent, ...) from a PKCS#1 or PKCS#8 PEM private key.
     */
    private static function parseRsaPrivateKey(string $pem): ?array {
        if (preg_match('/-----BEGIN RSA PRIVATE KEY-----([\s\S]*?)-----END RSA PRIVATE KEY-----/', $pem, $matches)) {
            $der = base64_decode(preg_replace('/\s+/', '', $matches[1]), true);
            if ($der === false) {
                return null;
            }
            $top = self::derChildren($der);
            if (count($top) < 1 || $top[0]['tag'] !== 0x30) {
                return null;
            }
            $fields = self::derChildren($top[0]['value']);
            return count($fields) >= 4 ? $fields : null;
        }

        if (preg_match('/-----BEGIN PRIVATE KEY-----([\s\S]*?)-----END PRIVATE KEY-----/', $pem, $matches)) {
            $der = base64_decode(preg_replace('/\s+/', '', $matches[1]), true);
            if ($der === false) {
                return null;
            }
            $outer = self::derChildren($der);
            if (count($outer) < 1 || $outer[0]['tag'] !== 0x30) {
                return null;
            }
            $fields = self::derChildren($outer[0]['value']);
            $keyDer = null;
            foreach ($fields as $field) {
                if ($field['tag'] === 0x04) {
                    $keyDer = $field['value'];
                    break;
                }
            }
            if ($keyDer === null) {
                return null;
            }
            $inner = self::derChildren($keyDer);
            if (count($inner) < 1 || $inner[0]['tag'] !== 0x30) {
                return null;
            }
            $fields = self::derChildren($inner[0]['value']);
            return count($fields) >= 4 ? $fields : null;
        }

        return null;
    }

    /**
     * Minimal DER TLV reader: tag + length (short and long form) + value.
     */
    private static function derReadTlv(string $der, int &$offset): ?array {
        $total = strlen($der);
        if ($offset >= $total) {
            return null;
        }
        $tag = ord($der[$offset]);
        $offset++;
        if ($offset >= $total) {
            return null;
        }
        $first = ord($der[$offset]);
        if (($first & 0x80) === 0) {
            $length = $first;
            $offset++;
        } else {
            $lengthBytes = $first & 0x7f;
            $offset++;
            if ($lengthBytes === 0 || $lengthBytes > 4 || $offset + $lengthBytes > $total) {
                return null;
            }
            $length = 0;
            for ($i = 0; $i < $lengthBytes; $i++) {
                $length = ($length << 8) | ord($der[$offset + $i]);
            }
            $offset += $lengthBytes;
        }
        if ($offset + $length > $total) {
            return null;
        }
        $value = substr($der, $offset, $length);
        $offset += $length;
        return ['tag' => $tag, 'value' => $value];
    }

    /**
     * Splits a DER value into its immediate children (first-level TLV scan).
     */
    private static function derChildren(string $der): array {
        $children = [];
        $offset = 0;
        while ($offset < strlen($der)) {
            $tlv = self::derReadTlv($der, $offset);
            if ($tlv === null) {
                break;
            }
            $children[] = $tlv;
        }
        return $children;
    }

    /**
     * OAuth2 service-account flow for an arbitrary Google API scope. Same
     * assertion/signing machinery as getAccessToken, parameterised by scope so
     * the admin panel can reach Firestore REST (scope: datastore).
     */
    private static function tokenForScope(string $scope): ?string {
        $raw = @file_get_contents(__DIR__ . '/service-account.json');
        $jsonKey = $raw !== false ? json_decode($raw, true) : null;
        if (is_array($jsonKey) !== true || empty($jsonKey['client_email']) || empty($jsonKey['private_key'])) {
            error_log('NotificationManager: service-account.json missing or invalid');
            return null;
        }

        $now = time();
        $header = base64url_encode(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
        $payload = base64url_encode(json_encode([
            'iss'   => $jsonKey['client_email'],
            'scope' => $scope,
            'aud'   => 'https://oauth2.googleapis.com/token',
            'exp'   => $now + 3600,
            'iat'   => $now,
        ]));
        $signature = self::signJwtAssertion("$header.$payload", (string)$jsonKey['private_key']);
        if ($signature === null) {
            error_log('NotificationManager: JWT signing failed');
            return null;
        }
        $jwt = "$header.$payload." . base64url_encode($signature);

        $ch = curl_init('https://oauth2.googleapis.com/token');
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt_array($ch, self::sslOpts());
        curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query([
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion'  => $jwt,
        ]));
        $response = curl_exec($ch);
        $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        if ($httpCode < 200 || $httpCode >= 300 || $response === false) {
            error_log('NotificationManager: token exchange failed: HTTP ' . $httpCode . ' ' . $response);
            return null;
        }
        $parsed = json_decode((string)$response, true);
        return $parsed['access_token'] ?? null;
    }

    /** OAuth2 token for Firestore REST (used by the admin pulse broadcast). */
    public static function firestoreAccessToken(): ?string {
        return self::tokenForScope('https://www.googleapis.com/auth/datastore');
    }

    /**
     * Lists the most recent documents in a Firestore collection via REST,
     * newest-first on integer-seconds `created_at` (the documented pulse shape).
     * Returns the HTTP status + raw JSON body so the caller can surface
     * per-step results (same convention as postFirestore).
     */
    public static function listFirestore(string $collection, int $limit = 20): array {
        $token = self::firestoreAccessToken();
        if ($token === null) {
            return ['status' => 0, 'body' => 'Could not obtain Firestore access token'];
        }

        $project = 'polygo-143cf';
        $url = "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/"
            . rawurlencode($collection)
            . '?pageSize=' . max(1, $limit)
            . '&orderBy=' . rawurlencode('created_at desc');

        $ch = curl_init($url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt_array($ch, self::sslOpts());
        curl_setopt($ch, CURLOPT_HTTPHEADER, [
            'Authorization: Bearer ' . $token,
        ]);
        $response = curl_exec($ch);
        $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($httpCode < 200 || $httpCode >= 300) {
            error_log('[polygo-api] Firestore list failed: HTTP ' . $httpCode . ' ' . $response);
        }

        return ['status' => $httpCode, 'body' => (string)$response];
    }

    /**
     * Writes a document to a Firestore collection via REST. Returns the HTTP
     * status + body so callers can surface per-step results to the admin UI.
     *
     * @param array  $fields      plain value array -> Firestore typed values
     * @param string $documentId  explicit document id; empty = Firestore-generated
     */
    public static function postFirestore(string $collection, array $fields, string $documentId = ''): array {
        $token = self::firestoreAccessToken();
        if ($token === null) {
            return ['status' => 0, 'body' => 'Could not obtain Firestore access token'];
        }

        $encoded = [];
        foreach ($fields as $key => $value) {
            if (is_int($value)) {
                $encoded[$key] = ['integerValue' => (string)$value];
            } elseif (is_float($value)) {
                $encoded[$key] = ['doubleValue' => $value];
            } elseif (is_bool($value)) {
                $encoded[$key] = ['booleanValue' => $value];
            } else {
                $encoded[$key] = ['stringValue' => (string)$value];
            }
        }

        $project = 'polygo-143cf';
        $url = "https://firestore.googleapis.com/v1/projects/$project/databases/(default)/documents/" . rawurlencode($collection);
        if ($documentId !== '') {
            $url .= '?documentId=' . rawurlencode($documentId);
        }

        $ch = curl_init($url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt_array($ch, self::sslOpts());
        curl_setopt($ch, CURLOPT_HTTPHEADER, [
            'Authorization: Bearer ' . $token,
            'Content-Type: application/json',
        ]);
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode(['fields' => $encoded]));
        $response = curl_exec($ch);
        $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        return ['status' => $httpCode, 'body' => (string)$response];
    }
}

function base64url_encode($data) {
    return str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($data));
}
