<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';

// Handle report actions
if ($_SERVER['REQUEST_METHOD'] === 'POST' && verifyCsrf()) {
    $action = $_POST['action'] ?? '';
    $report_id = $_POST['report_id'] ?? 0;
    $listing_id = $_POST['listing_id'] ?? 0;
    
    if ($action === 'resolve' && $report_id) {
        try {
            $stmt = $pdo->prepare("INSERT INTO admin_report_actions (report_id, status) VALUES (?, 'resolved') ON DUPLICATE KEY UPDATE status = VALUES(status)");
            $stmt->execute([$report_id]);
            $message = "Report resolved successfully!";
        } catch (PDOException $e) {
            $error = "Failed to resolve report.";
        }
    }
    
    if ($action === 'dismiss' && $report_id) {
        try {
            $stmt = $pdo->prepare("INSERT INTO admin_report_actions (report_id, status) VALUES (?, 'dismissed') ON DUPLICATE KEY UPDATE status = VALUES(status)");
            $stmt->execute([$report_id]);
            $message = "Report dismissed successfully!";
        } catch (PDOException $e) {
            $error = "Failed to dismiss report.";
        }
    }
    
    if ($action === 'remove_listing' && $listing_id && $report_id) {
        try {
            // Start transaction
            $pdo->beginTransaction();
            
            // Archive the same listing that Android reads.
            $stmt = $pdo->prepare("UPDATE listings SET archived_at = NOW() WHERE id = ?");
            $stmt->execute([$listing_id]);
            
            // Resolve the report
            $stmt = $pdo->prepare("INSERT INTO admin_report_actions (report_id, status) VALUES (?, 'resolved') ON DUPLICATE KEY UPDATE status = VALUES(status)");
            $stmt->execute([$report_id]);
            
            $pdo->commit();
            $message = "Listing removed and report resolved!";
        } catch (PDOException $e) {
            $pdo->rollBack();
            $error = "Failed to remove listing.";
        }
    }
}

// Get filter parameters
$status = $_GET['status'] ?? '';

// Build query
$query = "SELECT r.id AS report_id, r.reason, r.details, r.created_at,
          COALESCE(a.status, 'pending') AS status, l.title AS listing_title, l.id AS listing_id,
          u.full_name AS reporter_username, u.full_name AS reporter_name,
          seller.full_name AS seller_username, seller.full_name AS seller_name
          FROM reports r LEFT JOIN admin_report_actions a ON a.report_id = r.id
          LEFT JOIN listings l ON r.target_type = 'listing' AND CAST(r.target_id AS UNSIGNED) = l.id
          LEFT JOIN users u ON r.reporter_id = u.id
          LEFT JOIN users seller ON l.owner_id = seller.id WHERE 1=1";
$params = [];

if ($status) {
    $query .= " AND COALESCE(a.status, 'pending') = ?";
    $params[] = $status;
}

$query .= " ORDER BY r.created_at DESC";

$stmt = $pdo->prepare($query);
$stmt->execute($params);
$reports = $stmt->fetchAll();

// Get statistics
$stmt = $pdo->query("SELECT COUNT(*) as total FROM reports");
$total_reports = $stmt->fetch()['total'];

$stmt = $pdo->query("SELECT COUNT(*) as total FROM reports r LEFT JOIN admin_report_actions a ON a.report_id = r.id WHERE COALESCE(a.status, 'pending') = 'pending'");
$pending_reports = $stmt->fetch()['total'];

$stmt = $pdo->query("SELECT COUNT(*) as total FROM admin_report_actions WHERE status = 'resolved'");
$resolved_reports = $stmt->fetch()['total'];

$stmt = $pdo->query("SELECT COUNT(*) as total FROM admin_report_actions WHERE status = 'dismissed'");
$dismissed_reports = $stmt->fetch()['total'];
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reports - PolyGo+ Admin</title>
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
                <!-- Page Header -->
                <div class="row mt-3 mb-4">
                    <div class="col-12">
                        <div class="d-flex justify-content-between align-items-center">
                            <div>
                                <h3 class="fw-bold">
                                    <i class="bi bi-flag"></i> Reports Management
                                </h3>
                                <p class="text-muted">Monitor and handle reported listings</p>
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Statistics Row -->
                <div class="row g-3 mb-4">
                    <div class="col-xl-3 col-md-6">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Total Reports</h6>
                                <h3 class="fw-bold"><?php echo $total_reports; ?></h3>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Pending</h6>
                                <h3 class="fw-bold text-warning"><?php echo $pending_reports; ?></h3>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Resolved</h6>
                                <h3 class="fw-bold text-success"><?php echo $resolved_reports; ?></h3>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Dismissed</h6>
                                <h3 class="fw-bold text-secondary"><?php echo $dismissed_reports; ?></h3>
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Messages -->
                <?php if ($message): ?>
                    <div class="alert alert-success alert-dismissible fade show">
                        <i class="bi bi-check-circle"></i> <?php echo $message; ?>
                        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                    </div>
                <?php endif; ?>
                
                <?php if ($error): ?>
                    <div class="alert alert-danger alert-dismissible fade show">
                        <i class="bi bi-exclamation-circle"></i> <?php echo $error; ?>
                        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                    </div>
                <?php endif; ?>
                
                <!-- Filters -->
                <div class="card border-0 shadow-sm mb-4">
                    <div class="card-body">
                        <form method="GET" class="row g-3">
                            <div class="col-md-6">
                                <select class="form-select" name="status">
                                    <option value="">All Status</option>
                                    <option value="pending" <?php echo $status === 'pending' ? 'selected' : ''; ?>>Pending</option>
                                    <option value="resolved" <?php echo $status === 'resolved' ? 'selected' : ''; ?>>Resolved</option>
                                    <option value="dismissed" <?php echo $status === 'dismissed' ? 'selected' : ''; ?>>Dismissed</option>
                                </select>
                            </div>
                            <div class="col-md-6">
                                <button type="submit" class="btn btn-primary w-100">
                                    <i class="bi bi-search"></i> Filter
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
                
                <!-- Reports Table -->
                <div class="card border-0 shadow-sm">
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead class="table-light">
                                    <tr>
                                        <th>#</th>
                                        <th>Listing</th>
                                        <th>Reported By</th>
                                        <th>Seller</th>
                                        <th>Reason</th>
                                        <th>Status</th>
                                        <th>Date</th>
                                        <th>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <?php if (count($reports) > 0): ?>
                                        <?php $count = 1; ?>
                                        <?php foreach ($reports as $report): ?>
                                            <tr>
                                                <td><?php echo $count++; ?></td>
                                                <td>
                                                    <div>
                                                        <div class="fw-semibold">
                                                            <?php echo htmlspecialchars($report['listing_title'] ?? 'Deleted Listing'); ?>
                                                        </div>
                                                        <small class="text-muted">ID: #<?php echo $report['listing_id']; ?></small>
                                                    </div>
                                                </td>
                                                <td>
                                                    <?php echo htmlspecialchars($report['reporter_name'] ?? $report['reporter_username'] ?? 'Unknown User'); ?>
                                                    <small class="d-block text-muted">@<?php echo htmlspecialchars($report['reporter_username'] ?? 'unknown'); ?></small>
                                                </td>
                                                <td>
                                                    <?php echo htmlspecialchars($report['seller_name'] ?? $report['seller_username'] ?? 'Unknown'); ?>
                                                    <small class="d-block text-muted">@<?php echo htmlspecialchars($report['seller_username'] ?? 'unknown'); ?></small>
                                                </td>
                                                <td>
                                                    <div class="text-truncate" style="max-width: 150px;" title="<?php echo htmlspecialchars($report['reason'] ?? 'No reason provided'); ?>">
                                                        <?php echo htmlspecialchars($report['reason'] ?? 'No reason provided'); ?>
                                                    </div>
                                                </td>
                                                <td>
                                                    <?php
                                                    $statusColors = [
                                                        'pending' => 'warning',
                                                        'resolved' => 'success',
                                                        'dismissed' => 'secondary'
                                                    ];
                                                    $color = $statusColors[$report['status']] ?? 'secondary';
                                                    ?>
                                                    <span class="badge bg-<?php echo $color; ?>">
                                                        <?php echo ucfirst($report['status']); ?>
                                                    </span>
                                                </td>
                                                <td>
                                                    <small><?php echo date('d/m/Y H:i', strtotime($report['created_at'])); ?></small>
                                                </td>
                                                <td>
                                                    <?php if ($report['status'] === 'pending'): ?>
                                                        <div class="btn-group btn-group-sm">
                                                            <form method="POST" class="d-inline"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="report_id" value="<?php echo $report['report_id']; ?>">
                                                                <input type="hidden" name="listing_id" value="<?php echo $report['listing_id']; ?>">
                                                                <input type="hidden" name="action" value="remove_listing">
                                                                <button type="submit" class="btn btn-danger" 
                                                                        title="Remove Listing & Resolve Report"
                                                                        onclick="return confirm('Remove this listing and resolve the report?')">
                                                                    <i class="bi bi-trash"></i> Remove
                                                                </button>
                                                            </form>
                                                            <form method="POST" class="d-inline"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="report_id" value="<?php echo $report['report_id']; ?>">
                                                                <input type="hidden" name="action" value="resolve">
                                                                <button type="submit" class="btn btn-success" title="Resolve Report">
                                                                    <i class="bi bi-check2"></i>
                                                                </button>
                                                            </form>
                                                            <form method="POST" class="d-inline"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="report_id" value="<?php echo $report['report_id']; ?>">
                                                                <input type="hidden" name="action" value="dismiss">
                                                                <button type="submit" class="btn btn-secondary" title="Dismiss Report"
                                                                        onclick="return confirm('Dismiss this report?')">
                                                                    <i class="bi bi-x"></i>
                                                                </button>
                                                            </form>
                                                        </div>
                                                    <?php else: ?>
                                                        <span class="text-muted">No actions</span>
                                                    <?php endif; ?>
                                                </td>
                                            </tr>
                                        <?php endforeach; ?>
                                    <?php else: ?>
                                        <tr>
                                            <td colspan="8" class="text-center text-muted py-4">
                                                <i class="bi bi-flag fs-2 d-block mb-2"></i>
                                                No reports found
                                            </td>
                                        </tr>
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
</body>
</html>
