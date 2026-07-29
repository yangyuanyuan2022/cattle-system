package com.cattlefarm.admin.report;

import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.common.DataConflictException;
import com.cattlefarm.admin.scope.DataScopeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final DataScopeService scope;

    public ReportService(JdbcTemplate jdbc, AuthService auth, DataScopeService scope) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.scope = scope;
    }

    /** Full-farm overview is intentionally only exposed to administrators and farm managers. */
    public ReportDtos.Overview overview(LocalDate start, LocalDate end) {
        DateRange range = dates(start, end);
        long farm = auth.currentFarmId();
        List<Long> cattle = scope.unrestricted() ? null : scope.accessibleCattleIds();
        return buildOverview(farm, range, cattle, scope.unrestricted() ? null : scope.accessibleTaskIds());
    }

    public ReportDtos.Inventory inventory(LocalDate start, LocalDate end) {
        DateRange range = dates(start, end);
        long farm = auth.currentFarmId();
        ReportDtos.Overview report = buildInventory(farm, range, visibleCattle());
        return new ReportDtos.Inventory(report.startDate(), report.endDate(), report.inventory(),
                report.lifecycleStages(), report.herds(), report.movements());
    }

    public ReportDtos.Section breeding(LocalDate start, LocalDate end) {
        DateRange range = dates(start, end);
        long farm = auth.currentFarmId();
        List<Long> cattle = visibleCattle();
        return new ReportDtos.Section(range.start(), range.end(), List.of(
                metric("BREEDING", "配种", countCattle("breeding_record", "cattle_id", "is_void=0 AND DATE(breeding_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "次"),
                metric("PREGNANT", "妊检阳性", countCattle("pregnancy_check", "cattle_id", "is_void=0 AND check_result='POSITIVE' AND DATE(check_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "次"),
                metric("CALVING", "产犊", countCattle("calving_record", "dam_cattle_id", "is_void=0 AND DATE(calving_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "次"),
                metric("ALIVE_CALF", "成活犊牛", sumCattle("calving_record", "dam_cattle_id", "alive_count", "is_void=0 AND DATE(calving_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "头")
        ));
    }

    public ReportDtos.Section health(LocalDate start, LocalDate end) {
        DateRange range = dates(start, end);
        long farm = auth.currentFarmId();
        List<Long> cattle = visibleCattle();
        return new ReportDtos.Section(range.start(), range.end(), List.of(
                metric("CASE", "健康病例", countCattle("health_case", "cattle_id", "is_void=0 AND DATE(discover_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "例"),
                metric("OPEN_CASE", "未结病例", countCattle("health_case", "cattle_id", "is_void=0 AND case_status<>'CLOSED'", farm, cattle), "例"),
                metric("TREATMENT", "诊疗", countCattle("treatment_record", "cattle_id", "is_void=0 AND DATE(treatment_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "次"),
                metric("VACCINATED", "防疫牛只", vaccinated(farm, cattle, range), "头")
        ));
    }

    public ReportDtos.Section tasks(LocalDate start, LocalDate end) {
        DateRange range = dates(start, end);
        long farm = auth.currentFarmId();
        List<Long> tasks = scope.unrestricted() ? null : scope.accessibleTaskIds();
        long total = countTasks(farm, tasks, "plan_date BETWEEN ? AND ?", range.start(), range.end());
        long done = countTasks(farm, tasks, "status='DONE' AND plan_date BETWEEN ? AND ?", range.start(), range.end());
        long overdue = countTasks(farm, tasks, "status='OVERDUE'");
        BigDecimal rate = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(done * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        return new ReportDtos.Section(range.start(), range.end(), List.of(
                metric("TOTAL", "计划任务", total, "项"), metric("DONE", "已完成", done, "项"),
                metric("OVERDUE", "当前逾期", overdue, "项"), new ReportDtos.Metric("RATE", "完成率", rate, "%")
        ));
    }

    private ReportDtos.Overview buildOverview(long farm, DateRange range, List<Long> cattle, List<Long> tasks) {
        ReportDtos.Overview inventory = buildInventory(farm, range, cattle);
        ReportDtos.Section breeding = breeding(range.start(), range.end());
        ReportDtos.Section health = health(range.start(), range.end());
        ReportDtos.Section task = tasks(range.start(), range.end());
        return new ReportDtos.Overview(range.start(), range.end(), inventory.inventory(), inventory.lifecycleStages(), inventory.herds(), inventory.movements(), breeding.metrics(), health.metrics(), feeding(farm, range), task.metrics());
    }

    private ReportDtos.Overview buildInventory(long farm, DateRange range, List<Long> cattle) {
        long total = countCattle("cattle", "cattle_id", "presence_status='IN_FIELD'", farm, cattle);
        long female = countCattle("cattle", "cattle_id", "presence_status='IN_FIELD' AND sex='FEMALE'", farm, cattle);
        long male = countCattle("cattle", "cattle_id", "presence_status='IN_FIELD' AND sex='MALE'", farm, cattle);
        long alert = countCattle("cattle", "cattle_id", "presence_status='IN_FIELD' AND health_status<>'NORMAL'", farm, cattle);
        List<ReportDtos.Metric> inventory = List.of(metric("TOTAL", "在场牛只", total, "头"), metric("FEMALE", "母牛", female, "头"), metric("MALE", "公牛", male, "头"), metric("HEALTH_ALERT", "健康异常", alert, "头"));
        List<ReportDtos.Breakdown> stages = breakdownCattle("SELECT lifecycle_stage code,lifecycle_stage label,COUNT(*) value FROM cattle WHERE farm_id=? AND presence_status='IN_FIELD'", " GROUP BY lifecycle_stage ORDER BY value DESC", farm, cattle);
        List<ReportDtos.Breakdown> herds = breakdownCattle("SELECT COALESCE(CAST(h.herd_id AS CHAR),'UNASSIGNED') code,COALESCE(h.herd_name,'未分群') label,COUNT(*) value FROM cattle c LEFT JOIN herd h ON h.herd_id=c.herd_id AND h.farm_id=c.farm_id WHERE c.farm_id=? AND c.presence_status='IN_FIELD'", " GROUP BY h.herd_id,h.herd_name ORDER BY value DESC", farm, cattle);
        List<ReportDtos.Metric> movements = List.of(
                metric("ENTRY", "入场", countCattle("cattle", "cattle_id", "entry_date BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "头"),
                metric("EXIT", "离场", countExit(farm, cattle, range), "头"),
                metric("TRANSFER", "转群", countTransfer(farm, cattle, range), "次"),
                metric("CALF", "新生犊牛", sumCattle("calving_record", "dam_cattle_id", "alive_count", "is_void=0 AND DATE(calving_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()), "头")
        );
        return new ReportDtos.Overview(range.start(), range.end(), inventory, stages, herds, movements, List.of(), List.of(), List.of(), List.of());
    }

    private List<ReportDtos.Metric> feeding(long farm, DateRange range) {
        BigDecimal feedKg = decimal("SELECT COALESCE(SUM(COALESCE(i.adjusted_amount_kg,i.planned_amount_kg)),0) FROM mixing_order o JOIN mixing_order_item i ON i.mixing_order_id=o.mixing_order_id AND i.farm_id=o.farm_id WHERE o.farm_id=? AND o.status='EXECUTED' AND o.feed_date BETWEEN ? AND ?", farm, range.start(), range.end());
        BigDecimal feedCost = decimal("SELECT COALESCE(SUM(COALESCE(i.adjusted_amount_kg,i.planned_amount_kg)*g.unit_price),0) FROM mixing_order o JOIN mixing_order_item i ON i.mixing_order_id=o.mixing_order_id AND i.farm_id=o.farm_id JOIN feed_ingredient g ON g.ingredient_id=i.ingredient_id AND g.farm_id=i.farm_id WHERE o.farm_id=? AND o.status='EXECUTED' AND o.feed_date BETWEEN ? AND ?", farm, range.start(), range.end());
        long executed = count("SELECT COUNT(*) FROM mixing_order WHERE farm_id=? AND status='EXECUTED' AND feed_date BETWEEN ? AND ?", farm, range.start(), range.end());
        return List.of(metric("ORDERS", "已执行配料单", executed, "单"), new ReportDtos.Metric("FEED_KG", "实际配料量", feedKg.setScale(1, RoundingMode.HALF_UP), "kg"), new ReportDtos.Metric("FEED_COST", "配料成本", feedCost.setScale(2, RoundingMode.HALF_UP), "元"), new ReportDtos.Metric("AVG_COST", "单均成本", executed == 0 ? BigDecimal.ZERO : feedCost.divide(BigDecimal.valueOf(executed), 2, RoundingMode.HALF_UP), "元/单"));
    }

    private List<Long> visibleCattle() { return scope.unrestricted() ? null : scope.accessibleCattleIds(); }
    private long vaccinated(long farm, List<Long> cattle, DateRange range) {
        if (cattle != null && cattle.isEmpty()) return 0;
        StringBuilder sql = new StringBuilder("SELECT COUNT(DISTINCT vc.cattle_id) FROM vaccination_execution_cattle vc JOIN vaccination_execution v ON v.execution_id=vc.execution_id AND v.farm_id=vc.farm_id WHERE vc.farm_id=? AND v.is_void=0 AND DATE(v.execution_date) BETWEEN ? AND ?");
        List<Object> args = new ArrayList<>(List.of(farm, range.start(), range.end()));
        appendIn(sql, args, cattle, "vc.cattle_id");
        return count(sql.toString(), args.toArray());
    }
    private long countExit(long farm, List<Long> cattle, DateRange range) { return countCattle("cattle_exit", "cattle_id", "restored_at IS NULL AND exit_date BETWEEN ? AND ?", farm, cattle, range.start(), range.end()); }
    private long countTransfer(long farm, List<Long> cattle, DateRange range) { return countCattle("transfer_record", "cattle_id", "DATE(transfer_date) BETWEEN ? AND ?", farm, cattle, range.start(), range.end()); }
    private long countCattle(String table, String cattleColumn, String condition, long farm, List<Long> cattle, Object... tail) { if (cattle != null && cattle.isEmpty()) return 0; StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" WHERE farm_id=? AND ").append(condition); List<Object> args = new ArrayList<>(); args.add(farm); for (Object item : tail) args.add(item); appendIn(sql, args, cattle, cattleColumn); return count(sql.toString(), args.toArray()); }
    private long sumCattle(String table, String cattleColumn, String valueColumn, String condition, long farm, List<Long> cattle, Object... tail) { if (cattle != null && cattle.isEmpty()) return 0; StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(").append(valueColumn).append("),0) FROM ").append(table).append(" WHERE farm_id=? AND ").append(condition); List<Object> args = new ArrayList<>(); args.add(farm); for (Object item : tail) args.add(item); appendIn(sql, args, cattle, cattleColumn); return count(sql.toString(), args.toArray()); }
    private List<ReportDtos.Breakdown> breakdownCattle(String prefix, String suffix, long farm, List<Long> cattle) { if (cattle != null && cattle.isEmpty()) return List.of(); StringBuilder sql = new StringBuilder(prefix); List<Object> args = new ArrayList<>(); args.add(farm); appendIn(sql, args, cattle, "cattle_id"); sql.append(suffix); return jdbc.query(sql.toString(), (r, n) -> new ReportDtos.Breakdown(r.getString("code"), r.getString("label"), r.getLong("value")), args.toArray()); }
    private long countTasks(long farm, List<Long> taskIds, String condition, Object... tail) { if (taskIds != null && taskIds.isEmpty()) return 0; StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM task WHERE farm_id=? AND ").append(condition); List<Object> args = new ArrayList<>(); args.add(farm); for (Object item : tail) args.add(item); appendIn(sql, args, taskIds, "task_id"); return count(sql.toString(), args.toArray()); }
    private void appendIn(StringBuilder sql, List<Object> args, List<Long> values, String column) { if (values == null) return; sql.append(" AND ").append(column).append(" IN (").append("?,".repeat(values.size())); sql.setLength(sql.length() - 1); sql.append(')'); args.addAll(values); }
    private DateRange dates(LocalDate start, LocalDate end) { LocalDate resolvedStart = start == null ? LocalDate.now().minusDays(29) : start; LocalDate resolvedEnd = end == null ? LocalDate.now() : end; if (resolvedEnd.isBefore(resolvedStart)) throw new DataConflictException("结束日期不能早于开始日期"); if (resolvedStart.plusYears(2).isBefore(resolvedEnd)) throw new DataConflictException("单次报表查询区间不能超过两年"); return new DateRange(resolvedStart, resolvedEnd); }
    private List<ReportDtos.Breakdown> breakdown(String sql, Object... args) { return jdbc.query(sql, (r, n) -> new ReportDtos.Breakdown(r.getString("code"), r.getString("label"), r.getLong("value")), args); }
    private long count(String sql, Object... args) { Number number = jdbc.queryForObject(sql, Number.class, args); return number == null ? 0 : number.longValue(); }
    private BigDecimal decimal(String sql, Object... args) { BigDecimal number = jdbc.queryForObject(sql, BigDecimal.class, args); return number == null ? BigDecimal.ZERO : number; }
    private ReportDtos.Metric metric(String code, String label, long value, String unit) { return new ReportDtos.Metric(code, label, BigDecimal.valueOf(value), unit); }
    private record DateRange(LocalDate start, LocalDate end) { }
}
