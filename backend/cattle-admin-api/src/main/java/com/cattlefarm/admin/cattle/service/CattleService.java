package com.cattlefarm.admin.cattle.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.cattle.CattleNotFoundException;
import com.cattlefarm.admin.cattle.CattlePageResponse;
import com.cattlefarm.admin.cattle.CattleResponse;
import com.cattlefarm.admin.cattle.CreateCattleRequest;
import com.cattlefarm.admin.cattle.CattleTimelineEventResponse;
import com.cattlefarm.admin.cattle.CattlePedigreeResponse;
import com.cattlefarm.admin.cattle.ArchiveCattleRequest;
import com.cattlefarm.admin.cattle.RestoreCattleRequest;
import com.cattlefarm.admin.cattle.UpdateCattleRequest;
import com.cattlefarm.admin.common.DataConflictException;
import com.cattlefarm.admin.scope.DataScopeService;
import com.cattlefarm.admin.cattle.mapper.CattleEventMapper;
import com.cattlefarm.admin.cattle.mapper.CattleMapper;
import com.cattlefarm.admin.cattle.model.CattleEntity;
import com.cattlefarm.admin.cattle.model.CattleEventEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class CattleService {
    private final CattleMapper cattleMapper;
    private final CattleEventMapper eventMapper;
    private final AuthService authService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScope;

    public CattleService(CattleMapper cattleMapper, CattleEventMapper eventMapper,
                         AuthService authService, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, DataScopeService dataScope) {
        this.cattleMapper = cattleMapper;
        this.eventMapper = eventMapper;
        this.authService = authService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataScope = dataScope;
    }

    @Transactional
    public CattleResponse create(CreateCattleRequest request, String idempotencyKey) {
        long farmId = authService.currentFarmId();
        long userId = StpUtil.getLoginIdAsLong();
        Long existingId = findIdempotentBusinessId(farmId, userId, idempotencyKey);
        if (existingId != null) return detail(existingId);

        Long breedId = parseOptionalId(request.breedId(), "品种");
        Long herdId = parseOptionalId(request.herdId(), "牛群");
        Long barnId = parseOptionalId(request.barnId(), "栏舍");
        Long sireId = parseOptionalId(request.sireId(), "父系");
        String sireText = normalizeSireText(request.sireText());
        validateCreateRelations(farmId, breedId, herdId, barnId);
        validateSire(farmId, sireId, sireText, null);

        long cattleId = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at)
                VALUES (?,?,?,?,?,?)
                """, farmId, userId, idempotencyKey, "/api/v1/cattle", cattleId, LocalDateTime.now().plusDays(1));

        CattleEntity cattle = new CattleEntity();
        cattle.setCattleId(cattleId);
        cattle.setFarmId(farmId);
        cattle.setEarTagNo(request.earTagNo().trim());
        cattle.setName(request.name());
        cattle.setSex(request.sex());
        cattle.setBreedId(breedId);
        cattle.setBirthDate(request.birthDate());
        cattle.setSourceType(request.sourceType());
        cattle.setEntryDate(request.entryDate());
        cattle.setLifecycleStage(request.lifecycleStage());
        cattle.setPresenceStatus("IN_FIELD");
        cattle.setHealthStatus("NORMAL");
        cattle.setHerdId(herdId);
        cattle.setBarnId(barnId);
        cattle.setSireId(sireId);
        cattle.setSireText(sireText);
        cattle.setRemark(request.remark());
        cattle.setCreatedBy(userId);
        cattle.setUpdatedBy(userId);
        cattle.setVersion(0);
        cattleMapper.insert(cattle);

        CattleEventEntity event = new CattleEventEntity();
        event.setEventId(IdWorker.getId());
        event.setFarmId(farmId);
        event.setCattleId(cattleId);
        event.setEventType("CATTLE_CREATED");
        event.setEventDate(LocalDateTime.now());
        event.setBusinessTable("cattle");
        event.setBusinessId(cattleId);
        event.setSummary("创建牛只档案，耳号：" + cattle.getEarTagNo());
        event.setOperatorId(userId);
        event.setIsVoid(0);
        eventMapper.insert(event);

        jdbcTemplate.update("""
                INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,
                    business_type,business_id,reason)
                VALUES (?,?,?,?,?,?,?,?)
                """, IdWorker.getId(), farmId, userId, "CATTLE", "CATTLE_CREATED", "CATTLE",
                cattleId, "创建牛只档案");

        return detail(cattleId);
    }

    public CattlePageResponse page(long page, long pageSize, String keyword,
                                   String presenceStatus, String lifecycleStage, String sex, String breedId,
                                   String sourceType, String healthStatus, String barnId) {
        long farmId = authService.currentFarmId();
        Long breed = parseOptionalId(breedId, "breed");
        Long barn = parseOptionalId(barnId, "barn");
        LambdaQueryWrapper<CattleEntity> query = new LambdaQueryWrapper<CattleEntity>()
                .eq(CattleEntity::getFarmId, farmId)
                .eq(StringUtils.hasText(presenceStatus), CattleEntity::getPresenceStatus, presenceStatus)
                .eq(StringUtils.hasText(lifecycleStage), CattleEntity::getLifecycleStage, lifecycleStage)
                .eq(StringUtils.hasText(sex), CattleEntity::getSex, sex)
                .eq(breed != null, CattleEntity::getBreedId, breed)
                .eq(StringUtils.hasText(sourceType), CattleEntity::getSourceType, sourceType)
                .eq(StringUtils.hasText(healthStatus), CattleEntity::getHealthStatus, healthStatus)
                .eq(barn != null, CattleEntity::getBarnId, barn)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(CattleEntity::getEarTagNo, keyword)
                        .or().like(CattleEntity::getName, keyword))
                .orderByDesc(CattleEntity::getCreatedAt);
        if (!dataScope.unrestricted()) {
            long userId=StpUtil.getLoginIdAsLong();
            query.and(w -> w.eq(CattleEntity::getCreatedBy,userId)
                    .or().inSql(CattleEntity::getHerdId,"SELECT s.scope_object_id FROM farm_user fu JOIN farm_user_data_scope s ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id WHERE fu.farm_id="+farmId+" AND fu.user_id="+userId+" AND s.scope_type='HERD'")
                    .or().inSql(CattleEntity::getBarnId,"SELECT s.scope_object_id FROM farm_user fu JOIN farm_user_data_scope s ON s.farm_user_id=fu.farm_user_id AND s.farm_id=fu.farm_id WHERE fu.farm_id="+farmId+" AND fu.user_id="+userId+" AND s.scope_type='BARN'")
                    .or().inSql(CattleEntity::getCattleId,"SELECT related_cattle_id FROM task WHERE farm_id="+farmId+" AND assignee_id="+userId+" AND status IN('PENDING','IN_PROGRESS','OVERDUE') AND related_cattle_id IS NOT NULL")
                    .or().inSql(CattleEntity::getHerdId,"SELECT related_herd_id FROM task WHERE farm_id="+farmId+" AND assignee_id="+userId+" AND status IN('PENDING','IN_PROGRESS','OVERDUE') AND related_herd_id IS NOT NULL")
                    .or().inSql(CattleEntity::getBarnId,"SELECT related_barn_id FROM task WHERE farm_id="+farmId+" AND assignee_id="+userId+" AND status IN('PENDING','IN_PROGRESS','OVERDUE') AND related_barn_id IS NOT NULL"));
        }
        IPage<CattleEntity> result = cattleMapper.selectPage(new Page<>(page, pageSize), query);
        return new CattlePageResponse(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(CattleResponse::from).toList());
    }

    public CattleResponse detail(long cattleId) {
        if(!dataScope.unrestricted())dataScope.assertCattle(cattleId);
        CattleEntity cattle = cattleMapper.selectOne(new LambdaQueryWrapper<CattleEntity>()
                .eq(CattleEntity::getCattleId, cattleId)
                .eq(CattleEntity::getFarmId, authService.currentFarmId()));
        if (cattle == null) throw new CattleNotFoundException();
        return CattleResponse.from(cattle);
    }

    public List<CattleTimelineEventResponse> timeline(long cattleId) {
        detail(cattleId);
        return eventMapper.selectList(new LambdaQueryWrapper<CattleEventEntity>()
                        .eq(CattleEventEntity::getFarmId, authService.currentFarmId())
                        .eq(CattleEventEntity::getCattleId, cattleId)
                        .eq(CattleEventEntity::getIsVoid, 0)
                        .orderByDesc(CattleEventEntity::getEventDate))
                .stream().map(CattleTimelineEventResponse::from).toList();
    }

    public CattlePedigreeResponse pedigree(long cattleId) {
        detail(cattleId);
        long farmId = authService.currentFarmId();
        Map<String,Object> row = jdbcTemplate.queryForMap("""
                SELECT c.sire_id,s.ear_tag_no sire_ear_tag,c.sire_text,c.dam_id,d.ear_tag_no dam_ear_tag
                FROM cattle c LEFT JOIN cattle s ON s.cattle_id=c.sire_id AND s.farm_id=c.farm_id
                LEFT JOIN cattle d ON d.cattle_id=c.dam_id AND d.farm_id=c.farm_id
                WHERE c.farm_id=? AND c.cattle_id=?
                """, farmId, cattleId);
        List<CattlePedigreeResponse.Relative> offspring = jdbcTemplate.query("""
                SELECT cattle_id,ear_tag_no,name,sex FROM cattle
                WHERE farm_id=? AND (dam_id=? OR sire_id=?) ORDER BY birth_date DESC,created_at DESC
                """, (rs,n) -> new CattlePedigreeResponse.Relative(Long.toString(rs.getLong(1)),rs.getString(2),rs.getString(3),rs.getString(4)), farmId,cattleId,cattleId);
        Object sireId=row.get("sire_id"),damId=row.get("dam_id");
        return new CattlePedigreeResponse(Long.toString(cattleId),sireId==null?null:sireId.toString(),(String)row.get("sire_ear_tag"),(String)row.get("sire_text"),damId==null?null:damId.toString(),(String)row.get("dam_ear_tag"),offspring);
    }

    @Transactional
    public CattleResponse update(long cattleId, UpdateCattleRequest request, String idempotencyKey) {
        long farmId = authService.currentFarmId();
        long userId = StpUtil.getLoginIdAsLong();
        Long existingId = findIdempotentBusinessId(farmId, userId, idempotencyKey);
        if (existingId != null) return detail(existingId);

        CattleResponse before = detail(cattleId);
        String newEarTag = request.earTagNo().trim();
        Long sireId = parseOptionalId(request.sireId(), "父系");
        String sireText = normalizeSireText(request.sireText());
        validateSire(farmId, sireId, sireText, cattleId);
        if (!"IN_FIELD".equals(before.presenceStatus()) && !Objects.equals(before.earTagNo(), newEarTag)) {
            throw new DataConflictException("已离场牛只不可修改耳号");
        }

        jdbcTemplate.update("""
                INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at)
                VALUES (?,?,?,?,?,?)
                """, farmId, userId, idempotencyKey, "/api/v1/cattle/" + cattleId,
                cattleId, LocalDateTime.now().plusDays(1));

        int affected = jdbcTemplate.update("""
                UPDATE cattle SET ear_tag_no=?, name=?, birth_date=?, sire_id=?, sire_text=?, remark=?,
                    updated_by=?, version=version+1
                WHERE cattle_id=? AND farm_id=? AND version=?
                """, newEarTag, request.name(), request.birthDate(), sireId, sireText, request.remark(), userId,
                cattleId, farmId, request.version());
        if (affected == 0) throw new DataConflictException("档案已被其他人修改，请刷新后重试");

        CattleResponse after = correctedSnapshot(before, request, newEarTag, sireId, sireText);
        long operationLogId = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,
                    business_type,business_id,reason,before_data,after_data)
                VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))
                """, operationLogId, farmId, userId, "CATTLE", "CATTLE_CORRECTED", "CATTLE",
                cattleId, request.changeReason().trim(), auditJson(before), auditJson(after));

        if (!Objects.equals(before.earTagNo(), after.earTagNo())) {
            CattleEventEntity event = new CattleEventEntity();
            event.setEventId(IdWorker.getId());
            event.setFarmId(farmId);
            event.setCattleId(cattleId);
            event.setEventType("CATTLE_EAR_TAG_CHANGED");
            event.setEventDate(LocalDateTime.now());
            event.setBusinessTable("operation_log");
            event.setBusinessId(operationLogId);
            event.setSummary("耳号由 " + before.earTagNo() + " 修改为 " + after.earTagNo());
            event.setOperatorId(userId);
            event.setIsVoid(0);
            eventMapper.insert(event);
        }
        return after;
    }

    @Transactional
    public CattleResponse archive(long cattleId, ArchiveCattleRequest request, String idempotencyKey) {
        long farmId = authService.currentFarmId();
        long userId = StpUtil.getLoginIdAsLong();
        Long existingId = findIdempotentBusinessId(farmId, userId, idempotencyKey);
        if (existingId != null) return detail(existingId);
        CattleResponse before = detail(cattleId);
        if (!"IN_FIELD".equals(before.presenceStatus())) throw new DataConflictException("该牛只已离场");
        if ("TREATING".equals(before.healthStatus()) && !request.treatingRiskConfirmed()) {
            throw new DataConflictException("治疗中牛只离场必须确认风险");
        }
        long exitId = IdWorker.getId();
        createIdempotency(farmId, userId, idempotencyKey, "/api/v1/cattle/" + cattleId + "/archive", cattleId);
        int affected = jdbcTemplate.update("""
                UPDATE cattle SET presence_status='EXITED', updated_by=?, version=version+1
                WHERE cattle_id=? AND farm_id=? AND version=? AND presence_status='IN_FIELD'
                """, userId, cattleId, farmId, request.version());
        if (affected == 0) throw new DataConflictException("档案状态已变更，请刷新后重试");
        jdbcTemplate.update("""
                INSERT INTO cattle_exit(exit_id,farm_id,cattle_id,exit_type,exit_date,reason,operator_id)
                VALUES (?,?,?,?,?,?,?)
                """, exitId, farmId, cattleId, request.exitType(), request.exitDate(), request.reason().trim(), userId);
        CattleResponse after = withPresence(before, "EXITED");
        long logId = writeOperationLog(farmId, userId, "CATTLE_ARCHIVED", cattleId, request.reason(), before, after);
        writeEvent(farmId, cattleId, "CATTLE_ARCHIVED", "cattle_exit", exitId,
                "牛只离场：" + exitTypeLabel(request.exitType()), userId);
        return after;
    }

    @Transactional
    public CattleResponse restore(long cattleId, RestoreCattleRequest request, String idempotencyKey) {
        long farmId = authService.currentFarmId();
        long userId = StpUtil.getLoginIdAsLong();
        Long existingId = findIdempotentBusinessId(farmId, userId, idempotencyKey);
        if (existingId != null) return detail(existingId);
        CattleResponse before = detail(cattleId);
        if (!"EXITED".equals(before.presenceStatus())) throw new DataConflictException("该牛只当前在场，无需恢复");
        Long exitId;
        try {
            exitId = jdbcTemplate.queryForObject("""
                    SELECT exit_id FROM cattle_exit
                    WHERE farm_id=? AND cattle_id=? AND restored_at IS NULL AND is_void=0
                    ORDER BY created_at DESC LIMIT 1
                    """, Long.class, farmId, cattleId);
        } catch (EmptyResultDataAccessException exception) {
            throw new DataConflictException("未找到可恢复的离场记录");
        }
        if (exitId == null) throw new DataConflictException("未找到可恢复的离场记录");
        createIdempotency(farmId, userId, idempotencyKey, "/api/v1/cattle/" + cattleId + "/restore", cattleId);
        int affected = jdbcTemplate.update("""
                UPDATE cattle SET presence_status='IN_FIELD', updated_by=?, version=version+1
                WHERE cattle_id=? AND farm_id=? AND version=? AND presence_status='EXITED'
                """, userId, cattleId, farmId, request.version());
        if (affected == 0) throw new DataConflictException("档案状态已变更，请刷新后重试");
        jdbcTemplate.update("UPDATE cattle_exit SET restored_at=NOW(), restored_by=?, restore_reason=? WHERE farm_id=? AND exit_id=?",
                userId, request.reason().trim(), farmId, exitId);
        CattleResponse after = withPresence(before, "IN_FIELD");
        writeOperationLog(farmId, userId, "CATTLE_RESTORED", cattleId, request.reason(), before, after);
        writeEvent(farmId, cattleId, "CATTLE_RESTORED", "cattle_exit", exitId,
                "牛只恢复在场", userId);
        return after;
    }

    private String auditJson(CattleResponse cattle) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("earTagNo", cattle.earTagNo());
        data.put("name", cattle.name());
        data.put("birthDate", cattle.birthDate());
        data.put("sireId", cattle.sireId());
        data.put("sireText", cattle.sireText());
        data.put("remark", cattle.remark());
        data.put("version", cattle.version());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计数据序列化失败", exception);
        }
    }

    private CattleResponse correctedSnapshot(CattleResponse before, UpdateCattleRequest request, String earTagNo,
                                             Long sireId, String sireText) {
        return new CattleResponse(
                before.cattleId(), before.farmId(), earTagNo, request.name(), before.sex(), before.breedId(),
                request.birthDate(), before.sourceType(), before.entryDate(), before.lifecycleStage(),
                before.presenceStatus(), before.healthStatus(), before.breedingStatus(), before.herdId(),
                before.barnId(), sireId == null ? null : sireId.toString(), sireText, request.remark(),
                before.createdAt(), before.version() + 1);
    }

    private CattleResponse withPresence(CattleResponse before, String presenceStatus) {
        return new CattleResponse(before.cattleId(), before.farmId(), before.earTagNo(), before.name(),
                before.sex(), before.breedId(), before.birthDate(), before.sourceType(), before.entryDate(),
                before.lifecycleStage(), presenceStatus, before.healthStatus(), before.breedingStatus(),
                before.herdId(), before.barnId(), before.sireId(), before.sireText(), before.remark(),
                before.createdAt(), before.version() + 1);
    }

    private void createIdempotency(long farmId, long userId, String key, String path, long cattleId) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at)
                VALUES (?,?,?,?,?,?)
                """, farmId, userId, key, path, cattleId, LocalDateTime.now().plusDays(1));
    }

    private long writeOperationLog(long farmId, long userId, String action, long cattleId, String reason,
                                   CattleResponse before, CattleResponse after) {
        long logId = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,
                    business_type,business_id,reason,before_data,after_data)
                VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))
                """, logId, farmId, userId, "CATTLE", action, "CATTLE", cattleId,
                reason.trim(), auditJson(before), auditJson(after));
        return logId;
    }

    private void writeEvent(long farmId, long cattleId, String eventType, String businessTable,
                            long businessId, String summary, long userId) {
        CattleEventEntity event = new CattleEventEntity();
        event.setEventId(IdWorker.getId());
        event.setFarmId(farmId);
        event.setCattleId(cattleId);
        event.setEventType(eventType);
        event.setEventDate(LocalDateTime.now());
        event.setBusinessTable(businessTable);
        event.setBusinessId(businessId);
        event.setSummary(summary);
        event.setOperatorId(userId);
        event.setIsVoid(0);
        eventMapper.insert(event);
    }

    private String exitTypeLabel(String exitType) {
        return switch (exitType) {
            case "SALE" -> "出售";
            case "DEATH" -> "死亡";
            case "CULL" -> "淘汰";
            default -> "其他";
        };
    }

    private Long findIdempotentBusinessId(long farmId, long userId, String key) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT business_id FROM idempotency_record
                    WHERE farm_id=? AND user_id=? AND idempotency_key=? AND expires_at > NOW()
                    """, Long.class, farmId, userId, key);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Long parseOptionalId(String value, String label) {
        if (!StringUtils.hasText(value)) return null;
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new DataConflictException(label + "编号格式错误");
        }
    }

    private String normalizeSireText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateSire(long farmId, Long sireId, String sireText, Long cattleId) {
        if (sireId != null && sireText != null) {
            throw new DataConflictException("系统内父牛和外部父系只能填写一项");
        }
        if (sireId == null) return;
        if (Objects.equals(sireId, cattleId)) {
            throw new DataConflictException("父系不能选择牛只自身");
        }
        if (count("SELECT COUNT(*) FROM cattle WHERE farm_id=? AND cattle_id=? AND sex='MALE'", farmId, sireId) == 0) {
            throw new DataConflictException("所选父系不存在、不是公牛或不属于当前牛场");
        }
    }

    private void validateCreateRelations(long farmId, Long breedId, Long herdId, Long barnId) {
        if (breedId != null && count("""
                SELECT COUNT(*) FROM dict_item i
                JOIN dict_type t ON t.dict_type_id=i.dict_type_id
                WHERE i.dict_item_id=? AND (i.farm_id IS NULL OR i.farm_id=?)
                  AND i.status='ENABLED' AND t.status='ENABLED' AND t.type_code='CATTLE_BREED'
                """, breedId, farmId) == 0) {
            throw new DataConflictException("所选品种不存在或已停用");
        }
        if (barnId != null && count("SELECT COUNT(*) FROM barn WHERE farm_id=? AND barn_id=? AND status='ENABLED'", farmId, barnId) == 0) {
            throw new DataConflictException("所选栏舍不存在或已停用");
        }
        if (herdId != null) {
            Map<String, Object> herd;
            try {
                herd = jdbcTemplate.queryForMap("SELECT barn_id FROM herd WHERE farm_id=? AND herd_id=? AND status='ENABLED'", farmId, herdId);
            } catch (EmptyResultDataAccessException exception) {
                throw new DataConflictException("所选牛群不存在或已停用");
            }
            Number herdBarnId = (Number) herd.get("barn_id");
            if (barnId != null && (herdBarnId == null || herdBarnId.longValue() != barnId)) {
                throw new DataConflictException("所选牛群不属于所选栏舍");
            }
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
