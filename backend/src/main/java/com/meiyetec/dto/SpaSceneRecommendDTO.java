package com.meiyetec.dto;
import com.meiyetec.entity.SpaSceneMode;
import com.meiyetec.entity.SpaSceneRoomSlot;
import java.util.List;
public class SpaSceneRecommendDTO {    private SpaSceneMode scene;    
private List<SpaSceneRoomSlot> slots;    private Integer availableRoomCount;    
private String suggestion;    public SpaSceneMode getScene() { return scene; }    
public void setScene(SpaSceneMode scene) { this.scene = scene; }    
public List<SpaSceneRoomSlot> getSlots() { return slots; }    
public void setSlots(List<SpaSceneRoomSlot> slots) { this.slots = slots; }    public Integer getAvailableRoomCount() { return availableRoomCount; }    
public void setAvailableRoomCount(Integer availableRoomCount) { this.availableRoomCount = availableRoomCount; }    
public String getSuggestion() { return suggestion; }    
public void setSuggestion(String suggestion) { this.suggestion = suggestion; }}
