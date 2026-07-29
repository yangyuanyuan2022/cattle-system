package com.cattlefarm.admin.config;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class LocalDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public LocalDataInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='admin'", Integer.class);
        if (count != null && count > 0) {
            Long farmId = jdbcTemplate.queryForObject("SELECT farm_id FROM sys_user WHERE username='admin'", Long.class);
            Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM sys_user WHERE username='admin'", Long.class);
            seedLocations(farmId, userId);
            return;
        }

        long farmId = IdWorker.getId();
        long userId = IdWorker.getId();
        long farmUserId = IdWorker.getId();
        long roleId = IdWorker.getId();

        jdbcTemplate.update("INSERT INTO farm(farm_id,farm_name,farm_code,farm_type) VALUES (?,?,?,'BEEF')",
                farmId, "示范肉牛场", "DEMO-BEEF");
        jdbcTemplate.update("""
                INSERT INTO sys_user(user_id,farm_id,username,real_name,password_hash,status)
                VALUES (?,?,?,?,?,'ENABLED')
                """, userId, farmId, "admin", "牛场管理员", passwordEncoder.encode("123456"));
        jdbcTemplate.update("""
                INSERT INTO farm_user(farm_user_id,farm_id,user_id,member_name,status)
                VALUES (?,?,?,?,'ENABLED')
                """, farmUserId, farmId, userId, "牛场管理员");
        jdbcTemplate.update("""
                INSERT INTO sys_role(role_id,farm_id,role_code,role_name,status)
                VALUES (?,?, 'ADMIN','管理员','ENABLED')
                """, roleId, farmId);
        jdbcTemplate.update("INSERT INTO farm_user_role(id,farm_user_id,role_id) VALUES (?,?,?)",
                IdWorker.getId(), farmUserId, roleId);
        seedLocations(farmId, userId);
    }

    private void seedLocations(long farmId, long userId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM barn WHERE farm_id=?", Integer.class, farmId);
        if (count != null && count > 0) return;
        long mainBarn = IdWorker.getId(), isolationBarn = IdWorker.getId();
        jdbcTemplate.update("INSERT INTO barn(barn_id,farm_id,barn_code,barn_name,barn_type,capacity,status,created_by,updated_by) VALUES (?,?,?,?,?,?,'ENABLED',?,?)",
                mainBarn, farmId, "BARN-01", "育肥一舍", "FATTENING", 80, userId, userId);
        jdbcTemplate.update("INSERT INTO barn(barn_id,farm_id,barn_code,barn_name,barn_type,capacity,status,created_by,updated_by) VALUES (?,?,?,?,?,?,'ENABLED',?,?)",
                isolationBarn, farmId, "BARN-ISO", "隔离观察舍", "ISOLATION", 12, userId, userId);
        jdbcTemplate.update("INSERT INTO herd(herd_id,farm_id,herd_code,herd_name,herd_type,barn_id,status,created_by,updated_by) VALUES (?,?,?,?,?,?,'ENABLED',?,?)",
                IdWorker.getId(), farmId, "HERD-01", "育肥一群", "FATTENING", mainBarn, userId, userId);
        jdbcTemplate.update("INSERT INTO herd(herd_id,farm_id,herd_code,herd_name,herd_type,barn_id,status,created_by,updated_by) VALUES (?,?,?,?,?,?,'ENABLED',?,?)",
                IdWorker.getId(), farmId, "HERD-ISO", "隔离观察群", "ISOLATION", isolationBarn, userId, userId);
    }
}
