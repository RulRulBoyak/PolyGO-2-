-- Migration: ensure transactions.status supports the 'declined' state.
-- Supersedes the enum redefinition in migration_add_security_logs.sql, which
-- omitted 'declined' and would break seller decline flows on any database
-- built from that migration in isolation. The master schema (polygo.sql)
-- already includes 'declined'; apply this once against live DBs that predate it.
ALTER TABLE transactions
    MODIFY status ENUM('offer_sent','accepted','declined','pickup','completed','cancelled') NOT NULL DEFAULT 'offer_sent';