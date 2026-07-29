CREATE TABLE barn (
    barn_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, barn_code VARCHAR(50) NOT NULL,
    barn_name VARCHAR(100) NOT NULL, barn_type VARCHAR(30) NULL, capacity INT NULL,
    manager_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ENABLED', remark VARCHAR(500) NULL,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_barn_farm_code UNIQUE (farm_id, barn_code),
    INDEX idx_barn_farm_status (farm_id, status), INDEX idx_barn_name (barn_name),
    CONSTRAINT chk_barn_capacity CHECK (capacity IS NULL OR capacity >= 0)
);

CREATE TABLE herd (
    herd_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, herd_code VARCHAR(50) NOT NULL,
    herd_name VARCHAR(100) NOT NULL, herd_type VARCHAR(30) NULL, barn_id BIGINT NULL,
    manager_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ENABLED', remark VARCHAR(500) NULL,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_herd_farm_code UNIQUE (farm_id, herd_code),
    INDEX idx_herd_farm_status (farm_id, status), INDEX idx_herd_barn (barn_id), INDEX idx_herd_name (herd_name)
);

CREATE TABLE transfer_batch (
    batch_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, transfer_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    transfer_date DATETIME NOT NULL, reason VARCHAR(255) NULL, operator_id BIGINT NOT NULL,
    total_count INT NOT NULL DEFAULT 0, is_void TINYINT NOT NULL DEFAULT 0, void_reason VARCHAR(255) NULL,
    voided_by BIGINT NULL, voided_at DATETIME NULL, created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, version INT NOT NULL DEFAULT 0,
    INDEX idx_transfer_batch_farm_date (farm_id, transfer_date), INDEX idx_transfer_batch_operator (operator_id),
    INDEX idx_transfer_batch_void (is_void)
);

CREATE TABLE transfer_record (
    transfer_id BIGINT PRIMARY KEY, batch_id BIGINT NOT NULL, farm_id BIGINT NOT NULL, cattle_id BIGINT NOT NULL,
    from_herd_id BIGINT NULL, from_barn_id BIGINT NULL, to_herd_id BIGINT NULL, to_barn_id BIGINT NULL,
    transfer_date DATETIME NOT NULL, reason VARCHAR(255) NULL, operator_id BIGINT NOT NULL,
    is_void TINYINT NOT NULL DEFAULT 0, void_reason VARCHAR(255) NULL, voided_by BIGINT NULL, voided_at DATETIME NULL,
    created_by BIGINT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, version INT NOT NULL DEFAULT 0,
    INDEX idx_transfer_record_batch (batch_id), INDEX idx_transfer_record_farm_cattle (farm_id, cattle_id),
    INDEX idx_transfer_record_date (transfer_date), INDEX idx_transfer_record_target (to_barn_id, to_herd_id),
    INDEX idx_transfer_record_void (is_void)
);
