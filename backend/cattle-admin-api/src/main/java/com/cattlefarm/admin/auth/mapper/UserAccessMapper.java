package com.cattlefarm.admin.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserAccessMapper {
    @Select("""
            SELECT r.role_code
            FROM farm_user fu
            JOIN farm_user_role fur ON fur.farm_user_id = fu.farm_user_id
            JOIN sys_role r ON r.role_id = fur.role_id
            WHERE fu.user_id = #{userId} AND fu.farm_id = #{farmId}
              AND fu.status = 'ENABLED' AND r.status = 'ENABLED'
            """)
    List<String> findRoleCodes(@Param("userId") Long userId, @Param("farmId") Long farmId);

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM farm_user fu
            JOIN farm_user_role fur ON fur.farm_user_id=fu.farm_user_id
            JOIN sys_role r ON r.role_id=fur.role_id AND r.status='ENABLED'
            JOIN sys_role_permission rp ON rp.role_id=r.role_id
            JOIN sys_permission p ON p.permission_id=rp.permission_id AND p.status='ENABLED'
            WHERE fu.user_id=#{userId} AND fu.farm_id=#{farmId} AND fu.status='ENABLED'
            """)
    List<String> findPermissionCodes(@Param("userId") Long userId, @Param("farmId") Long farmId);
}
