<?php
require_once __DIR__ . '/config.php';

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
    $fileName = time() . '_' . basename($file['name']);
    $targetFilePath = $targetDir . $fileName;
    $fileType = strtolower(pathinfo($targetFilePath, PATHINFO_EXTENSION));

    // Allow certain file formats
    $allowTypes = array('jpg', 'png', 'jpeg', 'gif');
    if (in_array($fileType, $allowTypes)) {
        // Upload file to server
        if (move_uploaded_file($file['tmp_name'], $targetFilePath)) {
            // Return the REAL URL that the Android app can use to show the image
            // We use the server IP (10.0.2.2 for emulator)
            $serverUrl = "http://10.0.2.2/polygo-api/uploads/" . $fileName;
            respond(true, 'Image uploaded successfully', ['url' => $serverUrl]);
        } else {
            respond(false, 'Sorry, there was an error uploading your file.');
        }
    } else {
        respond(false, 'Sorry, only JPG, JPEG, PNG, & GIF files are allowed.');
    }
} catch (Exception $e) {
    respond(false, 'Server error: ' . $e->getMessage());
}
