<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();
$message = '';
$error = '';
$categoryIcons = [
    'ic_category_food' => ['Food', 'bi-cup-hot'],
    'ic_category_drink' => ['Drinks', 'bi-cup-straw'],
    'ic_category_tech' => ['Technology / Electronics', 'bi-laptop'],
    'ic_category_fashion' => ['Fashion', 'bi-bag'],
    'ic_category_books' => ['Books', 'bi-book'],
    'ic_category_repair' => ['Repair', 'bi-tools'],
    'ic_category_home' => ['Home', 'bi-house'],
    'ic_category_laundry' => ['Laundry', 'bi-basket'],
    'ic_category_delivery' => ['Delivery', 'bi-bicycle'],
    'ic_category_service' => ['Services', 'bi-person-workspace'],
    'ic_category_printing' => ['Printing', 'bi-printer']
];

// Handle category actions
if ($_SERVER['REQUEST_METHOD'] === 'POST' && !verifyCsrf()) {
    $error = 'Your session expired. Refresh the page and try again.';
} elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    $category_id = $_POST['category_id'] ?? 0;
    
    // Add category
    if ($action === 'add') {
        $category_name = trim($_POST['category_name'] ?? '');
        $description = trim($_POST['description'] ?? '');
        $icon = trim($_POST['icon'] ?? 'ic_category_tech');
        
        if (empty($category_name)) {
            $error = "Category name is required.";
        } elseif (mb_strlen($category_name) > 80) {
            $error = "Category name must be 80 characters or fewer.";
        } elseif (!array_key_exists($icon, $categoryIcons)) {
            $error = "Choose an icon from the PolyGo icon catalogue.";
        } else {
            try {
                $stmt = $pdo->prepare("INSERT INTO categories (name, icon_res) VALUES (?, ?)");
                $stmt->execute([$category_name, $icon]);
                auditAdminAction($pdo, 'create', 'category', (int)$pdo->lastInsertId(), $category_name);
                $message = "Category added successfully!";
            } catch (PDOException $e) {
                if ($e->getCode() == 23000) {
                    $error = "Category already exists!";
                } else {
                    $error = "Failed to add category: " . $e->getMessage();
                }
            }
        }
    }
    
    // Edit category
    if ($action === 'edit') {
        $category_name = trim($_POST['category_name'] ?? '');
        $description = trim($_POST['description'] ?? '');
        $icon = trim($_POST['icon'] ?? 'ic_category_tech');
        
        if (empty($category_name)) {
            $error = "Category name is required.";
        } elseif (mb_strlen($category_name) > 80) {
            $error = "Category name must be 80 characters or fewer.";
        } elseif (!array_key_exists($icon, $categoryIcons)) {
            $error = "Choose an icon from the PolyGo icon catalogue.";
        } else {
            try {
                $stmt = $pdo->prepare("UPDATE categories SET name = ?, icon_res = ? WHERE id = ?");
                $stmt->execute([$category_name, $icon, $category_id]);
                auditAdminAction($pdo, 'update', 'category', (int)$category_id, $category_name);
                $message = "Category updated successfully!";
            } catch (PDOException $e) {
                $error = "Failed to update category.";
            }
        }
    }
    
    // Delete category
    if ($action === 'delete' && $category_id) {
        try {
            // Check if category has listings
            $stmt = $pdo->prepare("SELECT COUNT(*) as count FROM listings l JOIN categories c ON l.category = c.name WHERE c.id = ?");
            $stmt->execute([$category_id]);
            $count = $stmt->fetch()['count'];
            
            if ($count > 0) {
                $error = "Cannot delete category. It has $count listings associated with it.";
            } else {
                $stmt = $pdo->prepare("DELETE FROM categories WHERE id = ?");
                $stmt->execute([$category_id]);
                auditAdminAction($pdo, 'delete', 'category', (int)$category_id, 'Deleted unused category');
                $message = "Category deleted successfully!";
            }
        } catch (PDOException $e) {
            $error = "Failed to delete category.";
        }
    }
}

// Get all categories
$stmt = $pdo->query("SELECT id AS category_id, name AS category_name, icon_res AS icon, '' AS description FROM categories ORDER BY name");
$categories = $stmt->fetchAll();

// Get count of listings per category
$stmt = $pdo->query("SELECT c.id AS category_id, COUNT(l.id) as count FROM categories c LEFT JOIN listings l ON l.category = c.name GROUP BY c.id");
$category_counts = [];
while ($row = $stmt->fetch()) {
    $category_counts[$row['category_id']] = $row['count'];
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Categories - PolyGo+ Admin</title>
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
                                    <i class="bi bi-tags"></i> Category Management
                                </h3>
                                <p class="text-muted">Manage product and service categories</p>
                            </div>
                            <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#addCategoryModal">
                                <i class="bi bi-plus-circle"></i> Add Category
                            </button>
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
                
                <!-- Categories Table -->
                <div class="card border-0 shadow-sm">
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead class="table-light">
                                    <tr>
                                        <th>#</th>
                                        <th>Icon</th>
                                        <th>Category Name</th>
                                        <th>Description</th>
                                        <th>Listings</th>
                                        <th>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <?php if (count($categories) > 0): ?>
                                        <?php $count = 1; ?>
                                        <?php foreach ($categories as $category): ?>
                                            <tr>
                                                <td><?php echo $count++; ?></td>
                                                <td>
                                                    <span class="badge bg-primary p-2">
                                                        <?php $iconKey = $category['icon'] ?? ''; ?>
                                                        <?php $preview = isset($categoryIcons[$iconKey]) ? $categoryIcons[$iconKey][1] : 'bi-tag'; ?>
                                                        <i class="bi <?php echo htmlspecialchars($preview); ?> fs-5"></i>
                                                    </span>
                                                </td>
                                                <td>
                                                    <span class="fw-semibold"><?php echo htmlspecialchars($category['category_name']); ?></span>
                                                </td>
                                                <td><?php echo htmlspecialchars($category['description'] ?? '-'); ?></td>
                                                <td>
                                                    <span class="badge bg-secondary">
                                                        <?php echo $category_counts[$category['category_id']] ?? 0; ?>
                                                    </span>
                                                </td>
                                                <td>
                                                    <div class="btn-group btn-group-sm">
                                                        <button type="button" class="btn btn-warning" 
                                                                data-bs-toggle="modal" 
                                                                data-bs-target="#editCategoryModal"
                                                                data-id="<?php echo $category['category_id']; ?>"
                                                                data-name="<?php echo htmlspecialchars($category['category_name']); ?>"
                                                                data-desc="<?php echo htmlspecialchars($category['description'] ?? ''); ?>"
                                                                data-icon="<?php echo htmlspecialchars($category['icon'] ?? 'bi-tag'); ?>">
                                                            <i class="bi bi-pencil"></i>
                                                        </button>
                                                        
                                                        <?php if (($category_counts[$category['category_id']] ?? 0) == 0): ?>
                                                            <form method="POST" class="d-inline"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                                                                <input type="hidden" name="category_id" value="<?php echo $category['category_id']; ?>">
                                                                <input type="hidden" name="action" value="delete">
                                                                <button type="submit" class="btn btn-danger" 
                                                                        onclick="return confirm('Delete this category?')">
                                                                    <i class="bi bi-trash"></i>
                                                                </button>
                                                            </form>
                                                        <?php else: ?>
                                                            <button type="button" class="btn btn-secondary" disabled 
                                                                    title="Cannot delete - has listings">
                                                                <i class="bi bi-trash"></i>
                                                            </button>
                                                        <?php endif; ?>
                                                    </div>
                                                </td>
                                            </tr>
                                        <?php endforeach; ?>
                                    <?php else: ?>
                                        <tr>
                                            <td colspan="6" class="text-center text-muted py-4">
                                                <i class="bi bi-tags fs-2 d-block mb-2"></i>
                                                No categories found. Click "Add Category" to create one.
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
    
    <!-- Add Category Modal -->
    <div class="modal fade" id="addCategoryModal" tabindex="-1">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title"><i class="bi bi-plus-circle"></i> Add New Category</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <form method="POST"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                    <div class="modal-body">
                        <input type="hidden" name="action" value="add">
                        
                        <div class="mb-3">
                            <label for="category_name" class="form-label">Category Name *</label>
                            <input type="text" class="form-control" id="category_name" name="category_name" required>
                        </div>
                        
                        <div class="mb-3">
                            <label for="description" class="form-label">Description</label>
                            <textarea class="form-control" id="description" name="description" rows="2"></textarea>
                        </div>
                        
                        <div class="mb-3">
                            <label for="icon" class="form-label">PolyGo icon</label>
                            <div class="input-group"><span class="input-group-text"><i id="iconPreview" class="bi bi-cup-hot"></i></span>
                            <select class="form-select" id="icon" name="icon"><?php foreach ($categoryIcons as $key => $meta): ?><option value="<?= $key ?>" data-preview="<?= $meta[1] ?>"><?= htmlspecialchars($meta[0]) ?></option><?php endforeach; ?></select></div>
                            <small class="text-muted">These icons render consistently in the Android app.</small>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                        <button type="submit" class="btn btn-primary">Add Category</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
    
    <!-- Edit Category Modal -->
    <div class="modal fade" id="editCategoryModal" tabindex="-1">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title"><i class="bi bi-pencil"></i> Edit Category</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <form method="POST"><input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                    <div class="modal-body">
                        <input type="hidden" name="action" value="edit">
                        <input type="hidden" name="category_id" id="edit_category_id">
                        
                        <div class="mb-3">
                            <label for="edit_category_name" class="form-label">Category Name *</label>
                            <input type="text" class="form-control" id="edit_category_name" name="category_name" required>
                        </div>
                        
                        <div class="mb-3">
                            <label for="edit_description" class="form-label">Description</label>
                            <textarea class="form-control" id="edit_description" name="description" rows="2"></textarea>
                        </div>
                        
                        <div class="mb-3">
                            <label for="edit_icon" class="form-label">PolyGo icon</label>
                            <div class="input-group"><span class="input-group-text"><i id="editIconPreview" class="bi bi-tag"></i></span>
                            <select class="form-select" id="edit_icon" name="icon"><?php foreach ($categoryIcons as $key => $meta): ?><option value="<?= $key ?>" data-preview="<?= $meta[1] ?>"><?= htmlspecialchars($meta[0]) ?></option><?php endforeach; ?></select></div>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                        <button type="submit" class="btn btn-primary">Save Changes</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
    
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Edit modal - populate fields when opened
        document.addEventListener('DOMContentLoaded', function() {
            var editModal = document.getElementById('editCategoryModal');
            editModal.addEventListener('show.bs.modal', function(event) {
                var button = event.relatedTarget;
                document.getElementById('edit_category_id').value = button.getAttribute('data-id');
                document.getElementById('edit_category_name').value = button.getAttribute('data-name');
                document.getElementById('edit_description').value = button.getAttribute('data-desc') || '';
                var editIcon = document.getElementById('edit_icon');
                var storedIcon = button.getAttribute('data-icon') || 'ic_category_tech';
                if (!editIcon.querySelector('option[value="' + storedIcon + '"]')) {
                    storedIcon = storedIcon === 'ic_category_services'
                        ? 'ic_category_service' : 'ic_category_tech';
                }
                editIcon.value = storedIcon;
                syncPreview('edit_icon', 'editIconPreview');
            });
            function syncPreview(selectId, previewId) {
                var select = document.getElementById(selectId);
                var option = select.options[select.selectedIndex];
                document.getElementById(previewId).className = 'bi ' + option.dataset.preview;
            }
            document.getElementById('icon').addEventListener('change', function() {
                syncPreview('icon', 'iconPreview');
            });
            document.getElementById('edit_icon').addEventListener('change', function() {
                syncPreview('edit_icon', 'editIconPreview');
            });
            syncPreview('icon', 'iconPreview');
        });
    </script>
</body>
</html>
