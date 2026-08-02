package com.cattlefarm.admin.feeding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class NutritionStandardService {
    private final List<GrowthStandard> growth;
    private final List<ReplacementStandard> replacementHeifer;
    private final List<MaintenanceStandard> adultMaintenance;
    private final NutrientIncrement latePregnancyIncrement;
    private final NutrientIncrement lactationIncrementPerMilkKg;

    public NutritionStandardService(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("feeding/nutrition-standards.json").getInputStream()) {
            Catalog catalog = mapper.readValue(input, Catalog.class);
            growth = catalog.growth();
            replacementHeifer = catalog.replacementHeifer();
            adultMaintenance = catalog.adultMaintenance();
            latePregnancyIncrement = catalog.latePregnancyIncrement();
            lactationIncrementPerMilkKg = catalog.lactationIncrementPerMilkKg();
        }
    }

    public ReplacementStandard nearestReplacement(BigDecimal weightKg) {
        return replacementHeifer.stream().min(Comparator.comparing(item -> item.weightKg().subtract(weightKg).abs())).orElseThrow();
    }
    public MaintenanceStandard nearestMaintenance(BigDecimal weightKg) {
        return adultMaintenance.stream().min(Comparator.comparing(item -> item.weightKg().subtract(weightKg).abs())).orElseThrow();
    }
    public NutrientIncrement latePregnancyIncrement(){return latePregnancyIncrement;}
    public NutrientIncrement lactationIncrementPerMilkKg(){return lactationIncrementPerMilkKg;}

    public GrowthStandard nearest(String bodySize, BigDecimal weightKg, BigDecimal averageDailyGainKg) {
        String normalized = bodySize == null ? "LARGE" : bodySize;
        return growth.stream().filter(item -> normalized.equals(item.bodySize()))
                .min(Comparator.comparing(item -> distance(item, weightKg, averageDailyGainKg)))
                .orElseThrow(() -> new IllegalStateException("营养标准数据缺失"));
    }

    private static BigDecimal distance(GrowthStandard item, BigDecimal weight, BigDecimal gain) {
        return item.weightKg().subtract(weight).abs().divide(BigDecimal.valueOf(50))
                .add(item.averageDailyGainKg().subtract(gain).abs().divide(new BigDecimal("0.25")));
    }

    private record Catalog(String sourceWorkbook,List<GrowthStandard> growth,List<ReplacementStandard> replacementHeifer,
                           List<MaintenanceStandard> adultMaintenance,NutrientIncrement latePregnancyIncrement,
                           NutrientIncrement lactationIncrementPerMilkKg,List<PeripartumStandard> peripartum) {}
    public record GrowthStandard(String bodySize,BigDecimal weightKg,BigDecimal averageDailyGainKg,
                                 BigDecimal crudeProteinRequiredKg,BigDecimal rdpRequiredG,
                                 BigDecimal maintenanceEnergyRequiredMcal,BigDecimal gainEnergyRequiredMcal,
                                 BigDecimal dryMatterIntakeKg,BigDecimal tdnPct,BigDecimal maintenanceNetEnergy,
                                 BigDecimal gainNetEnergy,BigDecimal crudeProteinPct,BigDecimal rdpPctOfCrudeProtein,
                                 BigDecimal starchPct,BigDecimal ndfPct) {}
    public record ReplacementStandard(BigDecimal weightKg,BigDecimal averageDailyGainKg,BigDecimal dryMatterIntakeKg,
                                      BigDecimal crudeProteinPct,BigDecimal tdnPct,BigDecimal digestibleEnergyMcalPerKg,
                                      BigDecimal metabolizableEnergyMcalPerKg,BigDecimal calciumPct,BigDecimal phosphorusPct,
                                      BigDecimal vitaminAThousandIuPerKg) {}
    public record MaintenanceStandard(BigDecimal weightKg,BigDecimal dryMatterIntakeKg,BigDecimal crudeProteinG,
                                      BigDecimal tdnKg,BigDecimal digestibleEnergyMcal,BigDecimal metabolizableEnergyMcal,
                                      BigDecimal calciumG,BigDecimal phosphorusG,BigDecimal vitaminAThousandIu) {}
    public record NutrientIncrement(BigDecimal dryMatterIntakeKg,BigDecimal crudeProteinG,BigDecimal tdnKg,
                                    BigDecimal digestibleEnergyMcal,BigDecimal metabolizableEnergyMcal,
                                    BigDecimal calciumG,BigDecimal phosphorusG) {}
    private record PeripartumStandard(String bodySize,BigDecimal crudeProteinPct,BigDecimal dryMatterPctOfBodyWeight,
                                     BigDecimal starchPct,BigDecimal minimumCrudeProteinG) {}
}
