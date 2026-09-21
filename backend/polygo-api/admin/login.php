<?php
require_once 'config/database.php';

if (isAdminLoggedIn()) {
    header('Location: index.php');
    exit();
}

$error = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $password = $_POST['password'] ?? '';

    if (!verifyCsrf()) {
        $error = 'Security token expired. Please reload the page and try again.';
    } elseif ($password === '') {
        $error = 'Please enter the admin password.';
    } else {
        try {
            $pdo = getConnection();
            $bucket = 'admin_login_' . sha1($_SERVER['REMOTE_ADDR'] ?? 'cli');
            if (adminRateLimit($pdo, $bucket, 5, 300)) {
                $error = 'Too many failed attempts. Try again in a few minutes.';
            } else {
                $expected = adminPassword();
                if ($expected !== null && hash_equals($expected, $password)) {
                    session_regenerate_id(true);
                    $_SESSION['admin_auth'] = true;
                    $_SESSION['full_name'] = 'PolyGo+ Administrator';
                    header('Location: index.php');
                    exit();
                }
                $error = 'Invalid admin password.';
            }
        } catch (Throwable $e) {
            $error = 'Login failed. Please try again.';
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>PolyGo+ Unified Admin Control Center</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
    <link href="assets/css/style.css" rel="stylesheet">
</head>
<body class="login-body">
    <div class="login-shell">
        <div class="login-brand">
            <div class="login-logo"><img src="assets/logo.svg" alt="PolyGo+"></div>
            <h1>PolyGo+</h1>
            <p>Unified Admin Control Center</p>
        </div>

        <div class="login-card-bg">
            <?php if ($error): ?>
                <div class="alert alert-danger alert-dismissible fade show" role="alert">
                    <i class="bi bi-exclamation-triangle"></i> <?php echo htmlspecialchars($error); ?>
                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                </div>
            <?php endif; ?>

            <form method="POST" action="">
                <input type="hidden" name="csrf_token" value="<?php echo csrfToken(); ?>">
                <div class="mb-3">
                    <label for="password" class="form-label">Admin Password</label>
                    <div class="input-group">
                        <span class="input-group-text"><i class="bi bi-shield-lock"></i></span>
                        <input type="password" class="form-control" id="password" name="password"
                               placeholder="Enter admin password" autofocus required>
                    </div>
                    <div class="form-text">Uses the shared <code>ADMIN_PASSWORD</code> from <code>secrets.php</code>.</div>
                </div>
                <button type="submit" class="btn btn-primary w-100">
                    <i class="bi bi-box-arrow-in-right"></i> Sign In
                </button>
            </form>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>