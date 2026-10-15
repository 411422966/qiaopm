package com.meiyetec.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.meiyetec.security.TenantContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充审计字段与租户字段：
 * - 新增：tenantId、createTime、updateTime
 * - 更新：updateTime
 *
 * 与 MyBatis-Plus 的 TenantLineInnerInterceptor 配合，避免
 * INSERT 时因 tenant_id 为 NULL 导致的写入失败。
 */
@Component
public class TenantMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "tenantId", Long.class,
                TenantContextHolder.getTenantIdOrNull());
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        // 兼容多种命名（PointRecord 用 createdAt）
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
