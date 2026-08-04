package com.cattlefarm.admin.feeding;

import com.cattlefarm.admin.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MicronutrientRecommendationServiceTest {
    private final MicronutrientRecommendationService service = new MicronutrientRecommendationService(null, null);

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

    @Test
    void actualSupplyUsesDailyRequirementForGapAndReadsHistoricalSnapshots() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        when(auth.currentFarmId()).thenReturn(11L);
        when(jdbc.queryForList(any(String.class), any(Object[].class))).thenReturn(List.of(
                row("10", "100", "0.2", "0.1")));

        FeedingDtos.MicronutrientRecommendation result = new MicronutrientRecommendationService(jdbc, auth).recommend(
                new FeedingDtos.MicronutrientRequest("GROWING", new BigDecimal("10"), 1, "99"));

        FeedingDtos.MicronutrientLine calcium = line(result, "钙");
        assertEquals(new BigDecimal("20.00"), calcium.actualDailyPerHead());
        assertEquals(new BigDecimal("10.00"), calcium.gapToMinPerHead());
        assertEquals("DEFICIENT", calcium.supplyStatus());
        verify(jdbc).queryForList(contains("CASE WHEN x.snapshot_at IS NOT NULL"), eq(11L), eq(99L));
    }

    @Test
    void missingNutrientValueInAnyUsedIngredientMakesSupplyUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        when(auth.currentFarmId()).thenReturn(11L);
        when(jdbc.queryForList(any(String.class), any(Object[].class))).thenReturn(List.of(
                row("5", "100", "0.2", "0.1"),
                row("5", "100", null, "0.1")));

        FeedingDtos.MicronutrientRecommendation result = new MicronutrientRecommendationService(jdbc, auth).recommend(
                new FeedingDtos.MicronutrientRequest("GROWING", new BigDecimal("10"), 1, "99"));

        assertNull(line(result, "钙").actualDailyPerHead());
        assertEquals("UNAVAILABLE", line(result, "钙").supplyStatus());
        assertEquals(new BigDecimal("10.00"), line(result, "磷").actualDailyPerHead());
    }

    private static Map<String, Object> row(String amount, String dryMatter, String calcium, String phosphorus) {
        Map<String, Object> row = new HashMap<>();
        row.put("daily_amount_kg", new BigDecimal(amount));
        row.put("dm", new BigDecimal(dryMatter));
        row.put("calcium", calcium == null ? null : new BigDecimal(calcium));
        row.put("phosphorus", phosphorus == null ? null : new BigDecimal(phosphorus));
        return row;
    }

    private static FeedingDtos.MicronutrientLine line(FeedingDtos.MicronutrientRecommendation result, String name) {
        return result.items().stream().filter(item -> item.nutrientName().equals(name)).findFirst().orElseThrow();
    }
}
