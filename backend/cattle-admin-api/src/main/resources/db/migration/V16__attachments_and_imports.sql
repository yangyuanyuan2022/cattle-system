CREATE TABLE attachment (
    attachment_id BIGINT PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_type VARCHAR(100) NULL,
    file_size BIGINT NULL,
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attachment_business (farm_id, business_type, business_id),
    INDEX idx_attachment_uploader (farm_id, uploaded_by, uploaded_at)
);

CREATE TABLE import_log (
    import_log_id BIGINT PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    module VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    error_summary TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VALIDATED',
    source_file_url VARCHAR(500) NULL,
    operator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_import_farm_module (farm_id, module, created_at),
    INDEX idx_import_operator (farm_id, operator_id, created_at)
);

CREATE TABLE import_error_detail (
    error_id BIGINT PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    import_log_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    field_name VARCHAR(100) NULL,
    raw_value VARCHAR(500) NULL,
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_import_error_batch (farm_id, import_log_id, row_no)
);
