<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$action = $input['action'] ?? 'list';

if ($action === 'list') {
    $query = $pdo->query('SELECT name, icon_res FROM categories WHERE is_published = 1 ORDER BY name ASC');
    $categories = $query->fetchAll();
    respond(true, 'Categories loaded', ['categories' => $categories]);
}

if ($action === 'list_majors') {
    $query = $pdo->query('SELECT id, name, faculty FROM majors WHERE is_published = 1 ORDER BY name ASC');
    $majors = $query->fetchAll();
    foreach ($majors as &$major) {
        $major['id'] = (int)$major['id'];
    }
    unset($major);
    respond(true, 'Majors loaded', ['majors' => $majors]);
}

if ($action === 'propose') {
    $userId = verify_jwt();
    $name = trim((string)($input['name'] ?? ''));

    if ($name === '') respond(false, 'Category name cannot be empty');

    try {
        $query = $pdo->prepare('INSERT INTO categories (name, proposed_by, is_published) VALUES (?, ?, 0)');
        $query->execute([$name, $userId]);
        respond(true, 'Category proposed and pending review');
    } catch (PDOException $e) {
        if ($e->getCode() === '23000') respond(false, 'This category already exists or is pending');
        respond(false, 'Could not propose category');
    }
}
