<?php
require_once __DIR__ . '/config.php';

$input = input_json();
$action = (string)($input['action'] ?? 'events');

function campus_int($value, int $minimum, int $maximum): int {
    $number = filter_var($value, FILTER_VALIDATE_INT);
    if ($number === false || $number < $minimum || $number > $maximum) {
        respond(false, 'Invalid value supplied');
    }
    return $number;
}

if ($action === 'events') {
    $stmt = $pdo->prepare('SELECT id, title, description, venue, starts_at, ends_at, theme, accent_color, emoji, label, cover_url, is_featured, organizer_name, organizer_contact, registration_url, map_url, capacity FROM campus_events WHERE is_published = 1 AND COALESCE(ends_at, starts_at) >= NOW() ORDER BY is_featured DESC, starts_at ASC LIMIT 100');
    $stmt->execute();
    $events = $stmt->fetchAll();
    foreach ($events as &$event) {
        $event['id'] = (string)$event['id'];
        $event['is_featured'] = (int)($event['is_featured'] ?? 0);
        if (!empty($event['cover_url']) && strpos($event['cover_url'], 'http') !== 0) {
            $event['cover_url'] = public_api_base_url() . $event['cover_url'];
        }
        $event['capacity'] = $event['capacity'] !== null ? (int)$event['capacity'] : 0;
    }
    unset($event);
    respond(true, 'Campus events loaded', ['events' => $events]);
}

if ($action === 'event') {
    $id = (int)($input['id'] ?? 0);
    if ($id <= 0) respond(false, 'Invalid event id');
    $stmt = $pdo->prepare('SELECT id, title, description, venue, starts_at, ends_at, theme, accent_color, emoji, label, cover_url, is_featured, organizer_name, organizer_contact, registration_url, map_url, capacity FROM campus_events WHERE id = ? AND is_published = 1');
    $stmt->execute([$id]);
    $event = $stmt->fetch();
    if (!$event) respond(false, 'Event not found');
    $event['id'] = (string)$event['id'];
    $event['is_featured'] = (int)($event['is_featured'] ?? 0);
    if (!empty($event['cover_url']) && strpos($event['cover_url'], 'http') !== 0) {
        $event['cover_url'] = public_api_base_url() . $event['cover_url'];
    }
    $event['capacity'] = $event['capacity'] !== null ? (int)$event['capacity'] : 0;
    respond(true, 'Event loaded', ['event' => $event]);
}

$writeActions = in_array($action, ['save_timetable', 'delete_timetable'], true);
$userId = verify_jwt($writeActions);
if (!$userId) respond(false, 'Unauthorized');

if ($action === 'timetable') {
    $stmt = $pdo->prepare('SELECT id, course, room, day_of_week, starts_at, ends_at FROM timetable_entries WHERE user_id = ? ORDER BY day_of_week, starts_at');
    $stmt->execute([$userId]);
    $entries = $stmt->fetchAll();
    foreach ($entries as &$e) {
        $e['id'] = (int)$e['id'];
        $e['day_of_week'] = (int)$e['day_of_week'];
    }
    unset($e);
    respond(true, 'Timetable loaded', ['timetable' => $entries]);
}
if ($action === 'save_timetable') {
    $course = trim((string)($input['course'] ?? ''));
    $room = trim((string)($input['room'] ?? ''));
    $start = (string)($input['starts_at'] ?? '');
    $end = (string)($input['ends_at'] ?? '');
    if ($course === '' || mb_strlen($course) > 100 || $room === '' || mb_strlen($room) > 80
        || !preg_match('/^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$/', $start)
        || !preg_match('/^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$/', $end) || $end <= $start) {
        respond(false, 'Enter a valid class and time range');
    }
    $day = campus_int($input['day_of_week'] ?? null, 1, 7);
    $id = (int)($input['id'] ?? 0);
    if ($id > 0) {
        $stmt = $pdo->prepare('UPDATE timetable_entries SET course=?, room=?, day_of_week=?, starts_at=?, ends_at=? WHERE id=? AND user_id=?');
        $stmt->execute([$course, $room, $day, $start, $end, $id, $userId]);
    } else {
        $stmt = $pdo->prepare('INSERT INTO timetable_entries (user_id, course, room, day_of_week, starts_at, ends_at) VALUES (?, ?, ?, ?, ?, ?)');
        $stmt->execute([$userId, $course, $room, $day, $start, $end]);
    }
    respond(true, 'Class saved');
}
if ($action === 'delete_timetable') {
    $stmt = $pdo->prepare('DELETE FROM timetable_entries WHERE id = ? AND user_id = ?');
    $stmt->execute([campus_int($input['id'] ?? null, 1, PHP_INT_MAX), $userId]);
    respond(true, 'Class deleted');
}
respond(false, 'Unsupported campus action');
