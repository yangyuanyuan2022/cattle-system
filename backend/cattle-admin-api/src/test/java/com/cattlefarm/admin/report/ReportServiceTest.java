package com.cattlefarm.admin.report;

import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.admin.scope.DataScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ReportServiceTest {
    @Test
    void inventoryReturnsZeroMetricsWhenCurrentScopeHasNoCattle() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        DataScopeService scope = mock(DataScopeService.class);
        when(auth.currentFarmId()).thenReturn(11L);
        when(scope.unrestricted()).thenReturn(false);
        when(scope.accessibleCattleIds()).thenReturn(List.of());

        ReportDtos.Inventory result = new ReportService(jdbc, auth, scope)
                .inventory(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertEquals(new BigDecimal("0"), result.metrics().get(0).value());
        assertEquals(0, result.lifecycleStages().size());
        assertEquals(0, result.herds().size());
        verifyNoInteractions(jdbc);
    }

    @Test
    void restrictedInventoryBindsVisibleCattleIdsIntoEveryAggregateQuery() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthService auth = mock(AuthService.class);
        DataScopeService scope = mock(DataScopeService.class);
        when(auth.currentFarmId()).thenReturn(11L);
        when(scope.unrestricted()).thenReturn(false);
        when(scope.accessibleCattleIds()).thenReturn(List.of(101L, 102L));
        when(jdbc.queryForObject(anyString(), eq(Number.class), any(Object[].class))).thenReturn(0L);

        Method method = ReportService.class.getDeclaredMethod("countCattle", String.class, String.class, String.class, long.class, List.class, Object[].class);
        method.setAccessible(true);
        long value = (long) method.invoke(new ReportService(jdbc, auth, scope), "cattle", "cattle_id", "presence_status='IN_FIELD'", 11L, List.of(101L, 102L), new Object[]{});

        assertEquals(0L, value);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(contains("cattle_id IN (?,?)"), eq(Number.class), arguments.capture());
        assertArrayEquals(new Object[]{11L, 101L, 102L}, arguments.getValue());
    }
}
