INSERT INTO app_user (username, password_hash, display_name, role, enabled)
VALUES
    ('sales', '$2a$10$GL11q2kqziK4HAR/x4bQYeiTxQbQVWUFHgp1F.V5.Xhgv0xC7/lIi', 'Sales', 'SALES', TRUE),
    ('warehouse', '$2a$10$9UgyO9IM7ls4Xi1l/9NygOduPfjpDhgXt9g15p/vxgbXfBSE.AOgm', 'Warehouse', 'WAREHOUSE', TRUE),
    ('manager', '$2a$10$MDFXlV/oUw5oQptQ85o/r.IQgKkC8K0MBLogydTeiNnWu33rTfJFe', 'Manager', 'MANAGER', TRUE)
ON CONFLICT (username) DO NOTHING;
