<?php
// Seeding script: only runnable from the local machine (or the Android emulator host bridge).
$maintenanceClient = $_SERVER['REMOTE_ADDR'] ?? '';
if (!in_array($maintenanceClient, ['127.0.0.1', '::1', '10.0.2.2'], true)) {
    http_response_code(403);
    echo '<h1>Forbidden</h1><p>This seeding script may only be run locally.</p>';
    exit;
}
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/ImpactEngine.php';
header('Content-Type: text/html; charset=utf-8');

/**
 * PolyGo+ Demo Data Seed
 * Populates users, listings, reviews, transactions (incl. completed deals that
 * credit the Green Impact system), messages, notifications and favorites so the
 * app can be tested end-to-end. Idempotent: run as seed_demo.php?reset=1 to
 * wipe demo rows and re-seed. Demo passwords are all "Test1234".
 */
function seed_say($msg) {
    echo "✅ {$msg}<br>";
}

try {
    echo "<h1>🌀 PolyGo+ Demo Data Seed</h1>";

    $demoStudents = [
        'A202601' => ['Aiman', 'aiman.test@pks.edu.my'],
        'D2001'   => ['Nurul', 'nurul@pks.edu.my'],
        'D2002'   => ['Wei Jie', 'weijie@pks.edu.my'],
        'D2003'   => ['Priya', 'priya@pks.edu.my'],
        'D2004'   => ['Daniel', 'daniel@pks.edu.my'],
        'D2005'   => ['Siti', 'siti@pks.edu.my'],
        'D2006'   => ['Afiq', 'afiq@pks.edu.my'],
        'D2007'   => ['Hana', 'hana@pks.edu.my'],
    ];
    $testerKey = 'A202601';
    $reset = isset($_GET['reset']);

    // ---------------------------------------------------------------- resolve existing users
    $placeholders = implode(',', array_fill(0, count($demoStudents), '?'));
    $studentIds = array_keys($demoStudents);
    $query = $pdo->prepare("SELECT id, student_id FROM users WHERE student_id IN ($placeholders)");
    $query->execute($studentIds);
    $existing = [];
    while ($row = $query->fetch()) {
        $existing[$row['student_id']] = (int)$row['id'];
    }

    if (!$reset && isset($existing[$testerKey])) {
        echo "<b>Demo data already exists</b> — re-run as <code>seed_demo.php?reset=1</code> to wipe and re-seed.<br>";
        exit;
    }

    if ($reset && count($existing) > 0) {
        $ids = array_values($existing);
        $ph = implode(',', array_fill(0, count($ids), '?'));
        $resetStatements = [
            "DELETE FROM impact_entries WHERE user_id IN ($ph)",
            "DELETE FROM user_impact WHERE user_id IN ($ph)",
            "DELETE FROM transactions WHERE buyer_id IN ($ph) OR seller_id IN ($ph)",
            "DELETE FROM notifications WHERE user_id IN ($ph)",
            "DELETE FROM favorites WHERE user_id IN ($ph)",
            "DELETE FROM reviews WHERE seller_id IN ($ph) OR reviewer_id IN ($ph)",
            "DELETE FROM messages WHERE sender_id IN ($ph)",
            "DELETE FROM threads WHERE buyer_id IN ($ph) OR seller_id IN ($ph)",
            "DELETE FROM listings WHERE owner_id IN ($ph)",
            "DELETE FROM users WHERE id IN ($ph)"
        ];
        foreach ($resetStatements as $sql) {
            $run = $pdo->prepare($sql);
            $run->execute($ids);
        }
        $existing = [];
        seed_say("Reset old demo rows");
    }

    // -------------------------------------------------------------------------------- users
    $passwordHash = password_hash('Test1234', PASSWORD_DEFAULT);
    $insertUser = $pdo->prepare('INSERT INTO users (full_name, student_id, email, password_hash) VALUES (?, ?, ?, ?)');
    $fetchUser = $pdo->prepare('SELECT id FROM users WHERE student_id = ? LIMIT 1');
    $userId = $existing;
    foreach ($demoStudents as $sid => $pair) {
        if (!isset($userId[$sid])) {
            $insertUser->execute([$pair[0], $sid, $pair[1], $passwordHash]);
            $fetchUser->execute([$sid]);
            $userId[$sid] = (int)$fetchUser->fetchColumn();
        }
    }
    seed_say('Users ready (' . count($userId) . ')');

    $U = function ($key) use ($userId) { return $userId[$key]; };

    // ------------------------------------------------------------------------------ listings
    // [ownerKey, category, title, description, price, image_url|null]
    $listings = [
        [$U('D2001'), 'Tech',          'iPhone 12 64GB (good condition)',        'RM-friendly condition, always in a case, battery 89%, includes charger + clear case.', 980, 'https://picsum.photos/seed/polygo1/600/600'],
        [$U('D2002'), 'Electronics',   'Samsung 27" LED Monitor',                'Barely used, no dead pixels, HDMI cable included. Great for dorms.', 320, 'https://picsum.photos/seed/polygo2/600/600'],
        [$U('D2003'), 'Books',         'Calculus Early Transcendentals',         '8th edition, some pencil notes in 2 chapters, cover intact.', 45, null],
        [$U('D2004'), 'Fashion',       'Uniqlo windbreaker (M)',                 'Worn twice, washed once. Navy blue, perfect for monsoon season.', 55, 'https://picsum.photos/seed/polygo4/600/600'],
        [$U('D2005'), 'Home',          'IKEA desk lamp',                         'White, works perfectly, low energy bulb included.', 18, null],
        [$U('D2006'), 'Repair',        'Bicycle tune-up service',                'One-year-old MTB, recently serviced. Free pickup around campus.', 35, null],
        [$U('D2007'), 'Food',          'Giant Kinoko bento voucher',             'RM12 dine-in voucher, expires end of month.', 12, null],
        [$U('D2001'), 'Drink',         'Coffee machine capsules (box of 40)',    'Nescafe Dolce Gusto, mixed flavours, sealed.', 25, null],
        [$U('D2002'), 'Tech',          'iPad Air 2022 (Wi-Fi)',                  '64GB, Space Grey, tempered glass since day 1, Apple pencil not included.', 1450, 'https://picsum.photos/seed/polygo9/600/600'],
        [$U('D2002'), 'Electronics',   'Bose QC45 noise-cancelling headphones',  'In-box, barely used, great battery. Ideal for library sessions.', 620, 'https://picsum.photos/seed/polygo10/600/600'],
        [$U('D2003'), 'Books',         'Linear Algebra notes binder',            'Handwritten A+ notes, full semester, also includes past papers.', 30, null],
        [$U('D2004'), 'Tech',          'Razer mechanical keyboard',              'Green switches, custom keycaps, fully functional.', 120, null],
        [$U('D2005'), 'Home',          'Study chair',                            'Comfortable desk chair, height adjustable, wheels fine.', 85, null],
        [$U('D2006'), 'Fashion',       'Nike running shoes (US 9)',              'Used for 3 runs only, cleaned. Lightweight trainers.', 150, 'https://picsum.photos/seed/polygo14/600/600'],
        [$U('D2001'), 'Laundry',       'Laundry service (5kg)',                  'Wash + fold, pick up and drop off on campus.', 10, null],
        [$U('D2002'), 'Delivery',      'Runner service (campus-wide)',           'Food, parcels, documents — same day delivery around PKS.', 8, null],
        [$U('D2003'), 'Repair',        'Laptop screen replacement',              'Parts + labour, most models, done within a week.', 180, null],
        [$U('D2004'), 'Tech',          'MacBook Air M1 256GB',                   'Excellent condition, battery cycles under 100, charger included.', 2400, 'https://picsum.photos/seed/polygo18/600/600'],
        [$U('D2005'), 'Books',         'Organic Chemistry, 3rd edition',         'Slightly worn corners, all pages intact, no writing.', 60, null],
        [$U('D2006'), 'Services',      'Videography for assignments',            'Event + project videography, UBS camera available, student pricing.', 90, null],
        [$U('A202601'), 'Tech',        'PS5 DualSense controller',               'Bought 6 months ago, used rarely, works perfectly, original box.', 200, 'https://picsum.photos/seed/polygo21/600/600'],
        [$U('A202601'), 'Books',       'Engineering Thermodynamics notes',       'Complete set of notes + tutorial solutions for EML3100.', 25, null],
    ];

    $insertListing = $pdo->prepare('INSERT INTO listings (owner_id, title, category, description, price, image_url, location) VALUES (?, ?, ?, ?, ?, ?, ?)');
    $listingId = [];
    foreach ($listings as $i => $item) {
        $insertListing->execute([$item[0], $item[2], $item[1], $item[3], (float)$item[4], $item[5], 'Near campus']);
        $listingId['l' . ($i + 1)] = (int)$pdo->lastInsertId();
    }
    seed_say('Listings created (' . count($listingId) . ')');

    $L = function ($key) use ($listingId) { return $listingId[$key]; };

    // ------------------------------------------------------------------------------- reviews
    // [sellerKey, listingKey, reviewerKey, stars, comment]  (reviewer != seller)
    $reviews = [
        [$U('D2001'), $L('l1'),  $U('D2002'), 5, 'As described, honest seller. Battery was really 89%.'],
        [$U('D2001'), $L('l8'),  $U('D2004'), 4, 'Smooth transaction, met at cafeteria.'],
        [$U('D2001'), $L('l1'),  $U('A202601'), 5, 'Arrived clean and working. Would buy again.'],
        [$U('D2002'), $L('l2'),  $U('D2003'), 5, 'Monitor has zero dead pixels, great price.'],
        [$U('D2002'), $L('l9'),  $U('D2005'), 4, 'iPad was well kept, minor scratch on back only.'],
        [$U('D2002'), $L('l10'), $U('A202601'), 5, 'Headphones brand new in box, super responsive.'],
        [$U('D2004'), $L('l4'),  $U('D2001'), 4, 'Jacket fits well, very honest listing.'],
        [$U('D2004'), $L('l12'), $U('D2006'), 5, 'Keyboard types beautifully, keycaps are nice.'],
        [$U('D2004'), $L('l18'), $U('D2007'), 3, 'Laptop good but expected newer OS version.'],
        [$U('A202601'), $L('l21'), $U('D2005'), 5, 'Controller works perfectly, kept warranty box.'],
    ];
    $insertReview = $pdo->prepare('INSERT INTO reviews (seller_id, listing_id, reviewer_id, reviewer_name, stars, comment) VALUES (?, ?, ?, ?, ?, ?)');
    $fetchName = $pdo->prepare('SELECT full_name FROM users WHERE id = ? LIMIT 1');
    foreach ($reviews as $r) {
        $fetchName->execute([$r[2]]);
        $reviewerName = $fetchName->fetchColumn();
        $insertReview->execute([$r[0], $r[1], $r[2], $reviewerName, $r[3], $r[4]]);
    }
    seed_say('Reviews created (' . count($reviews) . ')');

    // -------------------------------------------------------------------------- transactions
    // [listingKey, buyerKey, status]
    $transactions = [
        [$L('l1'),  $U('A202601'), 'completed'], // tester buys iPhone
        [$L('l9'),  $U('A202601'), 'completed'], // tester buys iPad
        [$L('l3'),  $U('A202601'), 'completed'], // tester buys calculus book
        [$L('l21'), $U('D2005'),   'completed'], // tester SELLS controller
        [$L('l2'),  $U('D2003'),   'completed'],
        [$L('l10'), $U('D2004'),   'completed'],
        [$L('l7'),  $U('D2001'),   'completed'],
        [$L('l8'),  $U('D2003'),   'accepted'],
        [$L('l11'), $U('D2005'),   'accepted'],
        [$L('l15'), $U('A202601'), 'offer_sent'], // tester offers on laundry
        [$L('l17'), $U('D2006'),   'offer_sent'],
        [$L('l13'), $U('A202601'), 'cancelled'],
    ];
    $insertTransaction = $pdo->prepare("INSERT INTO transactions (listing_id, buyer_id, seller_id, amount, status) VALUES (?, ?, ?, ?, ?)");
    $getOwner = $pdo->prepare('SELECT owner_id FROM listings WHERE id = ? LIMIT 1');
    $markCredited = $pdo->prepare('UPDATE transactions SET impact_credited = 1 WHERE id = ?');
    $completedCount = 0;
    foreach ($transactions as $t) {
        [$listing, $buyer, $status] = $t;
        $getOwner->execute([$listing]);
        $seller = (int)$getOwner->fetchColumn();
        $amount = $status === 'completed' ? 900.0 : 100.0;
        $insertTransaction->execute([$listing, $buyer, $seller, $amount, $status]);
        if ($status === 'completed') {
            $txnId = (int)$pdo->lastInsertId();
            $markCredited->execute([$txnId]);
            ImpactEngine::creditTransaction($pdo, $txnId); // also tests the green engine
            $completedCount++;
        }
    }
    seed_say("Transactions created (" . count($transactions) . ", $completedCount completed → impact credited)");

    // -------------------------------------------------------------------- messages & threads
    $insertThread = $pdo->prepare('INSERT INTO threads (listing_id, buyer_id, seller_id) VALUES (?, ?, ?)');
    $insertMessage = $pdo->prepare('INSERT INTO messages (thread_id, sender_id, text, is_read) VALUES (?, ?, ?, ?)');
    $insertThread->execute([$L('l1'), $U('A202601'), $U('D2001')]);
    $thread1 = (int)$pdo->lastInsertId();
    $insertMessage->execute([$thread1, $U('D2001'), 'Hi! The iPhone is still available. Battery 89%, always in a case.', 0]);
    $insertMessage->execute([$thread1, $U('A202601'), "Great, can I offer RM 950?", 1]);
    $insertMessage->execute([$thread1, $U('D2001'), 'Sure, that works!', 1]);
    $insertMessage->execute([$thread1, $U('A202601'), 'Deal, let us meet near Cafeteria B later.', 1]);

    $insertThread->execute([$L('l21'), $U('D2006'), $U('A202601')]); // inquiry on tester's controller
    $thread2 = (int)$pdo->lastInsertId();
    $insertMessage->execute([$thread2, $U('D2006'), 'Is the DualSense controller still available? Firm on RM180?', 0]);
    $insertMessage->execute([$thread2, $U('A202601'), 'Still available, I can do RM190.', 1]);
    seed_say('Threads + messages created');

    // ------------------------------------------------------------------------- notifications
    $insertNotification = $pdo->prepare('INSERT INTO notifications (user_id, title, body, is_read) VALUES (?, ?, ?, ?)');
    $insertNotification->execute([$U('A202601'), 'New Offer Received!', 'Afiq offered RM180 for your PS5 DualSense controller.', 0]);
    $insertNotification->execute([$U('A202601'), 'Deal Completed!', 'Your iPhone 12 sale is complete. Green impact credited!', 0]);
    $insertNotification->execute([$U('A202601'), 'New message', 'Nurul replied to your message.', 1]);
    $insertNotification->execute([$U('D2001'), 'New Offer Received!', 'Aiman offered RM950 for your iPhone 12.', 1]);
    seed_say('Notifications created');

    // ----------------------------------------------------------------------------- favorites
    $insertFavorite = $pdo->prepare('INSERT IGNORE INTO favorites (user_id, listing_id) VALUES (?, ?)');
    $insertFavorite->execute([$U('A202601'), $L('l2')]);
    $insertFavorite->execute([$U('A202601'), $L('l9')]);
    seed_say('Favorites created');

    // --------------------------------------------------------------------------------- summary
    $count = function ($table) use ($pdo) {
        return (int)$pdo->query("SELECT COUNT(*) FROM $table")->fetchColumn();
    };
    echo "<h3>Summary</h3>";
    echo "<ul>";
    echo "<li>Users: {$count('users')}</li>";
    echo "<li>Listings: {$count('listings')}</li>";
    echo "<li>Reviews: {$count('reviews')}</li>";
    echo "<li>Transactions: {$count('transactions')}</li>";
    echo "<li>Impact credited entries: {$count('impact_entries')}</li>";
    echo "<li>Impact-tracked users: {$count('user_impact')}</li>";
    echo "</ul>";
    echo "<p>Demo accounts (password <b>Test1234</b>): <b>A202601</b> + D2001–D2007</p>";

} catch (Exception $e) {
    echo '<h1>❌ Seed Failed</h1>' . htmlspecialchars($e->getMessage());
}