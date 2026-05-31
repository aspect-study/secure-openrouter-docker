-- ============================================================
-- V3__seed_admin_user.sql
-- Seeds the default admin user.
-- INSERT IGNORE: idempotent — re-running is safe.
--
-- Password: Admin@2026! (BCrypt strength 12)
-- IMPORTANT: Change this password immediately after first login
-- via POST /api/auth/change-password.
-- ============================================================

INSERT IGNORE INTO users (email, password_hash, role, active, created_at, updated_at)
VALUES (
    'admin@openrouter.local',
    '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
    'ADMIN',
    1,
    NOW(6),
    NOW(6)
);
