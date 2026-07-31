package com.cattlefarm.admin.feeding;

import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import org.apache.commons.math3.exception.TooManyIterationsException;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ConcentrateOptimizationService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public ConcentrateOptimizationService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    public FeedingDtos.ConcentrateOptimizeResult optimize(FeedingDtos.ConcentrateOptimizeRequest request) {
        List<Long> ids;
        try { ids = request.ingredientIds().stream().distinct().map(Long::valueOf).toList(); }
        catch (NumberFormatException exception) { throw new DataConflictException("原料编号格式错误"); }
        if (ids.size() < 2) throw new DataConflictException("自动配比至少选择两种原料");
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> params = new ArrayList<>(); params.add(auth.currentFarmId()); params.addAll(ids);
        List<Ingredient> items = jdbc.query("SELECT ingredient_id,ingredient_name,ingredient_type,dry_matter_pct,crude_protein_pct,metabolizable_energy_value,tdn_pct,ndf_pct,crude_fat_pct,starch_pct,unit_price FROM feed_ingredient WHERE farm_id=? AND status='ENABLED' AND ingredient_id IN (" + placeholders + ")",
                (r, n) -> new Ingredient(r.getLong(1), r.getString(2), r.getString(3), r.getBigDecimal(4), r.getBigDecimal(5), r.getBigDecimal(6), r.getBigDecimal(7), r.getBigDecimal(8), r.getBigDecimal(9), r.getBigDecimal(10), r.getBigDecimal(11)), params.toArray());
        if (items.size() != ids.size()) throw new DataConflictException("所选原料包含已停用或不存在的数据");
        Map<Long, Ingredient> byId = new HashMap<>(); items.forEach(item -> byId.put(item.id(), item));
        List<Ingredient> ordered = ids.stream().map(byId::get).toList();
        if (ordered.stream().noneMatch(item -> "ENERGY".equals(item.type()))) throw new DataConflictException("自动精料必须至少选择一种能量饲料");
        if (ordered.stream().noneMatch(item -> "PROTEIN".equals(item.type()))) throw new DataConflictException("自动精料必须至少选择一种蛋白饲料");
        List<String> missing = new ArrayList<>();
        for (Ingredient item : ordered) {
            if (item.dm() == null || item.dm().signum() <= 0) missing.add(item.name() + "缺少干物质");
            if (item.price() == null || item.price().signum() <= 0) missing.add(item.name() + "缺少单价");
            if (!isSupplement(item) && (item.cp() == null || item.ndf() == null || item.fat() == null || item.starch() == null || energy(item) == null)) missing.add(item.name() + "缺少自动优化所需营养数据");
        }
        if (!missing.isEmpty()) throw new DataConflictException(String.join("；", missing));

        int count = ordered.size();
        double[] objective = new double[count];
        List<LinearConstraint> constraints = new ArrayList<>();
        double[] sum = new double[count]; Arrays.fill(sum, 1d);
        constraints.add(new LinearConstraint(sum, Relationship.EQ, 1d));
        constraints.add(nutrientConstraint(ordered, request.targetCrudeProteinPct(), Ingredient::cp, Relationship.GEQ));
        constraints.add(nutrientConstraint(ordered, request.minimumMetabolizableEnergy(), ConcentrateOptimizationService::energy, Relationship.GEQ));
        constraints.add(nutrientConstraint(ordered, request.maximumNdfPct(), Ingredient::ndf, Relationship.LEQ));
        constraints.add(nutrientConstraint(ordered, request.maximumCrudeFatPct(), Ingredient::fat, Relationship.LEQ));
        constraints.add(nutrientConstraint(ordered, request.minimumStarchPct(), Ingredient::starch, Relationship.GEQ));
        constraints.add(nutrientConstraint(ordered, request.maximumStarchPct(), Ingredient::starch, Relationship.LEQ));
        for (int i = 0; i < count; i++) {
            Ingredient item = ordered.get(i); objective[i] = item.price().doubleValue();
            double[] bound = new double[count]; bound[i] = 1d;
            constraints.add(new LinearConstraint(bound, Relationship.GEQ, isSupplement(item) ? 0.001d : 0.02d));
            constraints.add(new LinearConstraint(bound, Relationship.LEQ, isSupplement(item) ? 0.05d : 0.80d));
        }
        PointValuePair solution;
        try {
            solution = new SimplexSolver(1e-8, 10).optimize(new MaxIter(2000), new LinearObjectiveFunction(objective, 0), new LinearConstraintSet(constraints), GoalType.MINIMIZE, new NonNegativeConstraint(true));
        } catch (NoFeasibleSolutionException exception) {
            throw new DataConflictException("当前原料无法同时满足营养目标，请降低粗蛋白/能量/淀粉下限，或提高 NDF/脂肪上限后重试");
        } catch (UnboundedSolutionException | TooManyIterationsException exception) {
            throw new DataConflictException("自动配比求解失败，请检查原料营养数据和目标范围");
        }
        double[] points = solution.getPoint();
        List<FeedingDtos.RecommendationRatio> ratios = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        int largestIndex = 0;
        for (int i = 1; i < points.length; i++) if (points[i] > points[largestIndex]) largestIndex = i;
        BigDecimal[] rounded = new BigDecimal[points.length];
        for (int i = 0; i < points.length; i++) { rounded[i] = BigDecimal.valueOf(points[i] * 100).setScale(2, RoundingMode.HALF_UP); allocated = allocated.add(rounded[i]); }
        rounded[largestIndex] = rounded[largestIndex].add(HUNDRED.subtract(allocated));
        for (int i = 0; i < ordered.size(); i++) ratios.add(new FeedingDtos.RecommendationRatio(Long.toString(ordered.get(i).id()), rounded[i]));
        return new FeedingDtos.ConcentrateOptimizeResult(ratios, BigDecimal.valueOf(solution.getValue()).setScale(2, RoundingMode.HALF_UP), List.of("结果按营养约束与原料价格优化，生产前仍需结合化验值复核"));
    }

    private static LinearConstraint nutrientConstraint(List<Ingredient> items, BigDecimal target, java.util.function.Function<Ingredient, BigDecimal> field, Relationship relationship) {
        double[] coefficients = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Ingredient item = items.get(i); BigDecimal value = field.apply(item);
            if (value == null && isSupplement(item)) value = BigDecimal.ZERO;
            coefficients[i] = item.dm().divide(HUNDRED).multiply(value.subtract(target)).doubleValue();
        }
        return new LinearConstraint(coefficients, relationship, 0d);
    }
    private static boolean isSupplement(Ingredient item) { return "MINERAL".equals(item.type()) || "ADDITIVE".equals(item.type()); }
    private static BigDecimal energy(Ingredient item) { return item.me() != null ? item.me() : item.tdn() == null ? null : item.tdn().multiply(new BigDecimal("0.03615")); }
    private record Ingredient(long id,String name,String type,BigDecimal dm,BigDecimal cp,BigDecimal me,BigDecimal tdn,BigDecimal ndf,BigDecimal fat,BigDecimal starch,BigDecimal price) {}
}
