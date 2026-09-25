<?php
require_once 'config/database.php';
require_once __DIR__ . '/../NotificationManager.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';

// Photos are stored as absolute URLs from the emulator's perspective
// (http://10.0.2.2/polygo-api/uploads/...). Rewrite origin to the host this
// admin panel is served from so the <img> resolves locally.
if (!function_exists('adminPhotoUrl')) {
    function adminPhotoUrl(?string $photo): string {
        $photo = trim((string) $photo);
        if ($photo === '') return '';
        $parts = parse_url($photo);
        if ($parts === false || empty($parts['scheme']) || empty($parts['host'])) {
            return $photo;
        }
        $path = $parts['path'] ?? '';
        if (!empty($parts['query'])) $path .= '?' . $parts['query'];
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
        return $scheme . '://' . $_SERVER['HTTP_HOST'] . '/' . ltrim($path, '/');
    }
}

// Approval state lives in users.is_verified/verification_status (the Android
// app reads these) and is mirrored into verification_requests for the audit log.
if ($_SERVER['REQUEST_METHOD'] === 'POST' && verifyCsrf()) {
    $action = $_POST['action'] ?? '';
    $user_id = (int) ($_POST['user_id'] ?? 0);

    try {
        if ($action === 'approve' && $user_id) {
            $pdo->beginTransaction();
            $stmt = $pdo->prepare("UPDATE users SET is_verified = 1, verification_status = 'approved', updated_at = NOW() WHERE id = ?");
            $stmt->execute([$user_id]);
            $stmt = $pdo->prepare("UPDATE verification_requests SET status = 'approved' WHERE user_id = ? AND status = 'pending'");
            $stmt->execute([$user_id]);
            $pdo->commit();
            auditAdminAction($pdo, 'approve_verification', 'user', $user_id, 'Matrix card approved');
            NotificationManager::sendToUser($pdo, $user_id, 'Account Verified',
                'Your Matrix Card was approved. You can now post listings.', ['type' => 'verification']);
            $message = 'Verification approved for user #' . $user_id . '.';
        } elseif ($action === 'reject' && $user_id) {
            $pdo->beginTransaction();
            $stmt = $pdo->prepare("UPDATE users SET is_verified = 0, verification_status = 'rejected', updated_at = NOW() WHERE id = ?");
            $stmt->execute([$user_id]);
            $stmt = $pdo->prepare("UPDATE verification_requests SET status = 'rejected' WHERE user_id = ? AND status = 'pending'");
            $stmt->execute([$user_id]);
            $pdo->commit();
            auditAdminAction($pdo, 'reject_verification', 'user', $user_id, 'Matrix card rejected');
            NotificationManager::sendToUser($pdo, $user_id, 'Verification Rejected',
                'Your Matrix Card was rejected. Please resubmit from the verification screen.', ['type' => 'verification']);
            $message = 'Verification rejected for user #' . $user_id . '.';
        }
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        $error = 'Action failed. Please try again.';
    }
}

try {
    $pending = $pdo->query(
        "SELECT u.id, u.full_name, u.student_id, u.email, u.verification_photo, u.verification_status, u.created_at
         FROM users u WHERE u.role != 'admin' AND u.verification_status = 'pending'
         ORDER BY u.updated_at DESC, u.created_at DESC LIMIT 50"
    )->fetchAll();
} catch (Throwable $e) {
    $pending = [];
}

try {
    $processed = $pdo->query(
        "SELECT u.id, u.full_name, u.student_id, u.email, u.verification_status, u.updated_at
         FROM users u WHERE u.role != 'admin' AND u.verification_status IN ('approved', 'rejected')
         ORDER BY COALESCE(u.updated_at, u.created_at) DESC LIMIT 10"
    )->fetchAll();
} catch (Throwable $e) {
    $processed = [];
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Verification - PolyGo+ Admin</title>
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
                        <h3 class="fw-bold"><i class="bi bi-patch-check"></i> Verification Bridge</h3>
                        <p class="page-head-sub">Approve or reject student verification photos. Verified sellers get a badge on their listings.</p>
                    </div>
                </div>

                <?php if ($message): ?>
                    <div class="alert alert-success alert-dismissible fade show"><i class="bi bi-check-circle"></i> <?php echo $message; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>
                <?php if ($error): ?>
                    <div class="alert alert-danger alert-dismissible fade show"><i class="bi bi-exclamation-triangle"></i> <?php echo $error; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>

                <?php if (count($pending) === 0): ?>
                    <div class="alert alert-info"><i class="bi bi-check2-circle"></i> All caught up — no pending verification requests.</div>
                <?php endif; ?>

                <div class="row g-4">
                    <?php foreach ($pending as $u): ?>
                        <div class="col-xl-4 col-md-6">
                            <div class="card">
                                <div class="card-body">
                                    <div class="d-flex align-items-center mb-3">
                                        <div class="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center me-2" style="width:44px;height:44px;font-size:16px;font-weight:bold;">
                                            <?php echo strtoupper(substr($u['full_name'] ?? '?', 0, 1)); ?>
                                        </div>
                                        <div>
                                            <div class="fw-semibold"><?php echo htmlspecialchars($u['full_name'] ?? ''); ?></div>
                                            <small class="text-muted"><?php echo htmlspecialchars($u['email']); ?> · <?php echo htmlspecialchars($u['student_id'] ?? '-'); ?></small>
                                        </div>
                                        <span class="badge bg-warning ms-auto"><i class="bi bi-hourglass-split"></i> Pending</span>
                                    </div>

                                    <?php $photo = adminPhotoUrl($u['verification_photo']); ?>
                                    <?php if ($photo !== ''): ?>
                                        <a href="<?php echo htmlspecialchars($photo); ?>" target="_blank" rel="noopener" class="d-block mb-3">
                                            <img src="<?php echo htmlspecialchars($photo); ?>" alt="Verification photo" class="verify-photo w-100" style="max-width:100%;max-height:180px;object-fit:cover;">
                                        </a>
                                        <small class="text-muted d-block mb-3"><i class="bi bi-link-45deg"></i> Click photo to view full size</small>
                                    <?php else: ?>
                                        <div class="alert alert-warning py-2"><i class="bi bi-image"></i> No verification photo uploaded</div>
                                    <?php endif; ?>

                                    <form method="POST" class="d-flex gap-2">
                                        <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                        <input type="hidden" name="user_id" value="<?php echo (int) $u['id']; ?>">
                                        <button type="submit" name="action" value="reject" class="btn btn-outline-danger flex-fill" data-confirm="Reject this verification?"><i class="bi bi-x-circle"></i> Reject</button>
                                        <button type="submit" name="action" value="approve" class="btn btn-success flex-fill" data-confirm="Approve this verification?"><i class="bi bi-check-circle"></i> Approve</button>
                                    </form>
                                </div>
                            </div>
                        </div>
                    <?php endforeach; ?>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="mb-0 fw-bold"><i class="bi bi-clock-history"></i> Recently Processed</h5>
                    </div>
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead>
                                    <tr><th>Member</th><th>Email</th><th>Result</th><th>Decided</th></tr>
                                </thead>
                                <tbody>
                                    <?php if ($processed): ?>
                                        <?php foreach ($processed as $u): ?>
                                            <tr>
                                                <td>
                                                    <span class="fw-semibold"><?php echo htmlspecialchars($u['full_name'] ?? ''); ?></span>
                                                    <small class="d-block text-muted"><?php echo htmlspecialchars($u['student_id'] ?? '-'); ?></small>
                                                </td>
                                                <td><?php echo htmlspecialchars($u['email']); ?></td>
                                                <td>
                                                    <?php if ($u['verification_status'] === 'approved'): ?>
                                                        <span class="badge bg-success"><i class="bi bi-patch-check"></i> Approved</span>
                                                    <?php else: ?>
                                                        <span class="badge bg-danger">Rejected</span>
                                                    <?php endif; ?>
                                                </td>
                                                <td><?php echo date('d/m/Y H:i', strtotime($u['updated_at'])); ?></td>
                                            </tr>
                                        <?php endforeach; ?>
                                    <?php else: ?>
                                        <tr><td colspan="4" class="text-center text-muted py-3">Nothing processed yet</td></tr>
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
