-- Preserve historical Flyway V2 checksum and existing foreign keys; disable only untouched demo passwords.
-- Provision a real manager with APP_BOOTSTRAP_ADMIN_USERNAME/PASSWORD on the next application start.
UPDATE app_user
SET enabled = FALSE, token_version = token_version + 1
WHERE
    (username = 'sales' AND password_hash = '$2a$10$GL11q2kqziK4HAR/x4bQYeiTxQbQVWUFHgp1F.V5.Xhgv0xC7/lIi')
 OR (username = 'warehouse' AND password_hash = '$2a$10$9UgyO9IM7ls4Xi1l/9NygOduPfjpDhgXt9g15p/vxgbXfBSE.AOgm')
 OR (username = 'manager' AND password_hash = '$2a$10$MDFXlV/oUw5oQptQ85o/r.IQgKkC8K0MBLogydTeiNnWu33rTfJFe');
