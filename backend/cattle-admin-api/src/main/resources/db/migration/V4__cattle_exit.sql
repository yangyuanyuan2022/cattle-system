CREATE TABLE cattle_exit (
    exit_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    cattle_id BIGINT NOT NULL,
    exit_type VARCHAR(20) NOT NULL,
    exit_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    restored_at DATETIME NULL,
    restored_by BIGINT NULL,
    restore_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exit_id),
    KEY idx_exit_cattle (farm_id, cattle_id, created_at),
    KEY idx_exit_status (farm_id, exit_type, exit_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
