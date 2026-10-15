package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 疗程效果报告（聚合快照，每次 {@code generate} 调用生成一条历史快照）。
 *
 * <p>作用：对 {@code member_treatment_session} + {@code member_treatment_progress} +
 * {@code biz_care_suggestion_log} + {@code biz_member_skin_profile} 进行聚合，
 * 输出美容 / 美发 / 美甲门店可读的"做完这套疗程到底有没有效"的客观判定依据，
 * 解决"美容行业决策依据不足"的 M06 缺口（PRD_FEATURE_VERIFICATION_REPORT § M06）。
 *
 * <p>关键设计：
 * <ul>
 *   <li>快照式：每次生成都是新行，不 UPDATE 旧行 — 这样可以对比"第 5 次的报告"和"第 10 次的报告"</li>
 *   <li>明细用 JSON 字符串保留：{@code sessionIdsJson} / {@code phasesSummaryJson}
 *       — 后续如果按业务需要可拆表，但保留 JSON 让单表查询即可复盘</li>
 *   <li>效果分用 {@code BigDecimal} 存前后值与差值，源数据来源
 *       {@code biz_member_skin_profile.moisture/oil/pigmentation/wrinkle/pore} 综合</li>
 *   <li>{@code tenantId} 多租户隔离；{@code recommendation} 是文本结论，
 *       由 {@code TreatmentReportService} 基于规则动态生成（中 / 英）</li>
 * </ul>
 */
@Data
@TableName("treatment_report")
public class TreatmentReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户隔离，所有查询必带 {@code tenant_id} */
    private Long tenantId;

    /** 报表所属会员 */
    private Long memberId;

    /** 报表对应的疗程方案 ID（指向 {@code biz_treatment_plan.id}） */
    private Long treatmentPlanId;

    /**
     * 方案名称快照（在报告生成时固化，避免方案改名后历史报告失真）。
     */
    private String planName;

    /** 方案规定总次数（来自 {@code biz_treatment_plan.total_sessions}） */
    private Integer totalSessions;

    /** 已完成次数（来自 {@code member_treatment_session}，排除软删） */
    private Integer completedSessions;

    /**
     * 已废弃次数：超过 {@code expectedFinishAt + cycleDays * 2} 仍未服务视为放弃。
     * 这里用 {@code member_treatment_progress.status = 3}（已停用）的 progress 数 + 该 progress 上 0 session 的样本。
     */
    private Integer abandonedSessions;

    /** 进行中 = total - completed - abandoned（冗余字段，前端展示加速） */
    private Integer ongoingSessions;

    /** 已完成 session 的 ID 列表，JSON 字符串（front-end 渲染时间线用） */
    private String sessionIdsJson;

    /** 每个阶段汇总 JSON：[{"phaseNo":1,"phaseName":"适应期","targetSessions":4,"completedSessions":3,"status":"IN_PROGRESS"}] */
    private String phasesSummaryJson;

    /** 报告期内命中过的"护理建议"条数 */
    private Integer careSuggestionsTotal;

    /** 其中 status='accepted' 视为已采纳（已采纳 = 已遵守） */
    private Integer careSuggestionsFollowed;

    /** 护理遵守率：followed / total，保留 4 位小数 */
    private BigDecimal careComplianceRate;

    /**
     * 效果分（前）：从 {@code biz_member_skin_profile} 在疗程开始前最后一次检测读取综合分（0~100）。
     * 找不到历史数据时为 null，表示基线缺失。
     */
    private BigDecimal effectScoreBefore;

    /** 效果分（后）：最近一次 session 时点的皮肤档案综合分 */
    private BigDecimal effectScoreAfter;

    /** 效果分差值 = after - before；为正表示改善 */
    private BigDecimal effectScoreDelta;

    /** 文本结论（CJK 默认中文，front-end 可按 locale 调），例如 "建议加强保湿" */
    private String recommendation;

    /** 生成者：SYSTEM 表示定时任务；其余为登录账号名 */
    private String generatedBy;

    /** 生成时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime generatedAt;

    /** 创建时间（审计） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 软删标记 */
    @TableLogic
    private Integer deleted;

    /** 自由备注，运营可手工填写或后续触达渠道占用 */
    private String remark;
}
