<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/NotificationManager.php';

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
        $sellerId = (int)($input['seller_id'] ?? 0);
        $amount = (float)($input['amount'] ?? 0);

        if ($listingId <= 0 || $sellerId <= 0 || $amount <= 0) {
            respond(false, 'Invalid transaction details');
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

        $query = $pdo->prepare('UPDATE transactions SET status = ? WHERE id = ?');
        if ($query->execute([$status, $transactionId])) {
            respond(true, 'Transaction updated');
        } else {
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
    respond(false, 'Database error: ' . $e->getMessage());
}
