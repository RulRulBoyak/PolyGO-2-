<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();

function greenQuery(PDO $pdo, string $sql, $default) {
    try {
        return $pdo->query($sql)->fetch();
    } catch (Throwable $e) {
        return $default;
    }
}

// Each completed deal credits BOTH the buyer and the seller (ImpactEngine::creditTransaction),
// so impact_entries has 2 rows per transaction. Every campus-wide aggregate below therefore
// dedupes to one row per transaction (MAX = the credited values) so a deal is never
// double-counted platform-wide.
$dedupeDeals = "(SELECT category_name,
                        MAX(co2_kg) AS co2_kg, MAX(water_l) AS water_l,
                        MAX(paper_kg) AS paper_kg, MAX(energy_kwh) AS energy_kwh,
                        MIN(created_at) AS created_at
                 FROM impact_entries GROUP BY transaction_id, category_name) g";

$totals = greenQuery($pdo,
    "SELECT COALESCE(SUM(co2_kg),0) AS co2, COALESCE(SUM(water_l),0) AS water,
            COALESCE(SUM(paper_kg),0) AS paper, COALESCE(SUM(energy_kwh),0) AS energy,
            COUNT(*) AS deals
     FROM $dedupeDeals", ['co2' => 0, 'water' => 0, 'paper' => 0, 'energy' => 0, 'deals' => 0]);

$byCategory = [];
$stmt = $pdo->query(
    "SELECT category_name, ROUND(SUM(co2_kg),2) AS co2, ROUND(SUM(water_l),2) AS water,
            ROUND(SUM(paper_kg),2) AS paper, ROUND(SUM(energy_kwh),2) AS energy, COUNT(*) AS cnt
     FROM $dedupeDeals GROUP BY category_name ORDER BY co2 DESC"
);
foreach ($stmt->fetchAll() as $row) {
    $byCategory[] = $row;
}

$monthly = [];
$stmt = $pdo->query(
    "SELECT DATE_FORMAT(created_at, '%Y-%m') AS ym, ROUND(SUM(co2_kg),2) AS co2, ROUND(SUM(water_l),2) AS water
     FROM $dedupeDeals GROUP BY ym ORDER BY ym")
;
foreach ($stmt->fetchAll() as $row) {
    $monthly[] = $row;
}

$fortnight = greenQuery($pdo,
    "SELECT COALESCE(SUM(co2_kg),0) AS co2, COALESCE(SUM(water_l),0) AS water
     FROM $dedupeDeals WHERE created_at >= NOW() - INTERVAL 14 DAY", ['co2' => 0, 'water' => 0]);

$cats = [];
foreach ($byCategory as $c) {
    $cats[] = $c['category_name'];
    $co2Series[] = (float) $c['co2'];
    $waterSeries[] = (float) $c['water'];
}
$months = [];
$mCo2 = [];
$mWater = [];
foreach ($monthly as $m) {
    $months[] = $m['ym'];
    $mCo2[] = (float) $m['co2'];
    $mWater[] = (float) $m['water'];
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Green Impact - PolyGo+ Admin</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
    <link href="assets/css/style.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js"></script>
</head>
<body>
    <div class="d-flex" id="wrapper">
        <?php include 'includes/sidebar.php'; ?>

        <div id="page-content-wrapper">
            <?php include 'includes/header.php'; ?>

            <div class="container-fluid px-4">
                <div class="row mt-3 mb-4">
                    <div class="col-12">
                        <h3 class="fw-bold"><i class="bi bi-recycle"></i> Green Impact Analytics</h3>
                        <p class="page-head-sub">Sustainability earned through completed marketplace deals</p>
                    </div>
                </div>

                <div class="row g-4">
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card success h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">CO₂ Saved</p>
                                        <h2 class="fw-bold"><?php echo number_format((float) $totals['co2'], 1); ?> kg</h2>
                                        <small class="text-muted">+<?php echo number_format((float) $fortnight['co2'], 1); ?> kg this fortnight</small>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-cloud-fog2"></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card primary h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Water Saved</p>
                                        <h2 class="fw-bold"><?php echo number_format((float) $totals['water'], 1); ?> L</h2>
                                        <small class="text-muted">+<?php echo number_format((float) $fortnight['water'], 1); ?> L this fortnight</small>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-droplet"></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card info h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Paper Saved</p>
                                        <h2 class="fw-bold"><?php echo number_format((float) $totals['paper'], 1); ?> kg</h2>
                                        <small class="text-muted">fresh cotton count</small>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-file-earmark-minus"></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-3 col-md-6">
                        <div class="card stat-card warning h-100">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div>
                                        <p class="page-head-sub mb-1 small">Energy Saved</p>
                                        <h2 class="fw-bold"><?php echo number_format((float) $totals['energy'], 1); ?> kWh</h2>
                                        <small class="text-muted">from <?php echo $totals['deals']; ?> green deals</small>
                                    </div>
                                    <div class="stat-icon"><i class="bi bi-lightning-charge"></i></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="row g-4 mt-2">
                    <div class="col-xl-7">
                        <div class="card">
                            <div class="card-header">
                                <h5 class="mb-0 fw-bold"><i class="bi bi-bar-chart"></i> Impact by Category</h5>
                            </div>
                            <div class="card-body">
                                <?php if (count($byCategory) > 0): ?>
                                    <canvas id="categoryChart" height="240"></canvas>
                                <?php else: ?>
                                    <div class="text-center text-muted py-3">No impact data yet — metrics are credited once a deal completes.</div>
                                <?php endif; ?>
                            </div>
                        </div>
                    </div>
                    <div class="col-xl-5">
                        <div class="card">
                            <div class="card-header">
                                <h5 class="mb-0 fw-bold"><i class="bi bi-graph-up"></i> Monthly Trend</h5>
                            </div>
                            <div class="card-body">
                                <?php if (count($monthly) > 0): ?>
                                    <canvas id="monthlyChart" height="240"></canvas>
                                <?php else: ?>
                                    <div class="text-center text-muted py-3">No monthly data yet.</div>
                                <?php endif; ?>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card mt-4">
                    <div class="card-header">
                        <h5 class="mb-0 fw-bold"><i class="bi bi-list-ul"></i> Category Breakdown</h5>
                    </div>
                    <div class="card-body p-0">
                        <div class="table-responsive">
                            <table class="table table-hover mb-0">
                                <thead>
                                    <tr><th>Category</th><th>Deals</th><th>CO₂ (kg)</th><th>Water (L)</th><th>Paper (kg)</th><th>Energy (kWh)</th></tr>
                                </thead>
                                <tbody>
                                    <?php if ($byCategory): ?>
                                        <?php foreach ($byCategory as $c): ?>
                                            <tr>
                                                <td><span class="fw-semibold"><?php echo htmlspecialchars($c['category_name'] ?? '—'); ?></span></td>
                                                <td><?php echo (int) $c['cnt']; ?></td>
                                                <td><?php echo number_format((float) $c['co2'], 2); ?></td>
                                                <td><?php echo number_format((float) $c['water'], 2); ?></td>
                                                <td><?php echo number_format((float) $c['paper'], 2); ?></td>
                                                <td><?php echo number_format((float) $c['energy'], 2); ?></td>
                                            </tr>
                                        <?php endforeach; ?>
                                    <?php else: ?>
                                        <tr><td colspan="6" class="text-center text-muted py-4">No categories recorded yet</td></tr>
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
    <script>
        Chart.defaults.color = '#4a5568';
        Chart.defaults.borderColor = '#dde3eb';
        Chart.defaults.font.family = "Inter, 'Segoe UI', system-ui, sans-serif";
        Chart.defaults.plugins.legend.labels.usePointStyle = true;
        Chart.defaults.plugins.legend.labels.boxWidth = 8;
        Chart.defaults.plugins.legend.labels.boxHeight = 8;
        Chart.defaults.plugins.tooltip.backgroundColor = '#ffffff';
        Chart.defaults.plugins.tooltip.titleColor = '#0d47a1';
        Chart.defaults.plugins.tooltip.titleFont.weight = '700';
        Chart.defaults.plugins.tooltip.bodyColor = '#444444';
        Chart.defaults.plugins.tooltip.borderColor = '#dde3eb';
        Chart.defaults.plugins.tooltip.borderWidth = 1;
        Chart.defaults.plugins.tooltip.cornerRadius = 8;
        Chart.defaults.plugins.tooltip.padding = 10;
        Chart.defaults.plugins.tooltip.shadowColor = 'rgba(13,71,161,.14)';
        Chart.defaults.plugins.tooltip.shadowBlur = 18;
        Chart.defaults.plugins.tooltip.shadowOffsetY = 4;
        Chart.defaults.plugins.tooltip.callbacks.label = function (ctx) {
            return ' ' + ctx.dataset.label + ': ' + Number(ctx.parsed.y).toLocaleString();
        };

        <?php if (count($byCategory) > 0): ?>
        new Chart(document.getElementById('categoryChart'), {
            type: 'bar',
            data: {
                labels: <?php echo json_encode($cats); ?>,
                datasets: [
                    { label: 'CO₂ (kg)', data: <?php echo json_encode($co2Series); ?>, backgroundColor: '#2e7d32', hoverBackgroundColor: '#245f2a', borderRadius: 5, borderSkipped: false, maxBarThickness: 30, categoryPercentage: 0.6 },
                    { label: 'Water (L)', data: <?php echo json_encode($waterSeries); ?>, backgroundColor: '#1e88e5', hoverBackgroundColor: '#1771c6', borderRadius: 5, borderSkipped: false, maxBarThickness: 30, categoryPercentage: 0.6 }
                ]
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                scales: { x: { stacked: true, grid: { display: false }, ticks: { color: '#4a5568' } },
                         y: { stacked: true, beginAtZero: true, grid: { color: '#eef1f4', drawTicks: false }, border: { display: false }, ticks: { color: '#4a5568' } } },
                plugins: { legend: { labels: { color: '#2f3e4d' }, align: 'end' } }
            }
        });
        <?php endif; ?>

        <?php if (count($monthly) > 0): ?>
        new Chart(document.getElementById('monthlyChart'), {
            type: 'line',
            data: {
                labels: <?php echo json_encode($months); ?>,
                datasets: [
                    { label: 'CO₂ (kg)', data: <?php echo json_encode($mCo2); ?>, borderColor: '#2e7d32', backgroundColor: 'rgba(46,125,50,0.10)', fill: true, tension: 0.35, borderWidth: 2, pointRadius: 3, pointHoverRadius: 5, pointBackgroundColor: '#fff', pointBorderColor: '#2e7d32', pointHoverBorderColor: '#2e7d32' },
                    { label: 'Water (L)', data: <?php echo json_encode($mWater); ?>, borderColor: '#1e88e5', backgroundColor: 'rgba(30,136,229,0.10)', fill: true, tension: 0.35, borderWidth: 2, pointRadius: 3, pointHoverRadius: 5, pointBackgroundColor: '#fff', pointBorderColor: '#1e88e5', pointHoverBorderColor: '#1e88e5' }
                ]
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                scales: { x: { grid: { display: false }, ticks: { color: '#4a5568', maxRotation: 0, autoSkip: true } },
                         y: { beginAtZero: true, grid: { color: '#eef1f4', drawTicks: false }, border: { display: false }, ticks: { color: '#4a5568' } } },
                plugins: { legend: { labels: { color: '#2f3e4d' }, align: 'end' } }
            }
        });
        <?php endif; ?>
    </script>
</body>
</html>