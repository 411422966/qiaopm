package com.meiyetec.controller;

import com.meiyetec.dto.LoginDTO;
import com.meiyetec.dto.LoginVO;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.SysLoginLog;
import com.meiyetec.entity.SysUser;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.AuthService;
import com.meiyetec.service.SysLoginLogService;
import com.meiyetec.util.JwtUtil;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@SysOpLog(module = "登录")
public class AuthController {

    // P1-AUTH-001: 登录失败速率限制
    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_MS = 15 * 60 * 1000L;
    private static final java.util.concurrent.ConcurrentHashMap<String, FailRecord> failMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static class FailRecord {
        int count;
        long firstFailAt;
        long lockedUntil;
    }

    private void checkLoginFailBlock(String key, String username) {
        FailRecord rec = failMap.computeIfAbsent(key, k -> new FailRecord());
        long now = System.currentTimeMillis();
        if (rec.lockedUntil > now) {
            long remain = (rec.lockedUntil - now) / 1000 / 60;
            throw new BusinessException(
                    BizCode.TOO_MANY_REQUESTS,
                    "登录失败次数过多，账号锁定 " + Math.max(1, remain) + " 分钟后重试");
        }
    }

    private void recordLoginFail(String key) {
        failMap.compute(key, (k, rec) -> {
            if (rec == null) rec = new FailRecord();
            if (rec.lockedUntil <= System.currentTimeMillis()) {
                rec.count = 0;
                rec.firstFailAt = System.currentTimeMillis();
            }
            rec.count++;
            if (rec.count >= MAX_FAIL_COUNT) {
                rec.lockedUntil = System.currentTimeMillis() + LOCK_DURATION_MS;
            }
            return rec;
        });
    }

    private void clearLoginFail(String key) {
        failMap.remove(key);
    }

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final SysLoginLogService loginLogService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto, HttpServletRequest request) {
        String ip = clientIp(request);
        String failKey = ip + ":" + dto.getUsername();
        checkLoginFailBlock(failKey, dto.getUsername());

        SysLoginLog log = new SysLoginLog();
        log.setUsername(dto.getUsername());
        log.setLoginType("password");
        log.setIp(clientIp(request));
        log.setUserAgent(request.getHeader("User-Agent"));
        try {
            LoginVO vo = authService.login(dto.getUsername(), dto.getPassword());
            // 登录成功后补全用户上下文（含 tenantId），供 SysLoginLog + LogAspect 共用
            SysUser fullUser = authService.getUserInfo(vo.getId());
            Long tenantId = fullUser != null && fullUser.getTenantId() != null
                    ? fullUser.getTenantId() : 1L;
            log.setTenantId(tenantId);
            // 关键：登录成功后立即设置 TenantContextHolder，
            // 让后续 LogAspect 写 sys_log 时能拿到正确的 tenantId。
            // 这里不主动 clear，留给请求结束 / JwtInterceptor 自然清理，
            // 否则同请求链路上 LogAspect 的 finally 阶段会读不到。
            TenantContextHolder.set(TenantContextHolder.fromUser(fullUser));
            log.setUserId(vo.getId());
            log.setStatus(1);
            log.setMessage("登录成功");
            loginLogService.asyncRecord(log);
            clearLoginFail(failKey);
            return Result.success("登录成功", vo);
        } catch (RuntimeException e) {
            log.setStatus(0);
            log.setMessage(e.getMessage());
            loginLogService.asyncRecord(log);
            recordLoginFail(failKey);
            throw e;
        }
    }

    @GetMapping("/userinfo")
    public Result<SysUser> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        // Authorization 必须存在于 Request Header 中，过滤器已保证。
        String token = authHeader.replace("Bearer ", "");
        Long userId = jwtUtil.extractUserId(token);
        SysUser user = authService.getUserInfo(userId);
        user.setPassword(null);
        return Result.success(user);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        SysLoginLog log = new SysLoginLog();
        log.setLoginType("password");
        log.setIp(clientIp(request));
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setStatus(1);
        log.setMessage("退出成功");
        try {
            if (authHeader != null) {
                String token = authHeader.replace("Bearer ", "");
                Long userId = jwtUtil.extractUserId(token);
                SysUser u = authService.getUserInfo(userId);
                if (u != null) {
                    // 兜底：如果 JwtInterceptor 没有 set 上下文，这里强制 set 一次
                    // 写完日志后再 clear（双重保险，正常路径由 JwtInterceptor.afterCompletion clear）
                    TenantContextHolder.set(TenantContextHolder.fromUser(u));
                    log.setUserId(userId);
                    log.setUsername(u.getUsername());
                    // 登录用户有 tenantId 时落库；缺失则兜底 1（与 admin 保持一致）
                    log.setTenantId(u.getTenantId() != null ? u.getTenantId() : 1L);
                }
            }
        } catch (Exception ignored) {
            // token 解析失败 / 用户不存在时，tenant_id 兜底为 1
            log.setTenantId(1L);
        }
        loginLogService.asyncRecord(log);
        // 防御性兜底：JwtInterceptor 已负责 afterCompletion clear，
        // 这里再清一次确保 TenantContextHolder 在 logout 后不留痕。
        TenantContextHolder.clear();
        return Result.success("退出成功", null);
    }

    /**
     * AUTH-005 Token 刷新
     *
     * 前端在拿到任意未过 refresh 宽限期的 token 后调用此接口，后端重新签发新 token。
     * 调用方：请求头 Authorization: Bearer <oldToken>；Body 可空。
     *
     * 错误场景：
     *  - 401 token 为空 / 无 Authorization 头
     *  - 401 token 被篡改 / 超过 7 天宽限期
     */
    @PostMapping("/refresh")
    public Result<java.util.Map<String, Object>> refresh(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error(401, "缺少 Authorization 头");
        }
        String oldToken = authHeader.substring("Bearer ".length()).trim();
        try {
            String newToken = jwtUtil.refresh(oldToken);
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("token", newToken);
            data.put("expiresIn", 86400L);
            return Result.success("refresh ok", data);
        } catch (io.jsonwebtoken.JwtException e) {
            return Result.error(401, "refresh failed: " + e.getMessage());
        }
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) return ip;
        return req.getRemoteAddr();
    }
}
