<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();

function dbInt(PDO $pdo, string $sql): int {
    try {
        return (int) $pdo->query($sql)->fetchColumn();
    } catch (Throwable $e) {
        return 0;
    }
}

function listing_status(array $l): string {
    if (!empty($l['archived_at'])) return 'removed';
    if ((int) ($l['is_available'] ?? 1) === 1) return 'active';
    return 'sold';
}

$stats = [
    'total_users'          => dbInt($pdo, "SELECT COUNT(*) FROM users WHERE role != 'admin'"),
    'banned_users'         => dbInt($pdo, "SELECT COUNT(*) FROM users WHERE role != 'admin' AND is_banned = 1"),
    'pending_verification' => dbInt($pdo, "SELECT COUNT(*) FROM users WHERE role != 'admin' AND verification_status = 'pending'"),
    'total_listings'       => dbInt($pdo, "SELECT COUNT(*) FROM listings WHERE archived_at IS NULL"),
    'active_listings'      => dbInt($pdo, "SELECT COUNT(*) FROM listings WHERE is_available = 1 AND archived_at IS NULL"),
    'sold_listings'        => dbInt($pdo, "SELECT COUNT(*) FROM listings WHERE is_available = 0 AND archived_at IS NULL"),
    'removed_listings'     => dbInt($pdo, "SELECT COUNT(*) FROM listings WHERE archived_at IS NOT NULL"),
    'pending_reports'      => dbInt($pdo, "SELECT COUNT(*) FROM reports r LEFT JOIN admin_report_actions a ON a.report_id = r.id WHERE r.target_type = 'listing' AND COALESCE(a.status, 'pending') = 'pending'"),
    'pending_pulse'        => dbInt($pdo, "SELECT COUNT(*) FROM campus_alerts WHERE status = 'pending'"),
    'co2_saved'            => dbInt($pdo, "SELECT ROUND(COALESCE(SUM(co2_kg), 0)) FROM (SELECT MAX(co2_kg) AS co2_kg FROM impact_entries GROUP BY transaction_id) g"),
    'water_saved'          => dbInt($pdo, "SELECT ROUND(COALESCE(SUM(water_l), 0)) FROM (SELECT MAX(water_l) AS water_l FROM impact_entries GROUP BY transaction_id) g"),
];

try {
    $recent_users = $pdo->query("SELECT id, full_name, student_id, email, is_verified, verification_status, is_banned FROM users WHERE role != 'admin' ORDER BY created_at DESC LIMIT 6")->fetchAll();
} catch (Throwable $e) {
    $recent_users = [];
}

try {
    $recent_listings = $pdo->query("SELECT l.id, l.title, l.price, l.is_available, l.archived_at, l.created_at, u.full_name FROM listings l LEFT JOIN users u ON l.owner_id = u.id ORDER BY l.created_at DESC LIMIT 6")->fetchAll();
} catch (Throwable $e) {
    $recent_listings = [];
}

$statusColors = ['active' => 'success', 'sold' => 'info', 'removed' => 'danger'];
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dashboard - PolyGo+ Admin</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
    <link href="assets/css/style.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"></script>
    <style>
        .chart-wrap { position: relative; }
        .chart-wrap canvas { max-width: 100%; }
        .chart-empty { position: absolute; inset: 0; z-index: 2; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: .3rem; color: #717171; text-align: center; padding: 1.5rem; font-size: .9rem; }
        .live-dot { display: inline-block; width: .5rem; height: .5rem; border-radius: 50%; background: #fff; margin-right: .3rem; vertical-align: baseline; animation: livePulse 1.4s ease-in-out infinite; }
        @keyframes livePulse { 0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(255,255,255,.5); } 50% { opacity: .55; box-shadow: 0 0 0 5px rgba(255,255,255,0); } }
        .spin { display: inline-block; animation: liveSpin .6s linear infinite; }
        @keyframes liveSpin { to { transform: rotate(360deg); } }
        .stat-flash { animation: statFlash 1s ease-out; }
        @keyframes statFlash { 0% { box-shadow: inset 0 0 0 999px #e3f2fd; } 100% { box-shadow: inset 0 0 0 999px transparent; } }
        .today-chip { display: inline-block; font-size: .78rem; font-weight: 700; color: #2e7d32; background: #e8f5e9; border-radius: 999px; padding: .18em .7em; margin-top: 6px; }
        .today-chip.flat { color: #717171; background: #f5f7fa; }
        .chart-head-icon { width: 32px; height: 32px; border-radius: 9px; display: inline-flex; align-items: center; justify-content: center; font-size: 1rem; margin-right: 10px; vertical-align: middle; }
        .chart-head-icon.blue { background: #e3f2fd; color: #0d47a1; }
        .chart-head-icon.green { background: #e8f5e9; color: #2e7d32; }
        .chart-head-icon.teal { background: #e0f2f1; color: #00897b; }
        .chart-head-icon.amber { background: #fff8e1; color: #e68a00; }
        .card-header .fw-bold { display: flex; align-items: center; }
    </style>
</head>
<body>
    <div class="d-flex" id="wrapper">
        <?php include 'includes/sidebar.php'; ?>

        <div id="page-content-wrapper">
            <?php include 'includes/header.php'; ?>

            <div class="container-fluid px-4">
                <div class="row mt-3 mb-4">
                    <div class="col-12">
                        <h3 class="fw-bold"><img src="assets/logo.svg" alt="PolyGo+" class="sidebar-logo me-1"> Welcome back, <?php echo htmlspecialchars($_SESSION['full_name'] ?? 'Administrator'); ?></h3>
                        <p class="page-head-sub">PolyGo+ Unified Admin Control Center — one hub for marketplace, community and sustainability</p>
                    </div>
                </div>

                <div class="row g-4">
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card primary h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Campus Users</p>
                                        <h2 class="fw-bold" id="statTotalUsers"><?php echo number_format($stats['total_users']); ?></h2>
                                        <small id="statBannedUsers" class="<?php echo $stats['banned_users'] ? 'text-danger' : 'text-muted'; ?>"><?php echo $stats['banned_users']; ?> banned</small>
                                        <span id="chipUsers" class="today-chip d-none"></span>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-people"></i></div>
                                </div>
                                <a href="users.php" class="text-decoration-none small mt-2 d-inline-block">Manage users <i class="bi bi-arrow-right"></i></a>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card success h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Live Listings</p>
                                        <h2 class="fw-bold" id="statTotalListings"><?php echo number_format($stats['total_listings']); ?></h2>
                                        <small id="statActiveSold" class="text-muted"><?php echo $stats['active_listings']; ?> active / <?php echo $stats['sold_listings']; ?> sold</small>
                                        <span id="chipListings" class="today-chip d-none"></span>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-box"></i></div>
                                </div>
                                <a href="listings.php" class="text-decoration-none small mt-2 d-inline-block">Review listings <i class="bi bi-arrow-right"></i></a>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card warning h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
<p class="page-head-sub mb-1 small">Community Queue</p>
                                        <h2 class="fw-bold" id="statQueue"><?php echo number_format($stats['pending_verification'] + $stats['pending_pulse']); ?></h2>
                                        <small id="statQueueSub" class="text-warning"><?php echo $stats['pending_verification']; ?> verification · <?php echo $stats['pending_pulse']; ?> announcements</small>
                                        <span id="chipQueue" class="today-chip d-none"></span>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-patch-check"></i></div>
                                </div>
                                <a href="verification.php" class="text-decoration-none small mt-2 d-inline-block">Review <i class="bi bi-arrow-right"></i></a>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card info h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Green Impact</p>
                                        <h2 class="fw-bold" id="statCo2"><?php echo number_format($stats['co2_saved']); ?> kg CO₂</h2>
                                        <small id="statWater" class="text-muted"><?php echo number_format($stats['water_saved']); ?> L water saved</small>
                                        <span id="chipCo2" class="today-chip d-none"></span>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-recycle"></i></div>
                                </div>
                                <a href="analytics.php" class="text-decoration-none small mt-2 d-inline-block">Full analytics <i class="bi bi-arrow-right"></i></a>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="row g-4 mt-2">
                    <div class="col-xl-8">
                        <div class="card">
                            <div class="card-header d-flex justify-content-between align-items-center">
                                <h5 class="mb-0 fw-bold"><span class="chart-head-icon blue"><i class="bi bi-activity"></i></span> Campus Activity — Last 14 Days</h5>
                                <div>
                                    <span id="liveBadge" class="badge bg-secondary"><i class="bi bi-hourglass-split"></i> connecting…</span>
                                    <span id="liveUpdated" class="badge bg-light text-muted ms-1"></span>
                                    <button id="refreshBtn" type="button" class="btn btn-sm btn-outline-primary ms-1" onclick="liveRefresh()"><i id="refreshIcon" class="bi bi-arrow-clockwise"></i> Refresh now</button>
                                </div>
                            </div>
                            <div class="card-body">
                                <div class="chart-wrap">
                                    <canvas id="activityChart" height="250"></canvas>
                                    <div id="actEmpty" class="chart-empty d-none">No campus activity in the last 14 days — new signups, listings and deals will appear here live.</div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-4">
                        <div class="card h-100">
                            <div class="card-header"><h5 class="mb-0 fw-bold"><span class="chart-head-icon teal"><i class="bi bi-box"></i></span> Listing Status</h5></div>
                            <div class="card-body">
                                <div class="chart-wrap">
                                    <canvas id="statusChart" height="250"></canvas>
                                    <div id="statusEmpty" class="chart-empty d-none">No listings yet — active, sold and removed counts will appear here.</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="row g-4 mt-2">
                    <div class="col-xl-6">
                        <div class="card h-100">
                            <div class="card-header"><h5 class="mb-0 fw-bold"><span class="chart-head-icon amber"><i class="bi bi-patch-check"></i></span> Verification Pipeline</h5></div>
                            <div class="card-body">
                                <div class="chart-wrap">
                                    <canvas id="verifyChart" height="220"></canvas>
                                    <div id="verifyEmpty" class="chart-empty d-none">No students registered yet — the verification pipeline will draw as users sign up.</div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-6">
                        <div class="card h-100">
                            <div class="card-header"><h5 class="mb-0 fw-bold"><span class="chart-head-icon green"><i class="bi bi-recycle"></i></span> Green Impact by Category</h5></div>
                            <div class="card-body">
                                <div class="chart-wrap">
                                    <canvas id="impactChart" height="220"></canvas>
                                    <div id="impactEmpty" class="chart-empty d-none">No green impact yet — metrics are credited once a deal completes.</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="row g-4 mt-2">
                    <div class="col-xl-6">
                        <div class="card">
                            <div class="card-header">
                                <h5 class="mb-0 fw-bold"><i class="bi bi-person-plus"></i> Recent Users</h5>
                            </div>
                            <div class="card-body p-0">
                                <div class="table-responsive">
                                    <table class="table table-hover mb-0">
                                        <thead>
                                            <tr><th>User</th><th>Email</th><th>Verification</th><th>Status</th></tr>
                                        </thead>
                                        <tbody>
                                            <?php if ($recent_users): ?>
                                                <?php foreach ($recent_users as $u): ?>
                                                    <tr>
                                                        <td>
                                                            <div class="d-flex align-items-center">
                                                                <div class="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center me-2" style="width:32px;height:32px;font-size:14px;font-weight:bold;">
                                                                    <?php echo strtoupper(substr($u['full_name'] ?? ($u['student_id'] ?? '?'), 0, 1)); ?>
                                                                </div>
                                                                <?php echo htmlspecialchars($u['full_name'] ?? ($u['student_id'] ?? '')); ?>
                                                                <?php if ((int) $u['is_banned'] === 1): ?>
                                                                    <span class="badge bg-danger ms-2">Banned</span>
                                                                <?php endif; ?>
                                                            </div>
                                                        </td>
                                                        <td><?php echo htmlspecialchars($u['email']); ?></td>
                                                        <td>
                                                            <?php echo $u['verification_status'] === 'approved' ? '<span class="badge bg-success">Verified</span>' : ($u['verification_status'] === 'pending' ? '<span class="badge bg-warning">Pending</span>' : '<span class="badge bg-secondary">' . htmlspecialchars(ucfirst($u['verification_status'] ?? 'unverified')) . '</span>'); ?>
                                                        </td>
                                                        <td><?php echo (int) $u['is_verified'] === 1 ? '<span class="badge bg-success">Active</span>' : '<span class="badge bg-secondary">Inactive</span>'; ?></td>
                                                    </tr>
                                                <?php endforeach; ?>
                                            <?php else: ?>
                                                <tr><td colspan="4" class="text-center text-muted py-3">No users found</td></tr>
                                            <?php endif; ?>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="col-xl-6">
                        <div class="card">
                            <div class="card-header">
                                <h5 class="mb-0 fw-bold"><i class="bi bi-clock-history"></i> Recent Listings</h5>
                            </div>
                            <div class="card-body p-0">
                                <div class="table-responsive">
                                    <table class="table table-hover mb-0">
                                        <thead>
                                            <tr><th>Title</th><th>Seller</th><th>Price</th><th>Status</th></tr>
                                        </thead>
                                        <tbody>
                                            <?php if ($recent_listings): ?>
                                                <?php foreach ($recent_listings as $l): ?>
                                                    <?php $ls = listing_status($l); ?>
                                                    <tr>
                                                        <td><?php echo htmlspecialchars($l['title'] ?? 'Untitled'); ?></td>
                                                        <td><?php echo htmlspecialchars($l['full_name'] ?? 'Unknown'); ?></td>
                                                        <td>RM <?php echo number_format((float) $l['price'], 2); ?></td>
                                                        <td><span class="badge bg-<?php echo $statusColors[$ls] ?? 'secondary'; ?>"><?php echo ucfirst($ls); ?></span></td>
                                                    </tr>
                                                <?php endforeach; ?>
                                            <?php else: ?>
                                                <tr><td colspan="4" class="text-center text-muted py-3">No listings found</td></tr>
                                            <?php endif; ?>
                                        </tbody>
                                    </table>
                                </div>
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
    <script>
        (function () {
            'use strict';
            var TICK = '#4a5568', LEGEND = '#2f3e4d', GRID = '#eef1f4', EDGE = '#dde3eb';
            var BLUE = '#0d47a1', BLUE_L = '#1e88e5', GREEN = '#2e7d32', GREEN_L = '#43a047',
                TEAL = '#00897b', AMBER = '#e68a00', RED = '#d32f2f';
            var INTERVAL = 15000;
            var charts = {};
            var fetching = false;
            var badge = document.getElementById('liveBadge');
            var updated = document.getElementById('liveUpdated');
            var refreshBtn = document.getElementById('refreshBtn');
            var refreshIcon = document.getElementById('refreshIcon');

            Chart.defaults.color = TICK;
            Chart.defaults.borderColor = EDGE;
            Chart.defaults.animation.duration = 500;
            Chart.defaults.font.family = "Inter, 'Segoe UI', system-ui, sans-serif";
            Chart.defaults.plugins.legend.labels.usePointStyle = true;
            Chart.defaults.plugins.legend.labels.boxWidth = 8;
            Chart.defaults.plugins.legend.labels.boxHeight = 8;
            Chart.defaults.plugins.legend.labels.padding = 14;
            Chart.defaults.plugins.tooltip.backgroundColor = '#ffffff';
            Chart.defaults.plugins.tooltip.titleColor = BLUE;
            Chart.defaults.plugins.tooltip.bodyColor = '#444444';
            Chart.defaults.plugins.tooltip.titleFont.weight = '700';
            Chart.defaults.plugins.tooltip.borderColor = EDGE;
            Chart.defaults.plugins.tooltip.borderWidth = 1;
            Chart.defaults.plugins.tooltip.padding = 10;
            Chart.defaults.plugins.tooltip.cornerRadius = 8;
            Chart.defaults.plugins.tooltip.boxPadding = 5;
            Chart.defaults.plugins.tooltip.shadowColor = 'rgba(13,71,161,.14)';
            Chart.defaults.plugins.tooltip.shadowBlur = 18;
            Chart.defaults.plugins.tooltip.shadowOffsetY = 4;

            var centerTotal = {
                id: 'centerTotal',
                afterDraw: function (chart) {
                    if (chart.config.type !== 'doughnut') return;
                    var data = chart.data.datasets[0].data || [];
                    var total = data.reduce(function (a, b) { return a + Number(b); }, 0);
                    if (!total) return;
                    var meta = chart.getDatasetMeta(0);
                    if (!meta.data.length) return;
                    var x = meta.data[0].x, y = meta.data[0].y, ctx = chart.ctx;
                    ctx.save();
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillStyle = BLUE;
                    ctx.font = 'bold 24px Inter, "Segoe UI", system-ui, sans-serif';
                    ctx.fillText(total.toLocaleString(), x, y - 8);
                    ctx.fillStyle = TICK;
                    ctx.font = '11px Inter, "Segoe UI", system-ui, sans-serif';
                    ctx.fillText('listings', x, y + 14);
                    ctx.restore();
                }
            };

            function fmtV(v) { return Number(v).toLocaleString(); }
            function pctAfter(ctx) {
                var total = (ctx.dataset.data || []).reduce(function (a, b) { return a + Number(b); }, 0);
                if (!total) return '';
                return Math.round(Number(ctx.parsed) * 100 / total) + '%';
            }

            function lineOpts() {
                return {
                    responsive: true, maintainAspectRatio: false,
                    interaction: { mode: 'index', intersect: false },
                    scales: {
                        x: { grid: { display: false }, ticks: { color: TICK, maxTicksLimit: 7, maxRotation: 0, autoSkip: true } },
                        y: { beginAtZero: true, grid: { color: GRID, drawTicks: false }, border: { display: false }, ticks: { color: TICK, precision: 0 } }
                    },
                    plugins: {
                        legend: { labels: { color: LEGEND }, align: 'end' },
                        tooltip: { callbacks: { label: function (ctx) { return ' ' + ctx.dataset.label + ': ' + fmtV(ctx.parsed.y); } } }
                    }
                };
            }
            function barOpts(stacked) {
                return {
                    responsive: true, maintainAspectRatio: false,
                    scales: {
                        x: { stacked: !!stacked, grid: { display: false }, ticks: { color: TICK } },
                        y: { beginAtZero: true, stacked: !!stacked, grid: { color: GRID, drawTicks: false }, border: { display: false }, ticks: { color: TICK, precision: 0 } }
                    },
                    plugins: { legend: { labels: { color: LEGEND }, align: 'end' } }
                };
            }
            function doughnutOpts() {
                return {
                    responsive: true, maintainAspectRatio: false,
                    cutout: '72%',
                    plugins: {
                        legend: { labels: { color: LEGEND }, position: 'bottom', padding: 10 },
                        tooltip: { callbacks: { afterLabel: pctAfter } }
                    }
                };
            }

            function setNum(id, value, fmt) {
                var el = document.getElementById(id);
                if (!el) return;
                var text = fmt(value);
                if (el.textContent === text) return;
                el.textContent = text;
                el.classList.remove('stat-flash');
                void el.offsetWidth;
                el.classList.add('stat-flash');
            }
            function setChip(id, val, suffix) {
                var el = document.getElementById(id);
                if (!el) return;
                el.classList.remove('d-none');
                if (val > 0) {
                    el.className = 'today-chip';
                    el.textContent = '+' + val.toLocaleString() + suffix;
                } else {
                    el.className = 'today-chip flat';
                    el.textContent = '0' + suffix;
                }
            }
            function tog(id, show) {
                var el = document.getElementById(id);
                if (el) el.classList.toggle('d-none', !show);
            }
            function hasData(arr) {
                return Array.isArray(arr) && arr.some(function (v) { return Number(v) > 0; });
            }
            function last(arr) {
                return arr && arr.length ? Number(arr[arr.length - 1]) : 0;
            }
            function setBusy(on) {
                if (refreshIcon) refreshIcon.classList.toggle('spin', on);
                if (refreshBtn) refreshBtn.disabled = on;
            }
            function setBadge(state, html) {
                if (!badge) return;
                badge.className = 'badge bg-' + state;
                badge.innerHTML = html;
            }

            function init() {
                charts.activity = new Chart(document.getElementById('activityChart'), {
                    type: 'line',
                    data: { labels: [], datasets: [
                        { label: 'New users', data: [], borderColor: BLUE_L, backgroundColor: 'rgba(30,136,229,0.10)', borderWidth: 2, fill: true, tension: 0.35, pointRadius: 0, pointHoverRadius: 5, pointHoverBackgroundColor: '#fff', pointHoverBorderColor: BLUE_L, pointHoverBorderWidth: 2 },
                        { label: 'New listings', data: [], borderColor: GREEN, backgroundColor: 'rgba(46,125,50,0.10)', borderWidth: 2, fill: true, tension: 0.35, pointRadius: 0, pointHoverRadius: 5, pointHoverBackgroundColor: '#fff', pointHoverBorderColor: GREEN, pointHoverBorderWidth: 2 },
                        { label: 'Completed deals', data: [], borderColor: AMBER, backgroundColor: 'rgba(230,138,0,0.10)', borderWidth: 2, fill: true, tension: 0.35, pointRadius: 0, pointHoverRadius: 5, pointHoverBackgroundColor: '#fff', pointHoverBorderColor: AMBER, pointHoverBorderWidth: 2 }
                    ] },
                    options: lineOpts()
                });
                charts.status = new Chart(document.getElementById('statusChart'), {
                    type: 'doughnut',
                    data: { labels: ['Active', 'Sold', 'Removed'], datasets: [{ data: [0, 0, 0], backgroundColor: [GREEN, BLUE_L, RED], borderColor: '#ffffff', borderWidth: 3, hoverOffset: 6, borderRadius: 5, spacing: 3 }] },
                    plugins: [centerTotal],
                    options: doughnutOpts()
                });
                charts.verify = new Chart(document.getElementById('verifyChart'), {
                    type: 'bar',
                    data: { labels: ['Unverified', 'Pending', 'Approved', 'Rejected'], datasets: [{ label: 'Users', data: [0, 0, 0, 0], backgroundColor: ['#9aa6b5', AMBER, GREEN, RED], hoverBackgroundColor: ['#7e8b9b', '#c97b00', '#245f2a', '#b02320'], borderRadius: 6, borderSkipped: false, maxBarThickness: 46, categoryPercentage: 0.62 }] },
                    options: barOpts(false)
                });
                charts.impact = new Chart(document.getElementById('impactChart'), {
                    type: 'bar',
                    data: { labels: [], datasets: [
                        { label: 'CO₂ (kg)', data: [], backgroundColor: GREEN_L, hoverBackgroundColor: '#37853b', borderRadius: 4, borderSkipped: false, maxBarThickness: 20 },
                        { label: 'Water (L)', data: [], backgroundColor: BLUE_L, hoverBackgroundColor: '#1771c6', borderRadius: 4, borderSkipped: false, maxBarThickness: 20 }
                    ] },
                    options: barOpts(true)
                });
                liveRefresh();
                setInterval(liveRefresh, INTERVAL);
            }

            function apply(d) {
                var n = function (v) { return Number(v).toLocaleString(); };

                setNum('statTotalUsers', d.stats.total_users, n);
                setNum('statBannedUsers', d.stats.banned_users, function (v) { return Number(v) + ' banned'; });
                setNum('statTotalListings', d.stats.total_listings, n);
                setNum('statActiveSold', [d.stats.active_listings, d.stats.sold_listings], function (a) { return a[0] + ' active / ' + a[1] + ' sold'; });
                setNum('statQueue', d.stats.pending_verification + d.stats.pending_pulse, n);
                setNum('statQueueSub', [d.stats.pending_verification, d.stats.pending_pulse], function (a) { return a[0] + ' verification · ' + a[1] + ' announcements'; });
                setNum('statCo2', d.stats.co2_saved, function (v) { return n(v) + ' kg CO₂'; });
                setNum('statWater', d.stats.water_saved, function (v) { return n(v) + ' L water saved'; });

                setChip('chipUsers', last(d.activity.users), ' today');
                setChip('chipListings', last(d.activity.listings), ' today');
                setChip('chipQueue', last(d.activity.deals), ' deals today');
                setChip('chipCo2', Math.round(last(d.activity.co2)), ' kg today');

                charts.activity.data.labels = d.activity.days.map(function (day) { return day.slice(5); });
                charts.activity.data.datasets[0].data = d.activity.users;
                charts.activity.data.datasets[1].data = d.activity.listings;
                charts.activity.data.datasets[2].data = d.activity.deals;
                charts.activity.update();

                charts.status.data.datasets[0].data = [d.listing_status.active, d.listing_status.sold, d.listing_status.removed];
                charts.status.update();

                charts.verify.data.datasets[0].data = [
                    d.verification.unverified, d.verification.pending,
                    d.verification.approved, d.verification.rejected
                ];
                charts.verify.update();

                charts.impact.data.labels = d.impact_by_category.map(function (c) { return c.category; });
                charts.impact.data.datasets[0].data = d.impact_by_category.map(function (c) { return c.co2; });
                charts.impact.data.datasets[1].data = d.impact_by_category.map(function (c) { return c.water; });
                charts.impact.update();

                tog('actEmpty', !(hasData(d.activity.users) || hasData(d.activity.listings) || hasData(d.activity.deals)));
                tog('statusEmpty', (d.listing_status.active + d.listing_status.sold + d.listing_status.removed) === 0);
                tog('verifyEmpty', (d.verification.unverified + d.verification.pending + d.verification.approved + d.verification.rejected) === 0);
                tog('impactEmpty', d.impact_by_category.length === 0);

                setBadge('primary', '<span class="live-dot"></span>LIVE');
                if (updated) {
                    var t = new Date(d.fetched_at * 1000);
                    updated.textContent = 'updated ' + t.toLocaleTimeString();
                    updated.title = t.toLocaleString();
                    updated.className = 'badge bg-light text-muted ms-1';
                }
            }

            function fail() {
                setBadge('danger', '<i class="bi bi-exclamation-triangle"></i> OFFLINE');
                if (updated) updated.textContent = 'poll failed — retrying…';
            }

            function liveRefresh() {
                if (fetching) return;
                fetching = true;
                setBusy(true);
                fetch('dashboard_json.php', { headers: { 'Accept': 'application/json' }, cache: 'no-store' })
                    .then(function (r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
                    .then(apply)
                    .catch(fail)
                    .then(function () {
                        fetching = false;
                        setBusy(false);
                    });
            }

            document.addEventListener('DOMContentLoaded', function () {
                if (typeof Chart === 'undefined') {
                    setBadge('danger', '<i class="bi bi-exclamation-triangle"></i> Chart.js failed to load');
                    return;
                }
                init();
            });

            window.liveRefresh = liveRefresh;
        })();
    </script>
</body>
</html>