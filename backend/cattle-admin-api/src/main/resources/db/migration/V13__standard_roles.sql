INSERT INTO sys_role(role_id,farm_id,role_code,role_name,status)
SELECT CAST(CONV(SUBSTRING(MD5(CONCAT(f.farm_id,':',x.role_code)),1,15),16,10) AS UNSIGNED),f.farm_id,x.role_code,x.role_name,'ENABLED' FROM farm f JOIN (
 SELECT 'FARM_MANAGER' role_code,'场长' role_name UNION ALL SELECT 'VET','兽医' UNION ALL SELECT 'BREEDER','繁育员' UNION ALL SELECT 'WORKER','饲养员'
)x LEFT JOIN sys_role r ON r.farm_id=f.farm_id AND r.role_code=x.role_code WHERE r.role_id IS NULL;
