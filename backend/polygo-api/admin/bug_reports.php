<?php
require_once 'config/database.php';
require_once __DIR__ . '/../NotificationManager.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && !verifyCsrf()) {
    $error = 'Your session expired. Refresh the page and try again.';
} elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $id = (int)($_POST['id'] ?? 0);
    $status = (string)($_POST['status'] ?? '');
    if ($id < 1 || !in_array($status, ['new', 'triaged', 'fixed'], true)) {
        $error = 'Invalid bug report action.';
    } else {
        $update = $pdo->prepare('UPDATE bug_reports SET status = ? WHERE id = ?');
        $update->execute([$status, $id]);
        if ($update->rowCount() > 0) {
            auditAdminAction($pdo, 'update_bug_status', 'bug_report', $id, 'Status -> ' . $status);
            if ($status === 'fixed') {
                $owner = $pdo->prepare('SELECT user_id FROM bug_reports WHERE id = ?');
                $owner->execute([$id]);
                $ownerId = (int)$owner->fetchColumn();
                if ($ownerId > 0) {
                    NotificationManager::sendToUser($pdo, $ownerId, 'Bug report update',
                        'Your PolyGo+ bug report has been marked fixed. Thank you for helping us improve.',
                        ['type' => 'bug_report', 'report_id' => (string)$id]);
                }
            }
            $message = 'Bug report #' . $id . ' marked ' . $status . '.';
        } else {
            $error = 'Bug report not found or already updated.';
        }
    }
}

$filter = (string)($_GET['status'] ?? '');
$allowedFilters = ['', 'new', 'triaged', 'fixed'];
if (!in_array($filter, $allowedFilters, true)) $filter = '';
$sql = 'SELECT b.*, u.full_name, u.email FROM bug_reports b LEFT JOIN users u ON u.id = b.user_id';
$params = [];
if ($filter !== '') {
    $sql .= ' WHERE b.status = ?';
    $params[] = $filter;
}
$sql .= ' ORDER BY b.created_at DESC LIMIT 200';
$query = $pdo->prepare($sql);
$query->execute($params);
$reports = $query->fetchAll();

function localUploadUrl(string $url): string {
    $path = (string)(parse_url($url, PHP_URL_PATH) ?: '');
    if (!preg_match('#/uploads/[^/]+$#', $path)) return '';
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    return $scheme . '://' . $_SERVER['HTTP_HOST'] . $path;
}
?>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Bug Reports - PolyGo+ Admin</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
    <link href="assets/css/style.css" rel="stylesheet">
</head>
<body>
<div class="d-flex" id="wrapper">
    <?php include 'includes/sidebar.php'; ?>
    <div id="page-content-wrapper">
        <?php include 'includes/header.php'; ?>
        <div class="container-fluid px-4 py-3">
            <h3 class="fw-bold"><i class="bi bi-bug"></i> Bug Reports</h3>
            <p class="text-muted">Triage reports sent from Account settings in the Android app.</p>
            <?php if ($message): ?><div class="alert alert-success"><?php echo htmlspecialchars($message); ?></div><?php endif; ?>
            <?php if ($error): ?><div class="alert alert-danger"><?php echo htmlspecialchars($error); ?></div><?php endif; ?>
            <form method="get" class="mb-3" style="max-width:320px">
                <select class="form-select" name="status" onchange="this.form.submit()">
                    <option value="">All statuses</option>
                    <?php foreach (['new', 'triaged', 'fixed'] as $status): ?>
                        <option value="<?php echo $status; ?>" <?php echo $filter === $status ? 'selected' : ''; ?>><?php echo ucfirst($status); ?></option>
                    <?php endforeach; ?>
                </select>
            </form>
            <div class="card border-0 shadow-sm"><div class="table-responsive">
                <table class="table table-hover align-middle mb-0">
                    <thead><tr><th>ID</th><th>Reporter</th><th>Problem</th><th>Context</th><th>Status</th><th>Actions</th></tr></thead>
                    <tbody>
                    <?php foreach ($reports as $report): ?>
                        <tr>
                            <td>#<?php echo (int)$report['id']; ?><small class="d-block text-muted"><?php echo htmlspecialchars($report['created_at']); ?></small></td>
                            <td><?php echo htmlspecialchars($report['full_name'] ?? 'Deleted user'); ?><small class="d-block text-muted"><?php echo htmlspecialchars($report['email'] ?? ''); ?></small></td>
                            <td style="min-width:280px"><details><summary><?php echo htmlspecialchars(mb_strimwidth($report['description'], 0, 90, '…')); ?></summary><p class="mt-2 mb-0" style="white-space:pre-wrap"><?php echo htmlspecialchars($report['description']); ?></p></details></td>
                            <td><small><?php echo htmlspecialchars(trim(($report['screen'] ?? '') . ' · ' . ($report['app_version'] ?? '') . ' · ' . ($report['device_model'] ?? '') . ' · Android ' . ($report['os_version'] ?? ''), ' ·')); ?></small><?php $shot = localUploadUrl((string)($report['screenshot_url'] ?? '')); if ($shot): ?><a class="d-block" href="<?php echo htmlspecialchars($shot); ?>" target="_blank" rel="noopener">View screenshot</a><?php endif; ?></td>
                            <td><span class="badge bg-<?php echo $report['status'] === 'fixed' ? 'success' : ($report['status'] === 'triaged' ? 'warning' : 'danger'); ?>"><?php echo htmlspecialchars(ucfirst($report['status'])); ?></span></td>
                            <td><form method="post" class="d-flex gap-1"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>"><input type="hidden" name="id" value="<?php echo (int)$report['id']; ?>"><button class="btn btn-sm btn-outline-warning" name="status" value="triaged">Triaged</button><button class="btn btn-sm btn-success" name="status" value="fixed">Fixed</button></form></td>
                        </tr>
                    <?php endforeach; ?>
                    <?php if (!$reports): ?><tr><td colspan="6" class="text-center text-muted py-5">No bug reports found.</td></tr><?php endif; ?>
                    </tbody>
                </table>
            </div></div>
        </div>
        <?php include 'includes/footer.php'; ?>
    </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
