<?php
require_once __DIR__ . '/config.php';

if (!defined('ADMIN_PASSWORD') || ADMIN_PASSWORD === '') {
    http_response_code(403);
    exit('Admin access is not configured. Define ADMIN_PASSWORD in secrets.php.');
}

session_start();

$loggedIn = isset($_SESSION['admin_ok']) && $_SESSION['admin_ok'] === true;

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['password'])) {
    if (hash_equals(ADMIN_PASSWORD, (string)$_POST['password'])) {
        $_SESSION['admin_ok'] = true;
        $loggedIn = true;
    } else {
        $error = 'Incorrect password';
    }
}

if (!$loggedIn) {
    header('Content-Type: text/html; charset=utf-8');
    echo '<!doctype html><html><head><title>PolyGo+ Admin</title>';
    echo '<meta name="viewport" content="width=device-width,initial-scale=1">';
    echo '<style>body{font-family:sans-serif;background:#f4f5f7;display:flex;justify-content:center;padding-top:10vh}';
    echo '.card{background:#fff;padding:32px;border-radius:12px;box-shadow:0 2px 10px rgba(0,0,0,.08);width:300px}';
    echo 'input{padding:10px;width:100%;box-sizing:border-box;margin-top:8px}button{padding:10px 16px;background:#1a73e8;color:#fff;border:0;border-radius:6px;cursor:pointer;margin-top:12px}</style>';
    echo '</head><body><div class="card"><h2>PolyGo+ Admin</h2>';
    if (isset($error)) echo '<p style="color:#c5221f">' . htmlspecialchars($error) . '</p>';
    echo '<form method="post"><input type="password" name="password" placeholder="Admin password" autofocus><button type="submit">Sign in</button></form></div></body></html>';
    exit;
}

if (isset($_GET['logout'])) {
    session_destroy();
    header('Location: admin_verify.php');
    exit;
}

header('Content-Type: text/html; charset=utf-8');
?>
<!doctype html>
<html>
<head>
    <title>PolyGo+ Admin — Verification</title>
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <style>
        body{font-family:system-ui,sans-serif;background:#f4f5f7;margin:0;padding:16px}
        .top{display:flex;justify-content:space-between;align-items:center;max-width:760px;margin:0 auto}
        h1{font-size:20px}.logout{color:#c5221f;text-decoration:none}
        .list{max-width:760px;margin:16px auto}.item{background:#fff;border-radius:10px;padding:14px;margin-bottom:10px;box-shadow:0 1px 5px rgba(0,0,0,.06)}
        .item img{width:80px;height:80px;object-fit:cover;border-radius:6px;float:right}
        .meta{font-size:13px;color:#5f6368}.btn{padding:8px 14px;border:0;border-radius:6px;cursor:pointer;color:#fff;margin-left:6px}
        .approve{background:#188038}.reject{background:#c5221f}.empty{background:#fff;padding:20px;border-radius:10px;text-align:center;color:#5f6368}
    </style>
</head>
<body>
<div class="top">
    <h1>Matrix Card Verification</h1>
    <a class="logout" href="?logout=1">Logout</a>
</div>
<div class="list" id="list"><p>Loading…</p></div>
<script>
const TOKEN = '<?php echo ADMIN_PASSWORD; ?>';
async function load() {
    try {
        const r = await fetch('verify.php', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'X-Admin-Token': TOKEN},
            body: JSON.stringify({action: 'admin_pending'})
        });
        const data = await r.json();
        const list = document.getElementById('list');
        if (!data.success) { list.innerHTML = '<div class="empty">' + data.message + '</div>'; return; }
        if (!data.pending || data.pending.length === 0) { list.innerHTML = '<div class="empty">No pending verification requests.</div>'; return; }
        list.innerHTML = '';
        data.pending.forEach(u => {
            const div = document.createElement('div');
            div.className = 'item';
            const img = u.verification_photo ? '<img src="' + u.verification_photo + '" alt="matrix">' : '';
            div.innerHTML = '<strong>' + u.full_name + '</strong>' + img +
                '<div class="meta">ID: ' + u.student_id + ' · ' + u.email + '</div>' +
                '<div class="meta">Submitted: ' + (u.updated_at || '—') + '</div><br>' +
                '<button class="btn approve">Approve</button><button class="btn reject">Reject</button>';
            const approve = div.querySelector('.approve');
            const reject = div.querySelector('.reject');
            const act = (a) => () => {
                approve.disabled = reject.disabled = true;
                fetch('verify.php', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json', 'X-Admin-Token': TOKEN},
                    body: JSON.stringify({action: a, user_id: u.id})
                }).then(r => r.json()).then(() => load());
            };
            approve.onclick = act('approve');
            reject.onclick = act('reject');
            list.appendChild(div);
        });
    } catch (e) {
        document.getElementById('list').innerHTML = '<div class="empty">Could not load pending requests.</div>';
    }
}
load();
</script>
</body>
</html>