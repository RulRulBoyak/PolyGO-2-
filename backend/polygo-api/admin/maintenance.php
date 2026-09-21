<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();

function settingValue(PDO $pdo, string $key): string {
    try {
        $stmt = $pdo->prepare("SELECT setting_value FROM app_settings WHERE setting_key = ?");
        $stmt->execute([$key]);
        return (string) $stmt->fetchColumn();
    } catch (Throwable $e) {
        return '';
    }
}

$maintenance = settingValue($pdo, 'maintenance') === '1';
$message = '';
$error = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && verifyCsrf()) {
    $mode = ($_POST['mode'] ?? '0') === '1' ? '1' : '0';
    $notice = trim((string) ($_POST['maintenance_message'] ?? ''));
    $homeMessages = trim((string) ($_POST['home_messages'] ?? ''));
    $homeInterval = max(5, min(60, (int)($_POST['home_message_interval_seconds'] ?? 8)));
    if (mb_strlen($notice) > 500) {
        $notice = mb_substr($notice, 0, 500);
    }
    try {
        $upsert = $pdo->prepare('INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)');
        $upsert->execute(['maintenance', $mode]);
        $upsert->execute(['maintenance_message', $notice]);
        $lines = array_slice(array_values(array_filter(array_map('trim',
            preg_split('/\R/', $homeMessages) ?: []))), 0, 12);
        $lines = array_map(static fn($line) => mb_substr($line, 0, 100), $lines);
        $upsert->execute(['home_messages', implode("\n", $lines)]);
        $upsert->execute(['home_message_interval_seconds', (string)$homeInterval]);
        auditAdminAction($pdo, 'set_maintenance_mode', 'system', null, $mode === '1' ? 'ON' : 'OFF');
        $maintenance = $mode === '1';
        $message = $mode === '1'
            ? 'Maintenance mode is now ACTIVE — all student apps will show the maintenance screen on next launch.'
            : 'Maintenance mode turned OFF — the app is live again.';
    } catch (Throwable $e) {
        $error = 'Could not save the maintenance flag: ' . $e->getMessage();
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Maintenance - PolyGo+ Admin</title>
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
                        <h3 class="fw-bold"><i class="bi bi-tools"></i> Maintenance Mode — Kill Switch</h3>
                        <p class="page-head-sub">Lock the whole app with one switch. status.php tells every student device to show the maintenance screen until you flip it back.</p>
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
                                <h5 class="mb-0 fw-bold"><i class="bi bi-speedometer2"></i> Current state</h5>
                            </div>
                            <div class="card-body">
                                <?php if ($maintenance): ?>
                                    <div class="alert alert-danger mb-3">
                                        <div class="d-flex align-items-center">
                                            <i class="bi bi-exclamation-octagon fs-2 me-3"></i>
                                            <div>
                                                <strong>MAINTENANCE IS ACTIVE</strong>
                                                <div class="small mt-1">Every student app checks status.php and locks to the maintenance screen. The optional message below is shown verbatim.</div>
                                            </div>
                                        </div>
                                    </div>
                                <?php else: ?>
                                    <div class="alert alert-success mb-3">
                                        <div class="d-flex align-items-center">
                                            <i class="bi bi-check-circle fs-2 me-3"></i>
                                            <div>
                                                <strong>APP IS LIVE</strong>
                                                <div class="small mt-1">status.php reports maintenance = false. Normal operation.</div>
                                            </div>
                                        </div>
                                    </div>
                                <?php endif; ?>

                                <form method="POST">
                                    <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                    <div class="row g-3">
                                        <div class="col-md-6">
                                            <label class="form-label">Mode</label>
                                            <select name="mode" class="form-select">
                                                <option value="0" <?php echo $maintenance ? '' : 'selected'; ?>>Live — app is open</option>
                                                <option value="1" <?php echo $maintenance ? 'selected' : ''; ?>>Maintenance — lock the app</option>
                                            </select>
                                            <div class="form-text">Flipping to maintenance locks the app at next launch / resume.</div>
                                        </div>
                                        <div class="col-md-8">
                                            <label class="form-label">Rotating Home messages</label>
                                            <textarea name="home_messages" class="form-control" rows="5" maxlength="1212" placeholder="One message or joke per line"><?php echo htmlspecialchars(settingValue($pdo, 'home_messages')); ?></textarea>
                                            <div class="form-text">Up to 12 lines. Empty uses the app’s localized defaults.</div>
                                        </div>
                                        <div class="col-md-4">
                                            <label class="form-label">Rotation timer (seconds)</label>
                                            <input name="home_message_interval_seconds" class="form-control" type="number" min="5" max="60" value="<?php echo (int)(settingValue($pdo, 'home_message_interval_seconds') ?: 8); ?>">
                                            <div class="form-text">Allowed range: 5–60 seconds.</div>
                                        </div>
                                        <div class="col-md-6">
                                            <label class="form-label">Message shown to students (optional)</label>
                                            <textarea name="maintenance_message" class="form-control" rows="3" maxlength="500" placeholder="e.g. Campus database upgrade — back online by 5pm."><?php echo htmlspecialchars(settingValue($pdo, 'maintenance_message')); ?></textarea>
                                        </div>
                                        <div class="col-12">
                                            <button type="submit" class="btn btn-lg btn-<?php echo $maintenance ? 'success' : 'danger'; ?> w-100"
                                                    data-confirm="Turn <?php echo $maintenance ? 'maintenance OFF and bring the app back online' : 'maintenance ON and lock every student app'; ?>?">
                                                <i class="bi bi-power"></i> <?php echo $maintenance ? 'Disable Maintenance — Go Live' : 'Enable Maintenance — Lock App'; ?>
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
                                    <li class="mb-2">The button writes the flag to <code>app_settings</code> (shared MariaDB database — no permanent app change).</li>
                                    <li class="mb-2"><b>status.php</b> reads it and reports <code>maintenance: true</code> to every device.</li>
                                    <li class="mb-2">The Android app checks <code>status.php</code> at launch, on Home resume and while retrying the error screen — all logged-in students lock to the maintenance screen, and unlock automatically when you turn it off.</li>
                                    <li class="mb-0">The optional message is displayed verbatim on the student device; leave it empty for the default copy.</li>
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
