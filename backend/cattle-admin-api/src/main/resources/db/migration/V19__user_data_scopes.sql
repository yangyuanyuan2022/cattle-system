CREATE TABLE farm_user_data_scope (
    scope_id BIGINT PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    farm_user_id BIGINT NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    scope_object_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_scope (farm_user_id, scope_type, scope_object_id),
    INDEX idx_scope_farm_user (farm_id, farm_user_id),
    INDEX idx_scope_object (farm_id, scope_type, scope_object_id)
);

INSERT INTO farm_user_data_scope(scope_id,farm_id,farm_user_id,scope_type,scope_object_id)
SELECT CAST(CONV(SUBSTRING(MD5(CONCAT(fu.farm_user_id,':ALL')),1,15),16,10) AS UNSIGNED),
       fu.farm_id,fu.farm_user_id,'ALL',NULL
FROM farm_user fu
WHERE EXISTS (
    SELECT 1 FROM farm_user_role fur JOIN sys_role r ON r.role_id=fur.role_id
    WHERE fur.farm_user_id=fu.farm_user_id AND r.role_code IN('ADMIN','FARM_MANAGER')
);

INSERT INTO farm_user_data_scope(scope_id,farm_id,farm_user_id,scope_type,scope_object_id)
SELECT CAST(CONV(SUBSTRING(MD5(CONCAT(fu.farm_user_id,':SELF_ASSIGNED')),1,15),16,10) AS UNSIGNED),
       fu.farm_id,fu.farm_user_id,'SELF_ASSIGNED',NULL
FROM farm_user fu
WHERE NOT EXISTS (SELECT 1 FROM farm_user_data_scope s WHERE s.farm_user_id=fu.farm_user_id);
