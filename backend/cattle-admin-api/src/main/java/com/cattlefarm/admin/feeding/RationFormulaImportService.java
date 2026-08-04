package com.cattlefarm.admin.feeding;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RationFormulaImportService {
    private static final String PATH = "/api/v1/feeding/ration-formulas/import";
    private static final Set<String> TARGET_TYPES = Set.of("HERD", "STAGE", "CUSTOM");
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final FeedingService feeding;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public RationFormulaImportService(JdbcTemplate jdbc, AuthService auth, FeedingService feeding) {
        this.jdbc = jdbc; this.auth = auth; this.feeding = feeding;
    }

    @Transactional
    public FeedingDtos.Formula importFormula(MultipartFile file, String formulaName, String targetType,
                                             String targetObjectId, String remark, String key) {
        long farm = auth.currentFarmId(), user = StpUtil.getLoginIdAsLong();
        Long replay = replay(farm, user, key);
        if (replay != null) return feeding.formulaDetail(replay);
        validateFile(file);
        String target = targetType == null ? "CUSTOM" : targetType.trim().toUpperCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(target)) throw new DataConflictException("目标类型必须为 HERD、STAGE 或 CUSTOM");
        Long targetId = parseTarget(targetObjectId);
        if ("HERD".equals(target)) {
            if (targetId == null) throw new DataConflictException("按牛群导入配方时必须指定 targetObjectId");
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM herd WHERE farm_id=? AND herd_id=?", Integer.class, farm, targetId);
            if (count == null || count == 0) throw new DataConflictException("目标牛群不存在");
        }
        ParsedFormula parsed = parse(file);
        String name = formulaName == null || formulaName.isBlank() ? baseName(file.getOriginalFilename()) + "-TMR" : formulaName.trim();
        if (name.length() > 100) throw new DataConflictException("配方名称不能超过 100 个字符");
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM ration_formula WHERE farm_id=? AND formula_name=?", Integer.class, farm, name);
        int version = next == null ? 1 : next;
        long formulaId = IdWorker.getId();
        idem(farm, user, key, formulaId);
        jdbc.update("INSERT INTO ration_formula(formula_id,farm_id,formula_name,version_no,target_type,target_object_id,daily_intake_kg,source_file,remark,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                formulaId, farm, name, version, target, targetId, parsed.total(), safeName(file.getOriginalFilename()), remark, user, user);
        List<FeedingDtos.FormulaItem> items = new ArrayList<>();
        for (ImportedLine line : parsed.lines()) {
            long ingredientId = findOrCreateIngredient(farm, user, line);
            BigDecimal ratio = line.amount().multiply(BigDecimal.valueOf(100)).divide(parsed.total(), 4, RoundingMode.HALF_UP);
            items.add(new FeedingDtos.FormulaItem(Long.toString(ingredientId), ratio, line.amount()));
        }
        feeding.replaceFormulaItems(formulaId, items);
        jdbc.update("INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason) VALUES(?,?,?,'FEEDING','FORMULA_IMPORTED','RATION_FORMULA',?,?)",
                IdWorker.getId(), farm, user, formulaId, "从葵花日粮计算表导入 " + parsed.lines().size() + " 种原料");
        return feeding.formulaDetail(formulaId);
    }

    private ParsedFormula parse(MultipartFile file) {
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheet("TMR");
            if (sheet == null) throw new DataConflictException("工作簿缺少 TMR 工作表");
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row header = sheet.getRow(1);
            if (header == null || !text(header.getCell(0), evaluator).contains("原料") || !text(header.getCell(10), evaluator).contains("饲喂量"))
                throw new DataConflictException("TMR 工作表结构不匹配：第 2 行必须包含原料和饲喂量列");
            List<ImportedLine> lines = new ArrayList<>(); Set<String> names = new HashSet<>();
            for (int index = 2; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index); if (row == null) continue;
                String name = text(row.getCell(0), evaluator).trim(); if (name.isBlank()) continue;
                BigDecimal amount = number(row.getCell(10), evaluator);
                if (amount == null || amount.signum() == 0) continue;
                if (amount.signum() < 0) throw new DataConflictException("TMR 第 " + (index + 1) + " 行饲喂量不能为负数");
                if (!names.add(name)) throw new DataConflictException("TMR 中原料重复：" + name);
                lines.add(new ImportedLine(name, amount.setScale(3, RoundingMode.HALF_UP), percentage(row.getCell(1), evaluator),
                        percentage(row.getCell(3), evaluator), number(row.getCell(6), evaluator), percentage(row.getCell(5), evaluator), number(row.getCell(9), evaluator)));
            }
            if (lines.isEmpty()) throw new DataConflictException("TMR 工作表没有填写任何正数饲喂量");
            BigDecimal total = lines.stream().map(ImportedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ParsedFormula(lines, total.setScale(2, RoundingMode.HALF_UP));
        } catch (DataConflictException e) { throw e; }
        catch (Exception e) { throw new DataConflictException("无法解析 Excel 文件，请确认文件未损坏且为葵花日粮计算表"); }
    }

    private long findOrCreateIngredient(long farm, long user, ImportedLine line) {
        try { return jdbc.queryForObject("SELECT ingredient_id FROM feed_ingredient WHERE farm_id=? AND ingredient_name=?", Long.class, farm, line.name()); }
        catch (EmptyResultDataAccessException ignored) {
            long id = IdWorker.getId();
            jdbc.update("INSERT INTO feed_ingredient(ingredient_id,farm_id,ingredient_name,ingredient_type,dry_matter_pct,crude_protein_pct,energy_value,ndf_pct,unit_price,remark,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, farm, line.name(), "IMPORTED", line.dryMatter(), line.crudeProtein(), line.energy(), line.ndf(), line.unitPrice(), "由葵花日粮计算表导入", user, user);
            jdbc.update("INSERT INTO operation_log(operation_log_id,farm_id,user_id,module_code,action_type,business_type,business_id,reason) VALUES(?,?,?,'FEEDING','INGREDIENT_IMPORTED','FEED_INGREDIENT',?,?)",
                    IdWorker.getId(), farm, user, id, "配方导入自动建立原料档案");
            return id;
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new DataConflictException("导入文件不能为空");
        String name = safeName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new DataConflictException("仅支持 .xlsx 导入文件");
        if (file.getSize() > 10L * 1024 * 1024) throw new DataConflictException("导入文件不能超过 10 MB");
    }
    private String text(Cell cell, FormulaEvaluator evaluator) { return cell == null ? "" : formatter.formatCellValue(cell, evaluator); }
    private BigDecimal number(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null; CellValue value = evaluator.evaluate(cell);
        if (value == null || value.getCellType() == CellType.BLANK || value.getCellType() == CellType.ERROR) return null;
        if (value.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(value.getNumberValue());
        String text = formatter.formatCellValue(cell, evaluator).trim().replace(",", "");
        try { return text.isBlank() ? null : new BigDecimal(text); } catch (NumberFormatException ignored) { return null; }
    }
    private BigDecimal percentage(Cell cell, FormulaEvaluator evaluator) {
        BigDecimal value = number(cell, evaluator); if (value == null || value.signum() < 0) return null;
        if (value.compareTo(BigDecimal.ONE) <= 0) value = value.multiply(BigDecimal.valueOf(100));
        return value.compareTo(BigDecimal.valueOf(100)) <= 0 ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }
    private Long parseTarget(String value) { if (value == null || value.isBlank()) return null; try { return Long.valueOf(value); } catch (NumberFormatException e) { throw new DataConflictException("targetObjectId 格式错误"); } }
    private Long replay(long farm,long user,String key){try{return jdbc.queryForObject("SELECT business_id FROM idempotency_record WHERE farm_id=? AND user_id=? AND idempotency_key=? AND request_path=? AND expires_at>NOW()",Long.class,farm,user,key,PATH);}catch(EmptyResultDataAccessException e){return null;}}
    private void idem(long farm,long user,String key,long id){jdbc.update("INSERT INTO idempotency_record(farm_id,user_id,idempotency_key,request_path,business_id,expires_at) VALUES(?,?,?,?,?,?)",farm,user,key,PATH,id,LocalDateTime.now().plusDays(1));}
    private String safeName(String name){if(name==null||name.isBlank())return "ration-formula.xlsx";String safe=name.replace('\\','/');safe=safe.substring(safe.lastIndexOf('/')+1);return safe.length()<=255?safe:safe.substring(safe.length()-255);}
    private String baseName(String name){String safe=safeName(name);int dot=safe.lastIndexOf('.');return dot>0?safe.substring(0,dot):safe;}
    private record ImportedLine(String name,BigDecimal amount,BigDecimal dryMatter,BigDecimal crudeProtein,BigDecimal energy,BigDecimal ndf,BigDecimal unitPrice){}
    private record ParsedFormula(List<ImportedLine> lines,BigDecimal total){}
}
