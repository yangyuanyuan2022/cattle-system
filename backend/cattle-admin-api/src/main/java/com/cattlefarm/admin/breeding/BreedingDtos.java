package com.cattlefarm.admin.breeding;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.*;import java.util.List;
public final class BreedingDtos{private BreedingDtos(){}
 public record Estrus(@NotBlank String cattleId,@NotNull LocalDateTime estrusTime,@Size(max=500)String symptoms,@Size(max=255)String suggestion){}
 public record Breed(@NotBlank String cattleId,@NotNull LocalDateTime breedingDate,@NotBlank @Pattern(regexp="AI|NATURAL")String breedingMethod,@NotBlank @Size(max=100)String semenOrBull,@Min(1)Integer breedingTimes,@Size(max=500)String remark){}
 public record Pregnancy(@NotBlank String cattleId,String breedingId,@NotNull LocalDateTime checkDate,@NotBlank @Pattern(regexp="POSITIVE|NEGATIVE|RECHECK")String checkResult,LocalDate expectedCalvingDate,@Size(max=500)String remark){}
 public record Calf(@NotBlank String earTagNo,@NotBlank @Pattern(regexp="MALE|FEMALE")String sex,@DecimalMin("0.01")BigDecimal birthWeight,@NotBlank @Pattern(regexp="ALIVE|WEAK|DEAD")String survivalStatus){}
 public record Calving(@NotBlank String damCattleId,String pregnancyCheckId,@NotNull LocalDateTime calvingDate,@Pattern(regexp="NORMAL|ASSISTED|DIFFICULT|UNKNOWN")String difficultyLevel,@Min(1)int calfCount,@Min(0)int aliveCount,@Size(max=100)String damCondition,@Size(max=500)String remark,@Valid List<Calf> calves){}
 public record EventItem(String businessId,String cattleId,String earTagNo,LocalDateTime eventDate,String eventType,String summary,String breedingStatus){}
 public record Result(String businessId,String cattleId,String breedingStatus,List<String> calfCattleIds){}
 public record HeatItem(String heatId,String cattleId,String earTagNo,LocalDateTime heatTime,String symptoms,String suggestion,String recorderName,int version){}
 public record InseminationItem(String inseminationId,String cattleId,String earTagNo,LocalDateTime inseminationDate,String method,String semenOrBull,int breedingTimes,String operatorName,String remark,int version){}
 public record PregnancyItem(String checkId,String cattleId,String earTagNo,String inseminationId,LocalDateTime checkDate,String result,LocalDate expectedCalvingDate,String checkerName,String remark,int version){}
 public record CalvingItem(String calvingId,String damCattleId,String damEarTagNo,String pregnancyCheckId,LocalDateTime calvingDate,String difficultyLevel,int calfCount,int aliveCount,String damCondition,String operatorName,String remark,List<String> calfCattleIds,int version){}
 public record DueCow(String cattleId,String earTagNo,String cattleName,String barnName,String herdName,String checkId,LocalDate expectedCalvingDate,long daysUntilCalving,String breedingStatus){}
}
