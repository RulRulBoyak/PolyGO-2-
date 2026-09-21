<nav class="navbar navbar-expand-lg navbar-light bg-white border-bottom">
    <div class="container-fluid px-4">
        <button class="btn btn-link d-lg-none" id="sidebarToggle">
            <i class="bi bi-list fs-4"></i>
        </button>
        <div class="ms-auto d-flex align-items-center">
            <span class="badge bg-primary me-2">Admin</span>
            <div class="dropdown">
                <a href="#" class="d-flex align-items-center text-decoration-none dropdown-toggle" id="dropdownUser" data-bs-toggle="dropdown">
                    <span class="me-2"><?php echo htmlspecialchars($_SESSION['full_name'] ?? 'Administrator'); ?></span>
                    <i class="bi bi-person-circle fs-4"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-end">
                    <li><a class="dropdown-item" href="#"><i class="bi bi-person"></i> Profile</a></li>
                    <li><a class="dropdown-item" href="#"><i class="bi bi-gear"></i> Settings</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="logout.php"><i class="bi bi-box-arrow-right"></i> Logout</a></li>
                </ul>
            </div>
        </div>
    </div>
</nav>
