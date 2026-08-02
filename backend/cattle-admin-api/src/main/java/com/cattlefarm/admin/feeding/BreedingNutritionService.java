package com.cattlefarm.admin.feeding;

import com.cattlefarm.admin.common.DataConflictException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class BreedingNutritionService {
    private final NutritionStandardService standards;
    public BreedingNutritionService(NutritionStandardService standards){this.standards=standards;}

    public FeedingDtos.BreedingNutritionRecommendation recommend(FeedingDtos.BreedingNutritionRequest request){
        FeedingDtos.BreedingNutrients perHead;
        BigDecimal referenceWeight;
        List<String> warnings=new ArrayList<>();
        if("REPLACEMENT_GROWTH".equals(request.productionStage())){
            NutritionStandardService.ReplacementStandard row=standards.nearestReplacement(request.weightKg());
            referenceWeight=row.weightKg();
            perHead=new FeedingDtos.BreedingNutrients(row.dryMatterIntakeKg(),
                    row.dryMatterIntakeKg().multiply(row.crudeProteinPct()).multiply(BigDecimal.TEN),
                    row.dryMatterIntakeKg().multiply(row.tdnPct()).divide(BigDecimal.valueOf(100)),
                    row.dryMatterIntakeKg().multiply(row.digestibleEnergyMcalPerKg()),
                    row.dryMatterIntakeKg().multiply(row.metabolizableEnergyMcalPerKg()),
                    row.dryMatterIntakeKg().multiply(row.calciumPct()).multiply(BigDecimal.TEN),
                    row.dryMatterIntakeKg().multiply(row.phosphorusPct()).multiply(BigDecimal.TEN),
                    row.dryMatterIntakeKg().multiply(row.vitaminAThousandIuPerKg()));
        }else{
            NutritionStandardService.MaintenanceStandard base=standards.nearestMaintenance(request.weightKg());
            referenceWeight=base.weightKg();
            perHead=new FeedingDtos.BreedingNutrients(base.dryMatterIntakeKg(),base.crudeProteinG(),base.tdnKg(),base.digestibleEnergyMcal(),base.metabolizableEnergyMcal(),base.calciumG(),base.phosphorusG(),base.vitaminAThousandIu());
            if("LATE_PREGNANCY".equals(request.productionStage())) perHead=add(perHead,standards.latePregnancyIncrement(),BigDecimal.ONE);
            if("LACTATION".equals(request.productionStage())){
                BigDecimal milk=request.milkKgPerDay()==null?BigDecimal.ZERO:request.milkKgPerDay();
                if(milk.signum()<=0)throw new DataConflictException("哺乳期必须填写日产奶量");
                perHead=add(perHead,standards.lactationIncrementPerMilkKg(),milk);
                warnings.add("工作簿泌乳增量按每产 1 kg 奶增加 0.5 kg 干物质计算");
            }
        }
        perHead=round(perHead);
        FeedingDtos.BreedingNutrients herd=scale(perHead,BigDecimal.valueOf(request.cattleCount()));
        BigDecimal dmi=perHead.dryMatterIntakeKg();
        BigDecimal cpPct=perHead.crudeProteinG().divide(dmi.multiply(BigDecimal.TEN),2,RoundingMode.HALF_UP);
        BigDecimal tdnPct=perHead.tdnKg().multiply(BigDecimal.valueOf(100)).divide(dmi,2,RoundingMode.HALF_UP);
        BigDecimal calciumPct=perHead.calciumG().divide(dmi.multiply(BigDecimal.TEN),2,RoundingMode.HALF_UP);
        BigDecimal phosphorusPct=perHead.phosphorusG().divide(dmi.multiply(BigDecimal.TEN),2,RoundingMode.HALF_UP);
        warnings.add("采用工作簿中最接近体重档位，实际使用前应结合体况、胎次、产奶量和原料化验值复核");
        return new FeedingDtos.BreedingNutritionRecommendation(request.productionStage(),referenceWeight,request.cattleCount(),perHead,herd,cpPct,tdnPct,calciumPct,phosphorusPct,warnings);
    }
    private static FeedingDtos.BreedingNutrients add(FeedingDtos.BreedingNutrients base,NutritionStandardService.NutrientIncrement increment,BigDecimal factor){return new FeedingDtos.BreedingNutrients(base.dryMatterIntakeKg().add(increment.dryMatterIntakeKg().multiply(factor)),base.crudeProteinG().add(increment.crudeProteinG().multiply(factor)),base.tdnKg().add(increment.tdnKg().multiply(factor)),base.digestibleEnergyMcal().add(increment.digestibleEnergyMcal().multiply(factor)),base.metabolizableEnergyMcal().add(increment.metabolizableEnergyMcal().multiply(factor)),base.calciumG().add(increment.calciumG().multiply(factor)),base.phosphorusG().add(increment.phosphorusG().multiply(factor)),base.vitaminAThousandIu());}
    private static FeedingDtos.BreedingNutrients scale(FeedingDtos.BreedingNutrients value,BigDecimal factor){return new FeedingDtos.BreedingNutrients(m(value.dryMatterIntakeKg(),factor),m(value.crudeProteinG(),factor),m(value.tdnKg(),factor),m(value.digestibleEnergyMcal(),factor),m(value.metabolizableEnergyMcal(),factor),m(value.calciumG(),factor),m(value.phosphorusG(),factor),m(value.vitaminAThousandIu(),factor));}
    private static BigDecimal m(BigDecimal value,BigDecimal factor){return value.multiply(factor).setScale(2,RoundingMode.HALF_UP);}
    private static FeedingDtos.BreedingNutrients round(FeedingDtos.BreedingNutrients value){return scale(value,BigDecimal.ONE);}
}
