<?php
require_once __DIR__ . '/config.php';

// SECURITY: Only logged-in users may use the AI helper. App Check is enforced
// here because verify_jwt(true) is an authenticated write path.
$ownerId = verify_jwt();

if (!defined('GEMINI_API_KEY') || GEMINI_API_KEY === '') {
    respond(false, 'AI is not configured on the server yet');
}

// Accept a single image the same way upload_image.php does (field name "image").
if (!isset($_FILES['image'])) {
    respond(false, 'No image file provided');
}

$file = $_FILES['image'];

if ($file['error'] !== UPLOAD_ERR_OK) {
    respond(false, 'Upload failed, please try again');
}

if ((int)$file['size'] <= 0 || (int)$file['size'] > 5 * 1024 * 1024) {
    respond(false, 'Image must be 5MB or smaller');
}

$finfo = finfo_open(FILEINFO_MIME_TYPE);
$mime = finfo_file($finfo, $file['tmp_name']);
finfo_close($finfo);
$allowedMimes = ['image/jpeg', 'image/png', 'image/gif'];
$imageInfo = @getimagesize($file['tmp_name']);
if (!in_array($mime, $allowedMimes, true) || $imageInfo === false) {
    respond(false, 'Sorry, only JPG, PNG, & GIF images are allowed.');
}

$base64Data = base64_encode(file_get_contents($file['tmp_name']));
if ($base64Data === false) {
    respond(false, 'Could not read the uploaded image');
}

$prompt = 'Analyze this image of an item being sold on a college campus. ' .
    'Suggest a professional product Title, a fair Price in RM (Ringgit Malaysia), ' .
    'and a short, attractive Description.';

$payload = [
    'contents' => [
        [
            'parts' => [
                ['inline_data' => ['mime_type' => $mime, 'data' => $base64Data]],
                ['text' => $prompt]
            ]
        ]
    ],
    'generationConfig' => [
        'responseMimeType' => 'application/json',
        'responseSchema' => [
            'type' => 'OBJECT',
            'properties' => [
                'title' => ['type' => 'STRING', 'description' => 'Professional product title'],
                'price' => ['type' => 'STRING', 'description' => 'Fair price in RM'],
                'description' => ['type' => 'STRING', 'description' => 'Short attractive description']
            ],
            'required' => ['title', 'price', 'description']
        ]
    ]
];

$ch = curl_init('https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=' . urlencode(GEMINI_API_KEY));
curl_setopt_array($ch, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => json_encode($payload),
    CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
    CURLOPT_TIMEOUT => 50
]);
$raw = curl_exec($ch);
$curlError = curl_error($ch);
$httpCode = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

if ($raw === false) {
    respond(false, 'AI service unreachable. ' . $curlError);
}
if ($httpCode >= 400) {
    respond(false, 'AI request failed (HTTP ' . $httpCode . ')');
}

$json = json_decode($raw, true);
$partText = $json['candidates'][0]['content']['parts'][0]['text'] ?? '';
if ($partText === '') {
    respond(false, 'AI returned no suggestions');
}

// Strip markdown code fences if the model wrapped the JSON in them.
if (strpos($partText, '```') !== false) {
    if (preg_match('/```(?:json)?\s*(.*?)\s*```/s', $partText, $m)) {
        $partText = $m[1];
    }
}

$suggestions = json_decode(trim($partText), true);
if (!is_array($suggestions) || trim((string)($suggestions['title'] ?? '')) === '') {
    respond(false, 'AI response could not be parsed');
}

$title = trim((string)$suggestions['title']);
$price = preg_replace('/[^0-9.]/', '', (string)($suggestions['price'] ?? ''));
$description = trim((string)($suggestions['description'] ?? ''));

respond(true, 'Suggestion ready', [
    'title' => $title,
    'price' => $price,
    'description' => $description
]);