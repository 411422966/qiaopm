package com.meiyetec.service;
import com.meiyetec.dto.SpaSceneRecommendDTO;
import com.meiyetec.entity.SpaSceneMode;
import com.meiyetec.entity.SpaSceneRoomSlot;
import java.util.List;
public interface SpaSceneModeService {    List<SpaSceneMode> listAll(Integer status);    SpaSceneMode getById(Long id);    SpaSceneMode save(SpaSceneMode entity);    SpaSceneMode update(SpaSceneMode entity);    void delete(Long id);    List<SpaSceneRoomSlot> listSlots(Long sceneId);    void saveSlots(Long sceneId, List<SpaSceneRoomSlot> slots);    SpaSceneRecommendDTO recommend(Long sceneId);}
