package com.cattlefarm.admin.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.RefreshTokenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    public RefreshTokenService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Issued issue(long userId,long farmId){byte[] bytes=new byte[48];RANDOM.nextBytes(bytes);String raw=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);long id=IdWorker.getId();LocalDateTime expiry=LocalDateTime.now().plusDays(30);jdbc.update("INSERT INTO auth_refresh_token(refresh_token_id,user_id,farm_id,token_hash,expires_at) VALUES(?,?,?,?,?)",id,userId,farmId,hash(raw),expiry);return new Issued(id,raw,expiry);}
    public TokenOwner consume(String raw){try{return jdbc.queryForObject("SELECT refresh_token_id,user_id,farm_id FROM auth_refresh_token WHERE token_hash=? AND revoked_at IS NULL AND expires_at>NOW() FOR UPDATE",(r,n)->new TokenOwner(r.getLong(1),r.getLong(2),r.getLong(3)),hash(raw));}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new RefreshTokenException("刷新令牌无效或已过期");}}
    public void replace(long oldId,long newId){jdbc.update("UPDATE auth_refresh_token SET revoked_at=NOW(),replaced_by=? WHERE refresh_token_id=? AND revoked_at IS NULL",newId,oldId);}
    public void revokeUser(long userId){jdbc.update("UPDATE auth_refresh_token SET revoked_at=NOW() WHERE user_id=? AND revoked_at IS NULL",userId);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public record Issued(long id,String token,LocalDateTime expiresAt){}
    public record TokenOwner(long tokenId,long userId,long farmId){}
}
