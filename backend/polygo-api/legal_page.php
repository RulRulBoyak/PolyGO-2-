<?php
declare(strict_types=1);

function renderLegalPage(string $title, string $body): never
{
    header('Content-Type: text/html; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    header('Referrer-Policy: no-referrer');
    header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
    $safeTitle = htmlspecialchars($title, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    echo '<!doctype html><html lang="en"><head><meta charset="utf-8">'
        . '<meta name="viewport" content="width=device-width,initial-scale=1">'
        . '<title>' . $safeTitle . ' · PolyGo+</title><style>'
        . 'body{margin:0;background:#f5f7fb;color:#172033;font:16px/1.65 system-ui,-apple-system,sans-serif}'
        . 'main{max-width:760px;margin:0 auto;padding:48px 22px 80px}article{background:#fff;border:1px solid #e4e9f1;border-radius:20px;padding:clamp(24px,5vw,48px);box-shadow:0 12px 36px #1e5aa414}'
        . 'h1{margin:0 0 8px;color:#0d47a1;font-size:clamp(28px,5vw,42px)}h2{margin-top:30px;font-size:20px}a{color:#0d47a1}small{color:#637083}'
        . '</style></head><body><main><article><h1>' . $safeTitle . '</h1><small>PolyGo+ · Last updated 22 September 2026</small>'
        . $body . '</article></main></body></html>';
    exit;
}
