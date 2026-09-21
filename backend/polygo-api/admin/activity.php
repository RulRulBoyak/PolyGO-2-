<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
try {
    $entries = $pdo->query('SELECT action_name, entity_type, entity_id, details, actor_label, ip_address, created_at FROM admin_audit_log ORDER BY id DESC LIMIT 200')->fetchAll();
} catch (Throwable $e) {
    $entries = [];
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Activity Log - PolyGo+ Admin</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
    <link href="assets/css/style.css" rel="stylesheet">
</head>
<body>
    <a class="skip-link" href="#activity">Skip to activity log</a>
    <div class="d-flex" id="wrapper">
        <?php include 'includes/sidebar.php'; ?>
        <div id="page-content-wrapper">
            <?php include 'includes/header.php'; ?>
            <main id="activity" class="container-fluid px-4">
                <div class="row mt-3 mb-4"><div class="col-12">
                    <h3 class="fw-bold"><i class="bi bi-journal-text"></i> Administrative activity</h3>
                    <p class="page-head-sub">A review trail for moderation decisions. Records capture the action, affected item, time and session context—not passwords or form contents.</p>
                </div></div>
                <div class="alert alert-info"><i class="bi bi-info-circle"></i> Use the least invasive action that resolves a problem. Restrictions and archives are reversible; permanent deletion is not offered here.</div>
                <div class="card"><div class="card-header d-flex justify-content-between align-items-center">
                    <h5 class="mb-0 fw-bold">Latest 200 events</h5><span class="text-muted small">Read-only</span>
                </div><div class="table-responsive"><table class="table table-hover mb-0">
                    <caption class="visually-hidden">Administrative moderation and management events</caption>
                    <thead><tr><th scope="col">When</th><th scope="col">Action</th><th scope="col">Record</th><th scope="col">Context</th><th scope="col">Operator</th></tr></thead>
                    <tbody><?php if ($entries): foreach ($entries as $entry): ?><tr>
                        <td class="text-nowrap"><?php echo htmlspecialchars(date('d M Y H:i', strtotime($entry['created_at']))); ?></td>
                        <td><span class="badge bg-primary"><?php echo htmlspecialchars(str_replace('_', ' ', $entry['action_name'])); ?></span></td>
                        <td><?php echo htmlspecialchars($entry['entity_type']); ?><?php if ($entry['entity_id']): ?> <span class="text-muted">#<?php echo (int) $entry['entity_id']; ?></span><?php endif; ?></td>
                        <td><?php echo htmlspecialchars($entry['details'] ?: '—'); ?></td>
                        <td><?php echo htmlspecialchars($entry['actor_label']); ?></td>
                    </tr><?php endforeach; else: ?><tr><td colspan="5" class="text-center text-muted py-4">No recorded actions yet.</td></tr><?php endif; ?></tbody>
                </table></div></div>
            </main>
            <?php include 'includes/footer.php'; ?>
        </div>
    </div>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="assets/js/main.js"></script>
</body>
</html>
