<?php
$current_page = basename($_SERVER['PHP_SELF']);
$pdo = getConnection();

try {
    $total_users = (int) $pdo->query("SELECT COUNT(*) FROM users WHERE role != 'admin'")->fetchColumn();
} catch (PDOException $e) {
    $total_users = 0;
}

try {
    $pending_verifications = (int) $pdo->query("SELECT COUNT(*) FROM users WHERE role != 'admin' AND verification_status = 'pending'")->fetchColumn();
} catch (PDOException $e) {
    $pending_verifications = 0;
}

try {
    $pending_reports = (int) $pdo->query("SELECT COUNT(*) FROM reports r LEFT JOIN admin_report_actions a ON a.report_id = r.id WHERE r.target_type = 'listing' AND COALESCE(a.status, 'pending') = 'pending'")->fetchColumn();
} catch (PDOException $e) {
    $pending_reports = 0;
}

try {
    $pending_pulse = (int) $pdo->query("SELECT COUNT(*) FROM campus_alerts WHERE status = 'pending'")->fetchColumn();
} catch (PDOException $e) {
    $pending_pulse = 0;
}

if (!function_exists('sidebarItem')) {
    function sidebarItem(string $href, string $icon, string $label, string $page, int $badge = 0, string $badgeClass = 'bg-danger'): void {
        $class = ($GLOBALS['current_page'] ?? '') === $page ? 'active' : '';
        echo '<a href="' . $href . '" class="list-group-item list-group-item-action bg-dark text-white ' . $class . '">';
        echo '<i class="bi ' . $icon . '"></i> ' . $label;
        if ($badge > 0) {
            echo '<span class="badge ' . $badgeClass . ' float-end">' . $badge . '</span>';
        }
        echo '</a>';
    }
}
?>
<div class="bg-dark text-white" id="sidebar-wrapper" style="min-height: 100vh; width: 260px; flex-shrink: 0;">
    <div class="sidebar-heading text-center py-4">
        <img src="assets/logo.svg" alt="PolyGo+" class="sidebar-logo">
        <h5 class="mt-2 fw-bold">PolyGo+</h5>
        <small class="text-muted">Admin Control Center</small>
    </div>
    <div class="list-group list-group-flush">
        <div class="sidebar-section-label">Overview</div>
        <?php sidebarItem('index.php', 'bi-speedometer2', 'Dashboard', 'index.php'); ?>

        <div class="sidebar-section-label">Store</div>
        <?php
        sidebarItem('listings.php', 'bi-box', 'Listings', 'listings.php');
        sidebarItem('categories.php', 'bi-tags', 'Categories', 'categories.php');
        sidebarItem('reports.php', 'bi-flag', 'Reports', 'reports.php', $pending_reports);
        ?>

        <div class="sidebar-section-label">Community</div>
        <?php
        sidebarItem('users.php', 'bi-people', 'Users', 'users.php', $total_users, 'bg-primary');
        sidebarItem('verification.php', 'bi-patch-check', 'Verification', 'verification.php', $pending_verifications);
        sidebarItem('pulse.php', 'bi-megaphone', 'Announcements', 'pulse.php', $pending_pulse);
        sidebarItem('events.php', 'bi-calendar-event', 'Campus Events', 'events.php');
        sidebarItem('admin_broadcast.php', 'bi-broadcast-pin', 'Remote Control', 'admin_broadcast.php');
        ?>

        <div class="sidebar-section-label">Insight</div>
        <?php sidebarItem('analytics.php', 'bi-graph-up', 'Green Impact', 'analytics.php'); ?>
        <?php sidebarItem('ai_chat.php', 'bi-robot', 'AI Assistant', 'ai_chat.php'); ?>

        <div class="sidebar-section-label">System</div>
        <?php
        sidebarItem('maintenance.php', 'bi-tools', 'Maintenance', 'maintenance.php');
        sidebarItem('firebase_monitor.php', 'bi-radio', 'Live Feed Monitor', 'firebase_monitor.php');
        ?>

        <hr class="text-muted">
        <a href="logout.php" class="list-group-item list-group-item-action bg-dark text-danger">
            <i class="bi bi-box-arrow-right"></i> Logout
        </a>
    </div>
</div>

<style>
    .list-group-item {
        border: none;
        border-radius: 0;
        padding: 11px 20px;
        transition: background 0.2s ease;
    }
    .list-group-item:hover {
        background: #1b2a41 !important;
    }
    .list-group-item.active {
        background: #1f6feb !important;
        border: none;
    }
    .list-group-item.active:hover {
        background: #1a5fc8 !important;
    }
    .sidebar-section-label {
        color: #6c7a8f;
        font-size: 11px;
        text-transform: uppercase;
        letter-spacing: 0.1em;
        padding: 14px 20px 4px;
    }
    #sidebar-wrapper {
        position: sticky;
        top: 0;
        height: 100vh;
        overflow-y: auto;
    }
    #sidebar-wrapper::-webkit-scrollbar {
        width: 4px;
    }
    #sidebar-wrapper::-webkit-scrollbar-track {
        background: #0d1b2a;
    }
    #sidebar-wrapper::-webkit-scrollbar-thumb {
        background: #1f6feb;
        border-radius: 2px;
    }
    .badge {
        font-size: 11px;
        padding: 4px 8px;
    }
</style>
