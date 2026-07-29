CREATE TABLE dict_type(dict_type_id BIGINT PRIMARY KEY,type_code VARCHAR(50) NOT NULL,type_name VARCHAR(100) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',remark VARCHAR(500),UNIQUE KEY uk_dict_type_code(type_code));
CREATE TABLE dict_item(dict_item_id BIGINT PRIMARY KEY,dict_type_id BIGINT NOT NULL,farm_id BIGINT NULL,item_code VARCHAR(50) NOT NULL,item_name VARCHAR(100) NOT NULL,sort_no INT NOT NULL DEFAULT 0,status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',remark VARCHAR(500),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY uk_dict_item_scope(dict_type_id,farm_id,item_code),INDEX idx_dict_item_type_farm(dict_type_id,farm_id,status));
CREATE TABLE system_param(param_id BIGINT PRIMARY KEY,farm_id BIGINT NOT NULL,param_code VARCHAR(100) NOT NULL,param_name VARCHAR(100) NOT NULL,param_value VARCHAR(255) NOT NULL,value_type VARCHAR(20) NOT NULL DEFAULT 'STRING',remark VARCHAR(500),updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY uk_param_farm_code(farm_id,param_code));
INSERT INTO dict_type(dict_type_id,type_code,type_name,status) VALUES (101,'CATTLE_BREED','牛只品种','ENABLED'),(102,'MEDICINE','常用药品','ENABLED'),(103,'EXIT_REASON','离场原因','ENABLED');
INSERT INTO system_param(param_id,farm_id,param_code,param_name,param_value,value_type)
SELECT CAST(CONV(SUBSTRING(MD5(CONCAT(farm_id,':PREGNANCY_CHECK_DAYS')),1,15),16,10) AS UNSIGNED),farm_id,'PREGNANCY_CHECK_DAYS','配种后妊检天数','30','NUMBER' FROM farm;
INSERT INTO system_param(param_id,farm_id,param_code,param_name,param_value,value_type)
SELECT CAST(CONV(SUBSTRING(MD5(CONCAT(farm_id,':PREGNANCY_RECHECK_DAYS')),1,15),16,10) AS UNSIGNED),farm_id,'PREGNANCY_RECHECK_DAYS','妊检复查间隔天数','14','NUMBER' FROM farm;
INSERT INTO system_param(param_id,farm_id,param_code,param_name,param_value,value_type)
SELECT CAST(CONV(SUBSTRING(MD5(CONCAT(farm_id,':CALVING_ALERT_DAYS')),1,15),16,10) AS UNSIGNED),farm_id,'CALVING_ALERT_DAYS','预产期提前提醒天数','14','NUMBER' FROM farm;
