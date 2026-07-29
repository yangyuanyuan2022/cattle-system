package com.cattlefarm.admin.cattle;

import java.util.List;

public record CattlePedigreeResponse(
        String cattleId,
        String sireId,
        String sireEarTagNo,
        String sireText,
        String damId,
        String damEarTagNo,
        List<Relative> offspring
) {
    public record Relative(String cattleId, String earTagNo, String name, String sex) {}
}
