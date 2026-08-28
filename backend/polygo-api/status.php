<?php
require_once __DIR__ . '/config.php';

// Flip to true while updating MySQL so the app shows the maintenance screen.
$maintenance = false;

respond(true, 'OK', [
    'maintenance' => $maintenance,
    'app' => 'PolyGo+',
]);
