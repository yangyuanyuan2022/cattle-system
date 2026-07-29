CREATE TABLE health_case (
 case_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, cattle_id BIGINT NOT NULL, case_no VARCHAR(50) NOT NULL,
 discover_date DATETIME NOT NULL, symptom VARCHAR(500) NOT NULL, severity VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
 case_status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING', responsible_vet_id BIGINT NULL, reporter_id BIGINT NOT NULL,
 closed_at DATETIME NULL, close_result VARCHAR(255) NULL, is_void TINYINT NOT NULL DEFAULT 0,
 void_reason VARCHAR(255) NULL, voided_by BIGINT NULL, voided_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 version INT NOT NULL DEFAULT 0, CONSTRAINT uk_health_case_no UNIQUE(farm_id,case_no),
 INDEX idx_health_case_cattle_status(farm_id,cattle_id,case_status), INDEX idx_health_case_discover(farm_id,discover_date), INDEX idx_health_case_severity(severity)
);
CREATE TABLE treatment_record (
 treatment_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, case_id BIGINT NOT NULL, cattle_id BIGINT NOT NULL,
 treatment_date DATETIME NOT NULL, diagnosis VARCHAR(500) NOT NULL, treatment_plan VARCHAR(1000) NULL,
 need_follow_up TINYINT NOT NULL DEFAULT 0, follow_up_date DATE NULL, vet_id BIGINT NOT NULL,
 is_void TINYINT NOT NULL DEFAULT 0, void_reason VARCHAR(255) NULL, voided_by BIGINT NULL, voided_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 version INT NOT NULL DEFAULT 0, INDEX idx_treatment_case_date(farm_id,case_id,treatment_date), INDEX idx_treatment_cattle(farm_id,cattle_id)
);
CREATE TABLE medication_item (
 medication_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, treatment_id BIGINT NOT NULL,
 medicine_name VARCHAR(100) NOT NULL, dosage DECIMAL(10,2) NULL, unit VARCHAR(20) NULL,
 usage_method VARCHAR(100) NULL, withdrawal_days INT NULL, remark VARCHAR(500) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_medication_treatment(treatment_id),
 INDEX idx_medication_farm_name(farm_id,medicine_name), CONSTRAINT chk_withdrawal_days CHECK(withdrawal_days IS NULL OR withdrawal_days>=0)
);
CREATE TABLE follow_up_record (
 follow_up_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, case_id BIGINT NOT NULL, cattle_id BIGINT NOT NULL,
 follow_up_date DATETIME NOT NULL, result VARCHAR(30) NOT NULL, description VARCHAR(1000) NULL, operator_id BIGINT NOT NULL,
 is_void TINYINT NOT NULL DEFAULT 0, void_reason VARCHAR(255) NULL, voided_by BIGINT NULL, voided_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 version INT NOT NULL DEFAULT 0, INDEX idx_follow_up_case_date(farm_id,case_id,follow_up_date), INDEX idx_follow_up_result(result)
);
