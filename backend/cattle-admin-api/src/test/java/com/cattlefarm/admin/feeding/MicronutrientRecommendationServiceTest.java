package com.cattlefarm.admin.feeding;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MicronutrientRecommendationServiceTest {
    private final MicronutrientRecommendationService service = new MicronutrientRecommendationService();

    @Test
    void growingRequirementsConvertConcentrationToHeadAndHerdDailyAmounts() {
        FeedingDtos.MicronutrientRecommendation result = service.recommend(
                new FeedingDtos.MicronutrientRequest("GROWING", new BigDecimal("8.6"), 100));

        FeedingDtos.MicronutrientLine calcium = line(result, "钙");
        assertEquals(new BigDecimal("25.80"), calcium.dailyMinPerHead());
        assertEquals(new BigDecimal("2580.00"), calcium.herdDailyMin());
        assertEquals("g/头/天", calcium.intakeUnit());

        FeedingDtos.MicronutrientLine copper = line(result, "铜");
        assertEquals(new BigDecimal("86.00"), copper.dailyMinPerHead());
        assertEquals("mg/头/天", copper.intakeUnit());

        FeedingDtos.MicronutrientLine vitaminA = line(result, "维生素 A");
        assertEquals(new BigDecimal("18920.00"), vitaminA.dailyMinPerHead());
    }

    @Test
    void pregnancyDoesNotInventCalciumAndPhosphorusTargetsMissingFromWorkbook() {
        FeedingDtos.MicronutrientRecommendation result = service.recommend(
                new FeedingDtos.MicronutrientRequest("PREGNANT", new BigDecimal("10"), 20));

        assertFalse(result.items().stream().anyMatch(item -> item.nutrientName().equals("钙") || item.nutrientName().equals("磷")));
        assertEquals(14, result.items().size());
    }

    private static FeedingDtos.MicronutrientLine line(FeedingDtos.MicronutrientRecommendation result, String name) {
        return result.items().stream().filter(item -> item.nutrientName().equals(name)).findFirst().orElseThrow();
    }
}
