<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';
$broadcast = null;

// The pulse feed rendered in the Android app is the campus_alerts table.
// A global broadcast = INSERT here (status approved) + FCM push to every
// device token + a document in Firestore so the pulse beacon can mirror it.
if ($_SERVER['REQUEST_METHOD'] === 'POST' && verifyCsrf()) {
    $action = $_POST['action'] ?? '';
    $id = (int) ($_POST['id'] ?? 0);
    $allowedTags = ['ANNOUNCEMENT', 'REQUEST', 'FLASH SALE', 'EVENT'];

    try {
        if ($action === 'announce') {
            $title = trim($_POST['title'] ?? '');
            $body = trim($_POST['body'] ?? '');
            $tag = in_array($_POST['tag'] ?? '', $allowedTags, true) ? $_POST['tag'] : 'ANNOUNCEMENT';

            if ($title === '' || $body === '') {
                $error = 'Both a title and message body are required.';
            } else {
                $adminId = (int) $pdo->query("SELECT id FROM users WHERE role = 'admin' AND is_banned = 0 ORDER BY id LIMIT 1")->fetchColumn();
                if ($adminId < 1) {
                    $error = 'No admin user in the database to author this announcement. Seed an admin account first.';
                } else {
                    $stmt = $pdo->prepare("INSERT INTO campus_alerts (user_id, user_name, tag, title, body, status, is_global) VALUES (?, 'PolyGo+ Admin', ?, ?, ?, 'approved', 1)");
                    $stmt->execute([$adminId, $tag, $title, $body]);
                    $alertId = (int) $pdo->lastInsertId();

                    require_once __DIR__ . '/../NotificationManager.php';
                    $tokens = $pdo->query("SELECT id FROM users WHERE fcm_token IS NOT NULL AND fcm_token != '' AND is_banned = 0")->fetchAll(PDO::FETCH_COLUMN);
                    $totalTokenUsers = (int) $pdo->query("SELECT COUNT(*) FROM users WHERE fcm_token IS NOT NULL AND fcm_token != ''")->fetchColumn();
                    $skipped = max(0, $totalTokenUsers - count($tokens));
                    $sent = 0;
                    $fail = 0;
                    foreach ($tokens as $uid) {
                        try {
                            if (NotificationManager::sendToUser($pdo, (int) $uid, '📢 ' . $title, $body, ['tag' => $tag, 'type' => 'global_announcement'])) {
                                $sent++;
                            } else {
                                $fail++;
                            }
                        } catch (Throwable $e) {
                            $fail++;
                        }
                    }
                    $fcmMsg = 'pushed to ' . count($tokens) . ' device(s)';

                    $pulseResult = NotificationManager::postFirestore('pulse', [
                        'tag'        => $tag,
                        'title'      => $title,
                        'body'       => $body,
                        'user_name'  => 'PolyGo+ Admin',
                        'status'     => 'approved',
                        'is_global'  => true,
                        'created_at' => time(),
                    ]);
                    if ($pulseResult['status'] >= 200 && $pulseResult['status'] < 300) {
                        $message = 'Announcement #' . $alertId . ' sent: DB + ' . $sent . ' FCM push(es) + Firestore OK' . ($skipped > 0 ? ' (' . $skipped . ' banned account skipped).' : '.');
                    } else {
                        $message = 'Announcement #' . $alertId . ' is live in MySQL + ' . $sent . ' FCM push(es), but the Firestore write failed (HTTP ' . $pulseResult['status'] . '): ' . mb_strimwidth(strip_tags($pulseResult['body']), 0, 120, '…') . '. Check service-account.json / network.' . ($skipped > 0 ? ' (' . $skipped . ' banned account skipped).' : '');
                    }
                }
            }
        } elseif ($action === 'hide' && $id) {
            $stmt = $pdo->prepare("UPDATE campus_alerts SET status = 'hidden' WHERE id = ?");
            $stmt->execute([$id]);
            $message = 'Thread hidden from the app.';
        } elseif ($action === 'approve' && $id) {
            $stmt = $pdo->prepare("UPDATE campus_alerts SET status = 'approved' WHERE id = ?");
            $stmt->execute([$id]);
            $message = 'Thread approved and visible in the app.';
        } elseif ($action === 'delete' && $id) {
            $stmt = $pdo->prepare("DELETE FROM campus_alerts WHERE id = ?");
            $stmt->execute([$id]);
            $message = 'Thread deleted.';
        }
    } catch (Throwable $e) {
        $error = 'Action failed: ' . $e->getMessage();
    }
}

try {
    $alerts = $pdo->query("SELECT id, user_name, tag, title, body, status, created_at FROM campus_alerts WHERE is_global = 0 ORDER BY id DESC LIMIT 100")->fetchAll();
} catch (Throwable $e) {
    $alerts = [];
}

$tagColors = ['ANNOUNCEMENT' => 'primary', 'REQUEST' => 'info', 'FLASH SALE' => 'warning', 'EVENT' => 'success'];
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Announcements - PolyGo+ Admin</title>
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
                        <h3 class="fw-bold"><i class="bi bi-megaphone"></i> Cloud Communications</h3>
                        <p class="page-head-sub">Broadcast global announcements to every student device and moderate the pulse feed</p>
                    </div>
                </div>

                <?php if ($message): ?>
                    <div class="alert alert-success alert-dismissible fade show"><i class="bi bi-check-circle"></i> <?php echo $message; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>
                <?php if ($error): ?>
                    <div class="alert alert-danger alert-dismissible fade show"><i class="bi bi-exclamation-triangle"></i> <?php echo $error; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>

                <div class="card mb-4">
                    <div class="card-header">
                        <h5 class="mb-0 fw-bold"><i class="bi bi-send"></i> New Global Announcement</h5>
                    </div>
                    <div class="card-body">
                        <form method="POST">
                            <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                            <input type="hidden" name="action" value="announce">
                            <div class="row g-3">
                                <div class="col-md-8">
                                    <label class="form-label">Title</label>
                                    <input type="text" class="form-control" name="title" placeholder="e.g. Midterm sale this Friday!" required>
                                </div>
                                <div class="col-md-4">
                                    <label class="form-label">Tag</label>
                                    <select name="tag" class="form-select">
                                        <option value="ANNOUNCEMENT">Announcement</option>
                                        <option value="REQUEST">Request</option>
                                        <option value="FLASH SALE">Flash Sale</option>
                                        <option value="EVENT">Event</option>
                                    </select>
                                </div>
                                <div class="col-12">
                                    <label class="form-label">Message</label>
                                    <textarea name="body" class="form-control" rows="4" placeholder="Write the announcement body…" required></textarea>
                                    <div class="form-text">Sends once to every device token, writes to MySQL <code>campus_alerts</code> and the Firestore <code>pulse</code> collection.</div>
                                </div>
                                <div class="col-12">
                                    <button type="submit" class="btn btn-primary"><i class="bi bi-send"></i> Broadcast to All Students</button>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header">
                        <h5 class="mb-0 fw-bold"><i class="bi bi-chat-dots"></i> Pulse Feed — Thread Moderation</h5>
                    </div>
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead>
                                    <tr><th>#</th><th>Thread</th><th>Tag</th><th>Author</th><th>Status</th><th>Posted</th><th>Actions</th></tr>
                                </thead>
                                <tbody>
                                    <?php if (count($alerts) > 0): ?>
                                        <?php foreach ($alerts as $a): ?>
                                            <tr>
                                                <td><?php echo (int) $a['id']; ?></td>
                                                <td>
                                                    <div class="fw-semibold"><?php echo htmlspecialchars($a['title']); ?></div>
                                                    <small class="text-muted text-break"><?php echo htmlspecialchars(mb_strimwidth($a['body'], 0, 90, '…')); ?></small>
                                                </td>
                                                <td><span class="badge bg-<?php echo $tagColors[$a['tag']] ?? 'secondary'; ?>"><?php echo htmlspecialchars($a['tag']); ?></span></td>
                                                <td><?php echo htmlspecialchars($a['user_name'] ?? 'Unknown'); ?></td>
                                                <td>
                                                    <?php echo match ($a['status']) {
                                                        'approved' => '<span class="badge bg-success">Visible</span>',
                                                        'hidden'   => '<span class="badge bg-danger">Hidden</span>',
                                                        default    => '<span class="badge bg-warning">Pending</span>',
                                                    }; ?>
                                                </td>
                                                <td><?php echo date('d/m/Y H:i', strtotime($a['created_at'])); ?></td>
                                                <td>
                                                    <div class="btn-group btn-group-sm">
                                                        <?php if ($a['status'] !== 'approved'): ?>
                                                            <form method="POST" class="d-inline">
                                                                <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="id" value="<?php echo (int) $a['id']; ?>">
                                                                <input type="hidden" name="action" value="approve">
                                                                <button type="submit" class="btn btn-success" title="Show in app"><i class="bi bi-check2-circle"></i></button>
                                                            </form>
                                                        <?php endif; ?>
                                                        <?php if ($a['status'] === 'approved'): ?>
                                                            <form method="POST" class="d-inline">
                                                                <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="id" value="<?php echo (int) $a['id']; ?>">
                                                                <input type="hidden" name="action" value="hide">
                                                                <button type="submit" class="btn btn-warning" title="Hide from app" data-confirm="Hide this thread from the app?"><i class="bi bi-eye-slash"></i></button>
                                                            </form>
                                                        <?php endif; ?>
                                                        <form method="POST" class="d-inline">
                                                            <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                            <input type="hidden" name="id" value="<?php echo (int) $a['id']; ?>">
                                                            <input type="hidden" name="action" value="delete">
                                                            <button type="submit" class="btn btn-danger" title="Delete thread" data-confirm="Delete this thread permanently?"><i class="bi bi-trash"></i></button>
                                                        </form>
                                                    </div>
                                                </td>
                                            </tr>
                                        <?php endforeach; ?>
                                    <?php else: ?>
                                        <tr><td colspan="7" class="text-center text-muted py-4"><i class="bi bi-chat-dots fs-2 d-block mb-2"></i>No pulse threads yet</td></tr>
                                    <?php endif; ?>
                                </tbody>
                            </table>
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
