package com.cattlefarm.admin.scope;

import cn.dev33.satoken.stp.StpUtil;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataScopeForbiddenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataScopeService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public DataScopeService(JdbcTemplate jdbc, AuthService auth) { this.jdbc = jdbc; this.auth = auth; }

    public boolean unrestricted() {
        return StpUtil.hasRole("ADMIN") || StpUtil.hasRole("FARM_MANAGER") || scopeTypes().contains("ALL");
    }

    public boolean canAccessCattle(long cattleId) {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        if (unrestricted()) return count("SELECT COUNT(*) FROM cattle WHERE farm_id=? AND cattle_id=?", farm, cattleId) > 0;
        return count("""
                SELECT COUNT(*) FROM cattle c
                WHERE c.farm_id=? AND c.cattle_id=? AND (
                  EXISTS (SELECT 1 FROM farm_user fu JOIN farm_user_data_scope s ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id
                          WHERE fu.farm_id=c.farm_id AND fu.user_id=? AND (
                            (s.scope_type='HERD' AND s.scope_object_id=c.herd_id) OR
                            (s.scope_type='BARN' AND s.scope_object_id=c.barn_id) OR
                            (s.scope_type='SELF_CREATED' AND c.created_by=?)
                          ))
                  OR EXISTS (SELECT 1 FROM task t WHERE t.farm_id=c.farm_id AND t.assignee_id=?
                             AND t.status IN('PENDING','IN_PROGRESS','OVERDUE')
                             AND (t.related_cattle_id=c.cattle_id OR t.related_herd_id=c.herd_id OR t.related_barn_id=c.barn_id))
                )
                """, farm, cattleId, user, user, user) > 0;
    }

    public void assertCattle(long cattleId) {
        if (!canAccessCattle(cattleId)) throw new DataScopeForbiddenException("该牛只不在当前用户的数据范围内");
    }

    /** Returns cattle visible to the current farm member for aggregate queries. */
    public List<Long> accessibleCattleIds() {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        if (unrestricted()) return jdbc.queryForList("SELECT cattle_id FROM cattle WHERE farm_id=?", Long.class, farm);
        return jdbc.queryForList("""
                SELECT c.cattle_id FROM cattle c WHERE c.farm_id=? AND (
                  EXISTS (SELECT 1 FROM farm_user fu JOIN farm_user_data_scope s
                          ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id
                          WHERE fu.farm_id=c.farm_id AND fu.user_id=? AND (
                            (s.scope_type='HERD' AND s.scope_object_id=c.herd_id) OR
                            (s.scope_type='BARN' AND s.scope_object_id=c.barn_id) OR
                            (s.scope_type='SELF_CREATED' AND c.created_by=?)))
                  OR EXISTS (SELECT 1 FROM task t WHERE t.farm_id=c.farm_id AND t.assignee_id=?
                             AND t.status IN('PENDING','IN_PROGRESS','OVERDUE') AND
                             (t.related_cattle_id=c.cattle_id OR t.related_herd_id=c.herd_id OR t.related_barn_id=c.barn_id))
                )
                """, Long.class, farm, user, user, user);
    }

    public boolean canAccessTask(long taskId) {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        if (unrestricted()) return count("SELECT COUNT(*) FROM task WHERE farm_id=? AND task_id=?", farm, taskId) > 0;
        return count("""
                SELECT COUNT(*) FROM task t WHERE t.farm_id=? AND t.task_id=? AND (
                  t.assignee_id=? OR t.created_by=? OR
                  (t.related_cattle_id IS NOT NULL AND EXISTS (
                    SELECT 1 FROM cattle c JOIN farm_user fu ON fu.farm_id=c.farm_id AND fu.user_id=?
                    JOIN farm_user_data_scope s ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id
                    WHERE c.farm_id=t.farm_id AND c.cattle_id=t.related_cattle_id AND
                      ((s.scope_type='HERD' AND s.scope_object_id=c.herd_id) OR (s.scope_type='BARN' AND s.scope_object_id=c.barn_id))
                  )) OR
                  EXISTS (SELECT 1 FROM farm_user fu JOIN farm_user_data_scope s ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id
                          WHERE fu.farm_id=t.farm_id AND fu.user_id=? AND
                           ((s.scope_type='HERD' AND s.scope_object_id=t.related_herd_id) OR
                            (s.scope_type='BARN' AND s.scope_object_id=t.related_barn_id)))
                )
                """, farm, taskId, user, user, user, user) > 0;
    }

    public void assertTask(long taskId) {
        if (!canAccessTask(taskId)) throw new DataScopeForbiddenException("该任务不在当前用户的数据范围内");
    }

    public List<Long> accessibleTaskIds() {
        long farm = auth.currentFarmId();
        if (unrestricted()) return jdbc.queryForList("SELECT task_id FROM task WHERE farm_id=?", Long.class, farm);
        return jdbc.queryForList("SELECT task_id FROM task WHERE farm_id=?", Long.class, farm).stream()
                .filter(this::canAccessTask).toList();
    }

    public boolean canAccessMixingOrder(long orderId) {
        long farm=auth.currentFarmId(),user=StpUtil.getLoginIdAsLong();
        if(unrestricted())return count("SELECT COUNT(*) FROM mixing_order WHERE farm_id=? AND mixing_order_id=?",farm,orderId)>0;
        return count("""
                SELECT COUNT(*) FROM mixing_order o WHERE o.farm_id=? AND o.mixing_order_id=? AND
                (o.assignee_id=? OR EXISTS(
                  SELECT 1 FROM farm_user fu JOIN farm_user_data_scope s ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id
                  WHERE fu.farm_id=o.farm_id AND fu.user_id=? AND s.scope_type='HERD' AND s.scope_object_id=o.target_herd_id
                ))
                """,farm,orderId,user,user)>0;
    }

    public void assertMixingOrder(long orderId){if(!canAccessMixingOrder(orderId))throw new DataScopeForbiddenException("该配料单不在当前用户的数据范围内");}

    public List<String> scopeTypes() {
        return jdbc.queryForList("""
                SELECT s.scope_type FROM farm_user fu JOIN farm_user_data_scope s ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id
                WHERE fu.farm_id=? AND fu.user_id=?
                """, String.class, auth.currentFarmId(), StpUtil.getLoginIdAsLong());
    }

    private long count(String sql,Object... args){Long n=jdbc.queryForObject(sql,Long.class,args);return n==null?0:n;}
}
