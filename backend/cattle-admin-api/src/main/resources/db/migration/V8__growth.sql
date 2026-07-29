CREATE TABLE weight_record (
 weight_id BIGINT PRIMARY KEY,farm_id BIGINT NOT NULL,cattle_id BIGINT NOT NULL,measure_date DATETIME NOT NULL,
 weight_kg DECIMAL(8,2) NOT NULL,measure_method VARCHAR(50) NULL,recorder_id BIGINT NOT NULL,remark VARCHAR(500) NULL,
 is_void TINYINT NOT NULL DEFAULT 0,void_reason VARCHAR(255) NULL,voided_by BIGINT NULL,voided_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 version INT NOT NULL DEFAULT 0,INDEX idx_weight_cattle_date(farm_id,cattle_id,measure_date),INDEX idx_weight_farm_date(farm_id,measure_date),CONSTRAINT chk_weight_positive CHECK(weight_kg>0)
);
CREATE TABLE body_condition_record (
 body_condition_id BIGINT PRIMARY KEY,farm_id BIGINT NOT NULL,cattle_id BIGINT NOT NULL,score_date DATETIME NOT NULL,
 score DECIMAL(3,1) NOT NULL,recorder_id BIGINT NOT NULL,remark VARCHAR(500) NULL,is_void TINYINT NOT NULL DEFAULT 0,
 void_reason VARCHAR(255) NULL,voided_by BIGINT NULL,voided_at DATETIME NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,version INT NOT NULL DEFAULT 0,
 INDEX idx_body_condition_cattle_date(farm_id,cattle_id,score_date),INDEX idx_body_condition_farm_date(farm_id,score_date),CONSTRAINT chk_body_condition_score CHECK(score>=1 AND score<=5)
);
