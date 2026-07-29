package com.cattlefarm.admin.correction;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import com.cattlefarm.admin.common.DataScopeForbiddenException;
import com.cattlefarm.admin.scope.DataScopeService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class BusinessCorrectionService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final DataScopeService scope;

    public BusinessCorrectionService(JdbcTemplate jdbc, AuthService auth, DataScopeService scope) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.scope = scope;
    }

    @Transactional
    public CorrectionResult voidBreeding(String kind, long id, VoidBusinessRequest request, String key) {
        BreedingMeta meta = breedingMeta(kind);
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        String path = meta.path() + id + "/void";
        if (replay(farm, user, key, path) != null) return breedingResult(meta, farm, id);
        Map<String, Object> row = row(meta.table(), meta.pk(), farm, id);
        ensureActive(row);
        long cattle = number(row.get(meta.cattleColumn()));
        if(!scope.unrestricted())scope.assertCattle(cattle);

        if ("INSEMINATION".equals(kind) && count("SELECT COUNT(*) FROM pregnancy_check WHERE farm_id=? AND breeding_id=? AND is_void=0", farm, id) > 0)
            throw new DataConflictException("该配种记录已有有效妊检，请先按时间倒序作废关联妊检");
        if ("PREGNANCY_CHECK".equals(kind) && count("SELECT COUNT(*) FROM calving_record WHERE farm_id=? AND pregnancy_check_id=? AND is_void=0", farm, id) > 0)
            throw new DataConflictException("该妊检记录已有有效产犊记录，请先作废关联产犊记录");
        if ("CALVING".equals(kind) && count("SELECT COUNT(*) FROM calving_calf_relation WHERE farm_id=? AND calving_id=?", farm, id) > 0)
            throw new DataConflictException("该产犊记录已创建犊牛档案，需先完成犊牛档案纠错流程");

        idem(farm, user, key, path, id);
        voidRow(meta.table(), meta.pk(), farm, id, request, user);
        voidEvents(farm, meta.table(), id);
        cancelSourceTasks(farm, user, meta.sourceType(), id, request.reason());
        String status = recomputeBreeding(farm, cattle, user);
        audit(farm, user, "BREEDING", meta.action(), meta.businessType(), id, request.reason());
        return new CorrectionResult(Long.toString(id), meta.businessType(), true, status);
    }

    @Transactional
    public CorrectionResult voidHealth(String kind, long id, VoidBusinessRequest request, String key) {
        HealthMeta meta = healthMeta(kind);
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        String path = meta.path() + id + "/void";
        if (replay(farm, user, key, path) != null) return healthResult(meta, farm, id);
        Map<String, Object> row = row(meta.table(), meta.pk(), farm, id);
        ensureActive(row);
        long cattle = number(row.get("cattle_id"));
        if(!scope.unrestricted())scope.assertCattle(cattle);
        long caseId = "CASE".equals(kind) ? id : number(row.get("case_id"));
        if ("CASE".equals(kind) && (count("SELECT COUNT(*) FROM treatment_record WHERE farm_id=? AND case_id=? AND is_void=0", farm, id) > 0
                || count("SELECT COUNT(*) FROM follow_up_record WHERE farm_id=? AND case_id=? AND is_void=0", farm, id) > 0))
            throw new DataConflictException("病例存在有效诊疗或复查记录，请先按时间倒序作废关联记录");

        idem(farm, user, key, path, id);
        voidRow(meta.table(), meta.pk(), farm, id, request, user);
        voidEvents(farm, meta.table(), id);
        if ("TREATMENT".equals(kind)) cancelSourceTasks(farm, user, "TREATMENT", id, request.reason());
        recomputeCase(farm, caseId, user);
        String status = recomputeHealth(farm, cattle, user);
        audit(farm, user, "HEALTH", meta.action(), meta.businessType(), id, request.reason());
        return new CorrectionResult(Long.toString(id), meta.businessType(), true, status);
    }

    @Transactional
    public CorrectionResult cancelVaccinationPlan(long id, VoidBusinessRequest request, String key) {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        String path = "/api/v1/vaccinations/plans/" + id + "/cancel";
        if (replay(farm, user, key, path) != null) return new CorrectionResult(Long.toString(id), "VACCINATION_PLAN", false, planStatus(farm, id));
        Map<String, Object> row = row("vaccination_plan", "plan_id", farm, id);
        String status = String.valueOf(row.get("status"));
        if ("CANCELLED".equals(status)) throw new DataConflictException("防疫计划已取消");
        if ("DONE".equals(status)) throw new DataConflictException("已完成防疫计划不可取消");
        idem(farm, user, key, path, id);
        int changed = jdbc.update("UPDATE vaccination_plan SET status='CANCELLED',cancel_reason=?,cancelled_by=?,cancelled_at=NOW(),updated_by=?,version=version+1 WHERE farm_id=? AND plan_id=? AND version=? AND status NOT IN('DONE','CANCELLED')",
                request.reason().trim(), user, user, farm, id, request.version());
        if (changed == 0) throw new DataConflictException("防疫计划已被其他人修改，请刷新后重试");
        cancelSourceTasks(farm, user, "VACCINATION_PLAN", id, request.reason());
        audit(farm, user, "VACCINATION", "VACCINATION_PLAN_CANCELLED", "VACCINATION_PLAN", id, request.reason());
        return new CorrectionResult(Long.toString(id), "VACCINATION_PLAN", false, "CANCELLED");
    }

    @Transactional
    public CorrectionResult voidVaccinationExecution(long id, VoidBusinessRequest request, String key) {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        String path = "/api/v1/vaccinations/executions/" + id + "/void";
        if (replay(farm, user, key, path) != null) return vaccinationExecutionResult(farm, id);
        Map<String, Object> row = row("vaccination_execution", "execution_id", farm, id);
        ensureActive(row);
        idem(farm, user, key, path, id);
        voidRow("vaccination_execution", "execution_id", farm, id, request, user);
        voidEvents(farm, "vaccination_execution", id);
        Long planId = row.get("plan_id") == null ? null : number(row.get("plan_id"));
        String status = "STANDALONE";
        if (planId != null) status = recomputeVaccinationPlan(farm, planId, user);
        audit(farm, user, "VACCINATION", "VACCINATION_EXECUTION_VOIDED", "VACCINATION_EXECUTION", id, request.reason());
        return new CorrectionResult(Long.toString(id), "VACCINATION_EXECUTION", true, status);
    }

    @Transactional
    public CorrectionResult voidGrowth(String kind, long id, VoidBusinessRequest request, String key) {
        boolean weight = "WEIGHT".equals(kind);
        String table = weight ? "weight_record" : "body_condition_record";
        String pk = weight ? "weight_id" : "body_condition_id";
        String path = weight ? "/api/v1/growth/weights/" + id + "/void" : "/api/v1/growth/body-conditions/" + id + "/void";
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        if (replay(farm, user, key, path) != null) return new CorrectionResult(Long.toString(id), kind, true, null);
        Map<String, Object> row = row(table, pk, farm, id);
        ensureActive(row);
        long cattle=number(row.get("cattle_id"));
        if(!scope.unrestricted())scope.assertCattle(cattle);
        if(StpUtil.hasRole("WORKER")&&!scope.unrestricted()&&number(row.get("recorder_id"))!=user)
            throw new DataScopeForbiddenException("饲养员只能作废本人登记的生长记录");
        idem(farm, user, key, path, id);
        voidRow(table, pk, farm, id, request, user);
        voidEvents(farm, table, id);
        audit(farm, user, "GROWTH", kind + "_VOIDED", kind, id, request.reason());
        return new CorrectionResult(Long.toString(id), kind, true, null);
    }

    private String recomputeBreeding(long farm, long cattle, long user) {
        String status;
        if (count("SELECT COUNT(*) FROM calving_record WHERE farm_id=? AND dam_cattle_id=? AND is_void=0", farm, cattle) > 0) status = "POSTPARTUM";
        else {
            Map<String, Object> check = latest("SELECT check_result value FROM pregnancy_check WHERE farm_id=? AND cattle_id=? AND is_void=0 ORDER BY check_date DESC,created_at DESC LIMIT 1", farm, cattle);
            if (check != null) status = switch (String.valueOf(check.get("value"))) { case "POSITIVE" -> "PREGNANT"; case "NEGATIVE" -> "WAIT_BREED"; default -> "BRED_WAIT_CHECK"; };
            else if (count("SELECT COUNT(*) FROM breeding_record WHERE farm_id=? AND cattle_id=? AND is_void=0", farm, cattle) > 0) status = "BRED_WAIT_CHECK";
            else if (count("SELECT COUNT(*) FROM estrus_record WHERE farm_id=? AND cattle_id=? AND is_void=0", farm, cattle) > 0) status = "WAIT_BREED";
            else status = null;
        }
        jdbc.update("UPDATE cattle SET breeding_status=?,updated_by=?,version=version+1 WHERE farm_id=? AND cattle_id=?", status, user, farm, cattle);
        return status;
    }

    private void recomputeCase(long farm, long caseId, long user) {
        if (count("SELECT COUNT(*) FROM health_case WHERE farm_id=? AND case_id=? AND is_void=0", farm, caseId) == 0) return;
        Map<String, Object> follow = latest("SELECT result value,follow_up_date event_date,description FROM follow_up_record WHERE farm_id=? AND case_id=? AND is_void=0 ORDER BY follow_up_date DESC,created_at DESC LIMIT 1", farm, caseId);
        if (follow != null && "RECOVERED".equals(String.valueOf(follow.get("value"))))
            jdbc.update("UPDATE health_case SET case_status='CLOSED',closed_at=?,close_result=?,version=version+1 WHERE farm_id=? AND case_id=?", follow.get("event_date"), follow.get("description"), farm, caseId);
        else jdbc.update("UPDATE health_case SET case_status='PROCESSING',closed_at=NULL,close_result=NULL,version=version+1 WHERE farm_id=? AND case_id=?", farm, caseId);
    }

    private String recomputeHealth(long farm, long cattle, long user) {
        String status;
        if (count("SELECT COUNT(*) FROM health_case WHERE farm_id=? AND cattle_id=? AND is_void=0 AND case_status='PROCESSING'", farm, cattle) == 0) status = "NORMAL";
        else if (count("SELECT COUNT(*) FROM treatment_record t JOIN health_case h ON h.case_id=t.case_id AND h.farm_id=t.farm_id WHERE t.farm_id=? AND t.cattle_id=? AND t.is_void=0 AND h.is_void=0 AND h.case_status='PROCESSING'", farm, cattle) > 0) status = "TREATING";
        else status = "OBSERVING";
        jdbc.update("UPDATE cattle SET health_status=?,updated_by=?,version=version+1 WHERE farm_id=? AND cattle_id=?", status, user, farm, cattle);
        return status;
    }

    private String recomputeVaccinationPlan(long farm, long plan, long user) {
        long target = count("SELECT COUNT(DISTINCT c.cattle_id) FROM cattle c WHERE c.farm_id=? AND c.presence_status='IN_FIELD' AND EXISTS(SELECT 1 FROM vaccination_plan_target t WHERE t.plan_id=? AND ((t.target_type='CATTLE' AND t.target_object_id=c.cattle_id) OR(t.target_type='BARN' AND t.target_object_id=c.barn_id) OR(t.target_type='HERD' AND t.target_object_id=c.herd_id)))", farm, plan);
        long done = count("SELECT COUNT(DISTINCT ec.cattle_id) FROM vaccination_execution e JOIN vaccination_execution_cattle ec ON ec.execution_id=e.execution_id WHERE e.farm_id=? AND e.plan_id=? AND e.is_void=0", farm, plan);
        String status = done == 0 ? "NOT_STARTED" : done >= target && target > 0 ? "DONE" : "IN_PROGRESS";
        jdbc.update("UPDATE vaccination_plan SET status=?,updated_by=?,version=version+1 WHERE farm_id=? AND plan_id=? AND status<>'CANCELLED'", status, user, farm, plan);
        jdbc.update("UPDATE task SET status=?,result=?,completed_by=NULL,completed_at=NULL,updated_by=?,version=version+1 WHERE farm_id=? AND source_type='VACCINATION_PLAN' AND source_id=? AND task_type='VACCINATION_EXECUTION' AND status<>'CANCELLED'",
                "DONE".equals(status) ? "DONE" : "PENDING", done + "/" + target + " 头已执行", user, farm, plan);
        return status;
    }

    private CorrectionResult breedingResult(BreedingMeta meta, long farm, long id) { Map<String,Object> r=row(meta.table(),meta.pk(),farm,id); return new CorrectionResult(Long.toString(id),meta.businessType(),number(r.get("is_void"))==1,null); }
    private CorrectionResult healthResult(HealthMeta meta, long farm, long id) { Map<String,Object> r=row(meta.table(),meta.pk(),farm,id); return new CorrectionResult(Long.toString(id),meta.businessType(),number(r.get("is_void"))==1,null); }
    private CorrectionResult vaccinationExecutionResult(long farm,long id){Map<String,Object> r=row("vaccination_execution","execution_id",farm,id);return new CorrectionResult(Long.toString(id),"VACCINATION_EXECUTION",number(r.get("is_void"))==1,r.get("plan_id")==null?"STANDALONE":planStatus(farm,number(r.get("plan_id"))));}
    private String planStatus(long farm,long id){try{return jdbc.queryForObject("SELECT status FROM vaccination_plan WHERE farm_id=? AND plan_id=?",String.class,farm,id);}catch(EmptyResultDataAccessException e){throw new DataConflictException("防疫计划不存在");}}
    private void ensureActive(Map<String,Object> row){if(number(row.get("is_void"))==1)throw new DataConflictException("记录已作废");}
    private Map<String,Object> row(String table,String pk,long farm,long id){try{return jdbc.queryForMap("SELECT * FROM "+table+" WHERE farm_id=? AND "+pk+"=?",farm,id);}catch(EmptyResultDataAccessException e){throw new DataConflictException("业务记录不存在");}}
    private Map<String,Object> latest(String sql,Object... args){try{return jdbc.queryForMap(sql,args);}catch(EmptyResultDataAccessException e){return null;}}
    private long count(String sql,Object... args){Number n=jdbc.queryForObject(sql,Number.class,args);return n==null?0:n.longValue();}
    private long number(Object value){return ((Number)value).longValue();}
    private void voidRow(String table,String pk,long farm,long id,VoidBusinessRequest r,long user){int n=jdbc.update("UPDATE "+table+" SET is_void=1,void_reason=?,voided_by=?,voided_at=NOW(),version=version+1 WHERE farm_id=? AND "+pk+"=? AND version=? AND is_void=0",r.reason().trim(),user,farm,id,r.version());if(n==0)throw new DataConflictException("记录已被其他人修改，请刷新后重试");}
    private void voidEvents(long farm,String table,long id){jdbc.update("UPDATE cattle_event SET is_void=1 WHERE farm_id=? AND business_table=? AND business_id=?",farm,table,id);}
    private void cancelSourceTasks(long farm,long user,String source,long id,String reason){jdbc.update("UPDATE task SET status='CANCELLED',cancel_reason=?,cancelled_by=?,cancelled_at=NOW(),updated_by=?,version=version+1 WHERE farm_id=? AND source_type=? AND source_id=? AND status IN('PENDING','IN_PROGRESS','OVERDUE')",reason.trim(),user,user,farm,source,id);}
    private void audit(long farm,long user,String module,String action,String type,long id,String reason){jdbc.update("INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason) VALUES(?,?,?,?,?,?,?,?)",IdWorker.getId(),farm,user,module,action,type,id,reason.trim());}
    private void idem(long farm,long user,String key,String path,long id){jdbc.update("INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at) VALUES(?,?,?,?,?,?)",farm,user,key,path,id,LocalDateTime.now().plusDays(1));}
    private Long replay(long farm,long user,String key,String path){try{return jdbc.queryForObject("SELECT business_id FROM idempotency_record WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path=? AND expires_at>NOW()",Long.class,farm,user,key,path);}catch(EmptyResultDataAccessException e){return null;}}
    private BreedingMeta breedingMeta(String kind){return switch(kind){case "HEAT"->new BreedingMeta("estrus_record","estrus_id","cattle_id","/api/v1/breeding/heats/","ESTRUS","ESTRUS_VOIDED","ESTRUS");case "INSEMINATION"->new BreedingMeta("breeding_record","breeding_id","cattle_id","/api/v1/breeding/inseminations/","BREEDING","BREEDING_VOIDED","BREEDING");case "PREGNANCY_CHECK"->new BreedingMeta("pregnancy_check","check_id","cattle_id","/api/v1/breeding/pregnancy-checks/","PREGNANCY_CHECK","PREGNANCY_CHECK_VOIDED","PREGNANCY_CHECK");case "CALVING"->new BreedingMeta("calving_record","calving_id","dam_cattle_id","/api/v1/breeding/calvings/","CALVING","CALVING_VOIDED","CALVING");default->throw new IllegalArgumentException();};}
    private HealthMeta healthMeta(String kind){return switch(kind){case "CASE"->new HealthMeta("health_case","case_id","/api/v1/health/cases/","HEALTH_CASE","HEALTH_CASE_VOIDED");case "TREATMENT"->new HealthMeta("treatment_record","treatment_id","/api/v1/health/treatments/","TREATMENT","TREATMENT_VOIDED");case "FOLLOW_UP"->new HealthMeta("follow_up_record","follow_up_id","/api/v1/health/follow-ups/","FOLLOW_UP","FOLLOW_UP_VOIDED");default->throw new IllegalArgumentException();};}
    private record BreedingMeta(String table,String pk,String cattleColumn,String path,String sourceType,String action,String businessType){}
    private record HealthMeta(String table,String pk,String path,String businessType,String action){}
}
