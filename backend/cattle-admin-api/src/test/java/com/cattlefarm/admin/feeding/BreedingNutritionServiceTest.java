package com.cattlefarm.admin.feeding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BreedingNutritionServiceTest {
    private final BreedingNutritionService service;

    BreedingNutritionServiceTest() throws Exception {
        service = new BreedingNutritionService(new NutritionStandardService(new ObjectMapper()));
    }

    @Test
    void latePregnancyAddsWorkbookIncrementToMaintenanceAndScalesHerd() {
        FeedingDtos.BreedingNutritionRecommendation result = service.recommend(
                new FeedingDtos.BreedingNutritionRequest("LATE_PREGNANCY", new BigDecimal("500"), null, 100));

        assertEquals(new BigDecimal("500"), result.referenceWeightKg());
        assertEquals(new BigDecimal("7.50"), result.perHeadDaily().dryMatterIntakeKg());
        assertEquals(new BigDecimal("727.00"), result.perHeadDaily().crudeProteinG());
        assertEquals(new BigDecimal("4.10"), result.perHeadDaily().tdnKg());
        assertEquals(new BigDecimal("750.00"), result.herdDaily().dryMatterIntakeKg());
        assertEquals(new BigDecimal("54.67"), result.tdnPct());
    }

    @Test
    void lactationIncrementIsMultipliedByDailyMilkYield() {
        FeedingDtos.BreedingNutritionRecommendation result = service.recommend(
                new FeedingDtos.BreedingNutritionRequest("LACTATION", new BigDecimal("500"), new BigDecimal("8"), 10));

        assertEquals(new BigDecimal("10.50"), result.perHeadDaily().dryMatterIntakeKg());
        assertEquals(new BigDecimal("1291.00"), result.perHeadDaily().crudeProteinG());
        assertEquals(new BigDecimal("6.15"), result.perHeadDaily().tdnKg());
    }

    @Test
    void lactationRejectsMissingMilkYield() {
        assertThrows(RuntimeException.class, () -> service.recommend(
                new FeedingDtos.BreedingNutritionRequest("LACTATION", new BigDecimal("500"), null, 10)));
    }
}
