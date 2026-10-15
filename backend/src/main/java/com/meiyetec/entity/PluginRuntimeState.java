package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * EXT-23：插件运行时状态持久化
 *
 * <p>pf4j 内存里的 pluginState 在重启后丢失；本表记录"最近一次已知状态"，用于：
 * <ul>
 *   <li>跨重启可观测（dashboard / 审计）</li>
 *   <li>异常告警（last_error 非空）</li>
 *   <li>恢复决策参考（注意：实际 pf4j 状态由启动期 loadAllFromDisk 重建）</li>
 * </ul>
 *
 * <p>主键策略：唯一键 {@code uk_tenant_plugin (tenant_id, plugin_code)}。
 * 租户隔离：tenant_id = 0 表示平台级；>0 表示特定租户记录。
 */
@Data
@TableName("plugin_runtime_state")
public class PluginRuntimeState {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID；0 = 平台级 */
    private Long tenantId;

    /** 业务编码（与 plugin_market.plugin_code 对齐） */
    private String pluginCode;

    /** 语义化版本 */
    private String version;

    /** pf4j 内部 pluginId */
    private String pluginId;

    /** 状态：CREATED / STARTED / STOPPED / FAILED / DISABLED */
    private String state;

    /** 最近一次错误信息（FAILED 时填） */
    private String lastError;

    /** 加载时间 */
    private LocalDateTime loadedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
