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
        $amountRaw = $input['amount'] ?? null;
        $amount = is_numeric($amountRaw) ? round((float)$amountRaw, 2) : 0;

        if ($listingId <= 0 || !is_finite($amount) || $amount <= 0 || $amount > 100000) {
            respond(false, 'Invalid transaction details');
        }
        if (!rate_limit_check($pdo, 'offer_uid:' . $userId, 10, 300)) {
            respond(false, 'Too many offers. Please wait a few minutes and try again.');
        }

        // Seller is derived from the listing, never trusted from the client.
        $listing = $pdo->prepare('SELECT owner_id, is_available, archived_at FROM listings WHERE id = ? LIMIT 1');
        $listing->execute([$listingId]);
        $row = $listing->fetch();
        if (!$row) {
            respond(false, 'Listing not found');
        }
        $sellerId = (int)$row['owner_id'];
        if ($sellerId === (int)$userId) {
            respond(false, 'You cannot make an offer on your own listing');
        }
        if ((int)$row['is_available'] !== 1 || $row['archived_at'] !== null) {
            respond(false, 'This listing is no longer available');
        }
        $blockCheck = $pdo->prepare('SELECT 1 FROM blocked_users WHERE (user_id = ? AND blocked_id = ?) OR (user_id = ? AND blocked_id = ?) LIMIT 1');
        $blockCheck->execute([$userId, $sellerId, $sellerId, $userId]);
        if ($blockCheck->fetchColumn()) respond(false, 'You cannot make an offer to this seller');
        $committed = $pdo->prepare('SELECT 1 FROM transactions WHERE listing_id = ? AND status IN ("accepted", "pickup", "completed") LIMIT 1');
        $committed->execute([$listingId]);
        if ($committed->fetchColumn()) respond(false, 'This listing already has an accepted offer');

        $existingOffer = $pdo->prepare('SELECT id FROM transactions
            WHERE listing_id = ? AND buyer_id = ? AND status IN ("offer_sent", "accepted", "pickup")
            ORDER BY id DESC LIMIT 1');
        $existingOffer->execute([$listingId, $userId]);
        $existingId = (int)$existingOffer->fetchColumn();
        if ($existingId > 0) {
            respond(false, 'You already have an active offer for this listing', ['id' => (string)$existingId]);
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

            respond(true, 'Offer sent successfully', ['id' => (string)$transactionId]);
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

        // Sync with the database enum: offer_sent, accepted, pickup, completed, declined, cancelled
        $allowed = ['offer_sent', 'accepted', 'pickup', 'completed', 'declined', 'cancelled'];
        if (!in_array($status, $allowed, true)) {
            respond(false, 'Invalid status');
        }

        try {
            $pdo->beginTransaction();

            // Load current state + participants. FOR UPDATE closes the race
            // between two clients trying to advance the same deal.
            $current = $pdo->prepare('SELECT buyer_id, seller_id, listing_id, status, amount FROM transactions WHERE id = ? FOR UPDATE');
            $current->execute([$transactionId]);
            $txn = $current->fetch();
            if (!$txn) {
                $pdo->rollBack();
                respond(false, 'Transaction not found or not authorized');
            }

            $buyerId = (int)$txn['buyer_id'];
            $sellerId = (int)$txn['seller_id'];
            $listingId = (int)$txn['listing_id'];
            if ($buyerId !== $userId && $sellerId !== $userId) {
                $pdo->rollBack();
                respond(false, 'Transaction not found or not authorized');
            }

            // Serialize all state changes for the listing, including two
            // different offers being accepted at the same instant.
            $listingLock = $pdo->prepare('SELECT is_available FROM listings WHERE id = ? FOR UPDATE');
            $listingLock->execute([$listingId]);
            $listingAvailable = $listingLock->fetchColumn();
            if ($listingAvailable === false) {
                $pdo->rollBack();
                respond(false, 'Listing for this transaction no longer exists');
            }
            if ($status === 'accepted' && (int)$listingAvailable !== 1) {
                $pdo->rollBack();
                respond(false, 'This listing is no longer available');
            }

            // Role-based state machine. Buyers may only withdraw (offer or an
            // accepted deal). Sellers drive the deal forward, and ONLY they can
            // complete it — completion marks the listing sold + credits green
            // impact, so it must never be reachable by a buyer. No skipping, no
            // self-transitions. The app completes directly after 'accepted',
            // so that shortcut is explicitly allowed for the seller.
            $from = (string)$txn['status'];
            $transition = $from . '>' . $status;
            $role = ($buyerId === $userId) ? 'buyer' : 'seller';
            $rules = [
                'buyer'  => ['offer_sent>cancelled', 'accepted>cancelled'],
                'seller' => ['offer_sent>accepted', 'offer_sent>declined', 'accepted>pickup', 'accepted>completed', 'pickup>completed']
            ];
            if ($from === $status || !in_array($transition, $rules[$role], true)) {
                $pdo->rollBack();
                respond(false, 'Transaction update is not allowed for this user');
            }

            $declinedBuyerIds = [];
            if ($status === 'accepted') {
                $exclusive = $pdo->prepare('SELECT id FROM transactions WHERE listing_id = ? AND id <> ? AND status IN ("accepted", "pickup", "completed") FOR UPDATE');
                $exclusive->execute([$listingId, $transactionId]);
                if ($exclusive->fetchColumn()) {
                    $pdo->rollBack();
                    respond(false, 'Another offer has already been accepted for this listing');
                }
                $otherOffers = $pdo->prepare('SELECT buyer_id FROM transactions WHERE listing_id = ? AND id <> ? AND status = "offer_sent" FOR UPDATE');
                $otherOffers->execute([$listingId, $transactionId]);
                $declinedBuyerIds = array_map('intval', $otherOffers->fetchAll(PDO::FETCH_COLUMN));
                $decline = $pdo->prepare('UPDATE transactions SET status = "declined" WHERE listing_id = ? AND id <> ? AND status = "offer_sent"');
                $decline->execute([$listingId, $transactionId]);
            }

            $query = $pdo->prepare('UPDATE transactions SET status = ?, impact_credited = IF(? = "completed" AND impact_credited = 0, 1, impact_credited) WHERE id = ?');
            $query->execute([$status, $status, $transactionId]);
            if ($status === 'completed') {
                // Mark listing as no longer available in the same unit of work.
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

            notifyDealUpdate($pdo, $transactionId, $listingId, $buyerId, $sellerId, $role, $status);
            foreach ($declinedBuyerIds as $declinedBuyerId) {
                NotificationManager::sendToUser($pdo, $declinedBuyerId, 'Offer Declined',
                    'Another offer was accepted for this listing.', [
                        'type' => 'transactions', 'listing_id' => (string)$listingId
                    ]);
            }

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
            SELECT t.*, l.title, l.location, u.full_name as seller_name,
                   EXISTS(SELECT 1 FROM reviews r WHERE r.reviewer_id = ? AND r.listing_id = t.listing_id) AS reviewed
            FROM transactions t
            JOIN listings l ON t.listing_id = l.id
            JOIN users u ON t.seller_id = u.id
            WHERE t.buyer_id = ? OR t.seller_id = ?
            ORDER BY t.created_at DESC
        ');
        $query->execute([$userId, $userId, $userId]);
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
                'role' => (int)$row['buyer_id'] === $userId ? 'buyer' : 'seller',
                'location' => $row['location'] ?? 'Near campus',
                'status' => ucfirst(str_replace('_', ' ', $row['status'])),
                'time' => strtotime($row['created_at']) * 1000,
                'reviewed' => (bool)$row['reviewed']
            ];
        }

        respond(true, 'Transactions fetched', ['transactions' => $transactions]);
    }

} catch (Exception $e) {
    error_log('[polygo-api] transactions error: ' . $e->getMessage());
    respond(false, 'Transaction failed, please try again');
}

/**
 * Push a status-change notification to the other party in the deal. The commit
 * has already happened; a notification failure must never fail the request, and
 * sendToUser() persists an inbox row independently of whether the push lands.
 */
function notifyDealUpdate(PDO $pdo, int $transactionId, int $listingId, int $buyerId, int $sellerId, string $role, string $status): void {
    try {
        if (in_array($status, ['accepted', 'declined', 'pickup', 'completed', 'cancelled'], true)) {
            $listingsQ = $pdo->prepare('SELECT title FROM listings WHERE id = ?');
            $listingsQ->execute([$listingId]);
            $itemTitle = (string)$listingsQ->fetchColumn();

            // Notify the other party, but name the actor who changed the deal.
            $peerId = $role === 'buyer' ? $sellerId : $buyerId;
            $actorId = $role === 'buyer' ? $buyerId : $sellerId;
            $nameQ = $pdo->prepare('SELECT full_name FROM users WHERE id = ?');
            $nameQ->execute([$actorId]);
            $actorName = (string)$nameQ->fetchColumn();

            // Deep-link target: the conversation thread for this deal pair.
            $threadQ = $pdo->prepare('SELECT id FROM threads WHERE listing_id = ?
                AND ((buyer_id = ? AND seller_id = ?) OR (buyer_id = ? AND seller_id = ?))
                ORDER BY id LIMIT 1');
            $threadQ->execute([$listingId, $buyerId, $sellerId, $sellerId, $buyerId]);
            $threadId = (int)$threadQ->fetchColumn();

            $data = [
                'type' => 'transactions',
                'transaction_id' => (string)$transactionId,
                'listing_id' => (string)$listingId
            ];
            if ($threadId > 0) {
                $data['thread_id'] = (string)$threadId;
            }

            $title = '';
            $body = '';
            if ($status === 'accepted') {
                $title = 'Offer Accepted';
                $body = $actorName . ' accepted your offer for "' . $itemTitle . '"';
            } elseif ($status === 'declined') {
                $title = 'Offer Declined';
                $body = $actorName . ' declined your offer for "' . $itemTitle . '"';
            } elseif ($status === 'pickup') {
                $title = 'Ready for Pickup';
                $body = 'Your item "' . $itemTitle . '" is ready for pickup';
            } elseif ($status === 'completed') {
                $title = 'Deal Completed';
                $body = 'Your deal for "' . $itemTitle . '" is complete';
            } elseif ($status === 'cancelled') {
                $title = 'Offer Cancelled';
                $body = $actorName . ' cancelled the offer for "' . $itemTitle . '"';
            }

            if ($title !== '') {
                NotificationManager::sendToUser($pdo, $peerId, $title, $body, $data);
            }
        }
    } catch (Throwable $e) {
        error_log('[polygo-api] deal notification failed: ' . $e->getMessage());
    }
}
