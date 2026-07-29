ALTER TABLE sys_role ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE TABLE sys_permission (
    permission_id BIGINT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    permission_type VARCHAR(20) NOT NULL DEFAULT 'API',
    parent_id BIGINT NULL,
    route_path VARCHAR(255) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    UNIQUE KEY uk_permission_code (permission_code),
    INDEX idx_permission_type_status (permission_type, status)
);

CREATE TABLE sys_role_permission (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_permission_permission (permission_id)
);

INSERT INTO sys_permission(permission_id,permission_code,permission_name,permission_type,sort_no) VALUES
    (201,'SYSTEM','用户、角色、审计与系统配置','API',10),
    (202,'FARM_MANAGEMENT','牛只、栏舍、任务、报表与生产管理','API',20),
    (203,'HEALTH','健康诊疗与防疫执行','API',30),
    (204,'BREEDING','发情、配种、妊检和产犊','API',40),
    (205,'FIELD_WORK','任务、称重、转群和配料执行','API',50);

INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT CAST(CONV(SUBSTRING(MD5(CONCAT(r.role_id,':',p.permission_id)),1,15),16,10) AS UNSIGNED),r.role_id,p.permission_id
FROM sys_role r JOIN sys_permission p ON
    r.role_code='ADMIN'
    OR (r.role_code='FARM_MANAGER' AND p.permission_code IN('FARM_MANAGEMENT','HEALTH','BREEDING','FIELD_WORK'))
    OR (r.role_code='VET' AND p.permission_code='HEALTH')
    OR (r.role_code='BREEDER' AND p.permission_code='BREEDING')
    OR (r.role_code='WORKER' AND p.permission_code='FIELD_WORK');
