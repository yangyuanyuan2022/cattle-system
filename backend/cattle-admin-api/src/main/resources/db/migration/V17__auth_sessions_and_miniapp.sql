ALTER TABLE sys_user
    ADD COLUMN wechat_openid VARCHAR(100) NULL,
    ADD UNIQUE KEY uk_user_wechat_openid (wechat_openid);

CREATE TABLE auth_refresh_token (
    refresh_token_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    replaced_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    INDEX idx_refresh_user_active (user_id, revoked_at, expires_at)
);
