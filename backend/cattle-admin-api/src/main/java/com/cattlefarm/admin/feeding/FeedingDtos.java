package com.cattlefarm.admin.feeding;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.*;import java.util.List;
public final class FeedingDtos{
 private FeedingDtos(){}
 public record Ingredient(String ingredientId,String ingredientName,String ingredientType,BigDecimal dryMatterPct,BigDecimal tdnPct,BigDecimal crudeProteinPct,BigDecimal starchPct,BigDecimal energyValue,BigDecimal gainEnergyValue,BigDecimal ndfPct,BigDecimal rdpPct,BigDecimal unitPrice,String status,String remark,int version){}
 public record CreateIngredient(@NotBlank String ingredientName,@NotBlank String ingredientType,@DecimalMin("0")@DecimalMax("100")BigDecimal dryMatterPct,@DecimalMin("0")@DecimalMax("200")BigDecimal tdnPct,@DecimalMin("0")@DecimalMax("100")BigDecimal crudeProteinPct,@DecimalMin("0")@DecimalMax("100")BigDecimal starchPct,BigDecimal energyValue,BigDecimal gainEnergyValue,@DecimalMin("0")@DecimalMax("100")BigDecimal ndfPct,@DecimalMin("0")@DecimalMax("100")BigDecimal rdpPct,@DecimalMin("0")BigDecimal unitPrice,String remark){}
 public record FormulaItem(@NotBlank String ingredientId,@DecimalMin("0.0001")BigDecimal ratioPct,@NotNull @DecimalMin("0.001")BigDecimal dailyAmountKg){}
 public record CreateFormula(@NotBlank String formulaName,@NotBlank @Pattern(regexp="HERD|STAGE|CUSTOM")String targetType,String targetObjectId,@NotNull @DecimalMin("0.01")BigDecimal dailyIntakeKg,String remark,@NotEmpty@Valid List<FormulaItem>items){}
 public record UpdateIngredient(@NotBlank String ingredientName,@NotBlank String ingredientType,@DecimalMin("0")@DecimalMax("100")BigDecimal dryMatterPct,@DecimalMin("0")@DecimalMax("200")BigDecimal tdnPct,@DecimalMin("0")@DecimalMax("100")BigDecimal crudeProteinPct,@DecimalMin("0")@DecimalMax("100")BigDecimal starchPct,BigDecimal energyValue,BigDecimal gainEnergyValue,@DecimalMin("0")@DecimalMax("100")BigDecimal ndfPct,@DecimalMin("0")@DecimalMax("100")BigDecimal rdpPct,@DecimalMin("0")BigDecimal unitPrice,@NotBlank@Pattern(regexp="ENABLED|DISABLED")String status,String remark,@NotNull Integer version){}
 public record Formula(String formulaId,String formulaName,int versionNo,String targetType,String targetObjectId,BigDecimal dailyIntakeKg,String sourceFile,String status,BigDecimal dryMatterKg,BigDecimal crudeProteinPct,BigDecimal ndfPct,BigDecimal dailyCost,List<FormulaLine>items,int rowVersion){public Formula(String formulaId,String formulaName,int versionNo,String targetType,String targetObjectId,BigDecimal dailyIntakeKg,String sourceFile,String status,BigDecimal dryMatterKg,BigDecimal crudeProteinPct,BigDecimal ndfPct,BigDecimal dailyCost,List<FormulaLine>items){this(formulaId,formulaName,versionNo,targetType,targetObjectId,dailyIntakeKg,sourceFile,status,dryMatterKg,crudeProteinPct,ndfPct,dailyCost,items,0);}}
 public record UpdateFormula(@NotBlank String formulaName,@NotBlank@Pattern(regexp="HERD|STAGE|CUSTOM")String targetType,String targetObjectId,@NotNull@DecimalMin("0.01")BigDecimal dailyIntakeKg,String remark,@NotEmpty@Valid List<FormulaItem>items,@NotNull Integer version){}
 public record FormulaLine(String ingredientId,String ingredientName,BigDecimal ratioPct,BigDecimal dailyAmountKg,BigDecimal unitPrice){}
 public record RecommendFormula(@Pattern(regexp="LARGE|MEDIUM") String bodySize,
                                @NotNull @DecimalMin("80") @DecimalMax("1200") BigDecimal currentWeightKg,
                                @NotNull @DecimalMin("100") @DecimalMax("1500") BigDecimal targetWeightKg,
                                @Min(30) @Max(730) int feedingDays,
                                @DecimalMin("35") @DecimalMax("80") BigDecimal roughageDryMatterPct,
                                @DecimalMin("0") @DecimalMax("35") BigDecimal proteinFeedDryMatterPct,
                                @NotEmpty List<@NotBlank String> ingredientIds){}
 public record RecommendationLine(String ingredientId,String ingredientName,String ingredientType,BigDecimal ratioPct,BigDecimal dailyAmountKg){}
 public record NutritionStandardTarget(String bodySize,BigDecimal referenceWeightKg,BigDecimal referenceDailyGainKg,
                                       BigDecimal dryMatterIntakeKg,BigDecimal tdnPct,BigDecimal crudeProteinPct,
                                       BigDecimal rdpPct,BigDecimal starchPct,BigDecimal ndfPct,
                                       BigDecimal maintenanceNetEnergy,BigDecimal gainNetEnergy){}
 public record FormulaRecommendation(BigDecimal averageDailyGainKg,BigDecimal dryMatterTargetKg,BigDecimal dailyIntakeKg,
                                     BigDecimal estimatedCrudeProteinPct,BigDecimal crudeProteinTargetMinPct,BigDecimal crudeProteinTargetMaxPct,
                                     BigDecimal estimatedNdfPct,BigDecimal ndfTargetMinPct,BigDecimal ndfTargetMaxPct,
                                     BigDecimal estimatedTdnPct,BigDecimal estimatedStarchPct,BigDecimal estimatedRdpPct,
                                     BigDecimal estimatedMaintenanceNetEnergy,BigDecimal estimatedGainNetEnergy,
                                     BigDecimal roughageDryMatterPct,BigDecimal proteinFeedDryMatterPct,NutritionStandardTarget standardTarget,
                                     BigDecimal estimatedDailyCost,BigDecimal pricedCostCoveragePct,List<String>missingPriceIngredients,
                                     List<RecommendationLine>items,List<String>warnings){}
 public record MicronutrientRequest(@NotBlank @Pattern(regexp="GROWING|PREGNANT|LACTATING") String productionStage,
                                    @NotNull @DecimalMin("0.1") @DecimalMax("40") BigDecimal dryMatterIntakeKg,
                                    @Min(1) @Max(100000) int cattleCount){}
 public record MicronutrientLine(String category,String nutrientName,String concentrationUnit,BigDecimal targetMin,BigDecimal targetMax,
                                 String intakeUnit,BigDecimal dailyMinPerHead,BigDecimal dailyMaxPerHead,
                                 BigDecimal herdDailyMin,BigDecimal herdDailyMax,String maximumTolerableConcentration,String deficiencySymptoms){}
 public record MicronutrientRecommendation(String productionStage,BigDecimal dryMatterIntakeKg,int cattleCount,
                                            List<MicronutrientLine>items,List<String>warnings){}
 public record BreedingNutritionRequest(@NotBlank @Pattern(regexp="REPLACEMENT_GROWTH|MAINTENANCE|LATE_PREGNANCY|LACTATION") String productionStage,
                                        @NotNull @DecimalMin("50") @DecimalMax("1000") BigDecimal weightKg,
                                        @DecimalMin("0") @DecimalMax("30") BigDecimal milkKgPerDay,
                                        @Min(1) @Max(100000) int cattleCount){}
 public record BreedingNutrients(BigDecimal dryMatterIntakeKg,BigDecimal crudeProteinG,BigDecimal tdnKg,
                                 BigDecimal digestibleEnergyMcal,BigDecimal metabolizableEnergyMcal,
                                 BigDecimal calciumG,BigDecimal phosphorusG,BigDecimal vitaminAThousandIu){}
 public record BreedingNutritionRecommendation(String productionStage,BigDecimal referenceWeightKg,int cattleCount,
                                                BreedingNutrients perHeadDaily,BreedingNutrients herdDaily,
                                                BigDecimal crudeProteinPct,BigDecimal tdnPct,BigDecimal calciumPct,BigDecimal phosphorusPct,
                                                List<String>warnings){}
 public record CreateOrder(@NotBlank String formulaId,String targetHerdId,String assigneeId,@Min(1)int cattleCount,@NotNull LocalDate feedDate){}
 public record Order(String orderId,String formulaId,String formulaName,String herdName,int cattleCount,LocalDate feedDate,String status,BigDecimal totalKg,BigDecimal totalCost,int version,List<OrderLine>items){}
 public record OrderLine(String ingredientId,String ingredientName,BigDecimal plannedAmountKg,BigDecimal adjustedAmountKg,BigDecimal unitPrice){}
 public record Action(@NotBlank String reason,@NotNull Integer version){}
 public record ActualItem(@NotBlank String ingredientId,@NotNull @DecimalMin("0")BigDecimal actualAmountKg){}
 public record Execute(@NotNull LocalDateTime executionTime,String deviationNote,@NotNull Integer version,@NotEmpty@Valid List<ActualItem>items){}
 public record ExecutionItem(String executionId,String orderId,LocalDateTime executionTime,String executorId,String executorName,String actualSummary,String deviationNote){}
}
