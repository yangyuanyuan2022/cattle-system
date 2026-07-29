package com.cattlefarm.admin.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.*;
import com.cattlefarm.admin.auth.model.SysUser;
import com.cattlefarm.admin.common.DataConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class MiniappAuthService {
    private final JdbcTemplate jdbc; private final AuthService auth; private final ObjectMapper json;
    private final String appId,appSecret;
    public MiniappAuthService(JdbcTemplate jdbc,AuthService auth,ObjectMapper json,
                              @Value("${wechat.miniapp.app-id:${WECHAT_APP_ID:}}")String appId,
                              @Value("${wechat.miniapp.app-secret:${WECHAT_APP_SECRET:}}")String appSecret){this.jdbc=jdbc;this.auth=auth;this.json=json;this.appId=appId;this.appSecret=appSecret;}

    @Transactional public LoginResponse bind(MiniappBindRequest request,String key,HttpServletRequest servletRequest){String openid=exchange(request.code());SysUser user=auth.authenticate(request.username(),request.password());long farm=user.getFarmId(),uid=user.getUserId();String path="/api/v1/auth/miniapp/bind";Long old=replay(farm,uid,key,path);if(old!=null){SysUser existing=auth.authenticate(request.username(),request.password());return auth.establishSession(existing,servletRequest,true);}try{idem(farm,uid,key,path,uid);int n=jdbc.update("UPDATE sys_user SET wechat_openid=?,updated_at=NOW(),version=version+1 WHERE user_id=? AND farm_id=? AND status='ENABLED' AND (wechat_openid IS NULL OR wechat_openid=?)",openid,uid,farm,openid);if(n==0)throw new DataConflictException("员工账号已绑定其他微信账号，请先解绑");}catch(DuplicateKeyException e){throw new DataConflictException("该微信账号已绑定其他员工账号");}audit(farm,uid,"MINIAPP_BOUND","绑定微信小程序账号");return auth.establishSession(user,servletRequest,true);}
    @Transactional public void unbind(String key){long farm=auth.currentFarmId(),user=StpUtil.getLoginIdAsLong();String path="/api/v1/auth/miniapp/unbind";if(replay(farm,user,key,path)!=null)return;idem(farm,user,key,path,user);jdbc.update("UPDATE sys_user SET wechat_openid=NULL,updated_at=NOW(),version=version+1 WHERE user_id=? AND farm_id=?",user,farm);audit(farm,user,"MINIAPP_UNBOUND","解绑微信小程序账号");}
    private String exchange(String code){if(appId.isBlank()||appSecret.isBlank())throw new DataConflictException("微信小程序 AppID/AppSecret 尚未配置");try{String query="appid="+enc(appId)+"&secret="+enc(appSecret)+"&js_code="+enc(code)+"&grant_type=authorization_code";HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/sns/jscode2session?"+query)).timeout(Duration.ofSeconds(8)).GET().build();HttpResponse<String>response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));JsonNode body=json.readTree(response.body());String openid=body.path("openid").asText();if(response.statusCode()!=200||openid.isBlank())throw new DataConflictException("微信登录凭证无效或已过期");return openid;}catch(DataConflictException e){throw e;}catch(Exception e){throw new DataConflictException("微信服务暂时不可用，请稍后重试");}}
    private String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}private void audit(long f,long u,String a,String reason){jdbc.update("INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason) VALUES(?,?,?,'AUTH',?,'USER',?,?)",IdWorker.getId(),f,u,a,u,reason);}private void idem(long f,long u,String k,String p,long id){jdbc.update("INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at) VALUES(?,?,?,?,?,?)",f,u,k,p,id,LocalDateTime.now().plusDays(1));}private Long replay(long f,long u,String k,String p){try{return jdbc.queryForObject("SELECT business_id FROM idempotency_record WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path=? AND expires_at>NOW()",Long.class,f,u,k,p);}catch(org.springframework.dao.EmptyResultDataAccessException e){return null;}}
}
