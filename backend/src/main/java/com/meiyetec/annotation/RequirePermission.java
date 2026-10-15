package com.meiyetec.annotation;

import java.lang.annotation.*;

/**
 * 权限码声明。Controller 方法或类上标注，可一次或多个。
 * 配合 PermissionAspect 完成接口层级鉴权。
 *
 * <p>示例：</p>
 * <pre>
 *  {@code @RequirePermission("member:add")}
 *  {@code @RequirePermission(value = {"member:edit", "member:audit"}, mode = RequirePermission.Mode.ANY)}
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    String[] value();

    /** ANY 表示任一即可（默认），ALL 表示必须全部拥有 */
    enum Mode { ANY, ALL }

    Mode mode() default Mode.ANY;
}
