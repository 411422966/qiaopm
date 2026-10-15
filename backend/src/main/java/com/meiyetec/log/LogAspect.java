package com.meiyetec.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.SysLog;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.SysLogService;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final SysLogService sysLogService;
    private final ObjectMapper objectMapper;

    private static final List<String> SENSITIVE_KEYS = List.of("password", "oldPassword", "newPassword", "token");

    @Pointcut("@annotation(com.meiyetec.annotation.SysOpLog) || @within(com.meiyetec.annotation.SysOpLog)")
    public void logPointcut() {}

    @Around("logPointcut() && !execution(* com.meiyetec.controller.SysLogController.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                SysLog lg = buildLog(pjp, result, error, (int) cost);
                saveAsync(lg);
            } catch (Exception e) {
                log.warn("save sys_log failed: {}", e.getMessage());
            }
        }
    }

    @Async
    void saveAsync(SysLog lg) {
        sysLogService.save(lg);
    }

    private SysLog buildLog(ProceedingJoinPoint pjp, Object result, Throwable error, int costMs) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method m = sig.getMethod();
        SysOpLog ann = m.getAnnotation(SysOpLog.class);
        if (ann == null) ann = m.getDeclaringClass().getAnnotation(SysOpLog.class);

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest req = attrs != null ? attrs.getRequest() : null;

        SysLog lg = new SysLog();
        lg.setTenantId(TenantContextHolder.get().getTenantId());
        lg.setUserId(LogContext.getUserId());
        lg.setUsername(LogContext.getUsername() != null ? LogContext.getUsername() : "anonymous");

        if (ann != null) {
            lg.setModule(empty(ann.module()) ? deriveModule(m.getDeclaringClass().getSimpleName()) : ann.module());
            lg.setAction(empty(ann.action()) ? deriveAction(req != null ? req.getMethod() : null) : ann.action());
            lg.setDescription(empty(ann.desc()) ? defaultDesc(lg.getModule(), lg.getAction()) : ann.desc());
        }

        if (req != null) {
            lg.setRequestMethod(req.getMethod());
            lg.setRequestUrl(req.getRequestURI());
            lg.setIp(clientIp(req));
            lg.setUserAgent(req.getHeader("User-Agent"));
            try {
                String params = serializeParams(req, pjp.getArgs());
                if (params != null && params.length() > 2000) params = params.substring(0, 2000) + "...(truncated)";
                lg.setRequestParams(params);
            } catch (Exception ignored) {}
        }

        lg.setCostMs(costMs);
        lg.setStatus(0);
        String err = null;

        if (error != null) {
            lg.setStatus(0);
            err = error.getClass().getSimpleName() + ": " + safeMsg(error.getMessage());
        } else if (result instanceof Result<?> r) {
            if (r.getCode() == 200) {
                lg.setStatus(1);
            } else {
                lg.setStatus(0);
                err = safeMsg(r.getMessage());
            }
        }

        lg.setErrorMsg(err);
        return lg;
    }

    private String clientIp(HttpServletRequest req) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String v = req.getHeader(h);
            if (v != null && !v.isEmpty() && !"unknown".equalsIgnoreCase(v)) {
                int idx = v.indexOf(',');
                return normalizeIp(idx > 0 ? v.substring(0, idx).trim() : v.trim());
            }
        }
        return normalizeIp(req.getRemoteAddr());
    }

    private String normalizeIp(String ip) {
        if (ip == null) return null;
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        if (ip.startsWith("::ffff:")) return ip.substring(7); // IPv4-mapped IPv6
        return ip;
    }

    private String serializeParams(HttpServletRequest req, Object[] args) throws Exception {
        if (req.getMethod().equalsIgnoreCase("GET")) {
            String q = req.getQueryString();
            if (q == null) q = req.getParameterMap().entrySet().stream()
                    .map(e -> e.getKey() + "=" + (e.getValue().length > 0 ? e.getValue()[0] : ""))
                    .collect(Collectors.joining("&"));
            return maskQuery(q);
        }
        if (args == null || args.length == 0) return null;
        Object body = Arrays.stream(args)
                .filter(a -> !(a instanceof HttpServletRequest)
                        && !(a instanceof jakarta.servlet.http.HttpServletResponse)
                        && !(a instanceof org.springframework.web.multipart.MultipartFile))
                .findFirst().orElse(null);
        if (body == null) return null;
        return maskJson(objectMapper.writeValueAsString(body));
    }

    private String maskQuery(String q) {
        if (q == null || q.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) { sb.append(pair); }
            else {
                String k = pair.substring(0, idx);
                String v = pair.substring(idx + 1);
                sb.append(k).append('=').append(SENSITIVE_KEYS.contains(k) ? "***" : v);
            }
            sb.append('&');
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private String maskJson(String json) {
        String out = json;
        for (String k : SENSITIVE_KEYS) {
            out = out.replaceAll("(?i)(\"" + k + "\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
        }
        return out;
    }

    private String deriveModule(String ctrlName) {
        if (ctrlName == null) return "系统";
        String n = ctrlName.replace("Controller", "");
        if (n.startsWith("Sys")) return "系统";
        return n;
    }

    private String deriveAction(String method) {
        if (method == null) return "";
        return switch (method) {
            case "POST" -> "create";
            case "PUT" -> "update";
            case "DELETE" -> "delete";
            case "GET" -> "query";
            default -> method.toLowerCase();
        };
    }

    private String defaultDesc(String module, String action) {
        return action + "-" + module;
    }

    private boolean empty(String s) { return s == null || s.isEmpty(); }

    private String safeMsg(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
