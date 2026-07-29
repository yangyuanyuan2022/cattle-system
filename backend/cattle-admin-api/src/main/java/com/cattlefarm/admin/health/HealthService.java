package com.cattlefarm.admin.health;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import com.cattlefarm.admin.scope.DataScopeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.*; import java.util.*;
import com.cattlefarm.admin.task.TaskService;

@Service
public class HealthService {
 private final JdbcTemplate jdbc; private final AuthService auth; private final ObjectMapper json;private final TaskService tasks;private final DataScopeService scope;
 public HealthService(JdbcTemplate jdbc,AuthService auth,ObjectMapper json,TaskService tasks,DataScopeService scope){this.jdbc=jdbc;this.auth=auth;this.json=json;this.tasks=tasks;this.scope=scope;}

 public List<HealthDtos.CaseItem> cases(String status){long farm=auth.currentFarmId();String filter=StringUtils.hasText(status)?status:null;
  return jdbc.query(caseSql()+" WHERE hc.farm_id=? AND hc.is_void=0 AND (? IS NULL OR hc.case_status=?) ORDER BY hc.discover_date DESC",this::mapCase,farm,filter,filter).stream().filter(x->scope.unrestricted()||scope.canAccessCattle(Long.parseLong(x.cattleId()))).toList();}
 public HealthDtos.CaseItem detail(long id){try{HealthDtos.CaseItem item=jdbc.queryForObject(caseSql()+" WHERE hc.farm_id=? AND hc.case_id=? AND hc.is_void=0",this::mapCase,auth.currentFarmId(),id);if(!scope.unrestricted())scope.assertCattle(Long.parseLong(item.cattleId()));return item;}catch(EmptyResultDataAccessException e){throw new DataConflictException("病例不存在");}}

 public HealthDtos.CaseDetail caseDetail(long id){HealthDtos.CaseItem item=detail(id);long farm=auth.currentFarmId();List<HealthDtos.TreatmentItem> treatments=jdbc.query("""
  SELECT t.treatment_id,t.treatment_date,t.diagnosis,t.treatment_plan,t.need_follow_up,t.follow_up_date,u.real_name,t.version
  FROM treatment_record t LEFT JOIN sys_user u ON u.user_id=t.vet_id AND u.farm_id=t.farm_id
  WHERE t.farm_id=? AND t.case_id=? AND t.is_void=0 ORDER BY t.treatment_date DESC,t.created_at DESC
  """,(rs,row)->new HealthDtos.TreatmentItem(Long.toString(rs.getLong(1)),rs.getTimestamp(2).toLocalDateTime(),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getDate(6)==null?null:rs.getDate(6).toLocalDate(),rs.getString(7),rs.getInt(8)),farm,id);List<HealthDtos.FollowUpItem> followUps=jdbc.query("""
  SELECT f.follow_up_id,f.follow_up_date,f.result,f.description,u.real_name,f.version
  FROM follow_up_record f LEFT JOIN sys_user u ON u.user_id=f.operator_id AND u.farm_id=f.farm_id
  WHERE f.farm_id=? AND f.case_id=? AND f.is_void=0 ORDER BY f.follow_up_date DESC,f.created_at DESC
  """,(rs,row)->new HealthDtos.FollowUpItem(Long.toString(rs.getLong(1)),rs.getTimestamp(2).toLocalDateTime(),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6)),farm,id);return new HealthDtos.CaseDetail(item,treatments,followUps);}

 @Transactional public HealthDtos.ActionResult report(HealthDtos.CreateCase r,String key){
  long farm=auth.currentFarmId(),user=StpUtil.getLoginIdAsLong();Long replay=replay(farm,user,key,"/api/v1/health/abnormalities");if(replay!=null)return result(replay,replay);
  long cattle=parse(r.cattleId(),"牛只"),caseId=IdWorker.getId(); CattleState before=cattle(farm,cattle);
  if(!"IN_FIELD".equals(before.presence()))throw new DataConflictException("已离场牛只不可上报健康异常");
  idem(farm,user,key,"/api/v1/health/abnormalities",caseId);
  jdbc.update("INSERT INTO health_case(case_id,farm_id,cattle_id,case_no,discover_date,symptom,severity,case_status,reporter_id) VALUES (?,?,?,?,?,?,?,'PROCESSING',?)",
   caseId,farm,cattle,"HC-"+caseId,r.discoverDate(),r.symptom().trim(),r.severity(),user);
  int affected=jdbc.update("UPDATE cattle SET health_status='OBSERVING',updated_by=?,version=version+1 WHERE farm_id=? AND cattle_id=? AND version=?",user,farm,cattle,before.version());
  if(affected==0)throw new DataConflictException("牛只档案已变更，请刷新后重试");
  event(farm,cattle,"HEALTH_ABNORMALITY_REPORTED",r.discoverDate(),"health_case",caseId,"上报健康异常："+r.symptom().trim(),user);
  audit(farm,user,"HEALTH_CASE_CREATED","HEALTH_CASE",caseId,"上报健康异常",Map.of("healthStatus",before.health()),Map.of("healthStatus","OBSERVING"));
  return result(caseId,caseId);
 }

 @Transactional public HealthDtos.ActionResult treatment(HealthDtos.CreateTreatment r,String key){
  long farm=auth.currentFarmId(),user=StpUtil.getLoginIdAsLong();Long replay=replay(farm,user,key,"/api/v1/health/treatments");
  if(replay!=null){Long caseId=jdbc.queryForObject("SELECT case_id FROM treatment_record WHERE treatment_id=? AND farm_id=?",Long.class,replay,farm);return result(caseId,replay);}
  long caseId=parse(r.caseId(),"病例"); CaseState cs=caseState(farm,caseId);if(!"PROCESSING".equals(cs.status()))throw new DataConflictException("病例已结案，不可新增诊疗");
  CattleState before=cattle(farm,cs.cattleId()); long id=IdWorker.getId(); idem(farm,user,key,"/api/v1/health/treatments",id);
  jdbc.update("INSERT INTO treatment_record(treatment_id,farm_id,case_id,cattle_id,treatment_date,diagnosis,treatment_plan,need_follow_up,follow_up_date,vet_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
   id,farm,caseId,cs.cattleId(),r.treatmentDate(),r.diagnosis().trim(),r.treatmentPlan(),r.needFollowUp(),r.followUpDate(),user);
  if(r.medications()!=null)for(HealthDtos.Medication m:r.medications())jdbc.update("INSERT INTO medication_item(medication_id,farm_id,treatment_id,medicine_name,dosage,unit,usage_method,withdrawal_days,remark) VALUES (?,?,?,?,?,?,?,?,?)",
   IdWorker.getId(),farm,id,m.medicineName().trim(),m.dosage(),m.unit(),m.usageMethod(),m.withdrawalDays(),m.remark());
  int affected=jdbc.update("UPDATE cattle SET health_status='TREATING',updated_by=?,version=version+1 WHERE farm_id=? AND cattle_id=? AND version=?",user,farm,cs.cattleId(),before.version());
  if(affected==0)throw new DataConflictException("牛只档案已变更，请刷新后重试");
  event(farm,cs.cattleId(),"CATTLE_TREATED",r.treatmentDate(),"treatment_record",id,"登记诊疗："+r.diagnosis().trim(),user);
  if(r.needFollowUp()){LocalDate plan=r.followUpDate()==null?r.treatmentDate().toLocalDate().plusDays(3):r.followUpDate();tasks.createAutomatic(farm,id,"TREATMENT","HEALTH_FOLLOW_UP","牛只健康复查",cs.cattleId(),null,null,plan,plan.plusDays(2),user,"IMPORTANT");}
  audit(farm,user,"TREATMENT_CREATED","TREATMENT",id,"登记诊疗",Map.of("healthStatus",before.health()),Map.of("healthStatus","TREATING"));
  return result(caseId,id);
 }

 @Transactional public HealthDtos.ActionResult followUp(HealthDtos.CreateFollowUp r,String key){
  long farm=auth.currentFarmId(),user=StpUtil.getLoginIdAsLong();Long replay=replay(farm,user,key,"/api/v1/health/follow-ups");
  if(replay!=null){Long caseId=jdbc.queryForObject("SELECT case_id FROM follow_up_record WHERE follow_up_id=? AND farm_id=?",Long.class,replay,farm);return result(caseId,replay);}
  long caseId=parse(r.caseId(),"病例");CaseState cs=caseState(farm,caseId);if(!"PROCESSING".equals(cs.status()))throw new DataConflictException("病例已结案，不可新增复查");
  CattleState before=cattle(farm,cs.cattleId());long id=IdWorker.getId();idem(farm,user,key,"/api/v1/health/follow-ups",id);
  jdbc.update("INSERT INTO follow_up_record(follow_up_id,farm_id,case_id,cattle_id,follow_up_date,result,description,operator_id) VALUES (?,?,?,?,?,?,?,?)",id,farm,caseId,cs.cattleId(),r.followUpDate(),r.result(),r.description(),user);
  String health=switch(r.result()){case "RECOVERED"->"NORMAL";case "OBSERVE"->"OBSERVING";default->"TREATING";};
  if("RECOVERED".equals(r.result()))jdbc.update("UPDATE health_case SET case_status='CLOSED',closed_at=?,close_result=?,version=version+1 WHERE case_id=? AND farm_id=?",r.followUpDate(),r.description(),caseId,farm);
  int affected=jdbc.update("UPDATE cattle SET health_status=?,updated_by=?,version=version+1 WHERE farm_id=? AND cattle_id=? AND version=?",health,user,farm,cs.cattleId(),before.version());
  if(affected==0)throw new DataConflictException("牛只档案已变更，请刷新后重试");
  event(farm,cs.cattleId(),"HEALTH_FOLLOW_UP",r.followUpDate(),"follow_up_record",id,"健康复查："+resultLabel(r.result()),user);
  tasks.finishOpenForCattle(farm,"HEALTH_FOLLOW_UP",cs.cattleId(),resultLabel(r.result()),user);
  audit(farm,user,"FOLLOW_UP_CREATED","FOLLOW_UP",id,"登记健康复查",Map.of("healthStatus",before.health()),Map.of("healthStatus",health));
  return result(caseId,id);
 }

 private String caseSql(){return """
  SELECT hc.*,c.ear_tag_no,c.name,c.health_status,(SELECT COUNT(*) FROM treatment_record t WHERE t.farm_id=hc.farm_id AND t.case_id=hc.case_id AND t.is_void=0) treatment_count,
  (SELECT MAX(DATE_ADD(DATE(t.treatment_date),INTERVAL m.withdrawal_days DAY)) FROM treatment_record t JOIN medication_item m ON m.treatment_id=t.treatment_id AND m.farm_id=t.farm_id WHERE t.farm_id=hc.farm_id AND t.case_id=hc.case_id AND t.is_void=0 AND m.withdrawal_days IS NOT NULL) withdrawal_until
  FROM health_case hc JOIN cattle c ON c.cattle_id=hc.cattle_id AND c.farm_id=hc.farm_id
  """;}
 private HealthDtos.CaseItem mapCase(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new HealthDtos.CaseItem(Long.toString(rs.getLong("case_id")),rs.getString("case_no"),Long.toString(rs.getLong("cattle_id")),rs.getString("ear_tag_no"),rs.getString("name"),rs.getTimestamp("discover_date").toLocalDateTime(),rs.getString("symptom"),rs.getString("severity"),rs.getString("case_status"),rs.getString("health_status"),rs.getLong("treatment_count"),rs.getDate("withdrawal_until")==null?null:rs.getDate("withdrawal_until").toLocalDate(),rs.getInt("version"));}
 private HealthDtos.ActionResult result(long caseId,long business){HealthDtos.CaseItem item=detail(caseId);Integer version=jdbc.queryForObject("SELECT version FROM cattle WHERE cattle_id=? AND farm_id=?",Integer.class,Long.parseLong(item.cattleId()),auth.currentFarmId());return new HealthDtos.ActionResult(Long.toString(business),item.caseId(),item.cattleId(),item.caseStatus(),item.healthStatus(),version==null?0:version,item.withdrawalUntil());}
 private CattleState cattle(long farm,long id){if(!scope.unrestricted())scope.assertCattle(id);try{return jdbc.queryForObject("SELECT presence_status,health_status,version FROM cattle WHERE farm_id=? AND cattle_id=?",(rs,row)->new CattleState(rs.getString(1),rs.getString(2),rs.getInt(3)),farm,id);}catch(EmptyResultDataAccessException e){throw new DataConflictException("牛只不存在");}}
 private CaseState caseState(long farm,long id){try{return jdbc.queryForObject("SELECT cattle_id,case_status FROM health_case WHERE farm_id=? AND case_id=? AND is_void=0",(rs,row)->new CaseState(rs.getLong(1),rs.getString(2)),farm,id);}catch(EmptyResultDataAccessException e){throw new DataConflictException("病例不存在");}}
 private void idem(long farm,long user,String key,String path,long id){jdbc.update("INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at) VALUES (?,?,?,?,?,?)",farm,user,key,path,id,LocalDateTime.now().plusDays(1));}
 private Long replay(long farm,long user,String key,String path){try{return jdbc.queryForObject("SELECT business_id FROM idempotency_record WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path=? AND expires_at>NOW()",Long.class,farm,user,key,path);}catch(EmptyResultDataAccessException e){return null;}}
 private void event(long farm,long cattle,String type,LocalDateTime date,String table,long id,String summary,long user){jdbc.update("INSERT INTO cattle_event(event_id,farm_id,cattle_id,event_type,event_date,business_table,business_id,summary,operator_id,is_void) VALUES (?,?,?,?,?,?,?,?,?,0)",IdWorker.getId(),farm,cattle,type,date,table,id,summary,user);}
 private void audit(long farm,long user,String action,String type,long id,String reason,Object before,Object after){jdbc.update("INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason,before_data,after_data) VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))",IdWorker.getId(),farm,user,"HEALTH",action,type,id,reason,toJson(before),toJson(after));}
 private String toJson(Object v){try{return json.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalStateException("审计数据序列化失败",e);}}
 private long parse(String v,String label){try{return Long.parseLong(v);}catch(NumberFormatException e){throw new DataConflictException(label+"编号格式错误");}}
 private String resultLabel(String v){return switch(v){case "RECOVERED"->"康复结案";case "OBSERVE"->"转观察";default->"继续治疗";};}
 private record CattleState(String presence,String health,int version){} private record CaseState(long cattleId,String status){}
}
