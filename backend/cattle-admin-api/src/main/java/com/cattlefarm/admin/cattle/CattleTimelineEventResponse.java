package com.cattlefarm.admin.cattle;

import com.cattlefarm.admin.cattle.model.CattleEventEntity;

import java.time.LocalDateTime;

public record CattleTimelineEventResponse(
        String eventId,
        String eventType,
        LocalDateTime eventDate,
        String summary,
        String operatorId
) {
    public static CattleTimelineEventResponse from(CattleEventEntity entity) {
        return new CattleTimelineEventResponse(
                entity.getEventId().toString(), entity.getEventType(), entity.getEventDate(),
                entity.getSummary(), entity.getOperatorId() == null ? null : entity.getOperatorId().toString());
    }
}
