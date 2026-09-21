<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';

// Handle listing actions
if ($_SERVER['REQUEST_METHOD'] === 'POST' && verifyCsrf()) {
    $action = $_POST['action'] ?? '';
    $listing_id = $_POST['listing_id'] ?? 0;
    
    if ($action === 'approve' && $listing_id) {
        try {
        $stmt = $pdo->prepare("UPDATE listings SET is_available = 1, archived_at = NULL WHERE id = ?");
            $stmt->execute([$listing_id]);
            auditAdminAction($pdo, 'restore_listing', 'listing', (int) $listing_id, 'Listing made active');
            $message = "Listing approved successfully!";
        } catch (PDOException $e) {
            $error = "Failed to approve listing.";
        }
    }
    
    if ($action === 'reject' && $listing_id) {
        try {
        $stmt = $pdo->prepare("UPDATE listings SET archived_at = NOW() WHERE id = ?");
            $stmt->execute([$listing_id]);
            auditAdminAction($pdo, 'archive_listing', 'listing', (int) $listing_id, 'Removed from marketplace');
            $message = "Listing removed successfully!";
        } catch (PDOException $e) {
            $error = "Failed to remove listing.";
        }
    }
    
    if ($action === 'delete' && $listing_id) {
        try {
        // Preserve listing evidence and allow authorised restoration.
        $stmt = $pdo->prepare("UPDATE listings SET archived_at = NOW(), is_available = 0 WHERE id = ?");
            $stmt->execute([$listing_id]);
            auditAdminAction($pdo, 'archive_listing', 'listing', (int) $listing_id, 'Legacy delete request converted to archive');
            $message = "Listing archived for review; it was not permanently deleted.";
        } catch (PDOException $e) {
            $error = "Failed to delete listing.";
        }
    }

    if ($action === 'change_category' && $listing_id) {
        $category = trim($_POST['category'] ?? '');
        try {
            $stmt = $pdo->prepare("UPDATE listings SET category = ? WHERE id = ?");
            $stmt->execute([$category, $listing_id]);
            auditAdminAction($pdo, 'change_listing_category', 'listing', (int) $listing_id, 'Category changed to ' . $category);
            $message = "Listing category changed to " . htmlspecialchars($category) . ".";
        } catch (PDOException $e) {
            $error = "Failed to change category.";
        }
    }

    if ($action === 'restore' && $listing_id) {
        try {
            $stmt = $pdo->prepare("UPDATE listings SET archived_at = NULL, is_available = 1 WHERE id = ?");
            $stmt->execute([$listing_id]);
            auditAdminAction($pdo, 'restore_listing', 'listing', (int) $listing_id, 'Listing restored');
            $message = "Listing restored to active.";
        } catch (PDOException $e) {
            $error = "Failed to restore listing.";
        }
    }
}

// Get filter parameters
$search = $_GET['search'] ?? '';
$status = $_GET['status'] ?? '';
$type = $_GET['type'] ?? '';

// Build query
$query = "SELECT l.id AS listing_id, l.title, l.description, l.price, l.category AS category_name, l.created_at,
          u.full_name, u.full_name AS username, u.is_verified AS seller_verified,
          CASE WHEN l.archived_at IS NOT NULL THEN 'removed' WHEN l.is_available = 0 THEN 'sold' ELSE 'active' END AS status
          FROM listings l LEFT JOIN users u ON l.owner_id = u.id WHERE 1=1";
$params = [];

if ($search) {
    $query .= " AND (l.title LIKE ? OR l.description LIKE ?)";
    $searchParam = "%$search%";
    $params = array_merge($params, [$searchParam, $searchParam]);
}

if ($status) {
    $query .= match ($status) {
        'active' => " AND l.is_available = 1 AND l.archived_at IS NULL",
        'sold' => " AND l.is_available = 0 AND l.archived_at IS NULL",
        'removed' => " AND l.archived_at IS NOT NULL",
        default => '',
    };
}

if ($type) {
    // Android listings do not distinguish product/service in the database.
}

$query .= " ORDER BY l.created_at DESC";

$stmt = $pdo->prepare($query);
$stmt->execute($params);
$listings = $stmt->fetchAll();

// Get statistics
$stmt = $pdo->query("SELECT COUNT(*) as total FROM listings");
$total_listings = $stmt->fetch()['total'];

$stmt = $pdo->query("SELECT COUNT(*) as total FROM listings WHERE 1 = 0");
$pending_listings = $stmt->fetch()['total'];

$stmt = $pdo->query("SELECT COUNT(*) as total FROM listings WHERE is_available = 1 AND archived_at IS NULL");
$active_listings = $stmt->fetch()['total'];

$stmt = $pdo->query("SELECT COUNT(*) as total FROM listings WHERE is_available = 0 AND archived_at IS NULL");
$sold_listings = $stmt->fetch()['total'];

$stmt = $pdo->query("SELECT COUNT(*) as total FROM listings WHERE archived_at IS NOT NULL");
$removed_listings = $stmt->fetch()['total'];

try {
    $categories = $pdo->query("SELECT name FROM categories WHERE is_published = 1 ORDER BY name")->fetchAll(PDO::FETCH_COLUMN);
} catch (PDOException $e) {
    $categories = [];
}
if (empty($categories)) {
    try {
        $categories = $pdo->query("SELECT DISTINCT category FROM listings WHERE category IS NOT NULL AND category != '' ORDER BY category")->fetchAll(PDO::FETCH_COLUMN);
    } catch (PDOException $e) {
        $categories = [];
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Listings - PolyGo+ Admin</title>
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
                                    <i class="bi bi-box"></i> Listing Management
                                </h3>
                                <p class="text-muted">Manage all product and service listings</p>
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Statistics Row -->
                <div class="row g-3 mb-4">
                    <div class="col-xl-2 col-md-4">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Total</h6>
                                <h3 class="fw-bold"><?php echo $total_listings; ?></h3>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-2 col-md-4">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Categories</h6>
                                <h3 class="fw-bold text-warning"><?php echo count($categories); ?></h3>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-2 col-md-4">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Active</h6>
                                <h3 class="fw-bold text-success"><?php echo $active_listings; ?></h3>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-2 col-md-4">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Sold</h6>
                                <h3 class="fw-bold text-info"><?php echo $sold_listings; ?></h3>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-2 col-md-4">
                        <div class="card border-0 shadow-sm">
                            <div class="card-body">
                                <h6 class="text-muted mb-1">Removed</h6>
                                <h3 class="fw-bold text-danger"><?php echo $removed_listings; ?></h3>
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
                            <div class="col-md-4">
                                <input type="text" class="form-control" name="search" 
                                       placeholder="Search listings..." 
                                       value="<?php echo htmlspecialchars($search); ?>">
                            </div>
                            <div class="col-md-4">
                                <select class="form-select" name="status">
                                    <option value="">All Status</option>
                                    <option value="active" <?php echo $status === 'active' ? 'selected' : ''; ?>>Active</option>
                                    <option value="sold" <?php echo $status === 'sold' ? 'selected' : ''; ?>>Sold</option>
                                    <option value="removed" <?php echo $status === 'removed' ? 'selected' : ''; ?>>Removed</option>
                                </select>
                            </div>
                            <div class="col-md-4">
                                <button type="submit" class="btn btn-primary w-100">
                                    <i class="bi bi-search"></i> Filter
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
                
                <!-- Listings Table -->
                <div class="card border-0 shadow-sm">
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead class="table-light">
                                    <tr>
                                        <th>#</th>
                                        <th>Title</th>
                                        <th>Seller</th>
                                        <th>Category</th>
                                        <th>Price</th>
                                        <th>Verified</th>
                                        <th>Status</th>
                                        <th>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <?php if (count($listings) > 0): ?>
                                        <?php $count = 1; ?>
                                        <?php foreach ($listings as $listing): ?>
                                            <tr>
                                                <td><?php echo $count++; ?></td>
                                                <td>
                                                    <div>
                                                        <div class="fw-semibold"><?php echo htmlspecialchars($listing['title']); ?></div>
                                                        <small class="text-muted">
                                                            <?php echo date('d/m/Y', strtotime($listing['created_at'])); ?>
                                                        </small>
                                                    </div>
                                                </td>
                                                <td>
                                                    <?php echo htmlspecialchars($listing['full_name'] ?? $listing['username'] ?? 'Unknown'); ?>
                                                    <?php if ((int) ($listing['seller_verified'] ?? 0) === 1): ?>
                                                        <span class="badge bg-success ms-1" title="Verified seller"><i class="bi bi-patch-check"></i></span>
                                                    <?php endif; ?>
                                                    <small class="d-block text-muted">@<?php echo htmlspecialchars($listing['username']); ?></small>
                                                </td>
                                                <td>
                                                    <form method="POST" class="d-flex gap-2" onsubmit="return true;">
                                                        <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                        <input type="hidden" name="listing_id" value="<?php echo (int) $listing['listing_id']; ?>">
                                                        <input type="hidden" name="action" value="change_category">
                                                        <select name="category" class="form-select form-select-sm" style="max-width: 170px;">
                                                            <?php foreach ($categories as $cat): ?>
                                                                <option value="<?php echo htmlspecialchars($cat); ?>" <?php echo $cat === $listing['category_name'] ? 'selected' : ''; ?>><?php echo htmlspecialchars($cat); ?></option>
                                                            <?php endforeach; ?>
                                                        </select>
                                                        <button type="submit" class="btn btn-sm btn-outline-primary" title="Save category"><i class="bi bi-check2"></i></button>
                                                    </form>
                                                </td>
                                                <td>
                                                    <?php if ($listing['price']): ?>
                                                        <span class="fw-bold">RM <?php echo number_format($listing['price'], 2); ?></span>
                                                    <?php else: ?>
                                                        <span class="text-muted">Free</span>
                                                    <?php endif; ?>
                                                </td>
                                                <td>
                                                    <?php if ((int) ($listing['seller_verified'] ?? 0) === 1): ?>
                                                        <span class="badge bg-success"><i class="bi bi-patch-check"></i> Verified Seller</span>
                                                    <?php else: ?>
                                                        <span class="badge bg-secondary">Unverified Seller</span>
                                                    <?php endif; ?>
                                                </td>
                                                <td>
                                                    <?php
                                                    $statusColors = [
                                                        'pending' => 'warning',
                                                        'active' => 'success',
                                                        'sold' => 'info',
                                                        'removed' => 'danger'
                                                    ];
                                                    $color = $statusColors[$listing['status']] ?? 'secondary';
                                                    ?>
                                                    <span class="badge bg-<?php echo $color; ?>">
                                                        <?php echo ucfirst($listing['status']); ?>
                                                    </span>
                                                </td>
                                                <td>
                                                    <div class="btn-group btn-group-sm">
                                                        <?php if ($listing['status'] === 'active'): ?>
                                                            <form method="POST" class="d-inline"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="listing_id" value="<?php echo $listing['listing_id']; ?>">
                                                                <input type="hidden" name="action" value="reject">
                                                                <button type="submit" class="btn btn-warning" title="Remove (archive) listing" data-confirm="Archive this listing? It will disappear from the marketplace.">
                                                                    <i class="bi bi-archive"></i>
                                                                </button>
                                                            </form>
                                                        <?php elseif ($listing['status'] === 'removed'): ?>
                                                            <form method="POST" class="d-inline"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="listing_id" value="<?php echo $listing['listing_id']; ?>">
                                                                <input type="hidden" name="action" value="restore">
                                                                <button type="submit" class="btn btn-success" title="Restore listing">
                                                                    <i class="bi bi-arrow-counterclockwise"></i>
                                                                </button>
                                                            </form>
                                                        <?php endif; ?>
                                                        
                                                        <form method="POST" class="d-inline"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                            <input type="hidden" name="listing_id" value="<?php echo $listing['listing_id']; ?>">
                                                            <input type="hidden" name="action" value="delete">
                                                            <button type="submit" class="btn btn-danger" title="Archive for review" data-confirm="Archive this listing for review? It will be hidden from the marketplace but can be restored.">
                                                                <i class="bi bi-archive"></i>
                                                            </button>
                                                        </form>
                                                    </div>
                                                </td>
                                            </tr>
                                        <?php endforeach; ?>
                                    <?php else: ?>
                                        <tr>
                                            <td colspan="8" class="text-center text-muted py-4">
                                                <i class="bi bi-box fs-2 d-block mb-2"></i>
                                                No listings found
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
