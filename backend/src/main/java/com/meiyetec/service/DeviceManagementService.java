package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.DeviceBinding;
import com.meiyetec.entity.DeviceCommand;
import com.meiyetec.entity.DeviceSession;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.DeviceBindingMapper;
import com.meiyetec.mapper.DeviceCommandMapper;
import com.meiyetec.mapper.DeviceSessionMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.9：设备管理 + 会话 + 远程命令
 *
 * <p>提供：
 * <ul>
 *   <li>设备绑定（user × device × platform）</li>
 *   <li>设备会话（含过期/失效）</li>
 *   <li>远程命令（REFRESH / LOCK / LOGOUT / PUSH / WIPE）</li>
 *   <li>多设备列表</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceManagementService {

    private final DeviceBindingMapper bindingMapper;
    private final DeviceSessionMapper sessionMapper;
    private final DeviceCommandMapper commandMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ====================================================================
    // 1. 设备绑定
    // ====================================================================

    @PostMapping("/bind")
    @Transactional(rollbackFor = Exception.class)
    public Result<DeviceBinding> bind(@RequestBody DeviceBinding d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        d.setTenantId(tenantId);
        if (d.getStatus() == null) d.setStatus("ACTIVE");
        if (d.getBindAt() == null) d.setBindAt(LocalDateTime.now());
        if (d.getLastSeenAt() == null) d.setLastSeenAt(LocalDateTime.now());

        DeviceBinding exist = bindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getUserId, d.getUserId())
                        .eq(DeviceBinding::getDeviceId, d.getDeviceId()));
        if (exist != null) {
            exist.setPlatformCode(d.getPlatformCode());
            exist.setDeviceName(d.getDeviceName());
            exist.setDeviceModel(d.getDeviceModel());
            exist.setOsVersion(d.getOsVersion());
            exist.setAppVersion(d.getAppVersion());
            exist.setPushToken(d.getPushToken());
            exist.setIp(d.getIp());
            exist.setLastSeenAt(LocalDateTime.now());
            exist.setStatus("ACTIVE");
            bindingMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        bindingMapper.insert(d);
        return Result.success("已绑定", d);
    }

    @PostMapping("/heartbeat")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> heartbeat(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String deviceId = (String) body.get("deviceId");
        DeviceBinding d = bindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getUserId, userId)
                        .eq(DeviceBinding::getDeviceId, deviceId));
        if (d != null) {
            d.setLastSeenAt(LocalDateTime.now());
            d.setStatus("ACTIVE");
            bindingMapper.updateById(d);
            return Result.success("心跳已更新", null);
        }
        return Result.error(BizCode.NOT_FOUND, "设备未绑定");
    }

    @PostMapping("/unbind")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbind(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        String deviceId = (String) body.get("deviceId");
        DeviceBinding d = bindingMapper.selectOne(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getUserId, userId)
                        .eq(DeviceBinding::getDeviceId, deviceId));
        if (d == null) return Result.error(BizCode.NOT_FOUND, "设备不存在");
        d.setStatus("REVOKED");
        bindingMapper.updateById(d);
        return Result.success("已解绑", null);
    }

    @GetMapping("/list")
    public Result<List<DeviceBinding>> listDevices(@RequestParam Long userId) {
        return Result.success(bindingMapper.selectList(
                new LambdaQueryWrapper<DeviceBinding>()
                        .eq(DeviceBinding::getUserId, userId)
                        .orderByDesc(DeviceBinding::getLastSeenAt)));
    }

    // ====================================================================
    // 2. 设备会话
    // ====================================================================

    @PostMapping("/session/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<DeviceSession> createSession(@RequestBody DeviceSession s) {
        if (s.getSessionToken() == null) s.setSessionToken(generateToken(48));
        if (s.getRefreshToken() == null) s.setRefreshToken(generateToken(64));
        if (s.getExpiresAt() == null) s.setExpiresAt(LocalDateTime.now().plusHours(2));
        if (s.getActive() == null) s.setActive(1);
        if (s.getStartedAt() == null) s.setStartedAt(LocalDateTime.now());
        sessionMapper.insert(s);
        return Result.success("已创建", s);
    }

    @PostMapping("/session/{token}/end")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> endSession(@PathVariable String token) {
        DeviceSession s = sessionMapper.selectOne(
                new LambdaQueryWrapper<DeviceSession>().eq(DeviceSession::getSessionToken, token));
        if (s == null) return Result.error(BizCode.NOT_FOUND, "会话不存在");
        s.setActive(0);
        s.setEndedAt(LocalDateTime.now());
        sessionMapper.updateById(s);
        return Result.success("已结束", null);
    }

    @PostMapping("/session/{token}/refresh")
    @Transactional(rollbackFor = Exception.class)
    public Result<DeviceSession> refreshSession(@PathVariable String token) {
        DeviceSession s = sessionMapper.selectOne(
                new LambdaQueryWrapper<DeviceSession>().eq(DeviceSession::getSessionToken, token));
        if (s == null) return Result.error(BizCode.NOT_FOUND, "会话不存在");
        if (s.getActive() == 0) return Result.error(BizCode.CONFLICT, "会话已失效");
        s.setSessionToken(generateToken(48));
        s.setExpiresAt(LocalDateTime.now().plusHours(2));
        sessionMapper.updateById(s);
        return Result.success("已续期", s);
    }

    @GetMapping("/session/list")
    public Result<List<DeviceSession>> listSessions(@RequestParam Long userId) {
        return Result.success(sessionMapper.selectList(
                new LambdaQueryWrapper<DeviceSession>()
                        .eq(DeviceSession::getUserId, userId)
                        .eq(DeviceSession::getActive, 1)
                        .orderByDesc(DeviceSession::getStartedAt)));
    }

    // ====================================================================
    // 3. 远程命令
    // ====================================================================

    @PostMapping("/command/send")
    @Transactional(rollbackFor = Exception.class)
    public Result<DeviceCommand> sendCommand(@RequestBody DeviceCommand c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        c.setTenantId(tenantId);
        if (c.getStatus() == null) c.setStatus("PENDING");
        if (c.getPriority() == null) c.setPriority(100);
        if (c.getCreatedAt() == null) c.setCreatedAt(LocalDateTime.now());
        if (c.getExpiresAt() == null) c.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        commandMapper.insert(c);
        return Result.success("已下发", c);
    }

    @PostMapping("/command/poll")
    public Result<List<DeviceCommand>> pollCommand(@RequestBody Map<String, Object> body) {
        String deviceId = (String) body.get("deviceId");
        List<DeviceCommand> cmds = commandMapper.selectList(
                new LambdaQueryWrapper<DeviceCommand>()
                        .eq(DeviceCommand::getDeviceId, deviceId)
                        .eq(DeviceCommand::getStatus, "PENDING")
                        .orderByAsc(DeviceCommand::getPriority)
                        .orderByAsc(DeviceCommand::getCreatedAt)
                        .last("LIMIT 10"));
        // 标记为已下发
        for (DeviceCommand c : cmds) {
            c.setStatus("DELIVERED");
            c.setDeliveredAt(LocalDateTime.now());
            commandMapper.updateById(c);
        }
        return Result.success(cmds);
    }

    @PostMapping("/command/{id}/ack")
    @Transactional(rollbackFor = Exception.class)
    public Result<DeviceCommand> ackCommand(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        DeviceCommand c = commandMapper.selectById(id);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "命令不存在");
        c.setStatus("ACK");
        c.setAckedAt(LocalDateTime.now());
        if (body != null && body.get("errorMessage") != null) {
            c.setErrorMessage((String) body.get("errorMessage"));
            if (c.getErrorMessage() != null && !c.getErrorMessage().isEmpty()) {
                c.setStatus("FAILED");
            }
        }
        commandMapper.updateById(c);
        return Result.success("已确认", c);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private static String generateToken(int len) {
        byte[] b = new byte[len];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}