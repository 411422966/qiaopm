package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.SyncEvent;
import com.meiyetec.entity.SyncSession;
import com.meiyetec.entity.SyncSessionMember;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.SyncEventMapper;
import com.meiyetec.mapper.SyncSessionMapper;
import com.meiyetec.mapper.SyncSessionMemberMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.9：多端同步
 *
 * <p>提供：
 * <ul>
 *   <li>同步会话（用户 × 多设备）</li>
 *   <li>会话成员管理（加入 / 离开）</li>
 *   <li>同步事件（NAV / FOCUS / STATE_CHANGE / ACTION）</li>
 *   <li>事件拉取（其他端 → 本端）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
public class MultiDeviceSyncService {

    private final SyncSessionMapper sessionMapper;
    private final SyncSessionMemberMapper memberMapper;
    private final SyncEventMapper eventMapper;

    // ====================================================================
    // 1. 同步会话
    // ====================================================================

    @PostMapping("/session/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<SyncSession> createSession(@RequestBody SyncSession s) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        s.setTenantId(tenantId);
        if (s.getSessionKey() == null) return Result.error(BizCode.BAD_REQUEST, "sessionKey 必填");
        if (s.getDeviceCount() == null) s.setDeviceCount(1);
        if (s.getEventCount() == null) s.setEventCount(0);
        if (s.getStatus() == null) s.setStatus("ACTIVE");
        if (s.getStartedAt() == null) s.setStartedAt(LocalDateTime.now());
        if (s.getLastEventAt() == null) s.setLastEventAt(LocalDateTime.now());

        SyncSession exist = sessionMapper.selectOne(
                new LambdaQueryWrapper<SyncSession>().eq(SyncSession::getSessionKey, s.getSessionKey()));
        if (exist != null) {
            exist.setEndedAt(null);
            exist.setStatus("ACTIVE");
            sessionMapper.updateById(exist);
            return Result.success("已恢复", exist);
        }
        sessionMapper.insert(s);
        return Result.success("已创建", s);
    }

    @PostMapping("/session/{key}/close")
    @Transactional(rollbackFor = Exception.class)
    public Result<SyncSession> closeSession(@PathVariable String key) {
        SyncSession s = sessionMapper.selectOne(
                new LambdaQueryWrapper<SyncSession>().eq(SyncSession::getSessionKey, key));
        if (s == null) return Result.error(BizCode.NOT_FOUND, "会话不存在");
        s.setStatus("CLOSED");
        s.setEndedAt(LocalDateTime.now());
        sessionMapper.updateById(s);
        // 标记所有成员离开
        List<SyncSessionMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<SyncSessionMember>().eq(SyncSessionMember::getSessionKey, key));
        for (SyncSessionMember m : members) {
            if (m.getActive() == 1) {
                m.setActive(0);
                m.setLeftAt(LocalDateTime.now());
                memberMapper.updateById(m);
            }
        }
        return Result.success("已关闭", s);
    }

    // ====================================================================
    // 2. 会话成员
    // ====================================================================

    @PostMapping("/member/join")
    @Transactional(rollbackFor = Exception.class)
    public Result<SyncSessionMember> join(@RequestBody SyncSessionMember m) {
        if (m.getSessionKey() == null || m.getDeviceId() == null)
            return Result.error(BizCode.BAD_REQUEST, "sessionKey / deviceId 必填");
        if (m.getActive() == null) m.setActive(1);
        if (m.getJoinedAt() == null) m.setJoinedAt(LocalDateTime.now());

        SyncSessionMember exist = memberMapper.selectOne(
                new LambdaQueryWrapper<SyncSessionMember>()
                        .eq(SyncSessionMember::getSessionKey, m.getSessionKey())
                        .eq(SyncSessionMember::getDeviceId, m.getDeviceId()));
        if (exist != null) {
            exist.setActive(1);
            exist.setJoinedAt(LocalDateTime.now());
            exist.setLeftAt(null);
            exist.setPlatformCode(m.getPlatformCode());
            memberMapper.updateById(exist);
        } else {
            memberMapper.insert(m);
        }

        // 更新 device_count
        updateDeviceCount(m.getSessionKey());
        return Result.success("已加入", exist != null ? exist : m);
    }

    @PostMapping("/member/leave")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> leave(@RequestBody Map<String, Object> body) {
        String sessionKey = (String) body.get("sessionKey");
        String deviceId = (String) body.get("deviceId");
        SyncSessionMember m = memberMapper.selectOne(
                new LambdaQueryWrapper<SyncSessionMember>()
                        .eq(SyncSessionMember::getSessionKey, sessionKey)
                        .eq(SyncSessionMember::getDeviceId, deviceId));
        if (m != null) {
            m.setActive(0);
            m.setLeftAt(LocalDateTime.now());
            memberMapper.updateById(m);
            updateDeviceCount(sessionKey);
        }
        return Result.success("已离开", null);
    }

    @GetMapping("/member/list")
    public Result<List<SyncSessionMember>> listMembers(@RequestParam String sessionKey) {
        return Result.success(memberMapper.selectList(
                new LambdaQueryWrapper<SyncSessionMember>()
                        .eq(SyncSessionMember::getSessionKey, sessionKey)
                        .orderByDesc(SyncSessionMember::getJoinedAt)));
    }

    private void updateDeviceCount(String sessionKey) {
        Long cnt = memberMapper.selectCount(
                new LambdaQueryWrapper<SyncSessionMember>()
                        .eq(SyncSessionMember::getSessionKey, sessionKey)
                        .eq(SyncSessionMember::getActive, 1));
        SyncSession s = sessionMapper.selectOne(
                new LambdaQueryWrapper<SyncSession>().eq(SyncSession::getSessionKey, sessionKey));
        if (s != null) {
            s.setDeviceCount(cnt.intValue());
            sessionMapper.updateById(s);
        }
    }

    // ====================================================================
    // 3. 同步事件
    // ====================================================================

    @PostMapping("/event/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<SyncEvent> publishEvent(@RequestBody SyncEvent e) {
        if (e.getSessionKey() == null || e.getFromDeviceId() == null || e.getEventType() == null)
            return Result.error(BizCode.BAD_REQUEST, "sessionKey / fromDeviceId / eventType 必填");
        if (e.getOccurredAt() == null) e.setOccurredAt(LocalDateTime.now());
        eventMapper.insert(e);

        SyncSession s = sessionMapper.selectOne(
                new LambdaQueryWrapper<SyncSession>().eq(SyncSession::getSessionKey, e.getSessionKey()));
        if (s != null) {
            s.setLastEventAt(LocalDateTime.now());
            s.setEventCount((s.getEventCount() == null ? 0 : s.getEventCount()) + 1);
            sessionMapper.updateById(s);
        }
        return Result.success("已发布", e);
    }

    /**
     * 拉取新事件（按其他设备）
     */
    @PostMapping("/event/pull")
    public Result<List<SyncEvent>> pullEvents(@RequestBody Map<String, Object> body) {
        String sessionKey = (String) body.get("sessionKey");
        String currentDeviceId = (String) body.get("deviceId");
        LocalDateTime since = body.get("since") == null ? LocalDateTime.now().minusMinutes(5)
                : LocalDateTime.parse((String) body.get("since"));

        List<SyncEvent> all = eventMapper.selectList(
                new LambdaQueryWrapper<SyncEvent>()
                        .eq(SyncEvent::getSessionKey, sessionKey)
                        .ge(SyncEvent::getOccurredAt, since)
                        .orderByAsc(SyncEvent::getOccurredAt));
        // 排除自身
        return Result.success(all.stream()
                .filter(e -> !e.getFromDeviceId().equals(currentDeviceId))
                .toList());
    }
}