package com.meiyetec.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：仅注册分页插件。
 *
 * 数据隔离方案说明（R2.0-A 决策）：
 * 1. 不使用 TenantLineInnerInterceptor 全局自动注入 tenant_id：
 *    业务表字段不一致（crm_member 之前没 tenant_id，且 mem_level / pos_store 等
 *    字典表仍无 tenant_id），自动注入会导致 "Unknown column 'tenant_id'" 失败。
 * 2. 改由 BaseEntityService 在 Service 层显式叠加
 *    `wrapper.eq(BaseEntity::getTenantId, ctx.tenantId)
 *           .in(BaseEntity::getStoreId, ctx.storeIds)`，
 *    同时通过 DataScopeInterceptor 在 Controller 入口校验 storeId 越权。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
