package com.cattlefarm.admin.exit;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExitItem(
        String exitId,
        String cattleId,
        String earTagNo,
        String exitType,
        LocalDate exitDate,
        String reason,
        String operatorName,
        LocalDateTime restoredAt,
        String restoreReason,
        boolean voided,
        String voidReason
) {
}
