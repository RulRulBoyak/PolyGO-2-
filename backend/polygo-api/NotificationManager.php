<?php
/**
 * Phase 2: Notification Manager (Secure HTTP v1 Version)
 * Handles sending Push Notifications to Android devices via Firebase Cloud Messaging.
 */
final class NotificationManager {

    /**
     * Sends a notification to a specific user by their ID.
     */
    public static function sendToUser(PDO $pdo, int $userId, string $title, string $body, array $data = []): bool {
        $query = $pdo->prepare('SELECT fcm_token FROM users WHERE id = ?');
        $query->execute([$userId]);
        $token = $query->fetchColumn();

        if (!$token) {
            return false;
        }

        return self::sendPush($token, $title, $body, $data);
    }

    /**
     * Sends FCM request using the secure HTTP v1 API.
     */
    private static function sendPush(string $targetToken, string $title, string $body, array $data): bool {
        $accessToken = self::getAccessToken();
        if (!$accessToken) return false;

        $project_id = "polygo-143cf"; // Extracted from your JSON

        // Data-only message: title/body travel in 'data' so the app can render
        // its own notification (unique id, custom icon) instead of the system default.
        $messageId = $data['message_id'] ?? bin2hex(random_bytes(6));
        $data['message_id'] = $messageId;
        $data['title'] = $title;
        $data['body'] = $body;

        $url = "https://fcm.googleapis.com/v1/projects/$project_id/messages:send";

        $payload = [
            'message' => [
                'token' => $targetToken,
                'data' => $data,
                'android' => [
                    'priority' => 'high',
                    'collapse_key' => 'polygo_notifications'
                ]
            ]
        ];

        $headers = [
            'Authorization: Bearer ' . $accessToken,
            'Content-Type: application/json'
        ];

        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload));

        $result = curl_exec($ch);
        curl_close($ch);

        return $result !== false;
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

        $signature = '';
        openssl_sign("$header.$payload", $signature, $jsonKey['private_key'], 'SHA256');
        $jwt = "$header.$payload." . base64url_encode($signature);

        $ch = curl_init('https://oauth2.googleapis.com/token');
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query([
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $jwt
        ]));

        $response = json_decode(curl_exec($ch), true);
        curl_close($ch);

        return $response['access_token'] ?? null;
    }
}

function base64url_encode($data) {
    return str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($data));
}
