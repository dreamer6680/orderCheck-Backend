-- Tokens issued before this migration are invalidated because they do not carry a tokenVersion claim.
ALTER TABLE app_user ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
