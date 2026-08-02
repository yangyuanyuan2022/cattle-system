package com.cattlefarm.admin.feeding;

import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class RationRecommendationService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<String> AUTOMATIC_TYPES = Set.of("ROUGHAGE", "ENERGY", "PROTEIN");
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final NutritionStandardService standards;

    public RationRecommendationService(JdbcTemplate jdbc, AuthService auth, NutritionStandardService standards) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.standards = standards;
    }

    public FeedingDtos.FormulaRecommendation recommend(FeedingDtos.RecommendFormula request) {
        if (request.targetWeightKg().compareTo(request.currentWeightKg()) <= 0) {
            throw new DataConflictException("目标体重必须大于当前体重");
        }
        BigDecimal adg = request.targetWeightKg().subtract(request.currentWeightKg())
                .divide(BigDecimal.valueOf(request.feedingDays()), 3, RoundingMode.HALF_UP);
        if (adg.compareTo(new BigDecimal("2.0")) > 0) {
            throw new DataConflictException("目标日增重超过 2.0 kg，请延长育肥周期或调整目标体重");
        }
        NutritionStandardService.GrowthStandard standard = standards.nearest(request.bodySize(), request.currentWeightKg(), adg);
        String productionStage = request.productionStage() == null ? "FINISHING" : request.productionStage();
        boolean finishing = "FINISHING".equals(productionStage);
        List<Long> ids;
        try { ids = request.ingredientIds().stream().distinct().map(Long::valueOf).toList(); }
        catch (NumberFormatException exception) { throw new DataConflictException("原料编号格式错误"); }
        if (ids.size() < 2) throw new DataConflictException("至少选择两种原料");
        long farm = auth.currentFarmId();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Ingredient> ingredients = jdbc.query(
                "SELECT ingredient_id,ingredient_name,ingredient_type,dry_matter_pct,tdn_pct,metabolizable_energy_value,crude_protein_pct,starch_pct,ndf_pct,pe_ndf_pct,adf_pct,ash_pct,crude_fat_pct,calcium_pct,phosphorus_pct,rdp_pct,energy_value,gain_energy_value,unit_price FROM feed_ingredient WHERE farm_id=? AND status='ENABLED' AND ingredient_id IN (" + placeholders + ")",
                (row, index) -> new Ingredient(row.getLong(1), row.getString(2), normalize(row.getString(3)), row.getBigDecimal(4), row.getBigDecimal(5), row.getBigDecimal(6), row.getBigDecimal(7), row.getBigDecimal(8), row.getBigDecimal(9), row.getBigDecimal(10), row.getBigDecimal(11), row.getBigDecimal(12), row.getBigDecimal(13), row.getBigDecimal(14), row.getBigDecimal(15), row.getBigDecimal(16), row.getBigDecimal(17), row.getBigDecimal(18), row.getBigDecimal(19)),
                params(farm, ids));
        if (ingredients.size() != ids.size()) throw new DataConflictException("所选原料包含已停用或不存在的数据");
        if (ingredients.stream().noneMatch(i -> "ROUGHAGE".equals(i.type()))) {
            throw new DataConflictException("配方必须至少选择一种粗饲料");
        }
        if (ingredients.stream().noneMatch(i -> "ENERGY".equals(i.type()))) {
            throw new DataConflictException("配方必须至少选择一种能量饲料");
        }
        boolean manualRatios = request.ingredientRatios() != null && !request.ingredientRatios().isEmpty();
        List<String> manualOnly = ingredients.stream().filter(i -> !AUTOMATIC_TYPES.contains(i.type())).map(Ingredient::name).toList();
        if (!manualRatios && !manualOnly.isEmpty()) {
            throw new DataConflictException(String.join("、", manualOnly) + " 属于矿物质、添加剂、水或待分类原料，不参与基础日粮自动分配，请生成后手动添加并由营养师复核");
        }

        List<String> warnings = new ArrayList<>();
        ingredients.forEach(i -> {
            if (i.dryMatter() == null || i.dryMatter().signum() <= 0) warnings.add(i.name() + "缺少有效干物质数据");
            if (i.crudeProtein() == null) warnings.add(i.name() + "缺少粗蛋白数据");
            if (i.ndf() == null) warnings.add(i.name() + "缺少 NDF 数据");
        });
        if (!warnings.isEmpty()) throw new DataConflictException(String.join("；", warnings) + "，请先完善原料档案");

        Map<Long, BigDecimal> requestedRatios = new LinkedHashMap<>();
        if (manualRatios) {
            try {
                request.ingredientRatios().forEach(item -> requestedRatios.merge(Long.valueOf(item.ingredientId()), item.dryMatterRatioPct(), BigDecimal::add));
            } catch (NumberFormatException exception) {
                throw new DataConflictException("原料比例中的编号格式错误");
            }
            if (!requestedRatios.keySet().equals(new HashSet<>(ids))) throw new DataConflictException("手动比例必须覆盖全部所选原料且不能包含其他原料");
            BigDecimal ratioTotal = requestedRatios.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (ratioTotal.subtract(HUNDRED).abs().compareTo(new BigDecimal("0.01")) > 0) throw new DataConflictException("手动配比合计必须等于 100%，当前为 " + ratioTotal.stripTrailingZeros().toPlainString() + "%");
        }

        BigDecimal roughageShare = request.roughageDryMatterPct() == null
                ? (finishing ? new BigDecimal("15") : new BigDecimal("55"))
                : request.roughageDryMatterPct();
        Map<String, List<Ingredient>> groups = new LinkedHashMap<>();
        ingredients.forEach(i -> groups.computeIfAbsent(i.type(), ignored -> new ArrayList<>()).add(i));
        BigDecimal cpTarget = standard.crudeProteinPct();
        BigDecimal proteinShare = request.proteinFeedDryMatterPct() == null
                ? proteinShareForTarget(groups, roughageShare, cpTarget)
                : request.proteinFeedDryMatterPct();
        if (manualRatios) {
            roughageShare = shareForType(ingredients, requestedRatios, "ROUGHAGE");
            proteinShare = shareForType(ingredients, requestedRatios, "PROTEIN");
        }
        if (!groups.containsKey("PROTEIN")) proteinShare = BigDecimal.ZERO;
        if (roughageShare.add(proteinShare).compareTo(new BigDecimal("95")) > 0) {
            throw new DataConflictException("粗饲料与蛋白料干物质占比合计不能超过 95%");
        }
        BigDecimal dryMatterTarget = standard.dryMatterIntakeKg().setScale(2, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> dryMatterAmounts = new LinkedHashMap<>();
        if (manualRatios) {
            requestedRatios.forEach((id, ratio) -> dryMatterAmounts.put(id, dryMatterTarget.multiply(ratio).divide(HUNDRED, 8, RoundingMode.HALF_UP)));
            warnings.add("当前采用人工设定的干物质比例，系统仅负责营养核算与风险提示");
        } else {
            allocateDryMatter(groups.get("ROUGHAGE"), dryMatterTarget, roughageShare, dryMatterAmounts);
            allocateDryMatter(groups.get("PROTEIN"), dryMatterTarget, proteinShare, dryMatterAmounts);
            allocateDryMatter(groups.get("ENERGY"), dryMatterTarget, HUNDRED.subtract(roughageShare).subtract(proteinShare), dryMatterAmounts);
        }
        Map<Long, BigDecimal> asFedAmounts = new LinkedHashMap<>();
        ingredients.forEach(i -> asFedAmounts.put(i.id(), dryMatterAmounts.getOrDefault(i.id(), BigDecimal.ZERO)
                .multiply(HUNDRED).divide(i.dryMatter(), 8, RoundingMode.HALF_UP)));
        BigDecimal dailyIntake = asFedAmounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
        List<FeedingDtos.RecommendationLine> lines = ingredients.stream().map(i -> {
            BigDecimal amount = asFedAmounts.getOrDefault(i.id(), BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);
            BigDecimal ratio = amount.multiply(HUNDRED).divide(dailyIntake, 2, RoundingMode.HALF_UP);
            return new FeedingDtos.RecommendationLine(Long.toString(i.id()), i.name(), i.type(), ratio, amount);
        }).filter(line -> line.ratioPct().signum() > 0).toList();
        BigDecimal cp = nutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::crudeProtein);
        BigDecimal ndf = nutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::ndf);
        BigDecimal tdn = nutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::tdn);
        BigDecimal starch = nutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::starch);
        BigDecimal rdp = rdpOfCrudeProtein(ingredients, dryMatterAmounts);
        BigDecimal metabolizableEnergy = nutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, item -> item.metabolizableEnergy() != null ? item.metabolizableEnergy() : derivedMetabolizableEnergy(item.tdn()));
        BigDecimal maintenanceEnergy = nutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::energyValue);
        BigDecimal gainEnergy = nutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::gainEnergyValue);
        BigDecimal peNdf = nullableNutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::peNdf);
        BigDecimal adf = nullableNutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::adf);
        BigDecimal ash = nullableNutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::ash);
        BigDecimal crudeFat = nullableNutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::crudeFat);
        BigDecimal calcium = nullableNutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::calcium);
        BigDecimal phosphorus = nullableNutrientOnDryMatter(ingredients, dryMatterAmounts, dryMatterTarget, Ingredient::phosphorus);
        BigDecimal cost = lines.stream().map(line -> line.dailyAmountKg().multiply(orZero(find(ingredients, Long.parseLong(line.ingredientId())).unitPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dryMatterUnitPrice = cost.divide(dryMatterTarget, 2, RoundingMode.HALF_UP);
        BigDecimal cpMin = cpTarget.subtract(new BigDecimal("0.75")).max(new BigDecimal("11.0"));
        BigDecimal cpMax = cpTarget.add(new BigDecimal("0.75")).min(new BigDecimal("16.0"));
        BigDecimal ndfMin = finishing ? new BigDecimal("12") : new BigDecimal("25");
        BigDecimal ndfMax = new BigDecimal("100");
        if (cp.compareTo(cpMin) < 0) warnings.add("粗蛋白低于目标范围，建议增加蛋白原料占比");
        if (cp.compareTo(cpMax) > 0) warnings.add("粗蛋白高于目标范围，可能增加饲料成本和氮排放");
        if (ndf.compareTo(ndfMin) < 0) warnings.add("NDF 低于安全范围，存在瘤胃酸中毒风险，请提高粗饲料比例");
        BigDecimal roughageMinimum = finishing ? new BigDecimal("10") : new BigDecimal("45");
        if (roughageShare.compareTo(roughageMinimum) < 0) warnings.add("粗料比例低于 GB/T 47364-2026 " + (finishing ? "肥育牛" : "生长牛") + "最低值 " + roughageMinimum + "%");
        if (tdn.subtract(standard.tdnPct()).abs().compareTo(new BigDecimal("3")) > 0) warnings.add("TDN 与工作簿目标相差超过 3 个百分点，请调整原料组合或精粗比");
        BigDecimal starchMin = finishing ? new BigDecimal("35") : new BigDecimal("15");
        BigDecimal starchMax = finishing ? new BigDecimal("50") : new BigDecimal("35");
        if (starch.compareTo(starchMin) < 0 || starch.compareTo(starchMax) > 0) warnings.add("淀粉不在 GB/T 47364-2026 " + (finishing ? "肥育牛" : "生长牛") + "建议范围 " + starchMin + "%-" + starchMax + "% 内");
        if (rdp.subtract(standard.rdpPctOfCrudeProtein()).abs().compareTo(new BigDecimal("5")) > 0) warnings.add("RDP 与工作簿目标相差超过 5 个百分点，请调整蛋白原料组合");
        if (maintenanceEnergy.subtract(standard.maintenanceNetEnergy()).abs().compareTo(new BigDecimal("0.15")) > 0) warnings.add("维持净能浓度偏离工作簿目标，请复核能量原料");
        if (gainEnergy.subtract(standard.gainNetEnergy()).abs().compareTo(new BigDecimal("0.15")) > 0) warnings.add("增重净能浓度偏离工作簿目标，实际日增重可能与计划不一致");
        BigDecimal adfMin = finishing ? new BigDecimal("5") : new BigDecimal("15");
        BigDecimal adfMax = finishing ? new BigDecimal("100") : new BigDecimal("25");
        if (adf != null && (adf.compareTo(adfMin) < 0 || adf.compareTo(adfMax) > 0)) warnings.add("ADF 不符合 GB/T 47364-2026 " + (finishing ? "肥育牛最低 5%" : "生长牛 15%-25%") + " 要求");
        BigDecimal fatMin = finishing ? new BigDecimal("4.5") : new BigDecimal("4.0");
        BigDecimal fatMax = finishing ? new BigDecimal("9.0") : new BigDecimal("6.0");
        if (crudeFat != null && (crudeFat.compareTo(fatMin) < 0 || crudeFat.compareTo(fatMax) > 0)) warnings.add("粗脂肪不在 GB/T 47364-2026 " + (finishing ? "肥育牛" : "生长牛") + "建议范围 " + fatMin + "%-" + fatMax + "% 内");
        List<String> missingExtendedMetrics = new ArrayList<>();
        if (peNdf == null) missingExtendedMetrics.add("PeNDF");
        if (adf == null) missingExtendedMetrics.add("ADF");
        if (ash == null) missingExtendedMetrics.add("粗灰分");
        if (crudeFat == null) missingExtendedMetrics.add("粗脂肪");
        if (calcium == null) missingExtendedMetrics.add("钙");
        if (phosphorus == null) missingExtendedMetrics.add("磷");
        if (!missingExtendedMetrics.isEmpty()) warnings.add("所选原料的" + String.join("、", missingExtendedMetrics) + "数据不完整，对应配方指标暂不出值，请补录化验数据");
        if (ingredients.stream().anyMatch(item -> item.metabolizableEnergy() == null && item.tdn() != null)) warnings.add("部分原料缺少实测代谢能，当前按 TDN 推算；录入化验值后将优先采用实测数据");
        List<String> missingPrices = ingredients.stream().filter(i -> i.unitPrice() == null || i.unitPrice().signum() <= 0).map(Ingredient::name).toList();
        BigDecimal pricedAmount = ingredients.stream().filter(i -> i.unitPrice() != null && i.unitPrice().signum() > 0)
                .map(i -> asFedAmounts.getOrDefault(i.id(), BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal priceCoverage = pricedAmount.multiply(HUNDRED).divide(dailyIntake, 1, RoundingMode.HALF_UP);
        if (!missingPrices.isEmpty()) warnings.add("部分原料未填写单价，当前成本仅覆盖日粮重量的 " + priceCoverage.stripTrailingZeros().toPlainString() + "%");
        FeedingDtos.NutritionStandardTarget target = new FeedingDtos.NutritionStandardTarget(
                standard.bodySize(), standard.weightKg(), standard.averageDailyGainKg(), standard.dryMatterIntakeKg(),
                standard.tdnPct(), standard.crudeProteinPct(), standard.rdpPctOfCrudeProtein(), starchMin, ndfMin,
                standard.maintenanceNetEnergy(), standard.gainNetEnergy(), standard.crudeProteinRequiredKg(),
                standard.maintenanceEnergyRequiredMcal(), standard.gainEnergyRequiredMcal(), productionStage,
                "GB/T 47364-2026 表11阈值；体重与日增重基础值来自葵花日粮计算（九版）");
        BigDecimal crudeProteinDailyKg = cp.multiply(dryMatterTarget).divide(HUNDRED, 3, RoundingMode.HALF_UP);
        BigDecimal metabolizableEnergyDaily = metabolizableEnergy.multiply(dryMatterTarget).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maintenanceEnergyDaily = maintenanceEnergy.multiply(dryMatterTarget).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gainEnergyDaily = gainEnergy.multiply(dryMatterTarget).setScale(2, RoundingMode.HALF_UP);
        warnings.add("本结果按肉牛育肥经验参数估算，启用前应结合原料化验值和当地营养师意见复核");
        return new FeedingDtos.FormulaRecommendation(adg, dryMatterTarget, dailyIntake, cp, cpMin, cpMax, ndf, ndfMin, ndfMax,
                tdn, starch, rdp, metabolizableEnergy, maintenanceEnergy, gainEnergy, peNdf, adf, ash, crudeFat, calcium, phosphorus, dryMatterUnitPrice,
                metabolizableEnergyDaily, maintenanceEnergyDaily, gainEnergyDaily, crudeProteinDailyKg,
                roughageShare.setScale(1, RoundingMode.HALF_UP), proteinShare.setScale(1, RoundingMode.HALF_UP), target,
                cost, priceCoverage, missingPrices, lines, warnings);
    }

    private static Object[] params(long farm, List<Long> ids) { List<Object> values = new ArrayList<>(); values.add(farm); values.addAll(ids); return values.toArray(); }
    private static BigDecimal shareForType(List<Ingredient> ingredients, Map<Long, BigDecimal> ratios, String type) { return ingredients.stream().filter(item -> type.equals(item.type())).map(item -> ratios.getOrDefault(item.id(), BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static void allocateDryMatter(List<Ingredient> values, BigDecimal target, BigDecimal share, Map<Long, BigDecimal> amounts) { if (values == null || values.isEmpty() || share.signum() == 0) return; BigDecimal each = target.multiply(share).divide(HUNDRED).divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP); values.forEach(i -> amounts.put(i.id(), each)); }
    private static BigDecimal proteinShareForTarget(Map<String, List<Ingredient>> groups, BigDecimal roughageShare, BigDecimal cpTarget) {
        List<Ingredient> proteins = groups.get("PROTEIN");
        if (proteins == null || proteins.isEmpty()) return BigDecimal.ZERO;
        BigDecimal roughageCp = average(groups.get("ROUGHAGE"), Ingredient::crudeProtein);
        BigDecimal energyCp = average(groups.get("ENERGY"), Ingredient::crudeProtein);
        BigDecimal proteinCp = average(proteins, Ingredient::crudeProtein);
        BigDecimal difference = proteinCp.subtract(energyCp);
        if (difference.abs().compareTo(new BigDecimal("0.01")) < 0) return BigDecimal.ZERO;
        BigDecimal concentrateShare = HUNDRED.subtract(roughageShare);
        BigDecimal numerator = cpTarget.multiply(HUNDRED).subtract(roughageShare.multiply(roughageCp)).subtract(concentrateShare.multiply(energyCp));
        BigDecimal maximum = concentrateShare.subtract(new BigDecimal("5")).min(new BigDecimal("35")).max(BigDecimal.ZERO);
        return numerator.divide(difference, 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO).min(maximum);
    }
    private static BigDecimal average(List<Ingredient> items, java.util.function.Function<Ingredient, BigDecimal> field) { if (items == null || items.isEmpty()) return BigDecimal.ZERO; return items.stream().map(i -> orZero(field.apply(i))).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(items.size()), 8, RoundingMode.HALF_UP); }
    private static BigDecimal nutrientOnDryMatter(List<Ingredient> items, Map<Long, BigDecimal> amounts, BigDecimal total, java.util.function.Function<Ingredient, BigDecimal> field) { return items.stream().map(i -> orZero(field.apply(i)).multiply(amounts.getOrDefault(i.id(), BigDecimal.ZERO))).reduce(BigDecimal.ZERO, BigDecimal::add).divide(total, 2, RoundingMode.HALF_UP); }
    private static BigDecimal nullableNutrientOnDryMatter(List<Ingredient> items, Map<Long, BigDecimal> amounts, BigDecimal total, java.util.function.Function<Ingredient, BigDecimal> field) { if(items.stream().anyMatch(item -> field.apply(item) == null)) return null; return nutrientOnDryMatter(items, amounts, total, field); }
    private static BigDecimal derivedMetabolizableEnergy(BigDecimal tdn) { return tdn == null ? BigDecimal.ZERO : tdn.multiply(new BigDecimal("0.03615")); }
    private static BigDecimal rdpOfCrudeProtein(List<Ingredient> items, Map<Long, BigDecimal> amounts) { BigDecimal protein = items.stream().map(i -> orZero(i.crudeProtein()).multiply(amounts.getOrDefault(i.id(), BigDecimal.ZERO))).reduce(BigDecimal.ZERO, BigDecimal::add); if (protein.signum() == 0) return BigDecimal.ZERO; BigDecimal degradable = items.stream().map(i -> orZero(i.rdp()).multiply(amounts.getOrDefault(i.id(), BigDecimal.ZERO))).reduce(BigDecimal.ZERO, BigDecimal::add); return degradable.multiply(HUNDRED).divide(protein, 2, RoundingMode.HALF_UP); }
    private static Ingredient find(List<Ingredient> items, long id) { return items.stream().filter(i -> i.id() == id).findFirst().orElseThrow(); }
    private static String normalize(String type) { return type == null ? "OTHER" : type.toUpperCase(Locale.ROOT); }
    private static BigDecimal orZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private record Ingredient(long id, String name, String type, BigDecimal dryMatter, BigDecimal tdn, BigDecimal metabolizableEnergy, BigDecimal crudeProtein, BigDecimal starch, BigDecimal ndf, BigDecimal peNdf, BigDecimal adf, BigDecimal ash, BigDecimal crudeFat, BigDecimal calcium, BigDecimal phosphorus, BigDecimal rdp, BigDecimal energyValue, BigDecimal gainEnergyValue, BigDecimal unitPrice) {}
}
