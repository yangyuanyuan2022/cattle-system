CREATE TABLE operation_log (
    operation_log_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    module_code VARCHAR(50) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operation_log_id),
    KEY idx_operation_business (farm_id, business_type, business_id, created_at),
    KEY idx_operation_user_time (farm_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
