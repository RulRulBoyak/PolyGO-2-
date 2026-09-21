<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();

header('Content-Type: application/json; charset=utf-8');

try {
    require_once __DIR__ . '/../NotificationManager.php';
    $result = NotificationManager::listFirestore('pulse', 20);
} catch (Throwable $e) {
    $result = ['status' => 0, 'body' => $e->getMessage()];
}

$ok = ($result['status'] >= 200 && $result['status'] < 300);
$docs = [];

if ($ok) {
    $payload = json_decode((string)$result['body'], true);
    $documents = $payload['documents'] ?? [];
    if (is_array($documents)) {
        foreach ($documents as $doc) {
            $fields = $doc['fields'] ?? [];
            $strip = static function (array $f, string $k): string {
                foreach ($f as $type => $value) {
                    if (strpos($type, 'Value') !== false) {
                        if (strpos($type, 'TimestampValue') !== false) {
                            return (string)$value;
                        }
                        return (string)$value;
                    }
                }
                return '';
            };
            $id = '';
            if (isset($doc['name']) && preg_match('#/documents/pulse/([^/]+)$#', (string)$doc['name'], $m)) {
                $id = rawurldecode($m[1]);
            }
            $docs[] = [
                'id'         => $id,
                'title'      => $strip($fields['title'] ?? [], 'title'),
                'body'       => $strip($fields['body'] ?? [], 'body'),
                'tag'        => $strip($fields['tag'] ?? [], 'tag'),
                'user_name'  => $strip($fields['user_name'] ?? [], 'user_name'),
                'created_at' => (int)$strip($fields['created_at'] ?? [], 'created_at'),
            ];
        }
    }
}

// Never leak the access token or service account — only doc data + status.
echo json_encode([
    'ok'         => $ok,
    'http'       => $result['status'],
    'error'      => $ok ? null : mb_strimwidth(strip_tags((string)$result['body']), 0, 160, '…'),
    'docs'       => $docs,
    'fetched_at' => time(),
]);
exit;