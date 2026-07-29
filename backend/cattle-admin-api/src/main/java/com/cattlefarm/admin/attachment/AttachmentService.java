package com.cattlefarm.admin.attachment;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import com.cattlefarm.admin.scope.DataScopeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AttachmentService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf", "xlsx", "xls", "csv", "docx");
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final DataScopeService scope;
    private final Path root;

    public AttachmentService(JdbcTemplate jdbc, AuthService auth, DataScopeService scope,
                             @Value("${attachment.storage.root:D:/Develop/cattle-system/data/attachments}") String root) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.scope = scope;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Transactional
    public AttachmentItem upload(String businessType, String businessId, MultipartFile file, String key) {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        String path = "/api/v1/attachments";
        Long old = replay(farm, user, key, path);
        if (old != null) return find(old);
        String type = normalizeBusinessType(businessType);
        long business = parseBusinessId(businessId);
        validateBusiness(farm, type, business);
        assertBusinessScope(farm,type,business);
        if (file == null || file.isEmpty()) throw new DataConflictException("附件内容不能为空");
        if (file.getSize() > MAX_BYTES) throw new DataConflictException("附件不能超过 10 MB");
        String original = safeOriginalName(file.getOriginalFilename());
        String extension = extension(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new DataConflictException("不支持该附件类型");

        long id = IdWorker.getId();
        Path directory = root.resolve(Long.toString(farm)).normalize();
        Path target = directory.resolve(id + "." + extension).normalize();
        if (!target.startsWith(directory)) throw new DataConflictException("附件路径无效");
        try {
            Files.createDirectories(directory);
            file.transferTo(target);
            idem(farm, user, key, path, id);
            jdbc.update("INSERT INTO attachment(attachment_id,farm_id,business_type,business_id,file_name,file_url,file_type,file_size,uploaded_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    id, farm, type, business, original, target.toString(), file.getContentType(), file.getSize(), user);
            audit(farm, user, "ATTACHMENT_UPLOADED", id, "上传附件：" + original);
            return find(id);
        } catch (IOException | RuntimeException exception) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new DataConflictException("附件保存失败");
        }
    }

    public AttachmentItem find(long id) {
        long farm = auth.currentFarmId();
        try {
            AttachmentItem item=jdbc.queryForObject("SELECT * FROM attachment WHERE farm_id=? AND attachment_id=?", (rs, row) -> new AttachmentItem(
                    Long.toString(rs.getLong("attachment_id")), rs.getString("business_type"), Long.toString(rs.getLong("business_id")),
                    rs.getString("file_name"), rs.getString("file_type"), rs.getLong("file_size"),
                    Long.toString(rs.getLong("uploaded_by")), rs.getTimestamp("uploaded_at").toLocalDateTime()), farm, id);assertBusinessScope(farm,item.businessType(),Long.parseLong(item.businessId()));return item;
        } catch (EmptyResultDataAccessException exception) {
            throw new DataConflictException("附件不存在");
        }
    }

    public List<AttachmentItem> list(String businessType, String businessId) {
        long farm = auth.currentFarmId();
        String type = normalizeBusinessType(businessType);
        long business = parseBusinessId(businessId);
        validateBusiness(farm, type, business);
        assertBusinessScope(farm, type, business);
        return jdbc.query("SELECT * FROM attachment WHERE farm_id=? AND business_type=? AND business_id=? ORDER BY uploaded_at DESC",
                (rs, row) -> new AttachmentItem(Long.toString(rs.getLong("attachment_id")), rs.getString("business_type"),
                        Long.toString(rs.getLong("business_id")), rs.getString("file_name"), rs.getString("file_type"),
                        rs.getLong("file_size"), Long.toString(rs.getLong("uploaded_by")),
                        rs.getTimestamp("uploaded_at").toLocalDateTime()), farm, type, business);
    }

    public Resource content(long id) {
        long farm = auth.currentFarmId();
        find(id);
        String stored;
        try { stored = jdbc.queryForObject("SELECT file_url FROM attachment WHERE farm_id=? AND attachment_id=?", String.class, farm, id); }
        catch (EmptyResultDataAccessException exception) { throw new DataConflictException("附件不存在"); }
        Path file = Path.of(stored).toAbsolutePath().normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new DataConflictException("附件文件不存在或已清理");
        return new FileSystemResource(file);
    }

    @Transactional
    public void delete(long id, String key) {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        String path = "/api/v1/attachments/" + id;
        if (replay(farm, user, key, path) != null) return;
        Map<String, Object> row;
        try { row = jdbc.queryForMap("SELECT * FROM attachment WHERE farm_id=? AND attachment_id=?", farm, id); }
        catch (EmptyResultDataAccessException exception) { throw new DataConflictException("附件不存在"); }
        long uploader = ((Number) row.get("uploaded_by")).longValue();
        if (uploader != user && !StpUtil.hasRole("ADMIN")) throw new DataConflictException("只能删除本人上传的附件");
        if (!"TEMP".equals(row.get("business_type")) || ((Number) row.get("business_id")).longValue() != 0)
            throw new DataConflictException("附件已被业务引用，不能删除");
        idem(farm, user, key, path, id);
        jdbc.update("DELETE FROM attachment WHERE farm_id=? AND attachment_id=?", farm, id);
        audit(farm, user, "ATTACHMENT_DELETED", id, "删除未引用附件");
        try { Files.deleteIfExists(Path.of(String.valueOf(row.get("file_url"))).toAbsolutePath().normalize()); }
        catch (IOException exception) { throw new DataConflictException("附件文件删除失败"); }
    }

    private void validateBusiness(long farm, String type, long id) {
        if ("TEMP".equals(type)) { if (id != 0) throw new DataConflictException("临时附件业务编号必须为 0"); return; }
        String[] target = switch (type) {
            case "CATTLE" -> new String[]{"cattle", "cattle_id"};
            case "HEALTH_CASE" -> new String[]{"health_case", "case_id"};
            case "TREATMENT" -> new String[]{"treatment_record", "treatment_id"};
            case "VACCINATION_EXECUTION" -> new String[]{"vaccination_execution", "execution_id"};
            case "BREEDING" -> new String[]{"breeding_record", "breeding_id"};
            case "PREGNANCY_CHECK" -> new String[]{"pregnancy_check", "check_id"};
            case "CALVING" -> new String[]{"calving_record", "calving_id"};
            case "TASK" -> new String[]{"task", "task_id"};
            case "MIXING_ORDER" -> new String[]{"mixing_order", "mixing_order_id"};
            default -> throw new DataConflictException("不支持该附件业务类型");
        };
        Number count = jdbc.queryForObject("SELECT COUNT(*) FROM " + target[0] + " WHERE farm_id=? AND " + target[1] + "=?", Number.class, farm, id);
        if (count == null || count.longValue() == 0) throw new DataConflictException("附件关联业务不存在或不属于当前牛场");
    }

    private void assertBusinessScope(long farm,String type,long id){
        if(scope.unrestricted()||"TEMP".equals(type))return;
        switch(type){
            case "CATTLE"->scope.assertCattle(id);
            case "TASK"->scope.assertTask(id);
            case "MIXING_ORDER"->scope.assertMixingOrder(id);
            case "HEALTH_CASE"->scope.assertCattle(cattleId(farm,"health_case","case_id",id));
            case "TREATMENT"->scope.assertCattle(cattleId(farm,"treatment_record","treatment_id",id));
            case "BREEDING"->scope.assertCattle(cattleId(farm,"breeding_record","breeding_id",id));
            case "PREGNANCY_CHECK"->scope.assertCattle(cattleId(farm,"pregnancy_check","check_id",id));
            case "CALVING"->{Long cattle=jdbc.queryForObject("SELECT dam_cattle_id FROM calving_record WHERE farm_id=? AND calving_id=?",Long.class,farm,id);scope.assertCattle(cattle);}
            case "VACCINATION_EXECUTION"->{List<Long>cattle=jdbc.queryForList("SELECT cattle_id FROM vaccination_execution_cattle WHERE farm_id=? AND execution_id=?",Long.class,farm,id);for(Long cattleId:cattle)scope.assertCattle(cattleId);}
            default->throw new DataConflictException("不支持该附件业务类型");
        }
    }
    private long cattleId(long farm,String table,String pk,long id){Long cattle=jdbc.queryForObject("SELECT cattle_id FROM "+table+" WHERE farm_id=? AND "+pk+"=?",Long.class,farm,id);return cattle;}

    private String normalizeBusinessType(String value) { if (!StringUtils.hasText(value)) throw new DataConflictException("业务类型不能为空"); return value.trim().toUpperCase(Locale.ROOT); }
    private long parseBusinessId(String value) { try { return Long.parseLong(value); } catch (Exception e) { throw new DataConflictException("业务编号格式错误"); } }
    private String safeOriginalName(String value) { String name = StringUtils.hasText(value) ? Paths.get(value).getFileName().toString() : "attachment"; return name.length() > 255 ? name.substring(name.length() - 255) : name; }
    private String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private void audit(long farm,long user,String action,long id,String reason){jdbc.update("INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason) VALUES(?,?,?,'ATTACHMENT',?,'ATTACHMENT',?,?)",IdWorker.getId(),farm,user,action,id,reason);}
    private void idem(long farm,long user,String key,String path,long id){jdbc.update("INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at) VALUES(?,?,?,?,?,?)",farm,user,key,path,id,LocalDateTime.now().plusDays(1));}
    private Long replay(long farm,long user,String key,String path){try{return jdbc.queryForObject("SELECT business_id FROM idempotency_record WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path=? AND expires_at>NOW()",Long.class,farm,user,key,path);}catch(EmptyResultDataAccessException e){return null;}}
}
