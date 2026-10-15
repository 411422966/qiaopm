-- ================================================================
-- M02-04 多账户分账：服务商 / 分店 / 员工
-- 建表时间：2026-09-06
-- ================================================================
--
-- 设计要点：
--  - 接收方账户复用 biz_dcep_sub_wallet（已有独立账本 + 流水）
--  - m02_split_rule: 分账规则（按服务 / 全局），定义三方的比例与子钱包 ID
--  - m02_split_record: 一次订单的分账记录（一对多 = 多方收款）
--  - m02_split_record_detail: 一笔分账的明细行（provider/branch/employee）
--  - status 状态机：PENDING(1) → SUCCESS(2) / FAILED(3) / PARTIAL(4)
--  - 比例和必须 = 100，DB 端 CHECK 兜底（前端表单也校验）
--  - 幂等键：uk_split_order (tenant_id, order_id) — 同一订单只分一次
--  - 所有金额 DECIMAL(15,2)；tenant_id 必填；deleted 默认 0
-- ================================================================

-- 1. 分账规则
CREATE TABLE IF NOT EXISTS m02_split_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    store_id        BIGINT DEFAULT NULL COMMENT '门店ID（null=全租户通用规则）',
    rule_name       VARCHAR(100) NOT NULL COMMENT '规则名称',
    -- 适用维度：ALL=全单  / SERVICE=按服务（service_id 必填）
    rule_scope      VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '规则范围: ALL/SERVICE',
    service_id      BIGINT DEFAULT NULL COMMENT '当 rule_scope=SERVICE 时指定服务ID',
    -- 接收方子钱包（必须存在于 biz_dcep_sub_wallet）
    provider_wallet_id BIGINT DEFAULT NULL COMMENT '服务商收款子钱包ID',
    provider_ratio  DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '服务商比例 (%)',
    branch_wallet_id BIGINT DEFAULT NULL COMMENT '分店收款子钱包ID',
    branch_ratio    DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '分店比例 (%)',
    employee_wallet_id BIGINT DEFAULT NULL COMMENT '员工收款子钱包ID',
    employee_ratio  DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '员工比例 (%)',
    -- 平台抽佣（剩余给默认账户；null/0=不抽）
    platform_fee_ratio DECIMAL(5,2) DEFAULT 0.00 COMMENT '平台抽佣比例 (%)',
    -- 生效时间
    effective_date  DATE DEFAULT NULL COMMENT '生效日期',
    expiry_date     DATE DEFAULT NULL COMMENT '失效日期',
    priority        INT NOT NULL DEFAULT 0 COMMENT '优先级（高优先级优先匹配）',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark          VARCHAR(500) DEFAULT NULL,
    create_by       BIGINT DEFAULT NULL,
    update_by       BIGINT DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_tenant (tenant_id),
    INDEX idx_store_service (tenant_id, store_id, service_id),
    INDEX idx_priority (tenant_id, priority DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M02-04 分账规则';

-- 2. 分账记录（订单维度）
CREATE TABLE IF NOT EXISTS m02_split_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    store_id        BIGINT DEFAULT NULL COMMENT '门店ID',
    order_id        BIGINT NOT NULL COMMENT '订单ID（pos_order.id）',
    order_no        VARCHAR(50) DEFAULT NULL COMMENT '订单号',
    rule_id         BIGINT DEFAULT NULL COMMENT '使用的规则ID',
    rule_name       VARCHAR(100) DEFAULT NULL COMMENT '规则名称（冗余）',
    -- 订单与分账金额
    order_amount    DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '订单实付金额',
    fee_amount      DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '平台抽佣金额',
    split_total     DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '实际分账总额（=order_amount - fee）',
    -- 状态：1 PENDING（未分）/ 2 SUCCESS（全部成功）/ 3 FAILED（全部失败）/ 4 PARTIAL（部分成功）
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1PENDING 2SUCCESS 3FAILED 4PARTIAL',
    error_msg       VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    -- 审计
    operator_id     BIGINT DEFAULT NULL COMMENT '触发人（SYSTEM=系统）',
    operator_name   VARCHAR(50) DEFAULT NULL COMMENT '触发人姓名',
    split_time      DATETIME DEFAULT NULL COMMENT '分账时间',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    -- 幂等键：同一订单只分一次
    UNIQUE KEY uk_split_order (tenant_id, order_id),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_split_time (tenant_id, split_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M02-04 分账记录';

-- 3. 分账明细（一笔订单对应 N 行：provider / branch / employee）
CREATE TABLE IF NOT EXISTS m02_split_record_detail (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    split_record_id BIGINT NOT NULL COMMENT '分账记录ID',
    -- 接收方：PROVIDER / BRANCH / EMPLOYEE / PLATFORM（抽佣也单独记一行，便于对账）
    receiver_role   VARCHAR(16) NOT NULL COMMENT '接收方角色: PROVIDER/BRANCH/EMPLOYEE/PLATFORM',
    receiver_type   VARCHAR(30) DEFAULT NULL COMMENT '子钱包类型（如 store_fund/brand_share）',
    wallet_id       BIGINT DEFAULT NULL COMMENT '子钱包ID',
    wallet_code     VARCHAR(50) DEFAULT NULL COMMENT '子钱包编号（冗余）',
    wallet_name     VARCHAR(100) DEFAULT NULL COMMENT '子钱包名称（冗余）',
    -- 关联业务对象（可选）：员工的 employee_id / 分店的 store_id / 服务商的 brand_id
    ref_id          BIGINT DEFAULT NULL COMMENT '关联业务对象ID',
    ref_name        VARCHAR(100) DEFAULT NULL COMMENT '关联业务对象名称',
    -- 金额
    ratio           DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '比例 (%)',
    amount          DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '应分金额',
    actual_amount   DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '实际入账金额',
    -- 状态：1 SUCCESS / 2 FAILED
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '1SUCCESS 2FAILED',
    error_msg       VARCHAR(500) DEFAULT NULL,
    ledger_id       BIGINT DEFAULT NULL COMMENT '子钱包账本流水ID（dcep_sub_wallet_ledger.id）',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    INDEX idx_record (split_record_id),
    INDEX idx_wallet (tenant_id, wallet_id),
    INDEX idx_receiver (tenant_id, receiver_role, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M02-04 分账明细';

-- 4. 示例分账规则（仅在生产无任何规则时插入一条默认兜底）
INSERT INTO m02_split_rule
    (tenant_id, store_id, rule_name, rule_scope, service_id,
     provider_wallet_id, provider_ratio,
     branch_wallet_id, branch_ratio,
     employee_wallet_id, employee_ratio,
     platform_fee_ratio, priority, status, remark, create_time)
SELECT 1, NULL, '默认分账规则（服务商60/分店30/员工10）', 'ALL', NULL,
       (SELECT id FROM biz_dcep_sub_wallet WHERE sub_wallet_code='SW-BRAND-001' LIMIT 1), 60.00,
       (SELECT id FROM biz_dcep_sub_wallet WHERE sub_wallet_code='SW-STORE1-001' LIMIT 1), 30.00,
       NULL, 10.00,
       0.00, 0, 1, 'M02-04 系统默认兜底规则', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM m02_split_rule WHERE tenant_id=1 AND rule_name='默认分账规则（服务商60/分店30/员工10）');
