<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';

// Live Alert "Remote Control". One admin-typed message, three synchronized
// delivery paths so it pops on every student's phone instantly:
//   1. campus_alerts INSERT  -> survives the app's pull-to-refresh (REST feed
//                              rebuilds from the DB) and shows on the Pulse list.
//   2. Firestore pulse doc   -> the app's real-time snapshot listener
//                              (CampusPulseActivity, orderBy created_at) appends
//                              it to the top of an open Pulse screen instantly.
//   3. FCM push              -> system notification on every registered token
//                              where is_banned = 0, even with the app closed.
// The Firestore doc field shape MUST match what the listener reads
// (title/body/tag/user_name + created_at as integer seconds) or the ordered
// query silently drops it (orderBy filters out documents missing the field).
if ($_SERVER['REQUEST_METHOD'] === 'POST' && verifyCsrf()) {
    $title = trim($_POST['title'] ?? '');
    $body = trim($_POST['body'] ?? '');
    $tag = in_array($_POST['tag'] ?? '', ['ANNOUNCEMENT', 'REQUEST', 'FLASH SALE', 'EVENT'], true) ? $_POST['tag'] : 'ANNOUNCEMENT';

    if ($body === '') {
        $error = 'Write a message before broadcasting.';
    } else {
        if ($title === '') {
            $title = 'Live Announcement';
        }
        try {
            require_once __DIR__ . '/../NotificationManager.php';

            $adminId = (int) $pdo->query("SELECT id FROM users WHERE role = 'admin' AND is_banned = 0 ORDER BY id LIMIT 1")->fetchColumn();
            if ($adminId < 1) {
                $error = 'No active admin user in the database to author this alert. Seed an admin account first.';
            } else {
                $stmt = $pdo->prepare("INSERT INTO campus_alerts (user_id, user_name, tag, title, body, status, is_global) VALUES (?, 'PolyGo+ Admin', ?, ?, ?, 'approved', 1)");
                $stmt->execute([$adminId, $tag, $title, $body]);
                $alertId = (int) $pdo->lastInsertId();

                $pulseResult = NotificationManager::postFirestore('pulse', [
                    'tag'        => $tag,
                    'title'      => $title,
                    'body'       => $body,
                    'user_name'  => 'PolyGo+ Admin',
                    'status'     => 'approved',
                    'is_global'  => true,
                    'created_at' => time(),
                ]);
                $fsOk = $pulseResult['status'] >= 200 && $pulseResult['status'] < 300;

                $tokens = $pdo->query("SELECT id FROM users WHERE fcm_token IS NOT NULL AND fcm_token != '' AND is_banned = 0")->fetchAll(PDO::FETCH_COLUMN);
                $totalTokenUsers = (int) $pdo->query("SELECT COUNT(*) FROM users WHERE fcm_token IS NOT NULL AND fcm_token != ''")->fetchColumn();
                $skipped = max(0, $totalTokenUsers - count($tokens));
                $sent = 0;
                $fail = 0;
                foreach ($tokens as $uid) {
                    try {
                        if (NotificationManager::sendToUser($pdo, (int) $uid, '📢 ' . $title, $body, ['tag' => $tag, 'type' => 'live_alert'])) {
                            $sent++;
                        } else {
                            $fail++;
                        }
                    } catch (Throwable $e) {
                        $fail++;
                    }
                }

                $message = 'Live alert #' . $alertId . ' broadcast: ' . $sent . ' sound notification(s) sent'
                    . ($fail > 0 ? ', ' . $fail . ' failed' : '')
                    . ' · Firestore ' . ($fsOk ? 'OK' : 'FAILED (HTTP ' . $pulseResult['status'] . '): ' . mb_strimwidth(strip_tags($pulseResult['body']), 0, 120, '…') . ' — check service-account.json / internet')
                    . ($skipped > 0 ? ' · ' . $skipped . ' banned account skipped.' : '.');
            }
        } catch (Throwable $e) {
            $error = 'Broadcast failed: ' . $e->getMessage();
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Remote Control - PolyGo+ Admin</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
    <link href="assets/css/style.css" rel="stylesheet">
</head>
<body>
    <div class="d-flex" id="wrapper">
        <?php include 'includes/sidebar.php'; ?>

        <div id="page-content-wrapper">
            <?php include 'includes/header.php'; ?>

            <div class="container-fluid px-4">
                <div class="row mt-3 mb-4">
                    <div class="col-12">
                        <h3 class="fw-bold"><i class="bi bi-broadcast-pin"></i> Remote Control — Live Alert</h3>
                        <p class="page-head-sub">Type a message on the website and every student phone Pops with it instantly</p>
                    </div>
                </div>

                <?php if ($message): ?>
                    <div class="alert alert-success alert-dismissible fade show"><i class="bi bi-check-circle"></i> <?php echo $message; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>
                <?php if ($error): ?>
                    <div class="alert alert-danger alert-dismissible fade show"><i class="bi bi-exclamation-triangle"></i> <?php echo $error; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>

                <div class="row">
                    <div class="col-lg-8">
                        <div class="card mb-4">
                            <div class="card-header">
                                <h5 class="mb-0 fw-bold"><i class="bi bi-send-check"></i> Broadcast a Live Alert</h5>
                            </div>
                            <div class="card-body">
                                <form method="POST">
                                    <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                    <div class="row g-3">
                                        <div class="col-md-9">
                                            <label class="form-label">Message <span class="text-danger">*</span></label>
                                            <textarea name="body" class="form-control form-control-lg" rows="4" placeholder="e.g. Main Hall closed for the annual general meeting — evening lectures moved to Block C" required></textarea>
                                        </div>
                                        <div class="col-md-3">
                                            <label class="form-label">Tag</label>
                                            <select name="tag" class="form-select">
                                                <option value="ANNOUNCEMENT">Announcement</option>
                                                <option value="REQUEST">Request</option>
                                                <option value="FLASH SALE">Flash Sale</option>
                                                <option value="EVENT">Event</option>
                                            </select>
                                            <label class="form-label mt-3">Title</label>
                                            <input type="text" name="title" class="form-control" placeholder="Live Announcement">
                                            <div class="form-text">Optional; defaults to "Live Announcement".</div>
                                        </div>
                                        <div class="col-12">
                                            <button type="submit" class="btn btn-danger btn-lg w-100" data-confirm="Send this live alert to EVERY student device right now?">
                                                <i class="bi bi-send-check"></i> PUSH TO ALL STUDENT DEVICES
                                            </button>
                                        </div>
                                    </div>
                                </form>
                            </div>
                        </div>
                    </div>
                    <div class="col-lg-4">
                        <div class="card mb-4">
                            <div class="card-header"><h5 class="mb-0 fw-bold"><i class="bi bi-info-circle"></i> How it works</h5></div>
                            <div class="card-body small text-muted">
                                <ol class="mb-0 ps-3">
                                    <li class="mb-2"><b>Firestore</b> writes one <code>pulse</code> document with the exact field shape the app's real-time listener reads — it pops on the top of every open Pulse screen immediately.</li>
                                    <li class="mb-2"><b>FCM push</b> sends a sound notification to every registered device token (banned accounts are skipped), so it pops even when students are on other screens or the app is closed.</li>
                                    <li class="mb-2"><b>MySQL</b> (<code>campus_alerts</code>) keeps it in the REST feed so it survives a pull-to-refresh.</li>
                                    <li>Firestore/FCM need a valid <code>secrets.php</code> + <code>service-account.json</code> and live internet; MySQL always works and stays authoritative.</li>
                                </ol>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <?php include 'includes/footer.php'; ?>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="assets/js/main.js"></script>
</body>
</html>
