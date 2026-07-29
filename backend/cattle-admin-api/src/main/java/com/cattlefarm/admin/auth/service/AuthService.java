package com.cattlefarm.admin.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cattlefarm.admin.auth.AuthException;
import com.cattlefarm.admin.auth.CurrentUserResponse;
import com.cattlefarm.admin.auth.LoginRequest;
import com.cattlefarm.admin.auth.LoginResponse;
import com.cattlefarm.admin.auth.mapper.SysUserMapper;
import com.cattlefarm.admin.auth.mapper.UserAccessMapper;
import com.cattlefarm.admin.auth.model.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final UserAccessMapper accessMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final RefreshTokenService refreshTokens;

    public AuthService(SysUserMapper userMapper, UserAccessMapper accessMapper,
                       PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate,
                       RefreshTokenService refreshTokens) {
        this.userMapper = userMapper;
        this.accessMapper = accessMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        SysUser user;
        try {
            user = authenticate(request.username(), request.password());
        } catch (AuthException exception) {
            writeLoginLog(request.username(), null, "FAILED", "INVALID_CREDENTIALS", servletRequest);
            throw exception;
        }

        return establishSession(user, servletRequest, true);
    }

    public SysUser authenticate(String username, String password) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || !"ENABLED".equals(user.getStatus()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("账号或密码错误");
        }
        return user;
    }

    @Transactional
    public LoginResponse refresh(com.cattlefarm.admin.auth.RefreshRequest request) {
        RefreshTokenService.TokenOwner owner = refreshTokens.consume(request.refreshToken());
        SysUser user = userMapper.selectById(owner.userId());
        if (user == null || !"ENABLED".equals(user.getStatus()) || user.getFarmId() == null || owner.farmId() != user.getFarmId()) {
            throw new AuthException("刷新令牌对应账号已停用或不存在");
        }
        LoginResponse response = establishSession(user, null, false);
        Long replacement = jdbcTemplate.queryForObject("SELECT refresh_token_id FROM auth_refresh_token WHERE token_hash=?", Long.class,
                sha256(response.refreshToken()));
        refreshTokens.replace(owner.tokenId(), replacement);
        return response;
    }

    @Transactional
    public LoginResponse establishSession(SysUser user, HttpServletRequest servletRequest, boolean writeLog) {
        List<String> actualRoles = accessMapper.findRoleCodes(user.getUserId(), user.getFarmId());
        List<String> permissions = accessMapper.findPermissionCodes(user.getUserId(), user.getFarmId());
        List<String> roles = expandRoles(actualRoles, permissions);
        StpUtil.login(user.getUserId());
        StpUtil.getSession().set("farmId", user.getFarmId()).set("roles", roles);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        if (writeLog && servletRequest != null) writeLoginLog(user.getUsername(), user.getUserId(), "SUCCESS", null, servletRequest);

        SaTokenInfo token = StpUtil.getTokenInfo();
        RefreshTokenService.Issued refresh = refreshTokens.issue(user.getUserId(), user.getFarmId());
        return new LoginResponse(token.getTokenValue(), refresh.token(), token.getTokenTimeout(),
                Long.toString(user.getUserId()), Long.toString(user.getFarmId()), user.getRealName(), roles);
    }

    @Transactional
    public void logout() {
        long userId = StpUtil.getLoginIdAsLong();
        refreshTokens.revokeUser(userId);
        StpUtil.logout();
    }

    public CurrentUserResponse currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        Long farmId = currentFarmId();
        List<String> roles = accessMapper.findRoleCodes(userId, farmId);
        List<String> permissions = accessMapper.findPermissionCodes(userId, farmId);
        return new CurrentUserResponse(Long.toString(userId), Long.toString(farmId), user.getUsername(), user.getRealName(), roles,
                permissions);
    }

    public Long currentFarmId() {
        return StpUtil.getSession().getLong("farmId");
    }

    private List<String> expandRoles(List<String> actual, List<String> permissions) {
        java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>(actual);
        if (permissions.contains("SYSTEM")) roles.add("ADMIN");
        if (permissions.contains("FARM_MANAGEMENT")) roles.add("FARM_MANAGER");
        if (permissions.contains("HEALTH")) roles.add("VET");
        if (permissions.contains("BREEDING")) roles.add("BREEDER");
        if (permissions.contains("FIELD_WORK")) roles.add("WORKER");
        return List.copyOf(roles);
    }

    private void writeLoginLog(String account, Long userId, String result, String reason,
                               HttpServletRequest request) {
        jdbcTemplate.update("""
                        INSERT INTO login_log(login_account,user_id,login_result,failure_reason,client_ip,user_agent)
                        VALUES (?,?,?,?,?,?)
                        """, account, userId, result, reason, request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
