package com.cattlefarm.admin.feeding;

import org.springframework.stereotype.Service;
import com.cattlefarm.admin.auth.service.AuthService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MicronutrientRecommendationService {
    private static final List<Standard> STANDARDS = List.of(
            standard("常量元素", "钙", "%", range("0.3"), null, null, "0.02 × DMI（原表表达）", "缺乏会阻碍骨骼发育并导致佝偻病"),
            standard("常量元素", "磷", "%", range("0.1", "0.17"), null, null, "0.007 × DMI（原表表达）", "生长速率和饲料转化率下降，繁殖功能受损"),
            standard("常量元素", "镁", "%", range("0.1"), range("0.12"), range("0.2"), "0.4%", "可能发生草痉挛、采食量下降和肌肉抽搐"),
            standard("常量元素", "钾", "%", range("0.6"), range("0.6"), range("0.7"), "2%", "干物质采食量和增重速率下降、肌肉无力"),
            standard("常量元素", "钠", "%", range("0.06", "0.08"), range("0.06", "0.08"), range("0.1"), null, "异食癖及采食量、生长速率下降"),
            standard("常量元素", "硫", "%", range("0.15"), range("0.15"), range("0.15"), "0.3-0.5%", "重度缺乏会导致厌食、掉重和体弱"),
            standard("微量元素", "铜", "mg/kg", range("10"), range("10"), range("10"), "40 mg/kg", "被毛粗乱、贫血、繁殖障碍和运动失调"),
            standard("微量元素", "铁", "mg/kg", range("50"), range("50"), range("50"), "500 mg/kg", "营养性贫血、食欲减退和体重减轻"),
            standard("微量元素", "锌", "mg/kg", range("30"), range("30"), range("30"), "500 mg/kg", "发育不良、皮肤病变和繁殖障碍"),
            standard("微量元素", "锰", "mg/kg", range("20"), range("40"), range("40"), "1000 mg/kg", "关节异常、运动失调和繁殖能力下降"),
            standard("微量元素", "碘", "mg/kg", range("0.5"), range("0.5"), range("0.5"), "50 mg/kg", "甲状腺肿、发育不良和繁殖障碍"),
            standard("微量元素", "硒", "mg/kg", range("0.1"), range("0.1"), range("0.1"), "5 mg/kg", "白肌症、步行困难、胎盘停滞和繁殖障碍"),
            standard("微量元素", "钴", "mg/kg", range("0.15"), range("0.15"), range("0.15"), "25 mg/kg", "食欲减退、贫血、体重减轻和繁殖障碍"),
            standard("维生素", "维生素 A", "IU/kg", range("2200"), range("3900"), range("2800"), null, "夜盲、繁殖异常，严重时流产或死产"),
            standard("维生素", "维生素 D", "IU/kg", range("275"), range("275"), range("275"), null, "犊牛佝偻症、成牛软骨症和生长受阻"),
            standard("维生素", "维生素 E", "IU/kg", range("35"), range("35"), range("35"), null, "肌肉萎缩、白肌病和抽搐")
    );
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public MicronutrientRecommendationService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    public FeedingDtos.MicronutrientRecommendation recommend(FeedingDtos.MicronutrientRequest request) {
        List<FeedingDtos.MicronutrientLine> lines = new ArrayList<>();
        Map<String, BigDecimal> actual = actualSupply(request);
        for (Standard standard : STANDARDS) {
            Range target = standard.forStage(request.productionStage());
            if (target == null) continue;
            BigDecimal dailyMin = daily(request.dryMatterIntakeKg(), target.min(), standard.unit());
            BigDecimal dailyMax = daily(request.dryMatterIntakeKg(), target.max(), standard.unit());
            BigDecimal count = BigDecimal.valueOf(request.cattleCount());
            BigDecimal actualDaily = actual.get(standard.name());
            BigDecimal gapMin = actualDaily == null ? null : dailyMin.subtract(actualDaily).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal gapMax = actualDaily == null ? null : dailyMax.subtract(actualDaily).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            String status = actualDaily == null ? "UNAVAILABLE" : actualDaily.compareTo(dailyMin) < 0 ? "DEFICIENT" : actualDaily.compareTo(dailyMax) > 0 ? "ABOVE_TARGET" : "OK";
            lines.add(new FeedingDtos.MicronutrientLine(standard.category(), standard.name(), standard.unit(), target.min(), target.max(),
                    "%".equals(standard.unit()) ? "g/头/天" : standard.unit().replace("/kg", "/头/天"), dailyMin, dailyMax,
                    dailyMin.multiply(count).setScale(2, RoundingMode.HALF_UP), dailyMax.multiply(count).setScale(2, RoundingMode.HALF_UP),
                    actualDaily, gapMin, gapMax, status, standard.maximum(), standard.symptoms()));
        }
        List<String> warnings = new ArrayList<>();
        if (!"GROWING".equals(request.productionStage())) warnings.add("原工作簿未提供该阶段的钙、磷目标值，结果中不推测补填");
        if (request.formulaId() == null || request.formulaId().isBlank()) warnings.add("未选择已保存日粮，实际供给和缺口暂不计算");
        else warnings.add("实际供给按所选已保存日粮的原料快照计算；缺口仅作预混料估算依据，投料前仍需核对化验值");
        warnings.add("硒、铜等微量元素过量可能中毒，未录入预混料成分与检测值前不得直接按目标量投料");
        return new FeedingDtos.MicronutrientRecommendation(request.productionStage(), request.dryMatterIntakeKg(), request.cattleCount(), lines, warnings);
    }

    private Map<String, BigDecimal> actualSupply(FeedingDtos.MicronutrientRequest request) {
        if (request.formulaId() == null || request.formulaId().isBlank() || jdbc == null || auth == null) return Map.of();
        long formulaId;
        try { formulaId = Long.parseLong(request.formulaId()); } catch (NumberFormatException e) { return Map.of(); }
        long farm = auth.currentFarmId();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT x.daily_amount_kg,CASE WHEN x.snapshot_at IS NOT NULL THEN x.dry_matter_pct_snapshot ELSE i.dry_matter_pct END AS dm,CASE WHEN x.snapshot_at IS NOT NULL THEN x.calcium_pct_snapshot ELSE i.calcium_pct END AS calcium,CASE WHEN x.snapshot_at IS NOT NULL THEN x.phosphorus_pct_snapshot ELSE i.phosphorus_pct END AS phosphorus FROM ration_formula_item x LEFT JOIN feed_ingredient i ON i.ingredient_id=x.ingredient_id AND i.farm_id=x.farm_id WHERE x.farm_id=? AND x.formula_id=?", farm, formulaId);
        if (rows.isEmpty()) return Map.of();
        BigDecimal calcium = BigDecimal.ZERO, phosphorus = BigDecimal.ZERO;
        boolean calciumComplete = true, phosphorusComplete = true, hasPositiveAmount = false;
        for (Map<String,Object> row : rows) {
            BigDecimal amount = decimal(row.get("daily_amount_kg"));
            BigDecimal dm = decimal(row.get("dm"));
            if (amount == null || amount.signum() <= 0) continue;
            hasPositiveAmount = true;
            if (dm == null) {
                calciumComplete = false;
                phosphorusComplete = false;
                continue;
            }
            BigDecimal dryMatter = amount.multiply(dm).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            BigDecimal ca = decimal(row.get("calcium"));
            BigDecimal ph = decimal(row.get("phosphorus"));
            if (ca == null) calciumComplete = false;
            else calcium = calcium.add(dryMatter.multiply(ca).multiply(BigDecimal.TEN));
            if (ph == null) phosphorusComplete = false;
            else phosphorus = phosphorus.add(dryMatter.multiply(ph).multiply(BigDecimal.TEN));
        }
        Map<String, BigDecimal> result = new java.util.HashMap<>();
        if (hasPositiveAmount && calciumComplete) result.put("钙", calcium.setScale(2, RoundingMode.HALF_UP));
        if (hasPositiveAmount && phosphorusComplete) result.put("磷", phosphorus.setScale(2, RoundingMode.HALF_UP));
        return result;
    }

    private static BigDecimal decimal(Object value) { return value instanceof Number n ? new BigDecimal(n.toString()) : null; }

    private static BigDecimal daily(BigDecimal intake, BigDecimal concentration, String unit) {
        BigDecimal multiplier = "%".equals(unit) ? BigDecimal.TEN : BigDecimal.ONE;
        return intake.multiply(concentration).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
    private static Standard standard(String category,String name,String unit,Range growing,Range pregnant,Range lactating,String maximum,String symptoms){return new Standard(category,name,unit,growing,pregnant,lactating,maximum,symptoms);}
    private static Range range(String value){return range(value,value);}
    private static Range range(String min,String max){return new Range(new BigDecimal(min),new BigDecimal(max));}
    private record Range(BigDecimal min,BigDecimal max){}
    private record Standard(String category,String name,String unit,Range growing,Range pregnant,Range lactating,String maximum,String symptoms){
        Range forStage(String stage){return switch(stage){case "PREGNANT"->pregnant;case "LACTATING"->lactating;default->growing;};}
    }
}
