<?php
/**
 * DEPRECATED — the Unified Admin Control Center (admin/verification.php) is the
 * single place for verification approvals now (ADMIN_PASSWORD session login,
 * CSRF-protected, with photo preview + audit trail).
 *
 * This legacy web tool is kept only so old bookmarks/emails keep working:
 * every request is redirected to the admin panel's verification bridge.
 */
header('Location: ' . (empty($_SERVER['HTTPS']) || $_SERVER['HTTPS'] === 'off' ? 'http://' : 'https://') . ($_SERVER['HTTP_HOST'] ?? 'localhost') . rtrim(dirname($_SERVER['SCRIPT_NAME']), '/\\') . '/admin/verification.php');
exit;