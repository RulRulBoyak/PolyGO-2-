<?php
declare(strict_types=1);
require_once __DIR__ . '/legal_page.php';

renderLegalPage('Delete your PolyGo+ account', '
<p>PolyGo+ lets you permanently delete your account and its associated data.</p>
<h2>Delete inside the app</h2>
<ol><li>Sign in to PolyGo+.</li><li>Open <strong>Profile</strong>, then <strong>Account &amp; privacy</strong>.</li><li>Select <strong>Delete account</strong> and confirm.</li></ol>
<h2>If you cannot access the app</h2>
<p>Email <a href="mailto:support@poliku.com?subject=PolyGo%2B%20account%20deletion">support@poliku.com</a> from the email address attached to the account. Include your student ID. We may ask for verification before deleting data.</p>
<h2>What is deleted</h2>
<p>Your profile, verification request, listings and uploaded listing/profile images, favorites, conversations, offers, reviews, notifications, reports, safety logs, follows, timetable entries, campus posts and bug reports are removed. Limited records may be retained only when required by law, security, fraud prevention or an unresolved dispute.</p>');
