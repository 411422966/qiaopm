-- ================================================================
-- M14 动态定价 A/B 测试实验
-- 建表时间：2026-09-04
-- ================================================================

-- 1. 定价实验配置
DROP TABLE IF EXISTS pricing_experiment;
CREATE TABLE pricing_experiment (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id               BIGINT NOT NULL DEFAULT 1,

    exp_key                 VARCHAR(64) NOT NULL COMMENT '实验 key（URL-safe，英文）',
    exp_name                VARCHAR(128) NOT NULL COMMENT '实验名称',
    status                  VARCHAR(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/RUNNING/PAUSED/COMPLETED',

    -- 控制组
    group_a_name            VARCHAR(64) DEFAULT '控制组A（原价）',
    group_a_mode            VARCHAR(16) DEFAULT 'ORIGINAL' COMMENT 'ORIGINAL/PERCENT/FIXED',
    group_a_value           DECIMAL(10,4) DEFAULT 0 COMMENT 'ORIGINAL=0, PERCENT=折扣%, FIXED=减多少',
    group_b_name            VARCHAR(64) DEFAULT '控制组B（9折）',
    group_b_mode            VARCHAR(16) DEFAULT 'PERCENT',
    group_b_value           DECIMAL(10,4) DEFAULT 10 COMMENT '10=减10%（打9折）',

    -- 变异组
    group_c_name            VARCHAR(64) DEFAULT '变异组C（8折）',
    group_c_mode            VARCHAR(16) DEFAULT 'PERCENT',
    group_c_value           DECIMAL(10,4) DEFAULT 20 COMMENT '20=减20%（打8折）',
    group_d_name            VARCHAR(64) DEFAULT '变异组D（7折）',
    group_d_mode            VARCHAR(16) DEFAULT 'PERCENT',
    group_d_value           DECIMAL(10,4) DEFAULT 30 COMMENT '30=减30%（打7折）',
    group_count             INT DEFAULT 2 COMMENT '实验组数量（2=控制组A/B，4=ABCD全部）',

    -- 流量分配
    control_traffic_ratio   DECIMAL(5,4) DEFAULT 0.5 COMMENT '控制组总流量占比（0.0~1.0）',
    control_ab_ratio        DECIMAL(5,4) DEFAULT 0.5 COMMENT '控制组内 A:B 分配（默认 1:1）',
    variant_cd_ratio        DECIMAL(5,4) DEFAULT 0.5 COMMENT '变异组内 C:D 分配（默认 1:1）',

    -- 适用条件
    applicable_projects      VARCHAR(500) DEFAULT 'ALL' COMMENT '适用项目（逗号分隔 ID，ALL=全部）',
    applicable_stores       VARCHAR(500) DEFAULT 'ALL' COMMENT '适用门店',
    applicable_member_levels VARCHAR(500) DEFAULT 'ALL' COMMENT '适用会员等级',
    min_order_amount        DECIMAL(12,2) DEFAULT 0 COMMENT '最低订单金额',

    -- 指标
    metric_type             VARCHAR(32) DEFAULT 'conversion_rate' COMMENT 'conversion_rate / revenue_per_session / avg_order_value',
    significance_threshold  DECIMAL(5,4) DEFAULT 0.95 COMMENT '统计显著性阈值',
    min_sample_size         INT DEFAULT 100 COMMENT '每组最少样本量',

    -- 统计（实时）
    control_sample_size     INT DEFAULT 0 COMMENT '控制组样本数',
    variant_sample_size     INT DEFAULT 0 COMMENT '变异组样本数',
    control_conversions     INT DEFAULT 0 COMMENT '控制组转化数',
    variant_conversions     INT DEFAULT 0 COMMENT '变异组转化数',
    control_metric_value    DECIMAL(12,4) COMMENT '控制组均值',
    variant_metric_value    DECIMAL(12,4) COMMENT '变异组均值',
    confidence_level        DECIMAL(5,4) COMMENT '置信度（0.0~1.0）',
    lift_percent            DECIMAL(8,4) COMMENT '提升幅度（%）',
    conclusion              VARCHAR(32) DEFAULT 'PENDING' COMMENT 'WIN_CONTROL / WIN_VARIANT / NO_SIGNIFICANCE / PENDING',

    start_date              DATE COMMENT '实验开始日期',
    end_date               DATE COMMENT '实验结束日期（null=无限期）',
    description             VARCHAR(500) DEFAULT NULL,
    operator_name          VARCHAR(50) DEFAULT NULL,

    create_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_exp_key (tenant_id, exp_key, deleted),
    INDEX idx_status (status),
    INDEX idx_conclusion (conclusion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价A/B实验配置';

-- 2. 实验结果记录
DROP TABLE IF EXISTS pricing_experiment_result;
CREATE TABLE pricing_experiment_result (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT NOT NULL DEFAULT 1,
    exp_id              BIGINT COMMENT '实验 ID',
    exp_key             VARCHAR(64) COMMENT '实验 key',
    member_id           BIGINT COMMENT '会员 ID',
    store_id            BIGINT COMMENT '门店 ID',
    project_id          BIGINT COMMENT '项目 ID',
    quote_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'quote 时间',
    variant             VARCHAR(8) NOT NULL COMMENT 'A / B / C / D',
    is_control          TINYINT NOT NULL DEFAULT 0 COMMENT '1=控制组 0=变异组',
    original_price      DECIMAL(12,2) NOT NULL COMMENT '原始价格',
    final_price         DECIMAL(12,2) NOT NULL COMMENT '最终价格',
    discount_amount     DECIMAL(12,2) DEFAULT 0 COMMENT '折扣金额',
    rule_id             BIGINT COMMENT '命中的规则 ID',
    rule_code           VARCHAR(64) COMMENT '命中的规则编码',

    conversion_status   VARCHAR(16) DEFAULT 'RECORDED' COMMENT 'RECORDED / CONVERTED / EXPIRED',
    converted_at       DATETIME COMMENT '转化时间',
    order_id           BIGINT COMMENT '关联订单 ID',
    conversion_amount  DECIMAL(12,2) COMMENT '转化金额',

    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_exp_id (exp_id),
    INDEX idx_exp_key (exp_key),
    INDEX idx_member (member_id),
    INDEX idx_variant (variant, is_control),
    INDEX idx_conversion_status (conversion_status),
    INDEX idx_quote_at (quote_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定价实验结果记录';

-- 3. 权限
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('pricing:experiment:list', '定价实验列表', 'pricing', 'menu', 1, 0, NOW()),
    ('pricing:experiment:create', '创建定价实验', 'pricing', 'menu', 1, 0, NOW()),
    ('pricing:experiment:stats', '定价实验统计', 'pricing', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 4. 示例实验数据
INSERT INTO pricing_experiment
(tenant_id, exp_key, exp_name, status, group_a_name, group_a_mode, group_a_value,
 group_b_name, group_b_mode, group_b_value, group_c_name, group_c_mode, group_c_value,
 group_count, control_traffic_ratio, metric_type, significance_threshold, min_sample_size,
 start_date, description, operator_name)
VALUES
    (1, 'EXP_PRICE_SENSITIVITY_Q3', 'Q3 定价敏感性测试', 'RUNNING',
     '控制组A（原价）', 'ORIGINAL', 0,
     '控制组B（9折）', 'PERCENT', 10,
     '变异组C（8折）', 'PERCENT', 20,
     3, 0.5, 'conversion_rate', 0.95, 200,
     CURDATE(), '测试不同折扣力度对转化率的影响', 'admin'),

    (1, 'EXP_LOYALTY_DISCOUNT', '会员折扣力度测试', 'DRAFT',
     '忠诚会员原价', 'ORIGINAL', 0,
     '忠诚会员9折', 'PERCENT', 10,
     '忠诚会员8折', 'PERCENT', 20,
     3, 0.5, 'avg_order_value', 0.95, 150,
     CURDATE(), '针对忠诚会员的差异化定价策略', 'admin');
