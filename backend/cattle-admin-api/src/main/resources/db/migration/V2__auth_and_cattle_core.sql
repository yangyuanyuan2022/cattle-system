ALTER TABLE farm
    ADD COLUMN contact_name VARCHAR(50) NULL AFTER farm_type,
    ADD COLUMN contact_phone VARCHAR(30) NULL AFTER contact_name,
    ADD COLUMN remark VARCHAR(500) NULL AFTER status;

ALTER TABLE cattle
    ADD COLUMN breed_id BIGINT NULL AFTER sex,
    ADD COLUMN breeding_status VARCHAR(30) NULL AFTER health_status,
    ADD COLUMN sire_id BIGINT NULL AFTER barn_id,
    ADD COLUMN dam_id BIGINT NULL AFTER sire_id,
    ADD COLUMN sire_text VARCHAR(100) NULL AFTER dam_id,
    ADD COLUMN photo_url VARCHAR(500) NULL AFTER sire_text,
    ADD COLUMN remark VARCHAR(500) NULL AFTER photo_url,
    ADD COLUMN created_by BIGINT NULL AFTER remark,
    ADD COLUMN updated_by BIGINT NULL AFTER created_at,
    ADD KEY idx_cattle_health (farm_id, health_status),
    ADD KEY idx_cattle_breeding (farm_id, breeding_status);

CREATE TABLE sys_user (
    user_id BIGINT NOT NULL,
    farm_id BIGINT NULL,
    username VARCHAR(50) NOT NULL,
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(30) NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_farm (farm_id),
    KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE farm_user (
    farm_user_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_name VARCHAR(50) NOT NULL,
    phone VARCHAR(30) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (farm_user_id),
    UNIQUE KEY uk_farm_user (farm_id, user_id),
    KEY idx_farm_user_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
    role_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_farm_code (farm_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE farm_user_role (
    id BIGINT NOT NULL,
    farm_user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_farm_user_role (farm_user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cattle_event (
    event_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    cattle_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_date DATETIME NOT NULL,
    business_table VARCHAR(100) NOT NULL,
    business_id BIGINT NOT NULL,
    summary VARCHAR(500) NOT NULL,
    operator_id BIGINT NULL,
    is_void TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_event_business (business_table, business_id, event_type),
    KEY idx_event_timeline (farm_id, cattle_id, event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
    record_id BIGINT NOT NULL AUTO_INCREMENT,
    farm_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    business_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_idempotency_scope (farm_id, user_id, idempotency_key),
    KEY idx_idempotency_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE login_log (
    login_log_id BIGINT NOT NULL AUTO_INCREMENT,
    login_account VARCHAR(100) NOT NULL,
    user_id BIGINT NULL,
    login_result VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(100) NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (login_log_id),
    KEY idx_login_account_time (login_account, created_at),
    KEY idx_login_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
