-- ============================================
-- R2.1-E：报表 SQL 聚合与异步导出
-- 2026-08-09
-- ============================================

-- 1. 报表导出任务表（异步）
DROP TABLE IF EXISTS report_export_task;
CREATE TABLE report_export_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    task_no VARCHAR(64) NOT NULL,
    report_code VARCHAR(64) NOT NULL COMMENT '报表编码: SALES/PAY/MEMBER/COMMISSION/PRODUCT',
    report_name VARCHAR(128) NOT NULL,
    date_range_start DATE,
    date_range_end DATE,
    -- 过滤参数（JSON）
    filter_json VARCHAR(1000),
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1待处理 2处理中 3成功 4失败 5已取消',
    progress INT DEFAULT 0 COMMENT '进度 0-100',
    file_path VARCHAR(500),
    file_url VARCHAR(500),
    file_size BIGINT,
    row_count INT,
    -- 耗时
    started_at DATETIME,
    finished_at DATETIME,
    duration_ms BIGINT,
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    error_msg VARCHAR(2000),
    -- 幂等
    idempotency_key VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_task (tenant_id, task_no),
    INDEX idx_status (status, deleted),
    INDEX idx_report (report_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表导出任务';

-- 2. 报表指标快照
DROP TABLE IF EXISTS report_metric_snapshot;
CREATE TABLE report_metric_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    snapshot_date DATE NOT NULL,
    metric_key VARCHAR(64) NOT NULL,
    metric_name VARCHAR(128),
    metric_value DECIMAL(18,4) DEFAULT 0,
    extra VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_store_date_metric (tenant_id, store_id, snapshot_date, metric_key),
    INDEX idx_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表指标快照';

-- 3. 报表指标口径定义（v1）
DROP TABLE IF EXISTS report_metric_caliber;
CREATE TABLE report_metric_caliber (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_key VARCHAR(64) NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL COMMENT 'SALES/PAY/MEMBER/COMMISSION/INVENTORY',
    formula VARCHAR(500) COMMENT '计算公式（人类可读）',
    sql_template VARCHAR(2000) COMMENT '实际 SQL 模板',
    unit VARCHAR(16) DEFAULT '元',
    description VARCHAR(500),
    version VARCHAR(16) DEFAULT 'v1',
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_metric_version (metric_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表指标口径';

-- 4. RBAC 权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'report:export', '报表导出', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1), 90;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'report:task', '导出任务', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report:export') t LIMIT 1), 10;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'report:caliber', '指标口径', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report:export') t LIMIT 1), 11;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'report:snapshot', '指标快照', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='report:export') t LIMIT 1), 12;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN')
  AND p.permission_code IN ('report:export','report:task','report:caliber','report:snapshot');

-- 5. 指标口径 v1 种子
INSERT IGNORE INTO report_metric_caliber (metric_key, metric_name, category, formula, sql_template, unit, description, version)
VALUES
('GMV',           '成交总额',     'SALES',     'SUM(final_amount) WHERE status IN (3,4) AND pay_time BETWEEN ? AND ?',
    'SELECT COALESCE(SUM(final_amount), 0) FROM pos_order WHERE tenant_id=? AND store_id=? AND deleted=0 AND status IN (3,4) AND pay_time BETWEEN ? AND ?',
    '元', '完成订单的总金额（含退款前）', 'v1'),
('NET_REVENUE',   '净营收',       'SALES',     'SUM(final_amount - refund_amount) WHERE status IN (3,4)',
    'SELECT COALESCE(SUM(final_amount - IFNULL(refund_amount, 0)), 0) FROM pos_order WHERE tenant_id=? AND store_id=? AND deleted=0 AND status IN (3,4) AND pay_time BETWEEN ? AND ?',
    '元', '成交总额扣除退款', 'v1'),
('ORDER_COUNT',   '订单数',       'SALES',     'COUNT(*) WHERE status IN (3,4)',
    'SELECT COUNT(*) FROM pos_order WHERE tenant_id=? AND store_id=? AND deleted=0 AND status IN (3,4) AND pay_time BETWEEN ? AND ?',
    '单', '已完成订单数', 'v1'),
('AVG_ORDER',     '客单价',       'SALES',     'NET_REVENUE / ORDER_COUNT',
    'SELECT IFNULL(SUM(final_amount - IFNULL(refund_amount,0))/NULLIF(COUNT(*),0), 0) FROM pos_order WHERE tenant_id=? AND store_id=? AND deleted=0 AND status IN (3,4) AND pay_time BETWEEN ? AND ?',
    '元', '平均每单成交金额', 'v1'),
('REFUND_AMOUNT', '退款金额',     'PAY',       'SUM(refund_amount)',
    'SELECT COALESCE(SUM(IFNULL(refund_amount,0)), 0) FROM pos_order WHERE tenant_id=? AND store_id=? AND deleted=0 AND pay_time BETWEEN ? AND ?',
    '元', '区间内退款总额', 'v1'),
('MEMBER_NEW',    '新增会员',     'MEMBER',    'COUNT(*) WHERE created_at BETWEEN ? AND ?',
    'SELECT COUNT(*) FROM crm_member WHERE tenant_id=? AND store_id=? AND deleted=0 AND created_at BETWEEN ? AND ?',
    '人', '区间内新增会员数', 'v1'),
('MEMBER_ACTIVE', '活跃会员',     'MEMBER',    'COUNT(DISTINCT member_id) WHERE order_time BETWEEN ? AND ?',
    'SELECT COUNT(DISTINCT member_id) FROM pos_order WHERE tenant_id=? AND store_id=? AND deleted=0 AND pay_time BETWEEN ? AND ?',
    '人', '区间内下过单的会员数', 'v1'),
('COMMISSION_TOTAL', '提成总额', 'COMMISSION','SUM(commission_amount) WHERE status IN (1,2,3)',
    'SELECT COALESCE(SUM(commission_amount), 0) FROM commission_record WHERE tenant_id=? AND store_id=? AND deleted=0 AND status IN (1,2,3) AND business_time BETWEEN ? AND ?',
    '元', '区间内所有提成（含未发放）', 'v1');

-- 6. 演示快照（区间为 2099-01 全月）
INSERT IGNORE INTO report_metric_snapshot (tenant_id, store_id, snapshot_date, metric_key, metric_name, metric_value)
SELECT 1, 1, DATE(snapshot_date), mc.metric_key, mc.metric_name, 0
FROM (
    SELECT '2099-01-01' snapshot_date UNION SELECT '2099-01-15'
) t
CROSS JOIN report_metric_caliber mc
WHERE mc.deleted = 0 AND mc.version = 'v1';

-- 7. 验证
SELECT 'report_export_task' tbl, COUNT(*) cnt FROM report_export_task
UNION ALL SELECT 'report_metric_snapshot', COUNT(*) FROM report_metric_snapshot
UNION ALL SELECT 'report_metric_caliber', COUNT(*) FROM report_metric_caliber;
