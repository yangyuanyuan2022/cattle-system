package com.cattlefarm.admin.report;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.List;
public final class ReportDtos{
 private ReportDtos(){}
 public record Metric(String code,String label,BigDecimal value,String unit){}
 public record Breakdown(String code,String label,long value){}
 public record Overview(LocalDate startDate,LocalDate endDate,List<Metric>inventory,List<Breakdown>lifecycleStages,List<Breakdown>herds,List<Metric>movements,List<Metric>breeding,List<Metric>healthVaccination,List<Metric>feeding,List<Metric>tasks){}
 public record Inventory(LocalDate startDate,LocalDate endDate,List<Metric>metrics,List<Breakdown>lifecycleStages,List<Breakdown>herds,List<Metric>movements){}
 public record Section(LocalDate startDate,LocalDate endDate,List<Metric>metrics){}
}
