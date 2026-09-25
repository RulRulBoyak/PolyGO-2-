<?php
require_once __DIR__ . '/config.php';

try {
    // SECURITY: The account being deleted is derived from the JWT,
    // never from a user-supplied id in the body (prevents cross-account deletion).
    $userId = verify_jwt();

    if ($userId <= 0) {
        respond(false, 'Unauthorized');
    }

    // 1. Collect uploaded image filenames so we can remove physical files too.
    $imageFiles = [];

    $profile = $pdo->prepare('SELECT profile_pic_url, verification_photo FROM users WHERE id = ? LIMIT 1');
    $profile->execute([$userId]);
    $profileImages = $profile->fetch();
    foreach (['profile_pic_url', 'verification_photo'] as $column) {
        if (!empty($profileImages[$column])) $imageFiles[] = (string)$profileImages[$column];
    }

    $listingQuery = $pdo->prepare('SELECT image_url FROM listings WHERE owner_id = ?');
    $listingQuery->execute([$userId]);
    while ($row = $listingQuery->fetch()) {
        if (!empty($row['image_url'])) {
            foreach (explode('|', (string)$row['image_url']) as $url) {
                $url = trim($url);
                if ($url !== '') {
                    $imageFiles[] = $url;
                }
            }
        }
    }

    // 2. Remove every row the user contributed to, in dependency order, inside
    //    one transaction. Deletes run explicitly (rather than relying purely on
    //    ON DELETE CASCADE) so the wipe stays complete even on databases
    //    created without foreign keys.
    $pdo->beginTransaction();
    $listingIds = $pdo->prepare('SELECT id FROM listings WHERE owner_id = ?');
    $listingIds->execute([$userId]);
    $ownedListingIds = array_map('strval', $listingIds->fetchAll(PDO::FETCH_COLUMN));

    // Leaf tables first (no table depends on these).
    $pdo->prepare('DELETE FROM impact_entries WHERE user_id = ?')->execute([$userId]);

    // Messages referenced by threads the user is part of, then the threads.
    $pdo->prepare('DELETE s FROM security_logs s INNER JOIN threads t ON t.id = s.thread_id WHERE t.buyer_id = ? OR t.seller_id = ?')->execute([$userId, $userId]);
    $pdo->prepare('DELETE m FROM messages m INNER JOIN threads t ON t.id = m.thread_id WHERE t.buyer_id = ? OR t.seller_id = ?')->execute([$userId, $userId]);
    $pdo->prepare('DELETE FROM threads WHERE buyer_id = ? OR seller_id = ?')->execute([$userId, $userId]);

    // User-owned / user-involved rows.
    $pdo->prepare('DELETE FROM user_impact WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM verification_requests WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM password_reset_codes WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM notifications WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM transactions WHERE buyer_id = ? OR seller_id = ?')->execute([$userId, $userId]);
    $pdo->prepare('DELETE FROM favorites WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE f FROM favorites f INNER JOIN listings l ON l.id = f.listing_id WHERE l.owner_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM listing_alerts WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE a FROM listing_alerts a INNER JOIN listings l ON l.id = a.listing_id WHERE l.owner_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM listings WHERE owner_id = ?')->execute([$userId]);

    // Tables with no FK constraint from users but tied to the user.
    $pdo->prepare('DELETE FROM reviews WHERE reviewer_id = ? OR seller_id = ?')->execute([$userId, $userId]);
    $reportDelete = $pdo->prepare('DELETE a FROM admin_report_actions a INNER JOIN reports r ON r.id = a.report_id WHERE r.reporter_id = ? OR (r.target_type = "user" AND r.target_id = ?)');
    $reportDelete->execute([$userId, (string)$userId]);
    $pdo->prepare('DELETE FROM reports WHERE reporter_id = ? OR (target_type = "user" AND target_id = ?)')->execute([$userId, (string)$userId]);
    foreach ($ownedListingIds as $listingId) {
        $pdo->prepare('DELETE a FROM admin_report_actions a INNER JOIN reports r ON r.id = a.report_id WHERE r.target_type = "listing" AND r.target_id = ?')->execute([$listingId]);
        $pdo->prepare('DELETE FROM reports WHERE target_type = "listing" AND target_id = ?')->execute([$listingId]);
    }

    // categories.proposed_by uses ON DELETE RESTRICT — remove before the user.
    $pdo->prepare('UPDATE categories SET proposed_by = NULL WHERE proposed_by = ?')->execute([$userId]);

    $pdo->prepare('DELETE FROM security_logs WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM user_follows WHERE follower_id = ? OR followed_id = ?')->execute([$userId, $userId]);
    $pdo->prepare('DELETE FROM bug_reports WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM timetable_entries WHERE user_id = ?')->execute([$userId]);
    $pdo->prepare('DELETE FROM campus_alerts WHERE user_id = ?')->execute([$userId]);

    // Remove any block relationships involving the user (both directions).
    $pdo->prepare('DELETE FROM blocked_users WHERE user_id = ? OR blocked_id = ?')->execute([$userId, $userId]);

    // Finally, remove the user record itself (last because of FK ordering).
    $delete = $pdo->prepare('DELETE FROM users WHERE id = ?');
    $delete->execute([$userId]);
    $pdo->commit();

    // 4. Remove physical uploaded images now that DB rows are gone.
    foreach (array_unique($imageFiles) as $fileUrl) {
        $basename = basename(parse_url($fileUrl, PHP_URL_PATH) ?: $fileUrl);
        if ($basename === '' || strpos($basename, '.') === false) {
            continue;
        }
        $candidate = __DIR__ . '/uploads/' . $basename;
        // Only touch files inside our own uploads directory.
        $realCandidate = realpath($candidate);
        $realUploads = realpath(__DIR__ . '/uploads');
        if ($realCandidate !== false && $realUploads !== false
            && strncmp($realCandidate, $realUploads, strlen($realUploads)) === 0
            && is_file($realCandidate)) {
            @unlink($realCandidate);
            $thumb = __DIR__ . '/uploads/thumbs/' . pathinfo($basename, PATHINFO_FILENAME) . '.thumb.jpg';
            if (is_file($thumb)) @unlink($thumb);
        }
    }

    respond(true, 'Account and all associated data deleted');
} catch (Throwable $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('[polygo-api] delete_account error: ' . $e->getMessage());
    respond(false, 'Failed to delete account');
}
