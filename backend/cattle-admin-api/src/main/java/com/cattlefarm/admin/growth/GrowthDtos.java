package com.cattlefarm.admin.growth;
import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.LocalDateTime;import java.util.List;
public final class GrowthDtos{private GrowthDtos(){}
 public record CreateWeight(@NotBlank String cattleId,@NotNull LocalDateTime measureDate,@NotNull @DecimalMin("0.01") BigDecimal weightKg,@Size(max=50) String measureMethod,@Size(max=500) String remark){}
 public record CreateBodyCondition(@NotBlank String cattleId,@NotNull LocalDateTime scoreDate,@NotNull @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal score,@Size(max=500) String remark){}
 public record WeightItem(String weightId,String cattleId,String earTagNo,String cattleName,LocalDateTime measureDate,BigDecimal weightKg,String measureMethod,BigDecimal changeKg,BigDecimal averageDailyGain,boolean abnormal,String warning,int version){}
 public record BodyConditionItem(String bodyConditionId,String cattleId,String earTagNo,String cattleName,LocalDateTime scoreDate,BigDecimal score,String remark,int version){}
 public record Trend(String cattleId,String earTagNo,List<WeightItem> weights,List<BodyConditionItem> bodyConditions){}
 public record HerdWeightPoint(LocalDateTime measureDate,BigDecimal averageWeightKg,long cattleCount){}
 public record HerdTrend(String herdId,String herdName,List<HerdWeightPoint> weights){}
}
