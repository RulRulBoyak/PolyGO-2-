<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/NotificationManager.php';
require_once __DIR__ . '/ImpactEngine.php';

try {
    // SECURITY: Verify JWT and get actual User ID
    $userId = verify_jwt();

    $input = input_json();
    $action = $input['action'] ?? 'list';

    // $userId is already set from token now, so we remove the manual override from input
    // $userId = (int)($input['user_id'] ?? 0);

    if ($userId <= 0) {
        respond(false, 'Unauthorized access');
    }

    if ($action === 'add') {
        $listingId = (int)($input['listing_id'] ?? 0);
        $amount = (float)($input['amount'] ?? 0);

        if ($listingId <= 0 || $amount <= 0) {
            respond(false, 'Invalid transaction details');
        }

        // Seller is derived from the listing, never trusted from the client.
        $listing = $pdo->prepare('SELECT owner_id, is_available FROM listings WHERE id = ? LIMIT 1');
        $listing->execute([$listingId]);
        $row = $listing->fetch();
        if (!$row) {
            respond(false, 'Listing not found');
        }
        $sellerId = (int)$row['owner_id'];
        if ($sellerId === (int)$userId) {
            respond(false, 'You cannot make an offer on your own listing');
        }
        if ((int)$row['is_available'] !== 1) {
            respond(false, 'This listing is no longer available');
        }

        $query = $pdo->prepare('INSERT INTO transactions (listing_id, buyer_id, seller_id, amount, status) VALUES (?, ?, ?, ?, "offer_sent")');
        if ($query->execute([$listingId, $userId, $sellerId, $amount])) {
            $transactionId = $pdo->lastInsertId();

            // Notify Seller
            $bQuery = $pdo->prepare('SELECT full_name FROM users WHERE id = ?');
            $bQuery->execute([$userId]);
            $buyerName = $bQuery->fetchColumn();

            $lQuery = $pdo->prepare('SELECT title FROM listings WHERE id = ?');
            $lQuery->execute([$listingId]);
            $itemTitle = $lQuery->fetchColumn();

            NotificationManager::sendToUser($pdo, $sellerId, "New Offer Received!", $buyerName . " offered RM " . number_format($amount, 2) . " for your " . $itemTitle, [
                'type' => 'offer',
                'transaction_id' => (string)$transactionId
            ]);

            respond(true, 'Offer sent successfully', ['id' => $transactionId]);
        } else {
            respond(false, 'Failed to send offer');
        }
    }
    else if ($action === 'update') {
        $transactionId = (int)($input['transaction_id'] ?? 0);
        $status = $input['status'] ?? '';

        if ($transactionId <= 0 || empty($status)) {
            respond(false, 'Missing update information');
        }

        // Only participants (buyer or seller) may update, and only to known states.
        // Sync with database enum: offer_sent, accepted, pickup, completed, cancelled
        $allowed = ['offer_sent', 'accepted', 'pickup', 'completed', 'cancelled', 'declined'];
        if (!in_array($status, $allowed, true)) {
            respond(false, 'Invalid status: ' . $status);
        }

        try {
            $pdo->beginTransaction();

            $query = $pdo->prepare('UPDATE transactions SET status = ?, impact_credited = IF(? = "completed" AND impact_credited = 0, 1, impact_credited) WHERE id = ? AND (buyer_id = ? OR seller_id = ?)');
            $query->execute([$status, $status, $transactionId, $userId, $userId]);
            if ($query->rowCount() === 0) {
                $pdo->rollBack();
                respond(false, 'Transaction not found or not authorized');
            }
            if ($status === 'completed') {
                // Mark listing as no longer available in the same unit of work.
                // NOTE: an already-sold listing is a valid completion target, so
                // we verify the listing exists via the JOIN (not affected rows,
                // which are 0 when the flag is already 0).
                $listingCheck = $pdo->prepare('SELECT l.id FROM listings l JOIN transactions t ON l.id = t.listing_id WHERE t.id = ?');
                $listingCheck->execute([$transactionId]);
                if ($listingCheck->fetch() === false) {
                    $pdo->rollBack();
                    respond(false, 'Listing for this transaction no longer exists');
                }
                $listingUpdate = $pdo->prepare('UPDATE listings l JOIN transactions t ON l.id = t.listing_id SET l.is_available = 0 WHERE t.id = ?');
                $listingUpdate->execute([$transactionId]);

                $check = $pdo->prepare('SELECT impact_credited FROM transactions WHERE id = ?');
                $check->execute([$transactionId]);
                if ((int)$check->fetchColumn() === 1) {
                    // First time this deal completed -> count its green impact.
                    $already = $pdo->prepare('SELECT COUNT(*) FROM impact_entries WHERE transaction_id = ?');
                    $already->execute([$transactionId]);
                    if ((int)$already->fetchColumn() === 0) {
                        ImpactEngine::creditTransaction($pdo, $transactionId);
                    }
                }
            }
            $pdo->commit();
            respond(true, 'Transaction updated');
        } catch (Throwable $t) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            error_log('[polygo-api] transactions update error: ' . $t->getMessage());
            respond(false, 'Failed to update transaction');
        }
    }
    else {
        // action = list
        // Fetch where user is either buyer or seller
        $query = $pdo->prepare('
            SELECT t.*, l.title, l.location, u.full_name as seller_name
            FROM transactions t
            JOIN listings l ON t.listing_id = l.id
            JOIN users u ON t.seller_id = u.id
            WHERE t.buyer_id = ? OR t.seller_id = ?
            ORDER BY t.created_at DESC
        ');
        $query->execute([$userId, $userId]);
        $rows = $query->fetchAll();

        // Map to app format
        $transactions = [];
        foreach($rows as $row) {
            $transactions[] = [
                'id' => (string)$row['id'],
                'listingId' => (string)$row['listing_id'],
                'title' => $row['title'],
                'amount' => 'RM ' . number_format($row['amount'], 2),
                'seller' => $row['seller_name'],
                'location' => $row['location'] ?? 'Near campus',
                'status' => ucfirst(str_replace('_', ' ', $row['status'])),
                'time' => strtotime($row['created_at']) * 1000,
                'reviewed' => false // Simplified for now
            ];
        }

        respond(true, 'Transactions fetched', ['transactions' => $transactions]);
    }

} catch (Exception $e) {
    error_log('[polygo-api] transactions error: ' . $e->getMessage());
    respond(false, 'Transaction failed, please try again');
}
