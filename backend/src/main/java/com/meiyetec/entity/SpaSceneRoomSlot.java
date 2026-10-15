package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/**
 * SPA 场景-房间组合 
*/
@TableName("biz_spa_scene_room_slot")
public class SpaSceneRoomSlot {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    private Long sceneId;    private Long roomId;    
private String roomName;    private Integer sortNo;    
private String roleLabel;    private Integer requiredCount;    private LocalDateTime createTime;    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;    public Long getId() { return id; }    
public void setId(Long id) { this.id = id; }    public Long getTenantId() { return tenantId; }    
public void setTenantId(Long tenantId) { this.tenantId = tenantId; }    public Long getStoreId() { return storeId; }    
public void setStoreId(Long storeId) { this.storeId = storeId; }    public Long getSceneId() { return sceneId; }    
public void setSceneId(Long sceneId) { this.sceneId = sceneId; }    public Long getRoomId() { return roomId; }    
public void setRoomId(Long roomId) { this.roomId = roomId; }    
public String getRoomName() { return roomName; }    
public void setRoomName(String roomName) { this.roomName = roomName; }    public Integer getSortNo() { return sortNo; }    
public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }    
public String getRoleLabel() { return roleLabel; }    
public void setRoleLabel(String roleLabel) { this.roleLabel = roleLabel; }    public Integer getRequiredCount() { return requiredCount; }    
public void setRequiredCount(Integer requiredCount) { this.requiredCount = requiredCount; }    public LocalDateTime getCreateTime() { return createTime; }    
public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }    public LocalDateTime getUpdateTime() { return updateTime; }    
public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }    public Integer getDeleted() { return deleted; }    
public void setDeleted(Integer deleted) { this.deleted = deleted; }}
