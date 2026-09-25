<?php
require_once __DIR__ . '/config.php';

$ownerId = verify_jwt();
if (!defined('GEMINI_API_KEY') || trim((string)GEMINI_API_KEY) === '') {
    respond(false, 'AI is not configured on the server yet');
}

$clientIp = $_SERVER['REMOTE_ADDR'] ?? '';
if (!rate_limit_check($pdo, 'ai_uid:' . $ownerId, 30, 3600) ||
    !rate_limit_check($pdo, 'ai_ip:' . $clientIp, 60, 3600)) {
    respond(false, 'Too many AI requests. Try again later.');
}

if (!isset($_FILES['image']) || !is_array($_FILES['image'])) {
    respond(false, 'Choose a product photo first');
}
$file = $_FILES['image'];
$mode = strtolower(trim((string)($_POST['mode'] ?? 'product')));
$mode = $mode === 'service' ? 'service' : 'product';
if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
    respond(false, 'The photo could not be uploaded. Please choose it again.');
}
$size = (int)($file['size'] ?? 0);
if ($size <= 0 || $size > 5 * 1024 * 1024) {
    respond(false, 'Photo must be 5 MB or smaller');
}

$finfo = finfo_open(FILEINFO_MIME_TYPE);
$mime = $finfo ? finfo_file($finfo, $file['tmp_name']) : false;
if ($finfo) finfo_close($finfo);
$imageInfo = @getimagesize($file['tmp_name']);
$allowedMimes = ['image/jpeg', 'image/png', 'image/webp'];
if (!is_string($mime) || !in_array($mime, $allowedMimes, true) || $imageInfo === false) {
    respond(false, 'Use a JPG, PNG, or WebP product photo');
}
$width = (int)($imageInfo[0] ?? 0);
$height = (int)($imageInfo[1] ?? 0);
if ($width < 64 || $height < 64 || $width * $height > 30_000_000) {
    respond(false, 'Photo dimensions are not supported');
}

$categoryRows = $pdo->query('SELECT name FROM categories WHERE is_published = 1 ORDER BY name')->fetchAll(PDO::FETCH_COLUMN);
$categories = array_values(array_filter(array_map(static fn($name) => trim((string)$name), $categoryRows)));
if (!$categories) {
    respond(false, 'Listing categories are not configured yet');
}

$bytes = file_get_contents($file['tmp_name']);
if ($bytes === false) respond(false, 'Could not read the selected photo');
$base64Data = base64_encode($bytes);
unset($bytes);

$categoryList = implode(', ', $categories);
$subject = $mode === 'service'
    ? 'a student service listing from one portfolio photo'
    : 'a second-hand campus marketplace product listing from one photo';
$prompt = "You help students draft {$subject}.\n" .
    "Return only the requested JSON. Use visible evidence; never invent a brand, model, size, condition, accessory, or defect. " .
    "If uncertain, use a generic accurate title and mention what the seller should verify. " .
    "Price is an estimated asking price in Malaysian Ringgit (RM), not a guarantee. " .
    "Choose exactly one category from: {$categoryList}. " .
    "Create 3 to 6 short search tags without # symbols, duplicates, prices, or unsupported claims. " .
    "Description must be concise, honest, and useful, at most 500 characters.";

$payload = [
    'contents' => [[
        'parts' => [
            ['inline_data' => ['mime_type' => $mime, 'data' => $base64Data]],
            ['text' => $prompt],
        ],
    ]],
    'generationConfig' => [
        'candidateCount' => 1,
        'temperature' => 0.2,
        'maxOutputTokens' => 800,
        'responseMimeType' => 'application/json',
        'responseSchema' => [
            'type' => 'OBJECT',
            'properties' => [
                'title' => ['type' => 'STRING', 'description' => 'Accurate listing title, maximum 80 characters'],
                'price' => ['type' => 'NUMBER', 'description' => 'Estimated asking price in RM, from 0.01 to 100000'],
                'description' => ['type' => 'STRING', 'description' => 'Honest description based only on visible evidence, maximum 500 characters'],
                'category' => ['type' => 'STRING', 'enum' => $categories],
                'tags' => [
                    'type' => 'ARRAY',
                    'items' => ['type' => 'STRING'],
                    'minItems' => 3,
                    'maxItems' => 6,
                ],
                'condition' => ['type' => 'STRING', 'enum' => ['New', 'Used - Like New', 'Used - Good', 'Used - Fair']],
                'confidence' => ['type' => 'NUMBER', 'minimum' => 0, 'maximum' => 1],
            ],
            'required' => ['title', 'price', 'description', 'category', 'tags', 'condition', 'confidence'],
        ],
    ],
];
unset($base64Data);

$payloadJson = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
if ($payloadJson === false) respond(false, 'Could not prepare the AI request');

$configuredModel = defined('GEMINI_MODEL') && preg_match('/^[a-zA-Z0-9._-]+$/', (string)GEMINI_MODEL)
    ? (string)GEMINI_MODEL : 'gemini-3.1-flash-lite';
$models = array_values(array_unique([$configuredModel, 'gemini-3.1-flash-lite']));
$raw = false;
$httpCode = 0;
$curlError = '';
$usedModel = $configuredModel;
foreach ($models as $model) {
    $usedModel = $model;
    $url = 'https://generativelanguage.googleapis.com/v1beta/models/' . rawurlencode($model) . ':generateContent';
    for ($attempt = 0; $attempt < 2; $attempt++) {
        if ($attempt > 0) usleep(1_000_000 + random_int(0, 250_000));
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $payloadJson,
            CURLOPT_HTTPHEADER => [
                'Content-Type: application/json',
                'x-goog-api-key: ' . GEMINI_API_KEY,
            ],
            CURLOPT_CONNECTTIMEOUT => 8,
            CURLOPT_TIMEOUT => 25,
        ]);
        $raw = curl_exec($ch);
        $curlError = curl_error($ch);
        $httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        if ($raw !== false && $httpCode < 400) break 2;
        if ($httpCode === 429 || $httpCode === 404) break;
        if ($attempt === 1 || ($httpCode !== 408 && $httpCode < 500)) break;
    }
}

if ($raw === false) {
    error_log('[polygo-api] AI transport failure: ' . $curlError);
    respond(false, 'AI is temporarily unavailable. Your listing draft is safe; try again.');
}
if ($httpCode >= 400) {
    $errorJson = json_decode((string)$raw, true);
    $providerMessage = trim((string)($errorJson['error']['message'] ?? ''));
    error_log('[polygo-api] AI HTTP failure: model=' . $usedModel . ' status=' . $httpCode .
        ($providerMessage === '' ? '' : ' ' . mb_substr($providerMessage, 0, 300)));
    $message = $httpCode === 429
        ? 'AI is busy right now. Please wait a moment and try again.'
        : 'AI could not analyze this photo. Your listing draft is safe.';
    respond(false, $message);
}

$json = json_decode($raw, true);
$finishReason = (string)($json['candidates'][0]['finishReason'] ?? '');
$parts = $json['candidates'][0]['content']['parts'] ?? [];
$textParts = [];
foreach ((array)$parts as $part) {
    $text = trim((string)($part['text'] ?? ''));
    if ($text !== '') $textParts[] = $text;
}
if (!$textParts) {
    $blocked = $finishReason === 'SAFETY' || isset($json['promptFeedback']['blockReason']);
    respond(false, $blocked
        ? 'AI cannot help with this photo. Try a clear photo of an allowed item.'
        : 'AI could not recognize the item. Try a clearer photo with one item in frame.');
}

$decodeSuggestion = static function (string $text): ?array {
    $text = trim($text);
    if (preg_match('/^```(?:json)?\s*(.*?)\s*```$/s', $text, $match)) $text = $match[1];
    $decoded = json_decode($text, true);
    if (is_string($decoded)) $decoded = json_decode($decoded, true);
    if (is_array($decoded)) return $decoded;
    $start = strpos($text, '{');
    $end = strrpos($text, '}');
    if ($start !== false && $end !== false && $end > $start) {
        $decoded = json_decode(substr($text, $start, $end - $start + 1), true);
        if (is_array($decoded)) return $decoded;
    }
    return null;
};
$suggestion = null;
foreach (array_reverse($textParts) as $textPart) {
    $suggestion = $decodeSuggestion($textPart);
    if ($suggestion !== null) break;
}
if ($suggestion === null && count($textParts) > 1) {
    $suggestion = $decodeSuggestion(implode("\n", $textParts));
}
if (!is_array($suggestion)) {
    error_log('[polygo-api] AI returned invalid structured output: finish=' . $finishReason .
        ' parts=' . count($textParts) . ' chars=' . array_sum(array_map('strlen', $textParts)));
    respond(false, 'AI returned an incomplete suggestion. Please try another photo.');
}

$cleanText = static function ($value, int $max): string {
    $text = trim(preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', (string)$value) ?? '');
    return mb_substr($text, 0, $max);
};
$title = $cleanText($suggestion['title'] ?? '', 80);
$description = $cleanText($suggestion['description'] ?? '', 500);
$price = is_numeric($suggestion['price'] ?? null) ? round((float)$suggestion['price'], 2) : 0;
$category = $cleanText($suggestion['category'] ?? '', 80);
$condition = $cleanText($suggestion['condition'] ?? '', 40);
$confidence = is_numeric($suggestion['confidence'] ?? null)
    ? max(0, min(1, (float)$suggestion['confidence'])) : 0;

$categoryMap = [];
foreach ($categories as $known) $categoryMap[mb_strtolower($known)] = $known;
$category = $categoryMap[mb_strtolower($category)] ?? '';
$conditions = ['New', 'Used - Like New', 'Used - Good', 'Used - Fair'];
if (!in_array($condition, $conditions, true)) $condition = 'Used - Good';
if ($title === '' || $description === '' || $category === '' || $price <= 0 || $price > 100000) {
    error_log('[polygo-api] AI validation failed: title=' . mb_strlen($title) .
        ' description=' . mb_strlen($description) . ' category=' . ($category === '' ? 'invalid' : 'ok') .
        ' price=' . $price);
    respond(false, 'AI returned an incomplete suggestion. Please try another photo.');
}

$tags = [];
foreach ((array)($suggestion['tags'] ?? []) as $tag) {
    $tag = mb_strtolower(ltrim($cleanText($tag, 24), "# \t\n\r\0\x0B"));
    $tag = trim(preg_replace('/[^\p{L}\p{N} -]+/u', '', $tag) ?? '');
    if ($tag !== '' && !in_array($tag, $tags, true)) $tags[] = $tag;
    if (count($tags) === 6) break;
}
if (!$tags) $tags[] = mb_strtolower($category);

respond(true, 'Suggestion ready', [
    'title' => $title,
    'price' => number_format($price, 2, '.', ''),
    'description' => $description,
    'category' => $category,
    'tags' => $tags,
    'condition' => $condition,
    'confidence' => $confidence,
]);
