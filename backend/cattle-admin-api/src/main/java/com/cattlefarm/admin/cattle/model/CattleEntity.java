package com.cattlefarm.admin.cattle.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("cattle")
public class CattleEntity {
    @TableId
    private Long cattleId;
    private Long farmId;
    private String earTagNo;
    private String name;
    private String sex;
    private Long breedId;
    private LocalDate birthDate;
    private String sourceType;
    private LocalDate entryDate;
    private String lifecycleStage;
    private String presenceStatus;
    private String healthStatus;
    private String breedingStatus;
    private Long herdId;
    private Long barnId;
    private Long sireId;
    private Long damId;
    private String sireText;
    private String photoUrl;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Integer version;

    public Long getCattleId() { return cattleId; }
    public void setCattleId(Long cattleId) { this.cattleId = cattleId; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public String getEarTagNo() { return earTagNo; }
    public void setEarTagNo(String earTagNo) { this.earTagNo = earTagNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public Long getBreedId() { return breedId; }
    public void setBreedId(Long breedId) { this.breedId = breedId; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public String getLifecycleStage() { return lifecycleStage; }
    public void setLifecycleStage(String lifecycleStage) { this.lifecycleStage = lifecycleStage; }
    public String getPresenceStatus() { return presenceStatus; }
    public void setPresenceStatus(String presenceStatus) { this.presenceStatus = presenceStatus; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public String getBreedingStatus() { return breedingStatus; }
    public void setBreedingStatus(String breedingStatus) { this.breedingStatus = breedingStatus; }
    public Long getHerdId() { return herdId; }
    public void setHerdId(Long herdId) { this.herdId = herdId; }
    public Long getBarnId() { return barnId; }
    public void setBarnId(Long barnId) { this.barnId = barnId; }
    public Long getSireId() { return sireId; }
    public void setSireId(Long sireId) { this.sireId = sireId; }
    public Long getDamId() { return damId; }
    public void setDamId(Long damId) { this.damId = damId; }
    public String getSireText() { return sireText; }
    public void setSireText(String sireText) { this.sireText = sireText; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
