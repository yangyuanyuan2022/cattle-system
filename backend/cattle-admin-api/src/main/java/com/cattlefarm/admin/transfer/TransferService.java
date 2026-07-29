package com.cattlefarm.admin.transfer;

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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TransferService {
 private final JdbcTemplate jdbc; private final AuthService auth; private final ObjectMapper json; private final DataScopeService scope;
 public TransferService(JdbcTemplate jdbc,AuthService auth,ObjectMapper json,DataScopeService scope){this.jdbc=jdbc;this.auth=auth;this.json=json;this.scope=scope;}

 @Transactional public TransferResponse transfer(CreateTransferRequest r,String key){
  long farmId=auth.currentFarmId(),userId=StpUtil.getLoginIdAsLong(); Long replay=findReplay(farmId,userId,key);
  if(replay!=null)return responseFor(replay);
  long cattleId=parseId(r.cattleId(),"牛只"),toBarnId=parseId(r.toBarnId(),"栏舍");
  scope.assertCattle(cattleId);
  Long toHerdId=StringUtils.hasText(r.toHerdId())?parseId(r.toHerdId(),"牛群"):null;
  CattlePosition before=findCattle(farmId,cattleId);
  if(!"IN_FIELD".equals(before.presenceStatus()))throw new DataConflictException("已离场牛只不可转群");
  if(same(before.barnId(),toBarnId)&&same(before.herdId(),toHerdId))throw new DataConflictException("目标位置与当前位置相同");
  BarnTarget barn=findBarn(farmId,toBarnId); if(!"ENABLED".equals(barn.status()))throw new DataConflictException("目标栏舍已停用");
  if(toHerdId!=null){HerdTarget herd=findHerd(farmId,toHerdId);if(!"ENABLED".equals(herd.status()))throw new DataConflictException("目标牛群已停用");
   if(!same(herd.barnId(),toBarnId))throw new DataConflictException("目标牛群不属于所选栏舍");}
  long batchId=IdWorker.getId(),transferId=IdWorker.getId();
  jdbc.update("INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at) VALUES (?,?,?,?,?,?)",
   farmId,userId,key,"/api/v1/transfers",transferId,LocalDateTime.now().plusDays(1));
  int affected=jdbc.update("UPDATE cattle SET barn_id=?,herd_id=?,updated_by=?,version=version+1 WHERE cattle_id=? AND farm_id=? AND version=? AND presence_status='IN_FIELD'",
   toBarnId,toHerdId,userId,cattleId,farmId,r.version());
  if(affected==0)throw new DataConflictException("牛只档案已变更，请刷新后重试");
  jdbc.update("INSERT INTO transfer_batch(batch_id,farm_id,transfer_type,transfer_date,reason,operator_id,total_count,created_by,updated_by) VALUES (?,?,'SINGLE',?,?,?,1,?,?)",
   batchId,farmId,r.transferDate(),r.reason().trim(),userId,userId,userId);
  jdbc.update("""
   INSERT INTO transfer_record(transfer_id,batch_id,farm_id,cattle_id,from_herd_id,from_barn_id,to_herd_id,to_barn_id,transfer_date,reason,operator_id,created_by,updated_by)
   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
   """,transferId,batchId,farmId,cattleId,before.herdId(),before.barnId(),toHerdId,toBarnId,r.transferDate(),r.reason().trim(),userId,userId,userId);
  jdbc.update("""
   INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason,before_data,after_data)
   VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))
   """,IdWorker.getId(),farmId,userId,"LOCATION","CATTLE_TRANSFERRED","TRANSFER",transferId,r.reason().trim(),
   toJson(snapshot(before.barnId(),before.herdId(),before.version())),toJson(snapshot(toBarnId,toHerdId,r.version()+1)));
  jdbc.update("""
   INSERT INTO cattle_event(event_id,farm_id,cattle_id,event_type,event_date,business_table,business_id,summary,operator_id,is_void)
   VALUES (?,?,?,?,?,'transfer_record',?,?,?,0)
   """,IdWorker.getId(),farmId,cattleId,"CATTLE_TRANSFERRED",r.transferDate(),transferId,"牛只转群至栏舍："+barn.name(),userId);
  return responseFor(transferId);
 }

 private TransferResponse responseFor(long id){long farmId=auth.currentFarmId();return jdbc.queryForObject("""
  SELECT tr.*,b.capacity,(SELECT COUNT(*) FROM cattle c2 WHERE c2.farm_id=tr.farm_id AND c2.barn_id=tr.to_barn_id AND c2.presence_status='IN_FIELD') current_count,c.version cattle_version
  FROM transfer_record tr JOIN barn b ON b.barn_id=tr.to_barn_id AND b.farm_id=tr.farm_id
  JOIN cattle c ON c.cattle_id=tr.cattle_id AND c.farm_id=tr.farm_id WHERE tr.transfer_id=? AND tr.farm_id=?
  """,(rs,row)->{Integer capacity=(Integer)rs.getObject("capacity");boolean exceeded=capacity!=null&&rs.getLong("current_count")>capacity;
   return new TransferResponse(Long.toString(rs.getLong("batch_id")),Long.toString(rs.getLong("transfer_id")),Long.toString(rs.getLong("cattle_id")),
    string(rs.getObject("from_barn_id")),string(rs.getObject("from_herd_id")),string(rs.getObject("to_barn_id")),string(rs.getObject("to_herd_id")),
    exceeded,exceeded?"目标栏舍当前牛只数已超过容量，请及时调整":null,rs.getInt("cattle_version"));},id,farmId);}
 private CattlePosition findCattle(long farmId,long id){try{return jdbc.queryForObject("SELECT barn_id,herd_id,presence_status,version FROM cattle WHERE farm_id=? AND cattle_id=?",
  (rs,row)->new CattlePosition((Long)rs.getObject("barn_id"),(Long)rs.getObject("herd_id"),rs.getString("presence_status"),rs.getInt("version")),farmId,id);}catch(EmptyResultDataAccessException e){throw new DataConflictException("牛只不存在");}}
 private BarnTarget findBarn(long farmId,long id){try{return jdbc.queryForObject("SELECT barn_name,status FROM barn WHERE farm_id=? AND barn_id=?",
  (rs,row)->new BarnTarget(rs.getString("barn_name"),rs.getString("status")),farmId,id);}catch(EmptyResultDataAccessException e){throw new DataConflictException("目标栏舍不存在");}}
 private HerdTarget findHerd(long farmId,long id){try{return jdbc.queryForObject("SELECT barn_id,status FROM herd WHERE farm_id=? AND herd_id=?",
  (rs,row)->new HerdTarget((Long)rs.getObject("barn_id"),rs.getString("status")),farmId,id);}catch(EmptyResultDataAccessException e){throw new DataConflictException("目标牛群不存在");}}
 private Long findReplay(long farmId,long userId,String key){try{return jdbc.queryForObject("SELECT business_id FROM idempotency_record WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path='/api/v1/transfers' AND expires_at>NOW()",Long.class,farmId,userId,key);}catch(EmptyResultDataAccessException e){return null;}}
 private long parseId(String value,String label){try{return Long.parseLong(value);}catch(NumberFormatException e){throw new DataConflictException(label+"编号格式错误");}}
 private Map<String,Object> snapshot(Long barnId,Long herdId,int version){Map<String,Object> m=new LinkedHashMap<>();m.put("barnId",string(barnId));m.put("herdId",string(herdId));m.put("version",version);return m;}
 private String toJson(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("审计数据序列化失败",e);}}
 private static boolean same(Long a,Long b){return a==null?b==null:a.equals(b);} private static String string(Object v){return v==null?null:v.toString();}
 private record CattlePosition(Long barnId,Long herdId,String presenceStatus,int version){} private record BarnTarget(String name,String status){} private record HerdTarget(Long barnId,String status){}
}
