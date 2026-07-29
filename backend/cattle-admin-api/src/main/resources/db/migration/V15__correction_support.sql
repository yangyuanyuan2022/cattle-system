ALTER TABLE cattle_exit
    ADD COLUMN is_void TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN void_reason VARCHAR(255) NULL,
    ADD COLUMN voided_by BIGINT NULL,
    ADD COLUMN voided_at DATETIME NULL,
    ADD INDEX idx_exit_void (farm_id, is_void);
