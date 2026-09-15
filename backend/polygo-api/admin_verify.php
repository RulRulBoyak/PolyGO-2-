<?php
require_once __DIR__ . '/config.php';

if (!defined('ADMIN_PASSWORD') || ADMIN_PASSWORD === '') {
    http_response_code(403);
    exit('Admin access is not configured. Define ADMIN_PASSWORD in secrets.php.');
}

// Session cookie hardening before session_start().
$secureCookie = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off');
session_set_cookie_params([
    'lifetime' => 0,
    'path'     => '/',
    'httponly' => true,
    'secure'   => $secureCookie,
    'samesite' => 'Lax'
]);
session_start();

$loggedIn = isset($_SESSION['admin_ok']) && $_SESSION['admin_ok'] === true;

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['password'])) {
    $ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
    if (!rate_limit_check($pdo, 'admin_login_ip:' . $ip, 10, 300)) {
        $error = 'Too many attempts, please try again later';
    } elseif (hash_equals(ADMIN_PASSWORD, (string)$_POST['password'])) {
        session_regenerate_id(true);
        $_SESSION['admin_ok'] = true;
        $_SESSION['admin_api_token'] = bin2hex(random_bytes(16));
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
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $params = session_get_cookie_params();
        setcookie(session_name(), '', time() - 42000, $params['path'], $params['domain'], $params['secure'], $params['httponly']);
    }
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
const TOKEN = '<?php echo htmlspecialchars((string)($_SESSION['admin_api_token'] ?? ''), ENT_QUOTES); ?>';
const safePhoto = (url) => {
    if (!url) return '';
    try {
        const u = new URL(url, window.location.origin);
        if (u.protocol !== 'http:' && u.protocol !== 'https:') return '';
        if (u.hostname !== window.location.hostname) return '';
        return u.href;
    } catch (e) {
        return '';
    }
};
const setEmpty = (list, msg) => {
    list.innerHTML = '';
    const div = document.createElement('div');
    div.className = 'empty';
    div.textContent = msg;
    list.appendChild(div);
};
async function load() {
    try {
        const r = await fetch('verify.php', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'X-Admin-Token': TOKEN},
            body: JSON.stringify({action: 'admin_pending'})
        });
        const data = await r.json();
        const list = document.getElementById('list');
        if (!data.success) { setEmpty(list, data.message || 'Could not load requests.'); return; }
        if (!data.pending || data.pending.length === 0) { setEmpty(list, 'No pending verification requests.'); return; }
        list.innerHTML = '';
        data.pending.forEach(u => {
            const div = document.createElement('div');
            div.className = 'item';

            const img = document.createElement('img');
            img.alt = 'matrix';
            const photo = safePhoto(u.verification_photo);
            if (photo) { img.src = photo; }

            const info = document.createElement('div');
            const name = document.createElement('strong');
            name.textContent = u.full_name || '';
            const meta1 = document.createElement('div');
            meta1.className = 'meta';
            meta1.textContent = 'ID: ' + (u.student_id || '') + ' · ' + (u.email || '');
            const meta2 = document.createElement('div');
            meta2.className = 'meta';
            meta2.textContent = 'Submitted: ' + (u.updated_at || '—');
            info.appendChild(name);
            info.appendChild(meta1);
            info.appendChild(meta2);

            const approve = document.createElement('button');
            approve.className = 'btn approve';
            approve.textContent = 'Approve';
            const reject = document.createElement('button');
            reject.className = 'btn reject';
            reject.textContent = 'Reject';
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

            if (photo) div.appendChild(img);
            div.appendChild(info);
            div.appendChild(approve);
            div.appendChild(reject);
            list.appendChild(div);
        });
    } catch (e) {
        setEmpty(document.getElementById('list'), 'Could not load pending requests.');
    }
}
load();
</script>
</body>
</html>