<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
$message = (string)($_SESSION['broadcast_message'] ?? '');
$error = (string)($_SESSION['broadcast_error'] ?? '');
unset($_SESSION['broadcast_message'], $_SESSION['broadcast_error']);
$broadcastNonce = (string)($_SESSION['remote_broadcast_nonce'] ?? '');
if ($broadcastNonce === '') {
    $broadcastNonce = bin2hex(random_bytes(16));
    $_SESSION['remote_broadcast_nonce'] = $broadcastNonce;
}

// Live Alert "Remote Control". One admin-typed message, two synchronized
// delivery paths so it pops on every student's phone instantly:
//   1. campus_alerts INSERT  -> survives the app's pull-to-refresh (REST feed
//                              rebuilds from the DB) and shows on the Pulse list.
//   2. FCM push              -> system notification on every registered token
//                              where is_banned = 0, even with the app closed.
if ($_SERVER['REQUEST_METHOD'] === 'POST' && !verifyCsrf()) {
    $error = 'Your session expired. Refresh the page and try again.';
} elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $title = trim($_POST['title'] ?? '');
    $body = trim($_POST['body'] ?? '');
    $tag = in_array($_POST['tag'] ?? '', ['ANNOUNCEMENT', 'REQUEST', 'FLASH SALE', 'EVENT'], true) ? $_POST['tag'] : 'ANNOUNCEMENT';
    $submittedNonce = (string)($_POST['broadcast_nonce'] ?? '');

    if ($submittedNonce === '' || !hash_equals($broadcastNonce, $submittedNonce)) {
        $error = 'This alert was already processed. Start a new alert to send again.';
    } elseif ($body === '' || mb_strlen($body) > 2000 || mb_strlen($title) > 150) {
        $error = 'Write a message before broadcasting.';
    } else {
        unset($_SESSION['remote_broadcast_nonce']);
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

                $tokens = $pdo->query("SELECT id FROM users WHERE fcm_token IS NOT NULL AND fcm_token != '' AND is_banned = 0")->fetchAll(PDO::FETCH_COLUMN);
                $totalTokenUsers = (int) $pdo->query("SELECT COUNT(*) FROM users WHERE fcm_token IS NOT NULL AND fcm_token != ''")->fetchColumn();
                $skipped = max(0, $totalTokenUsers - count($tokens));
                $sent = 0;
                $fail = 0;
                foreach ($tokens as $uid) {
                    try {
                        if (NotificationManager::sendToUser($pdo, (int) $uid, '📢 ' . $title, $body, [
                            'tag' => $tag,
                            'type' => 'live_alert',
                            'message_id' => 'announcement_' . $alertId,
                        ])) {
                            $sent++;
                        } else {
                            $fail++;
                        }
                    } catch (Throwable $e) {
                        $fail++;
                    }
                }

                auditAdminAction($pdo, 'broadcast_alert', 'campus_alert', $alertId, $tag . ': ' . $title);
                $message = 'Live alert #' . $alertId . ' broadcast: ' . $sent . ' sound notification(s) sent'
                    . ($fail > 0 ? ', ' . $fail . ' failed' : '')
                    . ($skipped > 0 ? ' · ' . $skipped . ' banned account skipped.' : '.');
            }
        } catch (Throwable $e) {
            $error = 'Broadcast failed: ' . $e->getMessage();
        }
    }
}

// POST/Redirect/GET prevents browser refresh from replaying a live alert.
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $_SESSION['broadcast_message'] = $message;
    $_SESSION['broadcast_error'] = $error;
    header('Location: admin_broadcast.php');
    exit;
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
                    <div class="alert alert-success alert-dismissible fade show"><i class="bi bi-check-circle"></i> <?php echo htmlspecialchars($message); ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>
                <?php if ($error): ?>
                    <div class="alert alert-danger alert-dismissible fade show"><i class="bi bi-exclamation-triangle"></i> <?php echo htmlspecialchars($error); ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
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
                                    <input type="hidden" name="broadcast_nonce" value="<?php echo htmlspecialchars($broadcastNonce); ?>">
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
                                    <li class="mb-2"><b>FCM push</b> sends a sound notification to every registered device token (banned accounts are skipped), so it pops even when students are on other screens or the app is closed.</li>
                                    <li class="mb-2"><b>MySQL</b> (<code>campus_alerts</code>) keeps it in the REST feed so it survives a pull-to-refresh.</li>
                                    <li>FCM needs a valid <code>service-account.json</code> and live internet; MySQL always works and stays authoritative.</li>
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
