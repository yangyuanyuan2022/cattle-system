package com.cattlefarm.admin.cattle;

import com.cattlefarm.admin.cattle.model.CattleEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CattleResponse(
        String cattleId,
        String farmId,
        String earTagNo,
        String name,
        String sex,
        String breedId,
        LocalDate birthDate,
        String sourceType,
        LocalDate entryDate,
        String lifecycleStage,
        String presenceStatus,
        String healthStatus,
        String breedingStatus,
        String herdId,
        String barnId,
        String sireId,
        String sireText,
        String remark,
        LocalDateTime createdAt,
        int version
) {
    public static CattleResponse from(CattleEntity entity) {
        return new CattleResponse(string(entity.getCattleId()), string(entity.getFarmId()), entity.getEarTagNo(),
                entity.getName(), entity.getSex(), string(entity.getBreedId()), entity.getBirthDate(),
                entity.getSourceType(), entity.getEntryDate(), entity.getLifecycleStage(),
                entity.getPresenceStatus(), entity.getHealthStatus(), entity.getBreedingStatus(),
                string(entity.getHerdId()), string(entity.getBarnId()), string(entity.getSireId()),
                entity.getSireText(), entity.getRemark(),
                entity.getCreatedAt(), entity.getVersion() == null ? 0 : entity.getVersion());
    }

    private static String string(Long value) { return value == null ? null : value.toString(); }
}
