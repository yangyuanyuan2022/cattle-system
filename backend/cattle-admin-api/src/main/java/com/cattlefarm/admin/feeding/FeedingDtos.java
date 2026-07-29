package com.cattlefarm.admin.feeding;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.*;import java.util.List;
public final class FeedingDtos{
 private FeedingDtos(){}
 public record Ingredient(String ingredientId,String ingredientName,String ingredientType,BigDecimal dryMatterPct,BigDecimal crudeProteinPct,BigDecimal energyValue,BigDecimal ndfPct,BigDecimal unitPrice,String status,String remark,int version){}
 public record CreateIngredient(@NotBlank String ingredientName,@NotBlank String ingredientType,@DecimalMin("0")@DecimalMax("100")BigDecimal dryMatterPct,@DecimalMin("0")@DecimalMax("100")BigDecimal crudeProteinPct,BigDecimal energyValue,@DecimalMin("0")@DecimalMax("100")BigDecimal ndfPct,@DecimalMin("0")BigDecimal unitPrice,String remark){}
 public record FormulaItem(@NotBlank String ingredientId,@DecimalMin("0.0001")BigDecimal ratioPct,@NotNull @DecimalMin("0.001")BigDecimal dailyAmountKg){}
 public record CreateFormula(@NotBlank String formulaName,@NotBlank @Pattern(regexp="HERD|STAGE|CUSTOM")String targetType,String targetObjectId,@NotNull @DecimalMin("0.01")BigDecimal dailyIntakeKg,String remark,@NotEmpty@Valid List<FormulaItem>items){}
 public record UpdateIngredient(@NotBlank String ingredientName,@NotBlank String ingredientType,@DecimalMin("0")@DecimalMax("100")BigDecimal dryMatterPct,@DecimalMin("0")@DecimalMax("100")BigDecimal crudeProteinPct,BigDecimal energyValue,@DecimalMin("0")@DecimalMax("100")BigDecimal ndfPct,@DecimalMin("0")BigDecimal unitPrice,@NotBlank@Pattern(regexp="ENABLED|DISABLED")String status,String remark,@NotNull Integer version){}
 public record Formula(String formulaId,String formulaName,int versionNo,String targetType,String targetObjectId,BigDecimal dailyIntakeKg,String sourceFile,String status,BigDecimal dryMatterKg,BigDecimal crudeProteinPct,BigDecimal ndfPct,BigDecimal dailyCost,List<FormulaLine>items,int rowVersion){public Formula(String formulaId,String formulaName,int versionNo,String targetType,String targetObjectId,BigDecimal dailyIntakeKg,String sourceFile,String status,BigDecimal dryMatterKg,BigDecimal crudeProteinPct,BigDecimal ndfPct,BigDecimal dailyCost,List<FormulaLine>items){this(formulaId,formulaName,versionNo,targetType,targetObjectId,dailyIntakeKg,sourceFile,status,dryMatterKg,crudeProteinPct,ndfPct,dailyCost,items,0);}}
 public record UpdateFormula(@NotBlank String formulaName,@NotBlank@Pattern(regexp="HERD|STAGE|CUSTOM")String targetType,String targetObjectId,@NotNull@DecimalMin("0.01")BigDecimal dailyIntakeKg,String remark,@NotEmpty@Valid List<FormulaItem>items,@NotNull Integer version){}
 public record FormulaLine(String ingredientId,String ingredientName,BigDecimal ratioPct,BigDecimal dailyAmountKg,BigDecimal unitPrice){}
 public record CreateOrder(@NotBlank String formulaId,String targetHerdId,String assigneeId,@Min(1)int cattleCount,@NotNull LocalDate feedDate){}
 public record Order(String orderId,String formulaId,String formulaName,String herdName,int cattleCount,LocalDate feedDate,String status,BigDecimal totalKg,BigDecimal totalCost,int version,List<OrderLine>items){}
 public record OrderLine(String ingredientId,String ingredientName,BigDecimal plannedAmountKg,BigDecimal adjustedAmountKg,BigDecimal unitPrice){}
 public record Action(@NotBlank String reason,@NotNull Integer version){}
 public record ActualItem(@NotBlank String ingredientId,@NotNull @DecimalMin("0")BigDecimal actualAmountKg){}
 public record Execute(@NotNull LocalDateTime executionTime,String deviationNote,@NotNull Integer version,@NotEmpty@Valid List<ActualItem>items){}
 public record ExecutionItem(String executionId,String orderId,LocalDateTime executionTime,String executorId,String executorName,String actualSummary,String deviationNote){}
}
