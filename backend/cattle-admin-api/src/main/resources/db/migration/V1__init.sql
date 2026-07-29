CREATE TABLE IF NOT EXISTS farm (
    farm_id BIGINT NOT NULL,
    farm_name VARCHAR(100) NOT NULL,
    farm_code VARCHAR(50) NULL,
    farm_type VARCHAR(30) NOT NULL DEFAULT 'BEEF',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (farm_id),
    UNIQUE KEY uk_farm_code (farm_code),
    KEY idx_farm_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cattle (
    cattle_id BIGINT NOT NULL,
    farm_id BIGINT NOT NULL,
    ear_tag_no VARCHAR(50) NOT NULL,
    name VARCHAR(50) NULL,
    sex VARCHAR(10) NOT NULL,
    birth_date DATE NULL,
    source_type VARCHAR(20) NOT NULL,
    entry_date DATE NOT NULL,
    lifecycle_stage VARCHAR(30) NOT NULL,
    presence_status VARCHAR(20) NOT NULL DEFAULT 'IN_FIELD',
    health_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    herd_id BIGINT NULL,
    barn_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (cattle_id),
    UNIQUE KEY uk_cattle_farm_ear_tag (farm_id, ear_tag_no),
    KEY idx_cattle_farm_status (farm_id, presence_status),
    KEY idx_cattle_farm_stage (farm_id, lifecycle_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
