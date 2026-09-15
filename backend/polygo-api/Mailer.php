<?php
declare(strict_types=1);

// Out-of-band delivery for OTPs and temporary passwords.
// Uses PHPMailer (composer autoload) when SMTP is configured in secrets.php;
// otherwise falls back to the PHP error log so the local/emulator workflow
// keeps working. Secrets are NEVER returned in an API response.

$autoload = __DIR__ . '/vendor/autoload.php';
if (is_file($autoload)) {
    require_once $autoload;
}

final class Mailer
{
    public static function send(string $to, string $subject, string $body): bool
    {
        $host = defined('SMTP_HOST') ? SMTP_HOST : '';
        $user = defined('SMTP_USER') ? SMTP_USER : '';
        $pass = defined('SMTP_PASS') ? SMTP_PASS : '';
        $from = defined('SMTP_FROM') ? SMTP_FROM : '';

        if ($host !== '' && class_exists(\PHPMailer\PHPMailer\PHPMailer::class)) {
            try {
                $mail = new \PHPMailer\PHPMailer\PHPMailer(true);
                $mail->isSMTP();
                $mail->Host = (string)$host;
                $port = defined('SMTP_PORT') ? (int)SMTP_PORT : 587;
                $mail->Port = $port;
                $mail->SMTPAuth = ($user !== '' && $pass !== '');
                if ($mail->SMTPAuth) {
                    $mail->Username = (string)$user;
                    $mail->Password = (string)$pass;
                }
                $mail->SMTPSecure = $port === 465
                    ? \PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_SMTPS
                    : \PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_STARTTLS;
                $mail->CharSet = 'UTF-8';
                $mail->setFrom($from !== '' ? $from : ($user !== '' ? $user : 'noreply@polygo.local'), 'PolyGo+');
                $mail->addAddress($to);
                $mail->Subject = $subject;
                $mail->Body = $body;
                return $mail->send();
            } catch (\Throwable $error) {
                error_log('[polygo-api] mail failed: ' . $error->getMessage());
                return false;
            }
        }

        // Dev fallback: surface the payload in the server log only.
        error_log("[polygo-api] [dev-mail] To=$to Subject=$subject Body=$body");
        return true;
    }
}