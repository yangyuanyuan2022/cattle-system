CREATE TABLE vaccination_plan (
 plan_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, plan_name VARCHAR(100) NOT NULL, vaccine_item VARCHAR(100) NOT NULL,
 plan_date DATE NOT NULL, due_date DATE NOT NULL, responsible_user_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', cancel_reason VARCHAR(255) NULL, cancelled_by BIGINT NULL,
 cancelled_at DATETIME NULL, remark VARCHAR(500) NULL, created_by BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by BIGINT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, version INT NOT NULL DEFAULT 0,
 INDEX idx_vaccination_plan_farm_status(farm_id,status), INDEX idx_vaccination_plan_due(farm_id,due_date), INDEX idx_vaccination_plan_item(vaccine_item)
);
CREATE TABLE vaccination_plan_target (
 target_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, plan_id BIGINT NOT NULL,
 target_type VARCHAR(20) NOT NULL, target_object_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_vaccine_plan_target UNIQUE(plan_id,target_type,target_object_id), INDEX idx_vaccine_target_object(farm_id,target_type,target_object_id)
);
CREATE TABLE vaccination_execution (
 execution_id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, plan_id BIGINT NULL, execution_date DATETIME NOT NULL,
 vaccine_item VARCHAR(100) NOT NULL, batch_no VARCHAR(100) NULL, executor_id BIGINT NOT NULL, remark VARCHAR(500) NULL,
 is_void TINYINT NOT NULL DEFAULT 0, void_reason VARCHAR(255) NULL, voided_by BIGINT NULL, voided_at DATETIME NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 version INT NOT NULL DEFAULT 0, INDEX idx_vaccine_execution_plan(farm_id,plan_id), INDEX idx_vaccine_execution_date(farm_id,execution_date)
);
CREATE TABLE vaccination_execution_cattle (
 id BIGINT PRIMARY KEY, farm_id BIGINT NOT NULL, execution_id BIGINT NOT NULL, cattle_id BIGINT NOT NULL,
 reaction VARCHAR(255) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_vaccine_exec_cattle UNIQUE(execution_id,cattle_id), INDEX idx_vaccine_exec_cattle(farm_id,cattle_id)
);
