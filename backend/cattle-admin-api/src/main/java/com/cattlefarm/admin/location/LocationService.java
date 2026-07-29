package com.cattlefarm.admin.location;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;
import java.time.LocalDateTime;

@Service
public class LocationService {
 private final JdbcTemplate jdbc; private final AuthService auth;
 public LocationService(JdbcTemplate jdbc, AuthService auth) { this.jdbc=jdbc; this.auth=auth; }
 public List<BarnResponse> barns(String status) {
  long farmId=auth.currentFarmId(); String filter=StringUtils.hasText(status)?status:null;
  return jdbc.query("""
   SELECT b.barn_id,b.barn_code,b.barn_name,b.barn_type,b.capacity,b.status,b.remark,b.version,COUNT(c.cattle_id) cattle_count
   FROM barn b LEFT JOIN cattle c ON c.farm_id=b.farm_id AND c.barn_id=b.barn_id AND c.presence_status='IN_FIELD'
   WHERE b.farm_id=? AND (? IS NULL OR b.status=?)
   GROUP BY b.barn_id,b.barn_code,b.barn_name,b.barn_type,b.capacity,b.status,b.remark,b.version ORDER BY b.created_at DESC
   """, (rs,row)->new BarnResponse(Long.toString(rs.getLong("barn_id")),rs.getString("barn_code"),rs.getString("barn_name"),
   rs.getString("barn_type"),(Integer)rs.getObject("capacity"),rs.getString("status"),rs.getString("remark"),rs.getLong("cattle_count"),rs.getInt("version")),farmId,filter,filter);
 }
 @Transactional public BarnResponse createBarn(CreateBarnRequest r,String key) {
  long farmId=auth.currentFarmId(), userId=StpUtil.getLoginIdAsLong(), id=IdWorker.getId();
  Long old=replay(farmId,userId,key,"/api/v1/barns");if(old!=null)return findBarn(old);
  idem(farmId,userId,key,"/api/v1/barns",id);
  jdbc.update("INSERT INTO barn(barn_id,farm_id,barn_code,barn_name,barn_type,capacity,status,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,'ENABLED',?,?,?)",
   id,farmId,r.barnCode().trim(),r.barnName().trim(),r.barnType(),r.capacity(),r.remark(),userId,userId);
  audit(farmId,userId,"BARN_CREATED","BARN",id);return findBarn(id);
 }
 public List<HerdResponse> herds(String status) {
  long farmId=auth.currentFarmId(); String filter=StringUtils.hasText(status)?status:null;
  return jdbc.query("""
   SELECT h.herd_id,h.herd_code,h.herd_name,h.herd_type,h.barn_id,b.barn_name,h.status,h.remark,h.version,COUNT(c.cattle_id) cattle_count
   FROM herd h LEFT JOIN barn b ON b.farm_id=h.farm_id AND b.barn_id=h.barn_id
   LEFT JOIN cattle c ON c.farm_id=h.farm_id AND c.herd_id=h.herd_id AND c.presence_status='IN_FIELD'
   WHERE h.farm_id=? AND (? IS NULL OR h.status=?)
   GROUP BY h.herd_id,h.herd_code,h.herd_name,h.herd_type,h.barn_id,b.barn_name,h.status,h.remark,h.version ORDER BY h.created_at DESC
   """,(rs,row)->new HerdResponse(Long.toString(rs.getLong("herd_id")),rs.getString("herd_code"),rs.getString("herd_name"),
   rs.getString("herd_type"),string(rs.getObject("barn_id")),rs.getString("barn_name"),rs.getString("status"),rs.getString("remark"),rs.getLong("cattle_count"),rs.getInt("version")),farmId,filter,filter);
 }
 @Transactional public HerdResponse createHerd(CreateHerdRequest r,String key) {
  long farmId=auth.currentFarmId(), userId=StpUtil.getLoginIdAsLong(); Long barnId=parseId(r.barnId(),"栏舍");
  Long old=replay(farmId,userId,key,"/api/v1/herds");if(old!=null)return findHerd(old);
  if(barnId!=null) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM barn WHERE barn_id=? AND farm_id=? AND status='ENABLED'",Integer.class,barnId,farmId);
   if(count==null||count==0) throw new DataConflictException("所选栏舍不存在或已停用"); }
  long id=IdWorker.getId();idem(farmId,userId,key,"/api/v1/herds",id); jdbc.update("INSERT INTO herd(herd_id,farm_id,herd_code,herd_name,herd_type,barn_id,status,remark,created_by,updated_by) VALUES (?,?,?,?,?,?,'ENABLED',?,?,?)",
   id,farmId,r.herdCode().trim(),r.herdName().trim(),r.herdType(),barnId,r.remark(),userId,userId);
  audit(farmId,userId,"HERD_CREATED","HERD",id);return findHerd(id);
 }
 @Transactional public BarnResponse updateBarn(long id,UpdateBarnRequest r,String key){long farm=auth.currentFarmId(),user=StpUtil.getLoginIdAsLong();String path="/api/v1/barns/"+id;Long old=replay(farm,user,key,path);if(old!=null)return findBarn(old);BarnResponse before=findBarn(id);if("DISABLED".equals(r.status())&&before.cattleCount()>0)throw new DataConflictException("栏舍内仍有在场牛只，不能停用");idem(farm,user,key,path,id);int n=jdbc.update("UPDATE barn SET barn_name=?,barn_type=?,capacity=?,status=?,remark=?,updated_by=?,version=version+1 WHERE barn_id=? AND farm_id=? AND version=?",r.barnName().trim(),r.barnType(),r.capacity(),r.status(),r.remark(),user,id,farm,r.version());if(n==0)throw new DataConflictException("栏舍已被其他人修改，请刷新后重试");audit(farm,user,"BARN_UPDATED","BARN",id);return findBarn(id);}
 @Transactional public HerdResponse updateHerd(long id,UpdateHerdRequest r,String key){long farm=auth.currentFarmId(),user=StpUtil.getLoginIdAsLong();String path="/api/v1/herds/"+id;Long old=replay(farm,user,key,path);if(old!=null)return findHerd(old);HerdResponse before=findHerd(id);if("DISABLED".equals(r.status())&&before.cattleCount()>0)throw new DataConflictException("牛群内仍有在场牛只，不能停用");Long barn=parseId(r.barnId(),"栏舍");if(barn!=null){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM barn WHERE farm_id=? AND barn_id=? AND status='ENABLED'",Integer.class,farm,barn);if(n==null||n==0)throw new DataConflictException("所选栏舍不存在或已停用");}idem(farm,user,key,path,id);int n=jdbc.update("UPDATE herd SET herd_name=?,herd_type=?,barn_id=?,status=?,remark=?,updated_by=?,version=version+1 WHERE herd_id=? AND farm_id=? AND version=?",r.herdName().trim(),r.herdType(),barn,r.status(),r.remark(),user,id,farm,r.version());if(n==0)throw new DataConflictException("牛群已被其他人修改，请刷新后重试");audit(farm,user,"HERD_UPDATED","HERD",id);return findHerd(id);}
 private void audit(long farm,long user,String action,String type,long id){jdbc.update("INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason) VALUES(?,?,?,'LOCATION',?,?,?,'更新位置基础资料')",IdWorker.getId(),farm,user,action,type,id);}
 private BarnResponse findBarn(long id){return barns(null).stream().filter(x->x.barnId().equals(Long.toString(id))).findFirst().orElseThrow(()->new DataConflictException("栏舍不存在"));}
 private HerdResponse findHerd(long id){return herds(null).stream().filter(x->x.herdId().equals(Long.toString(id))).findFirst().orElseThrow(()->new DataConflictException("牛群不存在"));}
 private void idem(long farm,long user,String key,String path,long id){jdbc.update("INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at) VALUES(?,?,?,?,?,?)",farm,user,key,path,id,LocalDateTime.now().plusDays(1));}
 private Long replay(long farm,long user,String key,String path){try{return jdbc.queryForObject("SELECT business_id FROM idempotency_record WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path=? AND expires_at>NOW()",Long.class,farm,user,key,path);}catch(EmptyResultDataAccessException e){return null;}}
 private Long parseId(String value,String label){if(!StringUtils.hasText(value))return null;try{return Long.valueOf(value);}catch(NumberFormatException e){throw new DataConflictException(label+"编号格式错误");}}
 private static String string(Object value){return value==null?null:value.toString();}
}
