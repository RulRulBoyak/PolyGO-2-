<?php
require_once 'config/database.php';
requireAdmin();
$pdo = getConnection();
$message = ''; $error = '';

$themes = [
    'default'  => ['name' => 'Default',  'from' => '#0D47A1', 'to' => '#1E88E5'],
    'ocean'    => ['name' => 'Ocean',    'from' => '#0077B6', 'to' => '#00B4D8'],
    'sunset'   => ['name' => 'Sunset',   'from' => '#E25822', 'to' => '#FFB03A'],
    'forest'   => ['name' => 'Forest',   'from' => '#14532D', 'to' => '#4ADE80'],
    'royal'    => ['name' => 'Royal',    'from' => '#4C1D95', 'to' => '#7C3AED'],
    'lavender' => ['name' => 'Lavender', 'from' => '#5E35B1', 'to' => '#9575CD'],
    'pink'     => ['name' => 'Pink',     'from' => '#AD1457', 'to' => '#F06292'],
    'midnight' => ['name' => 'Midnight', 'from' => '#263238', 'to' => '#546E7A'],
];

function eventDate(?string $value, bool $required): ?string {
    if (($value ?? '') === '') {
        if ($required) throw new InvalidArgumentException('Start time is required.');
        return null;
    }
    $date = DateTime::createFromFormat('Y-m-d\TH:i', $value);
    if (!$date || $date->format('Y-m-d\TH:i') !== $value) {
        throw new InvalidArgumentException('Enter a valid date and time.');
    }
    return $date->format('Y-m-d H:i:s');
}

// Site-relative cover path (/polygo-api/uploads/...). Made absolute for the
// Android client at request time so emulator / device / prod hosts all work.
$apiRoot = basename(__DIR__) === 'admin' ? dirname(__DIR__) : __DIR__;

function uploadCover(string $apiRoot): ?string {
    $file = $_FILES['cover'] ?? null;
    if (!$file || !isset($file['tmp_name']) || !is_uploaded_file($file['tmp_name'])) return null;
    if ($file['error'] !== UPLOAD_ERR_OK || (int)$file['size'] <= 0 || (int)$file['size'] > 5 * 1024 * 1024) {
        throw new InvalidArgumentException('Cover image must be 5MB or smaller.');
    }
    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mime = finfo_file($finfo, $file['tmp_name']);
    finfo_close($finfo);
    $imageInfo = @getimagesize($file['tmp_name']);
    if (!in_array($mime, ['image/jpeg', 'image/png', 'image/gif'], true) || $imageInfo === false) {
        throw new InvalidArgumentException('Cover must be a JPG, PNG or GIF image.');
    }
    $ext = $mime === 'image/png' ? 'png' : ($mime === 'image/gif' ? 'gif' : 'jpg');
    $targetDir = $apiRoot . '/uploads/';
    if (!is_dir($targetDir)) mkdir($targetDir, 0777, true);
    $name = time() . '_event_' . bin2hex(random_bytes(8)) . '.' . $ext;
    if (!move_uploaded_file($file['tmp_name'], $targetDir . $name)) {
        throw new InvalidArgumentException('Cover upload failed, please try again.');
    }
    return '/polygo-api/uploads/' . $name;
}

function removeCoverFile(string $apiRoot, ?string $coverUrl): void {
    if (!$coverUrl) return;
    $fileName = basename($coverUrl);
    $path = $apiRoot . '/uploads/' . $fileName;
    if ($fileName !== '' && is_file($path)) @unlink($path);
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && !verifyCsrf()) {
    $error = 'Your session expired. Refresh the page and try again.';
} elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    $id = (int)($_POST['id'] ?? 0);
    if ($action === 'delete' && $id > 0) {
        $old = $pdo->prepare('SELECT cover_url FROM campus_events WHERE id = ?');
        $old->execute([$id]);
        $row = $old->fetch();
        $pdo->prepare('DELETE FROM campus_events WHERE id = ?')->execute([$id]);
        if ($row) removeCoverFile($apiRoot, $row['cover_url']);
        $message = 'Event deleted.';
    } elseif ($action === 'save') {
        $title = trim($_POST['title'] ?? '');
        $description = trim($_POST['description'] ?? '');
        $venue = trim($_POST['venue'] ?? '');
        try {
            $starts = eventDate(trim($_POST['starts_at'] ?? ''), true);
            $ends = eventDate(trim($_POST['ends_at'] ?? ''), false);
        } catch (InvalidArgumentException $exception) {
            $starts = null; $ends = null; $error = $exception->getMessage();
        }
        $theme = $_POST['theme'] ?? 'default';
        if (!isset($themes[$theme])) $theme = 'default';
        $accent = strtoupper(trim($_POST['accent_color'] ?? ''));
        if ($accent !== '' && !preg_match('/^#[0-9A-F]{6}$/', $accent)) $accent = '';
        $emoji = trim($_POST['emoji'] ?? '');
        $label = trim($_POST['label'] ?? '');
        $organizerName = trim($_POST['organizer_name'] ?? '');
        $organizerContact = trim($_POST['organizer_contact'] ?? '');
        $registrationUrl = trim($_POST['registration_url'] ?? '');
        $mapUrl = trim($_POST['map_url'] ?? '');
        $capacityRaw = trim($_POST['capacity'] ?? '');
        $capacity = $capacityRaw === '' ? null : filter_var($capacityRaw, FILTER_VALIDATE_INT);
        if ($registrationUrl !== '' && !filter_var($registrationUrl, FILTER_VALIDATE_URL)) {
            $error = 'Registration link must be a valid URL.';
        } elseif ($mapUrl !== '' && !filter_var($mapUrl, FILTER_VALIDATE_URL)) {
            $error = 'Maps link must be a valid URL.';
        } elseif ($capacity !== null && ($capacity === false || $capacity < 1 || $capacity > 99999)) {
            $error = 'Capacity must be a number between 1 and 99999.';
        }
        try {
            $coverUrl = uploadCover($apiRoot);
        } catch (InvalidArgumentException $exception) {
            $error = $exception->getMessage();
            $coverUrl = null;
        }
        if ($error !== '') {
            // Keep the validation error above.
        } elseif ($title === '' || $description === '' || $venue === '') {
            $error = 'Title, description, venue and start time are required.';
        } elseif (mb_strlen($title) > 120 || mb_strlen($venue) > 120
            || mb_strlen($description) > 10000 || mb_strlen($label) > 40 || mb_strlen($emoji) > 16
            || mb_strlen($organizerName) > 120 || mb_strlen($organizerContact) > 120
            || mb_strlen($registrationUrl) > 500 || mb_strlen($mapUrl) > 300) {
            $error = 'Event content is too long.';
        } elseif ($ends !== null && $ends <= $starts) {
            $error = 'End time must be after the start time.';
        } else {
            $published = isset($_POST['is_published']) ? 1 : 0;
            $featured = isset($_POST['is_featured']) ? 1 : 0;
            $existing = null;
            if ($id > 0) {
                $old = $pdo->prepare('SELECT cover_url FROM campus_events WHERE id = ?');
                $old->execute([$id]);
                $existing = $old->fetch();
            }
            $existingCover = $existing['cover_url'] ?? null;
            if (isset($_POST['remove_cover']) && !$coverUrl && $existingCover) {
                removeCoverFile($apiRoot, $existingCover);
                $existingCover = null;
            } elseif ($coverUrl) {
                removeCoverFile($apiRoot, $existingCover);
                $existingCover = $coverUrl;
            }
            $nullIfEmpty = static fn(string $v): ?string => $v !== '' ? $v : null;
            if ($id > 0) {
                $pdo->prepare('UPDATE campus_events SET title=?, description=?, venue=?, starts_at=?, ends_at=?, is_published=?, theme=?, accent_color=?, emoji=?, label=?, cover_url=?, is_featured=?, organizer_name=?, organizer_contact=?, registration_url=?, map_url=?, capacity=? WHERE id=?')
                    ->execute([$title, $description, $venue, $starts, $ends ?: null, $published, $theme,
                        $accent !== '' ? $accent : null, $emoji !== '' ? $emoji : null, $label !== '' ? $label : null,
                        $existingCover, $featured, $nullIfEmpty($organizerName), $nullIfEmpty($organizerContact),
                        $nullIfEmpty($registrationUrl), $nullIfEmpty($mapUrl), $capacity, $id]);
            } else {
                $pdo->prepare('INSERT INTO campus_events (title, description, venue, starts_at, ends_at, is_published, theme, accent_color, emoji, label, cover_url, is_featured, organizer_name, organizer_contact, registration_url, map_url, capacity) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
                    ->execute([$title, $description, $venue, $starts, $ends ?: null, $published, $theme,
                        $accent !== '' ? $accent : null, $emoji !== '' ? $emoji : null, $label !== '' ? $label : null,
                        $existingCover, $featured, $nullIfEmpty($organizerName), $nullIfEmpty($organizerContact),
                        $nullIfEmpty($registrationUrl), $nullIfEmpty($mapUrl), $capacity]);
            }
            $message = $published
                ? 'Event published to Android. It will appear while its start time is in the future.'
                : 'Event saved as a draft. Publish it to show it in Android.';
        }
    }
}
$events = $pdo->query('SELECT * FROM campus_events ORDER BY is_featured DESC, starts_at DESC')->fetchAll();
?>
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Campus Events - PolyGo+</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet"><link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet"><link href="assets/css/style.css" rel="stylesheet">
<style>
.event-preview { width: 100%; max-width: 280px; margin: 0 auto; border-radius: 18px; overflow: hidden; box-shadow: 0 10px 24px rgba(13,71,161,.16); background: #fff; }
.event-preview .evp-head { position: relative; height: 96px; }
.event-preview .evp-emoji { position: absolute; left: 12px; top: 12px; font-size: 34px; line-height: 1; }
.event-preview .evp-date { position: absolute; left: 12px; bottom: 10px; font-size: 11px; font-weight: 700; background: rgba(255,255,255,.88); border-radius: 999px; padding: 4px 10px; }
.event-preview .evp-featured { position: absolute; top: 10px; right: 10px; font-size: 10px; font-weight: 800; letter-spacing: .5px; text-transform: uppercase; color: #fff; background: rgba(0,0,0,.34); border-radius: 999px; padding: 4px 9px; }
.event-preview .evp-body { padding: 14px 16px 16px; }
.event-preview .evp-label { display: inline-block; font-size: 10px; font-weight: 700; color: #fff; border-radius: 999px; padding: 3px 10px; margin-bottom: 8px; }
.event-preview .evp-title { font-size: 16px; font-weight: 700; color: #111827; }
.event-preview .evp-venue { font-size: 12.5px; color: #6b7280; margin-top: 2px; }
.event-preview .evp-desc { font-size: 12.5px; color: #6b7280; margin-top: 8px; line-height: 1.4; }
.theme-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.theme-swatch { width: 100%; aspect-ratio: 5/3; border-radius: 10px; border: 2px solid #fff; box-shadow: 0 0 0 1px #dde3eb; cursor: pointer; transition: transform .12s ease, box-shadow .12s ease; }
.theme-swatch:hover { transform: translateY(-1px); }
.theme-swatch.active { box-shadow: 0 0 0 2px #0d47a1, 0 0 0 4px rgba(13,71,161,.18); }
.emoji-quick button { font-size: 18px; }
.detail-mini { margin-top: 14px; background: #f8fafc; border: 1px solid #eef1f4; border-radius: 12px; padding: 10px 12px; font-size: 12px; }
.detail-mini-title { font-weight: 700; color: #111827; font-size: 11px; letter-spacing: .4px; text-transform: uppercase; margin-bottom: 6px; }
.detail-mini-row { display: flex; align-items: center; gap: 8px; padding: 3px 0; color: #6b7280; }
.detail-mini-row i { color: #0d47a1; }
.detail-mini-row .text-primary { color: #0d47a1 !important; font-weight: 600; }
</style></head>
<body><div class="d-flex" id="wrapper"><?php include 'includes/sidebar.php'; ?><div id="page-content-wrapper" class="w-100"><?php include 'includes/header.php'; ?>
<main class="container-fluid px-4 py-4"><div class="d-flex justify-content-between align-items-center mb-4"><div><h3 class="fw-bold mb-1"><i class="bi bi-calendar-event"></i> Campus Events</h3><p class="text-muted mb-0">Publish verified campus activities to the student calendar.</p></div><button class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#eventModal"><i class="bi bi-plus-lg"></i> Add event</button></div>
<?php if ($message): ?><div class="alert alert-success"><?= htmlspecialchars($message) ?></div><?php endif; ?><?php if ($error): ?><div class="alert alert-danger"><?= htmlspecialchars($error) ?></div><?php endif; ?>
<div class="card border-0 shadow-sm"><div class="table-responsive"><table class="table align-middle mb-0"><thead><tr><th>Event</th><th>When</th><th>Venue</th><th>Card</th><th>Status</th><th></th></tr></thead><tbody><?php foreach ($events as $event): ?><?php $now = time(); $started = strtotime($event['starts_at']) <= $now; $finished = strtotime($event['ends_at'] ?: $event['starts_at']) < $now; ?><?php $status = !$event['is_published'] ? 'Draft' : ($finished ? 'Past' : ($started ? 'Ongoing' : 'Published')); ?><?php $t = $themes[$event['theme']] ?? $themes['default']; ?><tr><td><strong><?= htmlspecialchars($event['title']) ?></strong><br><small class="text-muted"><?= htmlspecialchars($event['description']) ?></small></td><td><?= htmlspecialchars($event['starts_at']) ?></td><td><?= htmlspecialchars($event['venue']) ?></td><td><span class="d-inline-block rounded-circle me-1 align-middle" style="width:14px;height:14px;background:linear-gradient(135deg,<?= $t['from'] ?>,<?= $t['to'] ?>);border:1px solid #dde3eb"></span><span class="align-middle"><?= htmlspecialchars($t['name']) ?></span><?php if ($event['emoji']): ?> <?= htmlspecialchars($event['emoji']) ?><?php endif; ?><?php if ($event['is_featured']): ?> <i class="bi bi-star-fill text-warning mx-1" title="Featured"></i><?php endif; ?></td><td><span class="badge <?= in_array($status, ['Published', 'Ongoing'], true) ? 'bg-success' : 'bg-secondary' ?>"><?= $status ?></span></td><td class="text-end"><button class="btn btn-sm btn-outline-primary" data-bs-toggle="modal" data-bs-target="#eventModal" data-event='<?= htmlspecialchars(json_encode($event), ENT_QUOTES, 'UTF-8') ?>'>Edit</button><form class="d-inline" method="post"><input type="hidden" name="csrf_token" value="<?= htmlspecialchars(csrfToken()) ?>"><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int)$event['id'] ?>"><button class="btn btn-sm btn-outline-danger" onclick="return confirm('Delete this event?')">Delete</button></form></td></tr><?php endforeach; ?></tbody></table></div></div></main></div></div>

<div class="modal fade" id="eventModal" tabindex="-1"><div class="modal-dialog modal-lg"><form method="post" class="modal-content" enctype="multipart/form-data"><input type="hidden" name="csrf_token" value="<?= htmlspecialchars(csrfToken()) ?>"><input type="hidden" name="action" value="save"><input id="eventId" type="hidden" name="id"><div class="modal-header"><h5 class="modal-title">Campus event</h5><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div><div class="modal-body"><div class="row g-4"><div class="col-md-4"><div class="event-preview"><div class="evp-head" id="evpHead"><img id="evpCover" src="" alt="" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover;display:none"><span class="evp-emoji" id="evpEmoji"></span><span class="evp-date" id="evpDate">Sat, 1 Jan</span><span class="evp-featured" id="evpFeatured" style="display:none">Featured</span></div><div class="evp-body"><span class="evp-label" id="evpLabel" style="display:none">Label</span><div class="evp-title" id="evpTitle">Event title</div><div class="evp-venue" id="evpVenue">Venue</div><div class="evp-desc" id="evpDesc">Short description appears here.</div></div></div><div class="detail-mini"><div class="detail-mini-title">Detail page preview</div><div class="detail-mini-row"><i class="bi bi-person-badge"></i><span class="text-muted" id="evpOrganizer">Organizer &amp; contact appear here</span></div><div class="detail-mini-row"><i class="bi bi-people"></i><span class="text-muted" id="evpCapacity" style="display:none"></span></div><div class="detail-mini-row"><i class="bi bi-box-arrow-up-right"></i><span class="text-primary" id="evpRegister" style="display:none">Registration link</span></div><div class="detail-mini-row"><i class="bi bi-geo-alt"></i><span class="text-primary" id="evpDirections" style="display:none">Get directions</span></div></div></div><div class="col-md-8">
<input id="eventTitle" class="form-control mb-3" name="title" placeholder="Event title" required>
<textarea id="eventDescription" class="form-control mb-1" name="description" rows="5" placeholder="Description — keep it short or write it long, it renders fully on the event detail page." required></textarea><div class="form-text text-end mb-3"><span id="eventDescCount">0 / 10,000</span></div>
<input id="eventVenue" class="form-control mb-3" name="venue" placeholder="Venue" required>
<div class="row g-2 mb-3"><div class="col"><label class="form-label">Starts</label><input id="eventStarts" class="form-control" type="datetime-local" name="starts_at" required></div><div class="col"><label class="form-label">Ends (optional)</label><input id="eventEnds" class="form-control" type="datetime-local" name="ends_at"></div></div>
<label class="form-label">Card theme</label><input type="hidden" name="theme" id="eventTheme" value="default"><div class="theme-grid mb-3" id="themeGrid"><?php foreach ($themes as $key => $theme): ?><button type="button" class="theme-swatch" data-theme="<?= $key ?>" title="<?= htmlspecialchars($theme['name']) ?>" style="background:linear-gradient(135deg,<?= $theme['from'] ?>,<?= $theme['to'] ?>)"></button><?php endforeach; ?></div>
<div class="row g-2 mb-3"><div class="col"><label class="form-label">Accent color (optional)</label><input id="eventAccentHex" class="form-control" name="accent_color" maxlength="7" placeholder="#RRGGBB"><input id="eventAccentPicker" class="form-control form-control-color mt-2" type="color" value="#0D47A1"></div><div class="col"><label class="form-label">Emoji badge</label><input id="eventEmoji" class="form-control" name="emoji" maxlength="16" placeholder="e.g. 🎓"><div class="emoji-quick d-flex gap-1 mt-2" id="emojiQuick"><button type="button" class="btn btn-sm btn-light border">🎓</button><button type="button" class="btn btn-sm btn-light border">🏆</button><button type="button" class="btn btn-sm btn-light border">🎉</button><button type="button" class="btn btn-sm btn-light border">🎭</button><button type="button" class="btn btn-sm btn-light border">💼</button><button type="button" class="btn btn-sm btn-light border">📚</button><button type="button" class="btn btn-sm btn-light border">⚽</button><button type="button" class="btn btn-sm btn-light border">🍕</button></div></div></div>
<label class="form-label">Label chip (optional)</label><input id="eventLabel" class="form-control mb-3" name="label" maxlength="40" placeholder="e.g. Competition, Career Fair, Free Food">
<label class="form-label">Cover image (optional)</label><input id="eventCover" class="form-control mb-1" type="file" name="cover" accept="image/jpeg,image/png,image/gif"><div class="form-check mb-3"><input id="eventRemoveCover" class="form-check-input" type="checkbox" name="remove_cover"><label class="form-check-label" for="eventRemoveCover">Remove current cover</label></div>
<div class="row g-2 mb-3"><div class="col"><label class="form-label">Organizer (optional)</label><input id="eventOrganizerName" class="form-control" name="organizer_name" maxlength="120" placeholder="e.g. Student Council"></div><div class="col"><label class="form-label">Organizer contact (optional)</label><input id="eventOrganizerContact" class="form-control" name="organizer_contact" maxlength="120" placeholder="Email / phone / WhatsApp"></div></div>
<div class="row g-2 mb-3"><div class="col"><label class="form-label">Registration link (optional)</label><input id="eventRegistrationUrl" class="form-control" name="registration_url" maxlength="500" placeholder="https://forms.gle/..."></div><div class="col"><label class="form-label">Maps / directions link (optional)</label><input id="eventMapUrl" class="form-control" name="map_url" maxlength="300" placeholder="https://maps.google.com/..."></div></div>
<div class="mb-3"><label class="form-label">Capacity (optional)</label><input id="eventCapacity" class="form-control" name="capacity" type="number" min="1" max="99999" placeholder="Max participants, e.g. 40" style="max-width:220px"></div>
<div class="d-flex gap-4"><div class="form-check"><input id="eventPublished" class="form-check-input" type="checkbox" name="is_published"><label class="form-check-label" for="eventPublished">Publish to Android</label></div><div class="form-check"><input id="eventFeatured" class="form-check-input" type="checkbox" name="is_featured"><label class="form-check-label" for="eventFeatured">Featured spotlight</label></div></div>
</div></div></div><div class="modal-footer"><button class="btn btn-primary">Save event</button></div></form></div></div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
<script>
const THEMES = {
    default: ['#0D47A1', '#1E88E5'], ocean: ['#0077B6', '#00B4D8'],
    sunset: ['#E25822', '#FFB03A'], forest: ['#14532D', '#4ADE80'],
    royal: ['#4C1D95', '#7C3AED'], lavender: ['#5E35B1', '#9575CD'],
    pink: ['#AD1457', '#F06292'], midnight: ['#263238', '#546E7A']
};
function lum(hex) {
    const n = parseInt(hex.slice(1), 16);
    const r = (n >> 16 & 255) / 255, g = (n >> 8 & 255) / 255, b = (n & 255) / 255;
    return 0.2126 * r + 0.7152 * g + 0.0722 * b; // falls short of perceptually-weighted; fine for chips
}
function updatePreview() {
    const head = evpHead, g = THEMES[eventTheme.value] || THEMES.default;
    head.style.background = 'linear-gradient(135deg,' + g[0] + ',' + g[1] + ')';
    evpCover.style.display = 'none';
    evpEmoji.textContent = eventEmoji.value.trim();
    evpTitle.textContent = eventTitle.value.trim() || 'Event title';
    evpVenue.textContent = eventVenue.value.trim() || 'Venue';
    evpDesc.textContent = eventDescription.value.trim() || 'Short description appears here.';
    eventDescCount.textContent = eventDescription.value.length + ' / 10,000';
    const s = eventStarts.value.replace('T', ' ');
    const d = s ? new Date(s.replace(' ', 'T')) : null;
    evpDate.textContent = d && !isNaN(d) ? d.toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : 'Sat, 1 Jan';
    const labelEl = evpLabel, label = eventLabel.value.trim();
    if (label) { labelEl.style.display = 'inline-block'; labelEl.textContent = label; }
    else { labelEl.style.display = 'none'; }
    const a = /^#[0-9a-fA-F]{6}$/.test(eventAccentHex.value) ? eventAccentHex.value.toUpperCase() : null;
    if (label && a) { labelEl.style.background = a; labelEl.style.color = lum(a) > 0.5 ? '#111827' : '#fff'; }
    else { labelEl.style.background = g[1]; labelEl.style.color = lum(g[1]) > 0.5 ? '#111827' : '#fff'; }
    evpDate.style.color = a ? a : '#0f172a';
    const fp = eventFeatured.checked;
    evpFeatured.style.display = fp ? 'block' : 'none';
}
function showCoverPreview(file) {
    if (!file) return;
    const url = URL.createObjectURL(file);
    evpCover.onload = () => { evpCover.style.display = 'block'; URL.revokeObjectURL(url); };
    evpCover.src = url;
}
function updateDetailPreview() {
    const orgName = eventOrganizerName.value.trim(), contact = eventOrganizerContact.value.trim();
    evpOrganizer.textContent = orgName || contact
        ? [orgName, contact].filter(Boolean).join(' · ')
        : 'Organizer & contact appear here';
    const cap = parseInt(eventCapacity.value, 10);
    evpCapacity.style.display = cap >= 1 ? '' : 'none';
    if (cap >= 1) evpCapacity.textContent = cap + (cap === 1 ? ' spot' : ' spots');
    const reg = eventRegistrationUrl.value.trim(), map = eventMapUrl.value.trim();
    const isUrl = v => /^https?:\/\/.+/i.test(v);
    evpRegister.style.display = isUrl(reg) ? '' : 'none';
    evpDirections.style.display = isUrl(map) ? '' : 'none';
}
['eventTitle', 'eventDescription', 'eventVenue', 'eventStarts', 'eventEnds', 'eventEmoji', 'eventLabel', 'eventAccentHex', 'eventFeatured']
    .forEach(id => document.getElementById(id).addEventListener('input', updatePreview));
['eventOrganizerName', 'eventOrganizerContact', 'eventRegistrationUrl', 'eventMapUrl', 'eventCapacity']
    .forEach(id => document.getElementById(id).addEventListener('input', updateDetailPreview));
document.querySelectorAll('.theme-swatch').forEach(sw => sw.addEventListener('click', () => {
    document.querySelectorAll('.theme-swatch').forEach(s => s.classList.remove('active'));
    sw.classList.add('active');
    eventTheme.value = sw.dataset.theme;
    if (!/^#[0-9a-fA-F]{6}$/.test(eventAccentHex.value)) {
        const g = THEMES[sw.dataset.theme];
        eventAccentHex.value = g[0];
    }
    updatePreview();
}));
document.querySelectorAll('#emojiQuick button').forEach(b => b.addEventListener('click', () => {
    eventEmoji.value = b.textContent;
    updatePreview();
}));
eventAccentPicker.addEventListener('input', () => { eventAccentHex.value = eventAccentPicker.value; updatePreview(); });
eventAccentHex.addEventListener('input', () => {
    eventAccentPicker.value = /^#[0-9a-fA-F]{6}$/.test(eventAccentHex.value) ? eventAccentHex.value : eventAccentPicker.value;
    updatePreview();
});
eventCover.addEventListener('change', () => { if (eventCover.files[0]) showCoverPreview(eventCover.files[0]); });
document.getElementById('eventModal').addEventListener('show.bs.modal', e => {
    const d = JSON.parse(e.relatedTarget.dataset.event || '{}');
    eventId.value = d.id || '';
    eventTitle.value = d.title || '';
    eventDescription.value = d.description || '';
    eventVenue.value = d.venue || '';
    eventStarts.value = (d.starts_at || '').replace(' ', 'T').slice(0, 16);
    eventEnds.value = (d.ends_at || '').replace(' ', 'T').slice(0, 16);
    eventTheme.value = THEMES[d.theme] ? d.theme : 'default';
    document.querySelectorAll('.theme-swatch').forEach(s =>
        s.classList.toggle('active', s.dataset.theme === eventTheme.value));
    eventAccentHex.value = d.accent_color || '';
    eventAccentPicker.value = d.accent_color || (THEMES[eventTheme.value] ? THEMES[eventTheme.value][0] : '#0D47A1');
    eventEmoji.value = d.emoji || '';
    eventLabel.value = d.label || '';
    eventFeatured.checked = d.id ? !!Number(d.is_featured) : false;
    eventPublished.checked = d.id ? !!Number(d.is_published) : true;
    eventRemoveCover.checked = false;
    eventCover.value = '';
    eventOrganizerName.value = d.organizer_name || '';
    eventOrganizerContact.value = d.organizer_contact || '';
    eventRegistrationUrl.value = d.registration_url || '';
    eventMapUrl.value = d.map_url || '';
    eventCapacity.value = parseInt(d.capacity, 10) >= 1 ? String(parseInt(d.capacity, 10)) : '';
    evpCover.style.display = 'none';
    if (d.cover_url) { evpCover.onload = null; evpCover.src = window.location.origin + d.cover_url; evpCover.style.display = 'block'; }
    updatePreview();
    updateDetailPreview();
});
</script></body></html>
