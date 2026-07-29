package com.cattlefarm.admin.cattle.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("cattle_event")
public class CattleEventEntity {
    @TableId
    private Long eventId;
    private Long farmId;
    private Long cattleId;
    private String eventType;
    private LocalDateTime eventDate;
    private String businessTable;
    private Long businessId;
    private String summary;
    private Long operatorId;
    private Integer isVoid;

    public Long getEventId() { return eventId; }
    public Long getFarmId() { return farmId; }
    public Long getCattleId() { return cattleId; }
    public String getEventType() { return eventType; }
    public LocalDateTime getEventDate() { return eventDate; }
    public String getBusinessTable() { return businessTable; }
    public Long getBusinessId() { return businessId; }
    public String getSummary() { return summary; }
    public Long getOperatorId() { return operatorId; }
    public Integer getIsVoid() { return isVoid; }

    public void setEventId(Long eventId) { this.eventId = eventId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public void setCattleId(Long cattleId) { this.cattleId = cattleId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setEventDate(LocalDateTime eventDate) { this.eventDate = eventDate; }
    public void setBusinessTable(String businessTable) { this.businessTable = businessTable; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public void setIsVoid(Integer isVoid) { this.isVoid = isVoid; }
}
