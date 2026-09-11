<?php
require_once __DIR__ . '/config.php';

// SECURITY: Require a valid JWT before accepting uploads.
$ownerId = verify_jwt();

// Folder where images will be stored
$targetDir = __DIR__ . "/uploads/";
if (!file_exists($targetDir)) {
    mkdir($targetDir, 0777, true);
}

try {
    // Check if file was uploaded
    if (!isset($_FILES['image'])) {
        respond(false, 'No image file provided');
    }

    $file = $_FILES['image'];

    if ($file['error'] !== UPLOAD_ERR_OK) {
        respond(false, 'Upload failed, please try again');
    }

    // Enforce a reasonable size limit (5MB)
    if ((int)$file['size'] <= 0 || (int)$file['size'] > 5 * 1024 * 1024) {
        respond(false, 'Image must be 5MB or smaller');
    }

    // Verify the file really is an image: MIME type check + image integrity check.
    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mime = finfo_file($finfo, $file['tmp_name']);
    finfo_close($finfo);
    $allowedMimes = ['image/jpeg', 'image/png', 'image/gif'];
    $imageInfo = @getimagesize($file['tmp_name']);
    if (!in_array($mime, $allowedMimes, true) || $imageInfo === false) {
        respond(false, 'Sorry, only JPG, PNG, & GIF images are allowed.');
    }

    // Derive extension from the verified MIME type, never from the client filename.
    if ($mime === 'image/png') {
        $ext = 'png';
    } elseif ($mime === 'image/gif') {
        $ext = 'gif';
    } else {
        $ext = 'jpg';
    }
    $fileName = time() . '_' . $ownerId . '_' . bin2hex(random_bytes(8)) . '.' . $ext;
    $targetFilePath = $targetDir . $fileName;

    if (move_uploaded_file($file['tmp_name'], $targetFilePath)) {
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        $host = $_SERVER['HTTP_HOST'] ?? '10.0.2.2';
        $baseUrl = $scheme . '://' . $host . '/polygo-api/uploads/';
        $thumbUrl = $baseUrl . $fileName;

        // Build a 200x200 cover-crop thumbnail for grid loading. Failure to make
        // one is non-fatal: the full image URL is still returned as a fallback.
        $thumbName = pathinfo($fileName, PATHINFO_FILENAME) . '.thumb.jpg';
        $thumbPath = __DIR__ . '/uploads/thumbs/' . $thumbName;
        if (makeThumbnail($targetFilePath, $thumbPath, $mime, 200)) {
            $thumbUrl = $baseUrl . 'thumbs/' . $thumbName;
        }

        respond(true, 'Image uploaded successfully', [
            'url' => $baseUrl . $fileName,
            'thumb_url' => $thumbUrl
        ]);
    } else {
        respond(false, 'Sorry, there was an error uploading your file.');
    }
} catch (Throwable $e) {
    error_log('[polygo-api] upload failed: ' . $e->getMessage());
    respond(false, 'Upload failed, please try again');
}