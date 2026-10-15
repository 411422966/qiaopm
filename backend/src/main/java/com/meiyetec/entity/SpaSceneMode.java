package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * SPA 场景模式（情侣/闺蜜/单人/家庭） 
*/
@TableName("biz_spa_scene_mode")
public class SpaSceneMode {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
private String sceneCode;    
private String sceneName;    
private String sceneIcon;    
private String coverImg;    
private String description;    private Integer guestCount;    private Integer durationMin;    private BigDecimal basePrice;    
private String tags;    private Integer sortNo;    private Integer status;    private Long createdBy;    private LocalDateTime createTime;    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;    public Long getId() { return id; }    
public void setId(Long id) { this.id = id; }    public Long getTenantId() { return tenantId; }    
public void setTenantId(Long tenantId) { this.tenantId = tenantId; }    public Long getStoreId() { return storeId; }    
public void setStoreId(Long storeId) { this.storeId = storeId; }    
public String getSceneCode() { return sceneCode; }    
public void setSceneCode(String sceneCode) { this.sceneCode = sceneCode; }    
public String getSceneName() { return sceneName; }    
public void setSceneName(String sceneName) { this.sceneName = sceneName; }    
public String getSceneIcon() { return sceneIcon; }    
public void setSceneIcon(String sceneIcon) { this.sceneIcon = sceneIcon; }    
public String getCoverImg() { return coverImg; }    
public void setCoverImg(String coverImg) { this.coverImg = coverImg; }    
public String getDescription() { return description; }    
public void setDescription(String description) { this.description = description; }    public Integer getGuestCount() { return guestCount; }    
public void setGuestCount(Integer guestCount) { this.guestCount = guestCount; }    public Integer getDurationMin() { return durationMin; }    
public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }    public BigDecimal getBasePrice() { return basePrice; }    
public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }    
public String getTags() { return tags; }    
public void setTags(String tags) { this.tags = tags; }    public Integer getSortNo() { return sortNo; }    
public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }    public Integer getStatus() { return status; }    
public void setStatus(Integer status) { this.status = status; }    public Long getCreatedBy() { return createdBy; }    
public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }    public LocalDateTime getCreateTime() { return createTime; }    
public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }    public LocalDateTime getUpdateTime() { return updateTime; }    
public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }    public Integer getDeleted() { return deleted; }    
public void setDeleted(Integer deleted) { this.deleted = deleted; }}
