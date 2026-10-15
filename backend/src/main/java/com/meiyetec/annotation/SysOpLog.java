package com.meiyetec.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysOpLog {
    String module() default "";
    String action() default "";
    String desc() default "";
}
