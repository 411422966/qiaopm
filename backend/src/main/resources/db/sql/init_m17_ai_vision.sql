-- ================================================================
-- M17-03 AI 视觉识别真实模型推理
-- 建表时间：2026-09-04
-- ================================================================

-- 1. CV 模型注册表
DROP TABLE IF EXISTS cv_model;
CREATE TABLE cv_model (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT DEFAULT 0 COMMENT '租户ID（0=全局）',
    model_code      VARCHAR(64) NOT NULL COMMENT '模型编码（如 TENCENT_FACE）',
    model_name      VARCHAR(128) NOT NULL COMMENT '模型名称',
    provider        VARCHAR(32) NOT NULL COMMENT '厂商：TENCENT/ALIYUN/BAIDU/AZURE/GOOGLE/LOCAL',
    model_type      VARCHAR(32) NOT NULL COMMENT '类型：FACE/SKIN/MEDICAL/SCENE/NAIL/HAIR',
    endpoint        VARCHAR(256) COMMENT 'API 端点 URL',
    api_key         VARCHAR(512) COMMENT 'API Key（加密存储）',
    api_secret      VARCHAR(512) COMMENT 'API Secret（加密存储）',
    app_id          VARCHAR(128) COMMENT '腾讯云 AppId / 阿里云 AppId',
    version         VARCHAR(32) DEFAULT '1.0' COMMENT '模型版本',
    accuracy        DECIMAL(5,2) COMMENT '准确率（%）',
    latency_ms      INT COMMENT '平均延迟（ms）',
    is_sandbox      TINYINT DEFAULT 1 COMMENT '沙箱模式：0=生产 1=沙箱',
    enabled         TINYINT DEFAULT 1 COMMENT '启用：0=禁用 1=启用',
    description     VARCHAR(500) COMMENT '描述',
    config_json     TEXT COMMENT '额外配置 JSON',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_id, model_code),
    INDEX idx_provider (provider),
    INDEX idx_model_type (model_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CV 模型注册表';

-- 2. CV 识别日志表
DROP TABLE IF EXISTS cv_recognition_log;
CREATE TABLE cv_recognition_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT COMMENT '租户ID',
    model_code      VARCHAR(64) COMMENT '调用的模型编码',
    biz_type        VARCHAR(32) NOT NULL COMMENT '业务类型：SKIN_TEST/FACE_RECOGNIZE/NAIL_TRYON/HAIR_ANALYSIS',
    image_url       VARCHAR(512) COMMENT '图像 URL',
    member_id       BIGINT COMMENT '会员ID',
    member_face_id  VARCHAR(128) COMMENT '人脸 ID（用于 1:N 搜索）',
    result_json     TEXT COMMENT '识别结果 JSON',
    confidence      DECIMAL(5,4) COMMENT '置信度（0~1）',
    latency_ms      INT COMMENT '实际延迟（ms）',
    provider        VARCHAR(32) COMMENT '实际调用的提供商',
    inference_mode  VARCHAR(16) DEFAULT 'http' COMMENT '推理模式：http/sdk/local/mock',
    status          VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/SKIPPED',
    error_message   VARCHAR(500) COMMENT '错误信息',
    request_payload TEXT COMMENT '请求载荷（调试）',
    response_payload TEXT COMMENT '响应载荷（调试）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_member (member_id),
    INDEX idx_model (model_code),
    INDEX idx_biz_type (biz_type),
    INDEX idx_created (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CV 识别日志';

-- 3. 权限
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('ai:vision:model', 'CV模型管理', 'ai', 'menu', 1, 0, NOW()),
    ('ai:vision:recognize', 'AI视觉识别', 'ai', 'menu', 1, 0, NOW()),
    ('ai:vision:log', '识别日志', 'ai', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 4. 示例模型数据
INSERT INTO cv_model (tenant_id, model_code, model_name, provider, model_type, endpoint, accuracy, latency_ms, is_sandbox, enabled, description)
VALUES
    (0, 'TENCENT_FACE',     '腾讯云人脸识别',     'TENCENT',  'FACE',    'https://iai.tencentcloudapi.com',          99.50, 300, 1, 1, '五官定位 + 颜值评分'),
    (0, 'ALI_SKIN_AI',     '阿里云皮肤检测',     'ALIYUN',   'SKIN',    'https://imageseg.cn-shanghai.aliyuncs.com', 94.20, 500, 1, 1, '肤质/色斑/皱纹'),
    (0, 'TENCENT_MED_AI',  '腾讯觅影',           'TENCENT',  'MEDICAL', 'https://medical.tencentcloudapi.com',        96.80, 800, 1, 1, '医学影像分析'),
    (0, 'BAIDU_FACE',      '百度人脸识别',       'BAIDU',    'FACE',    'https://aip.baidubce.com',                   98.70, 250, 1, 1, '1:N 人脸搜索'),
    (0, 'AZURE_CV',        'Azure Cognitive',    'AZURE',    'SCENE',   'https://api.cognitive.microsoft.com',         95.00, 400, 1, 1, '通用图像分析'),
    (0, 'GOOGLE_VISION',   'Google Vision',      'GOOGLE',   'SCENE',   'https://vision.googleapis.com',               97.00, 350, 1, 1, '海外首选'),
    (0, 'TENCENT_NAIL_AI', '腾讯美甲试戴',       'TENCENT',  'NAIL',    'https://iai.tencentcloudapi.com',             92.00, 600, 1, 1, '美甲款式试戴'),
    (0, 'LOCAL_SKIN_CNN',  '本地皮肤 CNN 模型',  'LOCAL',    'SKIN',    'http://localhost:8081/infer',               91.50, 50,  1, 1, '本地推理（低延迟）');
