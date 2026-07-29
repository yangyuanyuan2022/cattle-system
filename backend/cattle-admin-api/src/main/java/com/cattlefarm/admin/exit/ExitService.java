package com.cattlefarm.admin.exit;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ExitService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public ExitService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    public List<ExitItem> list() {
        long farmId = auth.currentFarmId();
        return jdbc.query("""
                SELECT e.*, c.ear_tag_no, u.real_name
                FROM cattle_exit e
                JOIN cattle c ON c.cattle_id=e.cattle_id AND c.farm_id=e.farm_id
                LEFT JOIN sys_user u ON u.user_id=e.operator_id
                WHERE e.farm_id=?
                ORDER BY e.exit_date DESC, e.created_at DESC
                """, (rs, rowNum) -> new ExitItem(
                Long.toString(rs.getLong("exit_id")),
                Long.toString(rs.getLong("cattle_id")),
                rs.getString("ear_tag_no"),
                rs.getString("exit_type"),
                rs.getDate("exit_date").toLocalDate(),
                rs.getString("reason"),
                rs.getString("real_name"),
                rs.getTimestamp("restored_at") == null ? null : rs.getTimestamp("restored_at").toLocalDateTime(),
                rs.getString("restore_reason"),
                rs.getInt("is_void") == 1,
                rs.getString("void_reason")
        ), farmId);
    }

    @Transactional
    public ExitItem voidExit(long exitId, VoidExitRequest request, String key) {
        long farmId = auth.currentFarmId();
        long userId = StpUtil.getLoginIdAsLong();
        String path = "/api/v1/exits/" + exitId + "/void";
        Long replay = findReplay(farmId, userId, key, path);
        if (replay != null) return find(exitId);

        Map<String, Object> exit;
        try {
            exit = jdbc.queryForMap("SELECT * FROM cattle_exit WHERE farm_id=? AND exit_id=?", farmId, exitId);
        } catch (EmptyResultDataAccessException exception) {
            throw new DataConflictException("离场记录不存在");
        }
        if (((Number) exit.get("is_void")).intValue() == 1) {
            throw new DataConflictException("离场记录已作废");
        }

        long cattleId = ((Number) exit.get("cattle_id")).longValue();
        if (exit.get("restored_at") == null) {
            int affected = jdbc.update("""
                    UPDATE cattle SET presence_status='IN_FIELD', updated_by=?, version=version+1
                    WHERE farm_id=? AND cattle_id=? AND version=? AND presence_status='EXITED'
                    """, userId, farmId, cattleId, request.cattleVersion());
            if (affected == 0) {
                throw new DataConflictException("牛只状态或档案版本已变化，请刷新后重试");
            }
        }

        createIdempotency(farmId, userId, key, path, exitId);
        jdbc.update("""
                UPDATE cattle_exit SET is_void=1, void_reason=?, voided_by=?, voided_at=NOW()
                WHERE farm_id=? AND exit_id=? AND is_void=0
                """, request.reason().trim(), userId, farmId, exitId);
        jdbc.update("""
                UPDATE cattle_event SET is_void=1
                WHERE farm_id=? AND business_table='cattle_exit' AND business_id=?
                """, farmId, exitId);
        jdbc.update("""
                INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason)
                VALUES(?,?,?,'CATTLE','CATTLE_EXIT_VOIDED','CATTLE_EXIT',?,?)
                """, IdWorker.getId(), farmId, userId, exitId, request.reason().trim());
        return find(exitId);
    }

    private ExitItem find(long exitId) {
        return list().stream()
                .filter(item -> item.exitId().equals(Long.toString(exitId)))
                .findFirst()
                .orElseThrow(() -> new DataConflictException("离场记录不存在"));
    }

    private Long findReplay(long farmId, long userId, String key, String path) {
        try {
            return jdbc.queryForObject("""
                    SELECT business_id FROM idempotency_record
                    WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path=? AND expires_at>NOW()
                    """, Long.class, farmId, userId, key, path);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void createIdempotency(long farmId, long userId, String key, String path, long businessId) {
        jdbc.update("""
                INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at)
                VALUES(?,?,?,?,?,?)
                """, farmId, userId, key, path, businessId, LocalDateTime.now().plusDays(1));
    }
}
