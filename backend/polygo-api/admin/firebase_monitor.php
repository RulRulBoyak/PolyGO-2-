<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();

try {
    $mirror = $pdo->query("SELECT id, user_name, tag, title, body, status, created_at FROM campus_alerts ORDER BY id DESC LIMIT 15")->fetchAll();
} catch (Throwable $e) {
    $mirror = [];
}

$tagBadge = [
    'ANNOUNCEMENT' => 'bg-primary',
    'REQUEST'      => 'bg-warning text-dark',
    'FLASH SALE'   => 'bg-danger',
    'EVENT'        => 'bg-info',
];
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Live Feed Monitor - PolyGo+ Admin</title>
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
                    <div class="col-12 d-flex justify-content-between align-items-start">
                        <div>
                            <h3 class="fw-bold"><i class="bi bi-radio"></i> Firebase Live Feed Monitor</h3>
                            <p class="page-head-sub">Real-time view of the Firestore pulse feed (via REST) next to the MySQL campus_alerts mirror. Auto-refreshes every 15 seconds.</p>
                        </div>
                        <button type="button" class="btn btn-outline-primary" onclick="monitorPoll(true)"><i class="bi bi-arrow-clockwise"></i> Refresh now</button>
                    </div>
                </div>

                <div class="row">
                    <div class="col-xl-7">
                        <div class="card mb-4">
                            <div class="card-header d-flex justify-content-between align-items-center">
                                <h5 class="mb-0 fw-bold"><i class="bi bi-lightning-charge"></i> Firestore pulse — live feed</h5>
                                <div>
                                    <span id="fbStatus" class="badge bg-secondary"><i class="bi bi-hourglass-split"></i> checking…</span>
                                    <span id="fbUpdated" class="badge bg-light text-muted ms-1"></span>
                                </div>
                            </div>
                            <div class="card-body p-0">
                                <div class="table-responsive">
                                    <table class="table table-hover mb-0">
                                        <thead>
                                            <tr><th>Tag</th><th>Title / Body</th><th>Author</th><th>Posted</th></tr>
                                        </thead>
                                        <tbody id="fbBody"></tbody>
                                    </table>
                                    <div id="fbEmpty" class="text-center text-muted py-4 d-none">
                                        <i class="bi bi-lightning-charge fs-2 d-block mb-2"></i>Waiting for pulse data…
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="col-xl-5">
                        <div class="card mb-4">
                            <div class="card-header"><h5 class="mb-0 fw-bold"><i class="bi bi-database"></i> MySQL mirror — REST feed (last 15)</h5></div>
                            <div class="card-body p-0">
                                <div class="table-responsive">
                                    <table class="table table-hover mb-0">
                                        <thead>
                                            <tr><th>Tag</th><th>Message</th><th>Status</th><th>Posted</th></tr>
                                        </thead>
                                        <tbody>
                                            <?php if (count($mirror) > 0): ?>
                                                <?php foreach ($mirror as $a): ?>
                                                    <tr>
                                                        <td><span class="badge <?php echo $tagBadge[$a['tag']] ?? 'bg-secondary'; ?>"><?php echo htmlspecialchars($a['tag']); ?></span></td>
                                                        <td>
                                                            <strong><?php echo htmlspecialchars($a['title'] ?? 'Untitled'); ?></strong>
                                                            <small class="d-block text-muted"><?php echo htmlspecialchars(mb_strimwidth($a['body'] ?? '', 0, 80, '…')); ?></small>
                                                        </td>
                                                        <td>
                                                            <?php echo $a['status'] === 'approved' ? '<span class="badge bg-success">Visible</span>' : ($a['status'] === 'hidden' ? '<span class="badge bg-danger">Hidden</span>' : '<span class="badge bg-warning">Pending</span>'); ?>
                                                        </td>
                                                        <td class="text-nowrap"><?php echo date('d/m/Y H:i', strtotime($a['created_at'])); ?></td>
                                                    </tr>
                                                <?php endforeach; ?>
                                            <?php else: ?>
                                                <tr><td colspan="4" class="text-center text-muted py-4">No campus alerts yet</td></tr>
                                            <?php endif; ?>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>

                        <div class="card">
                            <div class="card-header"><h5 class="mb-0 fw-bold"><i class="bi bi-info-circle"></i> Why two lists</h5></div>
                            <div class="card-body small text-muted">
                                <ol class="mb-0 ps-3">
                                    <li class="mb-2"><b>Firestore</b> is the real-time "broadcast" layer — the app's landing on an open Pulse screen streams straight from here.</li>
                                    <li class="mb-2"><b>MySQL (campus_alerts)</b> is the source of truth — the REST feed rebuilds from it on pull-to-refresh. Both should agree after a broadcast.</li>
                                    <li class="mb-0">Doc-shape contract: every pulse document must carry <code>user_name</code> + <code>created_at</code> as integer seconds (<code>time()</code>), or the app's <code>orderBy("created_at")</code> listener silently drops it.</li>
                                </ol>
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
        var TAG_BADGE = {
            'ANNOUNCEMENT': 'bg-primary',
            'REQUEST': 'bg-warning text-dark',
            'FLASH SALE': 'bg-danger',
            'EVENT': 'bg-info'
        };

        function esc(s) {
            return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
                return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
            });
        }

        function fmtTime(sec) {
            if (!sec) return '—';
            var d = new Date(sec * 1000);
            var pad = function (n) { return n < 10 ? '0' + n : '' + n; };
            return pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear() + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
        }

        function renderRows(docs) {
            var body = document.getElementById('fbBody');
            var empty = document.getElementById('fbEmpty');
            body.innerHTML = '';
            if (!docs || docs.length === 0) {
                empty.classList.remove('d-none');
                return;
            }
            empty.classList.add('d-none');
            docs.forEach(function (d) {
                var tr = document.createElement('tr');
                tr.innerHTML = '<td><span class="badge ' + (TAG_BADGE[d.tag] || 'bg-secondary') + '">' + esc(d.tag || '—') + '</span></td>'
                    + '<td><strong>' + esc(d.title || 'Untitled') + '</strong>'
                    + '<small class="d-block text-muted">' + esc((d.body || '').slice(0, 90)) + '</small></td>'
                    + '<td>' + esc(d.user_name || 'Unknown') + '</td>'
                    + '<td class="text-nowrap">' + fmtTime(d.created_at) + '</td>';
                body.appendChild(tr);
            });
        }

        function monitorPoll(manual) {
            var status = document.getElementById('fbStatus');
            var updated = document.getElementById('fbUpdated');
            fetch('firestore_pulse_json.php', { cache: 'no-store' })
                .then(function (r) { return r.json(); })
                .then(function (data) {
                    if (data.ok) {
                        status.className = 'badge bg-success';
                        status.innerHTML = '<i class="bi bi-cloud-check"></i> Firebase OK';
                        updated.textContent = 'updated ' + fmtTime(data.fetched_at);
                        renderRows(data.docs);
                    } else {
                        status.className = 'badge bg-danger';
                        status.innerHTML = '<i class="bi bi-cloud-slash"></i> Firestore ' + (data.http || 'unreachable');
                        updated.textContent = data.error ? ' ' + esc(data.error) : '';
                        renderRows([]);
                    }
                })
                .catch(function (err) {
                    status.className = 'badge bg-danger';
                    status.innerHTML = '<i class="bi bi-cloud-slash"></i> endpoint unreachable';
                    updated.textContent = '';
                    renderRows([]);
                });
        }

        monitorPoll();
        setInterval(function () { monitorPoll(false); }, 15000);
    </script>
</body>
</html>