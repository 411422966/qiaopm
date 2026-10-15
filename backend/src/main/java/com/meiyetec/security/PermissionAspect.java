package com.meiyetec.security;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 接口权限码拦截：基于 @RequirePermission 注解。
 * <p>与 controller 层 @SysOpLog 互相独立；权限不通过不会污染操作日志。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    @Around("@annotation(com.meiyetec.annotation.RequirePermission) || @within(com.meiyetec.annotation.RequirePermission)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method m = sig.getMethod();

        RequirePermission ann = m.getAnnotation(RequirePermission.class);
        if (ann == null) {
            ann = m.getDeclaringClass().getAnnotation(RequirePermission.class);
        }
        if (ann == null) {
            return pjp.proceed();
        }

        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) {
            throw BusinessException.unauthorized();
        }

        // SUPER_ADMIN 通配 *：拥有任意权限码
        if (ctx.getPermissions() != null && ctx.getPermissions().contains("*")) {
            return pjp.proceed();
        }

        Set<String> need = new HashSet<>();
        for (String s : ann.value()) need.add(s);
        boolean ok = ann.mode() == RequirePermission.Mode.ALL
                ? ctx.hasAllPermissions(ann.value())
                : need.stream().anyMatch(ctx::hasPermission);

        if (!ok) {
            log.warn("[PermDenied] user={} need={} mode={}", ctx.getUserId(), need, ann.mode());
            throw new BusinessException(BizCode.PERMISSION_DENIED,
                    "需要权限码：" + String.join(", ", ann.value()));
        }
        return pjp.proceed();
    }
}
