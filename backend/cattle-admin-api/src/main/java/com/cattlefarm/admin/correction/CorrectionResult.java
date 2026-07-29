package com.cattlefarm.admin.correction;

public record CorrectionResult(
        String businessId,
        String businessType,
        boolean voided,
        String relatedStatus
) {
}
