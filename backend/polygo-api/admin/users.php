<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';

// Real schema gates: role 'Student'/'admin', is_verified, verification_status,
// is_banned. Banning archives a user's listings and blocks their JWTs via the
// API's verify_jwt() check (../config.php isUserBanned).
if ($_SERVER['REQUEST_METHOD'] === 'POST' && verifyCsrf()) {
    $action = $_POST['action'] ?? '';
    $user_id = (int) ($_POST['user_id'] ?? 0);

    try {
        if ($action === 'verify' && $user_id) {
            $stmt = $pdo->prepare("UPDATE users SET is_verified = 1, verification_status = 'approved', updated_at = NOW() WHERE id = ? AND role != 'admin'");
            $stmt->execute([$user_id]);
            auditAdminAction($pdo, 'verify_user', 'user', $user_id, 'Verification approved');
            $message = 'User verified successfully.';
        } elseif ($action === 'suspend' && $user_id) {
            $stmt = $pdo->prepare("UPDATE users SET is_verified = 0, verification_status = 'unverified', updated_at = NOW() WHERE id = ? AND role != 'admin'");
            $stmt->execute([$user_id]);
            auditAdminAction($pdo, 'suspend_verification', 'user', $user_id, 'Verification reset');
            $message = 'User verification suspended.';
        } elseif ($action === 'ban' && $user_id) {
            $pdo->beginTransaction();
            $stmt = $pdo->prepare("UPDATE listings SET archived_at = NOW(), is_available = 0 WHERE owner_id = ? AND archived_at IS NULL");
            $stmt->execute([$user_id]);
            $stmt = $pdo->prepare("UPDATE users SET is_banned = 1, is_verified = 0, banned_at = NOW(), updated_at = NOW() WHERE id = ? AND role != 'admin'");
            $stmt->execute([$user_id]);
            $pdo->commit();
            auditAdminAction($pdo, 'ban_user', 'user', $user_id, 'Listings archived and sign-in blocked');
            $message = 'User banned and all their listings archived.';
        } elseif ($action === 'unban' && $user_id) {
            $stmt = $pdo->prepare("UPDATE users SET is_banned = 0, banned_at = NULL, updated_at = NOW() WHERE id = ? AND role != 'admin'");
            $stmt->execute([$user_id]);
            auditAdminAction($pdo, 'unban_user', 'user', $user_id, 'Account access restored; listings remain archived');
            $message = 'User unbanned.';
        } elseif ($action === 'delete' && $user_id) {
            // Retain account evidence; use a reversible ban rather than deleting student data.
            $stmt = $pdo->prepare("UPDATE users SET is_banned = 1, is_verified = 0, banned_at = NOW(), updated_at = NOW() WHERE id = ? AND role != 'admin'");
            $stmt->execute([$user_id]);
            auditAdminAction($pdo, 'restrict_user', 'user', $user_id, 'Legacy delete request converted to reversible restriction');
            $message = 'User access restricted. The account was retained for review rather than deleted.';
        }
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        $error = 'Action failed. Please try again.';
    }
}

$search = trim($_GET['search'] ?? '');
$filter = $_GET['filter'] ?? '';

$query = "SELECT id, full_name, student_id, email, is_verified, verification_status, is_banned, banned_at, created_at
          FROM users WHERE role != 'admin'";
$params = [];

if ($search !== '') {
    $query .= " AND (full_name LIKE ? OR email LIKE ? OR student_id LIKE ?)";
    $params = array_merge($params, ["%$search%", "%$search%", "%$search%"]);
}

$query .= match ($filter) {
    'verified'    => " AND is_verified = 1 AND verification_status = 'approved'",
    'pending'     => " AND verification_status = 'pending'",
    'banned'      => " AND is_banned = 1",
    default       => '',
};
$query .= ' ORDER BY created_at DESC';

$stmt = $pdo->prepare($query);
$stmt->execute($params);
$users = $stmt->fetchAll();

$stats = [];
foreach (['total' => "SELECT COUNT(*) FROM users WHERE role != 'admin'",
          'verified' => "SELECT COUNT(*) FROM users WHERE role != 'admin' AND is_verified = 1",
          'pending' => "SELECT COUNT(*) FROM users WHERE role != 'admin' AND verification_status = 'pending'",
          'banned' => "SELECT COUNT(*) FROM users WHERE role != 'admin' AND is_banned = 1"] as $key => $sql) {
    try {
        $stats[$key] = (int) $pdo->query($sql)->fetchColumn();
    } catch (Throwable $e) {
        $stats[$key] = 0;
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Users - PolyGo+ Admin</title>
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
                        <h3 class="fw-bold"><i class="bi bi-shield-check"></i> Community Members</h3>
                        <p class="page-head-sub">Manage, verify and enforce bans across the campus marketplace</p>
                    </div>
                </div>

                <div class="row g-4 mb-4">
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card primary h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Total Members</p>
                                        <h2 class="fw-bold"><?php echo $stats['total']; ?></h2>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-people"></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card success h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Verified</p>
                                        <h2 class="fw-bold"><?php echo $stats['verified']; ?></h2>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-patch-check"></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card warning h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Pending Verification</p>
                                        <h2 class="fw-bold"><?php echo $stats['pending']; ?></h2>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-hourglass-split"></i></div>
                                </div>
                                <a href="verification.php" class="text-decoration-none small mt-2 d-inline-block">Review queue <i class="bi bi-arrow-right"></i></a>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card danger h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Banned</p>
                                        <h2 class="fw-bold"><?php echo $stats['banned']; ?></h2>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-person-slash"></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <?php if ($message): ?>
                    <div class="alert alert-success alert-dismissible fade show"><i class="bi bi-check-circle"></i> <?php echo $message; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>
                <?php if ($error): ?>
                    <div class="alert alert-danger alert-dismissible fade show"><i class="bi bi-exclamation-triangle"></i> <?php echo $error; ?><button type="button" class="btn-close" data-bs-dismiss="alert"></button></div>
                <?php endif; ?>

                <div class="card mb-4">
                    <div class="card-body">
                        <form method="GET" class="row g-3">
                            <div class="col-md-6">
                                <input type="text" class="form-control" name="search" placeholder="Search name, email or student ID..."
                                       value="<?php echo htmlspecialchars($search); ?>">
                            </div>
                            <div class="col-md-4">
                                <select class="form-select" name="filter">
                                    <option value="">All Members</option>
                                    <option value="verified" <?php echo $filter === 'verified' ? 'selected' : ''; ?>>Verified</option>
                                    <option value="pending" <?php echo $filter === 'pending' ? 'selected' : ''; ?>>Pending Verification</option>
                                    <option value="banned" <?php echo $filter === 'banned' ? 'selected' : ''; ?>>Banned</option>
                                </select>
                            </div>
                            <div class="col-md-2">
                                <button type="submit" class="btn btn-primary w-100"><i class="bi bi-search"></i> Filter</button>
                            </div>
                        </form>
                    </div>
                </div>

                <div class="card">
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead>
                                    <tr><th>#</th><th>Member</th><th>Email</th><th>Student ID</th><th>Verification</th><th>Account</th><th>Actions</th></tr>
                                </thead>
                                <tbody>
                                    <?php if (count($users) > 0): ?>
                                        <?php $n = 1; ?>
                                        <?php foreach ($users as $u): ?>
                                            <tr>
                                                <td><?php echo $n++; ?></td>
                                                <td>
                                                    <div class="d-flex align-items-center">
                                                        <div class="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center me-2" style="width:36px;height:36px;font-size:14px;font-weight:bold;">
                                                            <?php echo strtoupper(substr($u['full_name'] ?? ($u['student_id'] ?? '?'), 0, 1)); ?>
                                                        </div>
                                                        <div>
                                                            <div class="fw-semibold"><?php echo htmlspecialchars($u['full_name'] ?? ''); ?></div>
                                                            <small class="text-muted">Joined <?php echo date('d/m/Y', strtotime($u['created_at'])); ?></small>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td><?php echo htmlspecialchars($u['email']); ?></td>
                                                <td><?php echo htmlspecialchars($u['student_id'] ?? '-'); ?></td>
                                                <td>
                                                    <?php
                                                    $vBadge = match ($u['verification_status']) {
                                                        'approved' => ['success', 'Verified'],
                                                        'pending'  => ['warning', 'Pending'],
                                                        'rejected' => ['danger', 'Rejected'],
                                                        default    => ['secondary', 'Unverified'],
                                                    };
                                                    ?>
                                                    <span class="badge bg-<?php echo $vBadge[0]; ?>"><i class="bi bi-<?php echo $vBadge[0] === 'success' ? 'patch-check' : 'question-circle'; ?>"></i> <?php echo $vBadge[1]; ?></span>
                                                </td>
                                                <td>
                                                    <?php if ((int) $u['is_banned'] === 1): ?>
                                                        <span class="badge bg-danger"><i class="bi bi-person-slash"></i> Banned<?php if (!empty($u['banned_at'])): ?> · <?php echo date('d M Y', strtotime($u['banned_at'])); ?><?php endif; ?></span>
                                                    <?php elseif ((int) $u['is_verified'] === 1): ?>
                                                        <span class="badge bg-success">Active</span>
                                                    <?php else: ?>
                                                        <span class="badge bg-secondary">Inactive</span>
                                                    <?php endif; ?>
                                                </td>
                                                <td>
                                                    <div class="btn-group btn-group-sm">
                                                        <?php if ($u['verification_status'] !== 'approved'): ?>
                                                            <form method="POST" class="d-inline">
                                                                <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="user_id" value="<?php echo (int) $u['id']; ?>">
                                                                <input type="hidden" name="action" value="verify">
                                                                <button type="submit" class="btn btn-success" title="Approve verification"><i class="bi bi-check2-circle"></i></button>
                                                            </form>
                                                        <?php endif; ?>

                                                        <?php if ((int) $u['is_banned'] === 1): ?>
                                                            <form method="POST" class="d-inline">
                                                                <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="user_id" value="<?php echo (int) $u['id']; ?>">
                                                                <input type="hidden" name="action" value="unban">
                                                                <button type="submit" class="btn btn-warning" title="Unban user" data-confirm="Unban this user?"><i class="bi bi-person-check"></i></button>
                                                            </form>
                                                        <?php else: ?>
                                                            <form method="POST" class="d-inline">
                                                                <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="user_id" value="<?php echo (int) $u['id']; ?>">
                                                                <input type="hidden" name="action" value="ban">
                                                                <button type="submit" class="btn btn-danger" title="Ban user (archives listings, blocks login)" data-confirm="Ban this user? Their listings will be archived and their JWT login revoked."><i class="bi bi-person-slash"></i></button>
                                                            </form>
                                                        <?php endif; ?>

                                                        <form method="POST" class="d-inline">
                                                            <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                            <input type="hidden" name="user_id" value="<?php echo (int) $u['id']; ?>">
                                                            <input type="hidden" name="action" value="delete">
                                                            <button type="submit" class="btn btn-outline-danger" title="Restrict account" data-confirm="Restrict this account? This keeps the account available for an authorised review."><i class="bi bi-shield-x"></i></button>
                                                        </form>
                                                    </div>
                                                </td>
                                            </tr>
                                        <?php endforeach; ?>
                                    <?php else: ?>
                                        <tr><td colspan="7" class="text-center text-muted py-4"><i class="bi bi-people fs-2 d-block mb-2"></i>No users found</td></tr>
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
