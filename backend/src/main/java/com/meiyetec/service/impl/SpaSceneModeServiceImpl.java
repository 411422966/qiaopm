package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.dto.SpaSceneRecommendDTO;
import com.meiyetec.entity.Room;
import com.meiyetec.entity.SpaSceneMode;
import com.meiyetec.entity.SpaSceneRoomSlot;
import com.meiyetec.mapper.RoomMapper;
import com.meiyetec.mapper.SpaSceneModeMapper;
import com.meiyetec.mapper.SpaSceneRoomSlotMapper;
import com.meiyetec.service.SpaSceneModeService;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SpaSceneModeServiceImpl implements SpaSceneModeService {

    private final SpaSceneModeMapper sceneMapper;
    private final SpaSceneRoomSlotMapper slotMapper;
    private final RoomMapper roomMapper;

    public SpaSceneModeServiceImpl(SpaSceneModeMapper sceneMapper,
                                   SpaSceneRoomSlotMapper slotMapper,
                                   RoomMapper roomMapper) {
        this.sceneMapper = sceneMapper;
        this.slotMapper = slotMapper;
        this.roomMapper = roomMapper;
    }

    @Override
    public List<SpaSceneMode> listAll(Integer status) {
        LambdaQueryWrapper<SpaSceneMode> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(SpaSceneMode::getStatus, status);
        qw.orderByAsc(SpaSceneMode::getSortNo);
        return sceneMapper.selectList(qw);
    }

    @Override
    public SpaSceneMode getById(Long id) {
        return sceneMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaSceneMode save(SpaSceneMode entity) {
        entity.setId(null);
        if (entity.getSortNo() == null) entity.setSortNo(0);
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getGuestCount() == null) entity.setGuestCount(1);
        if (entity.getDurationMin() == null) entity.setDurationMin(60);
        if (entity.getBasePrice() == null) entity.setBasePrice(BigDecimal.ZERO);
        sceneMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaSceneMode update(SpaSceneMode entity) {
        if (entity.getId() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "id 必填");
        }
        sceneMapper.updateById(entity);
        return sceneMapper.selectById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        sceneMapper.deleteById(id);
        slotMapper.delete(new LambdaQueryWrapper<SpaSceneRoomSlot>()
                .eq(SpaSceneRoomSlot::getSceneId, id));
    }

    @Override
    public List<SpaSceneRoomSlot> listSlots(Long sceneId) {
        return slotMapper.selectList(new LambdaQueryWrapper<SpaSceneRoomSlot>()
                .eq(SpaSceneRoomSlot::getSceneId, sceneId)
                .orderByAsc(SpaSceneRoomSlot::getSortNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSlots(Long sceneId, List<SpaSceneRoomSlot> slots) {
        slotMapper.delete(new LambdaQueryWrapper<SpaSceneRoomSlot>()
                .eq(SpaSceneRoomSlot::getSceneId, sceneId));
        if (slots == null || slots.isEmpty()) return;
        for (int i = 0; i < slots.size(); i++) {
            SpaSceneRoomSlot s = slots.get(i);
            s.setId(null);
            s.setSceneId(sceneId);
            if (s.getSortNo() == null) s.setSortNo(i + 1);
            if (s.getRequiredCount() == null) s.setRequiredCount(1);
            slotMapper.insert(s);
        }
    }

    @Override
    public SpaSceneRecommendDTO recommend(Long sceneId) {
        SpaSceneMode scene = sceneMapper.selectById(sceneId);
        if (scene == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "场景不存在");
        }
        List<SpaSceneRoomSlot> slots = listSlots(sceneId);
        int available = 0;
        if (slots != null && !slots.isEmpty()) {
            Set<Long> roomIds = slots.stream()
                    .map(SpaSceneRoomSlot::getRoomId)
                    .collect(Collectors.toCollection(HashSet::new));
            List<Room> rooms = roomMapper.selectBatchIds(roomIds);
            long online = rooms.stream()
                    .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                    .count();
            available = (int) online;
        }
        SpaSceneRecommendDTO dto = new SpaSceneRecommendDTO();
        dto.setScene(scene);
        dto.setSlots(slots == null ? new ArrayList<>() : slots);
        dto.setAvailableRoomCount(available);
        dto.setSuggestion(buildSuggestion(scene, available, slots == null ? 0 : slots.size()));
        return dto;
    }

    private String buildSuggestion(SpaSceneMode scene, int available, int slotCount) {
        if (slotCount == 0) {
            return String.format("「%s」场景尚未配置房间组合，请前往配置", scene.getSceneName());
        }
        if (available == 0) {
            return String.format("当前可用房间 0 间，建议调整排程后再预约");
        }
        if (available < slotCount) {
            return String.format("可用 %d 间，部分房间不可用，建议确认后预约", available);
        }
        return String.format("可用 %d 间，可直接下单", available);
    }
}
