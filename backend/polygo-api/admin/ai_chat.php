<?php
require_once 'config/database.php';
requireAdmin();

$pdo = getConnection();

// Text-only Gemini chat for the admin control center. Uses the same server-side
// GEMINI_API_KEY from ../secrets.php that ai_suggest.php already uses — the key
// never leaves the server.

const AI_MODEL = 'gemini-3.6-flash';
const AI_MAX_MESSAGES = 40;
const AI_MAX_PROMPT = 2000;

/** Real, deduplicated campus metrics injected into the prompt so the AI never invents numbers. */
function aiGreenContext(PDO $pdo): string {
    try {
        $totals = $pdo->query("SELECT COUNT(*) AS deals,
                COALESCE(SUM(co2_kg),0) AS co2, COALESCE(SUM(water_l),0) AS water,
                COALESCE(SUM(paper_kg),0) AS paper, COALESCE(SUM(energy_kwh),0) AS energy
            FROM (SELECT MAX(co2_kg) AS co2_kg, MAX(water_l) AS water_l,
                         MAX(paper_kg) AS paper_kg, MAX(energy_kwh) AS energy_kwh
                  FROM impact_entries GROUP BY transaction_id) g")->fetch();
        $fortnight = (float) $pdo->query("SELECT COALESCE(SUM(co2_kg),0)
            FROM (SELECT MAX(co2_kg) AS co2_kg, MIN(created_at) AS created_at
                  FROM impact_entries GROUP BY transaction_id) g
            WHERE created_at >= NOW() - INTERVAL 14 DAY")->fetchColumn();
        $top = $pdo->query("SELECT category_name, ROUND(SUM(co2_kg),2) AS co2, COUNT(*) AS cnt
            FROM (SELECT category_name, MAX(co2_kg) AS co2_kg
                  FROM impact_entries GROUP BY transaction_id, category_name) g
            GROUP BY category_name ORDER BY co2 DESC LIMIT 1")->fetch();
    } catch (Throwable $e) {
        return '[Green impact data is temporarily unavailable — rely on general guidance only.]';
    }
    $dealCount = (int) ($totals['deals'] ?? 0);
    if ($dealCount === 0) {
        return '[Campus-wide Green Impact: no completed deals yet. No CO2/water/paper/energy credited so far.]';
    }
    return sprintf(
        '[Campus-wide Green Impact, deduplicated by completed deal: %d deals; %.1f kg CO2; %.0f L water; %.1f kg paper; %.1f kWh energy saved. Last 14 days: %.1f kg CO2. Top category: %s (%.1f kg CO2 across %d deals).]',
        $dealCount,
        (float) ($totals['co2'] ?? 0),
        (float) ($totals['water'] ?? 0),
        (float) ($totals['paper'] ?? 0),
        (float) ($totals['energy'] ?? 0),
        $fortnight,
        (string) ($top['category_name'] ?? 'n/a'),
        (float) ($top['co2'] ?? 0),
        (int) ($top['cnt'] ?? 0)
    );
}

$error = '';
$notice = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = trim((string) ($_POST['action'] ?? ''));

    if ($action === 'reset') {
        unset($_SESSION['ai_chat_history']);
        header('Location: ai_chat.php');
        exit();
    }

    if (verifyCsrf()) {
        $message = trim((string) ($_POST['message'] ?? ''));
        if (mb_strlen($message) > AI_MAX_PROMPT) {
            $error = 'Message too long (max ' . AI_MAX_PROMPT . ' characters).';
        } elseif ($message === '') {
            $error = 'Type a message first.';
        } elseif (adminRateLimit($pdo, 'ai_chat_admin', 30, 3600)) {
            $error = 'Too many AI requests. Try again later.';
        } elseif (!defined('GEMINI_API_KEY') || GEMINI_API_KEY === '') {
            $error = 'GEMINI_API_KEY is not configured in ../secrets.php yet.';
        } else {
            if (!isset($_SESSION['ai_chat_history']) || !is_array($_SESSION['ai_chat_history'])) {
                $_SESSION['ai_chat_history'] = [];
            }
            $history = $_SESSION['ai_chat_history'];
            $history[] = ['role' => 'user', 'parts' => [['text' => $message]]];
            $history = array_slice($history, -AI_MAX_MESSAGES);

            $system = "You are the Green Impact assistant for the PolyGo+ campus marketplace admin panel.\n"
                . "Answer concisely, in plain text, and use the real metrics in [brackets] when they matter. "
                . "Never invent numbers that are not in the context. Helpful tasks: summarize campus impact, "
                . "explain the methodology (reuse displaces manufacturing; tech ~45 kg CO2, services 0 kg), "
                . "and draft student announcements.\n"
                . "Keep replies under ~120 words unless asked for detail.\n"
                . aiGreenContext($pdo);

            $payload = [
                'contents'           => $history,
                'systemInstruction'  => ['parts' => [['text' => $system]]],
            ];

            $ch = curl_init('https://generativelanguage.googleapis.com/v1beta/models/' . AI_MODEL . ':generateContent?key=' . urlencode(GEMINI_API_KEY));
            curl_setopt_array($ch, [
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_POST           => true,
                CURLOPT_POSTFIELDS     => json_encode($payload),
                CURLOPT_HTTPHEADER     => ['Content-Type: application/json'],
                CURLOPT_TIMEOUT        => 60,
            ]);
            $raw = curl_exec($ch);
            $curlError = curl_error($ch);
            $httpCode = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
            curl_close($ch);

            if ($raw === false) {
                $error = 'AI service unreachable (' . $curlError . '). Try again.';
            } elseif ($httpCode >= 400) {
                $error = 'AI request failed (HTTP ' . $httpCode . ').';
            } else {
                $json = json_decode($raw, true);
                $reply = trim((string) ($json['candidates'][0]['content']['parts'][0]['text'] ?? ''));
                if ($reply === '') {
                    $error = 'AI returned an empty response. Try rewording the question.';
                } else {
                    $history[] = ['role' => 'model', 'parts' => [['text' => $reply]]];
                    $_SESSION['ai_chat_history'] = $history;
                    header('Location: ai_chat.php');
                    exit();
                }
            }
        }
    } else {
        $error = 'Security token mismatch. Please try again.';
    }
}

$history = isset($_SESSION['ai_chat_history']) && is_array($_SESSION['ai_chat_history']) ? $_SESSION['ai_chat_history'] : [];

function aiChatBubble(string $role, string $text): void {
    $isModel = $role === 'model';
    $escaped = htmlspecialchars($text, ENT_QUOTES, 'UTF-8');
    $escaped = preg_replace('/\*\*(.+?)\*\*/s', '<strong>$1</strong>', $escaped) ?? $escaped;
    $score = $isModel ? 'bg-white border' : 'bg-primary text-white';
    $align = $isModel ? '' : 'justify-content-end';
    $label = $isModel ? 'Assistant' : 'You';
    echo '<div class="d-flex ' . $align . ' mb-3">';
    echo '<div class="' . $score . ' rounded-3 px-3 py-2 chat-bubble" style="max-width:78%;">';
    echo '<small class="' . ($isModel ? 'text-muted' : 'text-white-50') . ' fw-bold d-block mb-1">' . $label . '</small>';
    echo '<div class="chat-text">' . nl2br($escaped) . '</div>';
    echo '</div></div>';
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Assistant - PolyGo+ Admin</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css" rel="stylesheet">
    <link href="assets/css/style.css" rel="stylesheet">
    <style>
        #aiChatLog { max-height: 62vh; overflow-y: auto; }
        .chat-bubble { word-break: break-word; }
        .chat-text { font-size: .95rem; }
    </style>
</head>
<body>
    <div class="d-flex" id="wrapper">
        <?php include 'includes/sidebar.php'; ?>

        <div id="page-content-wrapper">
            <?php include 'includes/header.php'; ?>

            <div class="container-fluid px-4">
                <div class="row mt-3 mb-3">
                    <div class="col-12 d-flex justify-content-between align-items-center">
                        <div>
                            <h3 class="fw-bold"><i class="bi bi-robot"></i> AI Green Assistant</h3>
                            <p class="page-head-sub mb-0">Chat with Gemini about campus Green Impact — answers are grounded in the real, deduplicated metrics.</p>
                        </div>
                        <?php if ($history): ?>
                            <form method="post" onsubmit="return confirm('Clear this conversation?');">
                                <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars(csrfToken()); ?>">
                                <input type="hidden" name="action" value="reset">
                                <button type="submit" class="btn btn-outline-danger btn-sm"><i class="bi bi-trash"></i> Clear conversation</button>
                            </form>
                        <?php endif; ?>
                    </div>
                </div>

                <?php if ($error): ?>
                    <div class="alert alert-danger py-2"><i class="bi bi-exclamation-triangle"></i> <?php echo htmlspecialchars($error); ?></div>
                <?php endif; ?>
                <?php if ($notice): ?>
                    <div class="alert alert-success py-2"><i class="bi bi-check-circle"></i> <?php echo htmlspecialchars($notice); ?></div>
                <?php endif; ?>

                <div class="card">
                    <div class="card-body">
                        <div id="aiChatLog">
                            <?php if (!$history): ?>
                                <div class="text-center text-muted py-5">
                                    <i class="bi bi-robot" style="font-size:2.5rem;"></i>
                                    <p class="mt-3 mb-0">Ask me anything about the campus Green Impact, e.g.<br>
                                        "Summarize this month's impact" · "Draft an announcement about our CO2 savings"
                                        · "Which category saves the most water?"</p>
                                </div>
                            <?php else: ?>
                                <?php foreach ($history as $msg): ?>
                                    <?php aiChatBubble((string) $msg['role'], (string) ($msg['parts'][0]['text'] ?? '')); ?>
                                <?php endforeach; ?>
                            <?php endif; ?>
                        </div>
                    </div>
                    <div class="card-footer bg-white">
                        <form method="post" class="d-flex gap-2">
                            <input type="hidden" name="csrf_token" value="<?php echo htmlspecialchars(csrfToken()); ?>">
                            <textarea name="message" rows="2" maxlength="<?php echo AI_MAX_PROMPT; ?>" required
                                class="form-control" placeholder="Message the Green Impact assistant…"
                                style="resize:none;"></textarea>
                            <button type="submit" class="btn btn-primary flex-shrink-0">
                                <i class="bi bi-send"></i> Send
                            </button>
                        </form>
                    </div>
                </div>
            </div>

            <?php include 'includes/footer.php'; ?>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="assets/js/main.js"></script>
    <script>
        (function () {
            var log = document.getElementById('aiChatLog');
            if (log) log.scrollTop = log.scrollHeight;
        })();
    </script>
</body>
</html>