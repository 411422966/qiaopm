-- ============================================
-- 款式偏好与材料包管理
-- P0-M02-009：款式偏好与材料包管理
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 款式库
-- ============================================
DROP TABLE IF EXISTS biz_style_library;
CREATE TABLE biz_style_library (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    style_code VARCHAR(64) NOT NULL COMMENT '款式编号',
    style_name VARCHAR(128) NOT NULL COMMENT '款式名称',
    style_type VARCHAR(32) DEFAULT 'nail_art' COMMENT '款式类型',
    style_tag VARCHAR(64) COMMENT '风格标签',
    color_scheme VARCHAR(500) COMMENT '主色系JSON',
    difficulty_level INT DEFAULT 1 COMMENT '难度等级1-5',
    duration_minutes INT DEFAULT 60 COMMENT '预计耗时(分钟)',
    base_price DECIMAL(15,2) DEFAULT 0 COMMENT '基础价格',
    vip_price DECIMAL(15,2) DEFAULT 0 COMMENT 'VIP价格',
    main_image VARCHAR(500) COMMENT '主图URL',
    detail_images TEXT COMMENT '详情图JSON',
    video_url VARCHAR(500) COMMENT '视频URL',
    description TEXT COMMENT '款式描述',
    operation_steps TEXT COMMENT '操作步骤',
    required_tools VARCHAR(2000) COMMENT '关键工具JSON',
    technician_level VARCHAR(32) COMMENT '技师等级',
    view_count INT DEFAULT 0 COMMENT '浏览数',
    favorite_count INT DEFAULT 0 COMMENT '收藏数',
    use_count INT DEFAULT 0 COMMENT '使用次数',
    rating_score DECIMAL(3,2) DEFAULT 0 COMMENT '评分',
    status TINYINT DEFAULT 1 COMMENT '状态',
    is_new TINYINT DEFAULT 0 COMMENT '是否新品',
    is_hot TINYINT DEFAULT 0 COMMENT '是否热门',
    publish_employee_id BIGINT COMMENT '发布人ID',
    publish_employee_name VARCHAR(64) COMMENT '发布人姓名',
    publish_time DATETIME COMMENT '发布时间',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_style_code (style_code),
    INDEX idx_style_type (style_type),
    INDEX idx_style_tag (style_tag),
    INDEX idx_status (status),
    INDEX idx_is_new (is_new),
    INDEX idx_is_hot (is_hot),
    INDEX idx_use_count (use_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='款式库';

-- ============================================
-- 2. 客户款式偏好
-- ============================================
DROP TABLE IF EXISTS biz_member_style_preference;
CREATE TABLE biz_member_style_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    preferred_type VARCHAR(32) COMMENT '偏好类型',
    preferred_styles VARCHAR(500) COMMENT '偏好风格标签JSON',
    preferred_colors VARCHAR(500) COMMENT '偏好色系JSON',
    avoid_colors VARCHAR(500) COMMENT '忌讳色系JSON',
    preferred_length VARCHAR(32) COMMENT '偏好长度',
    preferred_shape VARCHAR(32) COMMENT '偏好形状',
    allergy_ingredients VARCHAR(500) COMMENT '过敏成分',
    preferred_technician_id BIGINT COMMENT '偏好技师ID',
    preferred_technician_name VARCHAR(64) COMMENT '偏好技师姓名',
    special_note VARCHAR(500) COMMENT '特殊备注',
    recent_styles VARCHAR(500) COMMENT '最近浏览款式',
    favorite_styles VARCHAR(500) COMMENT '收藏款式',
    history_styles VARCHAR(2000) COMMENT '历史订单款式',
    recommend_count INT DEFAULT 0 COMMENT '推荐次数',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_id (member_id),
    INDEX idx_preferred_type (preferred_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户款式偏好';

-- ============================================
-- 3. 材料包
-- ============================================
DROP TABLE IF EXISTS biz_material_package;
CREATE TABLE biz_material_package (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    package_code VARCHAR(64) NOT NULL COMMENT '材料包编号',
    package_name VARCHAR(128) NOT NULL COMMENT '材料包名称',
    package_type VARCHAR(32) DEFAULT 'kit' COMMENT '包类型',
    purpose VARCHAR(500) COMMENT '包用途',
    main_image VARCHAR(500) COMMENT '主图',
    total_cost DECIMAL(15,2) DEFAULT 0 COMMENT '总成本',
    sale_price DECIMAL(15,2) DEFAULT 0 COMMENT '售价',
    discount_rate DECIMAL(3,2) DEFAULT 1.0 COMMENT '折扣',
    final_price DECIMAL(15,2) DEFAULT 0 COMMENT '实际售价',
    style_count INT DEFAULT 0 COMMENT '包含款式数',
    serve_count INT DEFAULT 0 COMMENT '预计款式数',
    apply_scope VARCHAR(500) COMMENT '适用对象',
    tags VARCHAR(500) COMMENT '标签',
    activity_id BIGINT COMMENT '关联活动ID',
    status TINYINT DEFAULT 1 COMMENT '是否上架',
    sales_count INT DEFAULT 0 COMMENT '销量',
    stock_quantity INT DEFAULT 0 COMMENT '库存',
    creator_id BIGINT COMMENT '创建人ID',
    creator_name VARCHAR(64) COMMENT '创建人姓名',
    publish_time DATETIME COMMENT '发布日期',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_package_code (package_code),
    INDEX idx_package_type (package_type),
    INDEX idx_status (status),
    INDEX idx_sales_count (sales_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料包';

-- ============================================
-- 4. 材料包明细
-- ============================================
DROP TABLE IF EXISTS biz_material_package_item;
CREATE TABLE biz_material_package_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    package_id BIGINT NOT NULL COMMENT '材料包ID',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    product_name VARCHAR(128) COMMENT '耗材名称',
    spec VARCHAR(128) COMMENT '耗材规格',
    quantity DECIMAL(15,2) DEFAULT 1 COMMENT '数量',
    unit VARCHAR(16) COMMENT '单位',
    unit_price DECIMAL(15,2) DEFAULT 0 COMMENT '单价',
    subtotal DECIMAL(15,2) DEFAULT 0 COMMENT '小计',
    individually TINYINT DEFAULT 1 COMMENT '是否可独立购买',
    sort_order INT DEFAULT 0 COMMENT '排序',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_package_id (package_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材料包明细';

-- ============================================
-- 5. 款式包关联
-- ============================================
DROP TABLE IF EXISTS biz_package_style_relation;
CREATE TABLE biz_package_style_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    package_id BIGINT NOT NULL COMMENT '材料包ID',
    style_id BIGINT NOT NULL COMMENT '款式ID',
    style_name VARCHAR(128) COMMENT '款式名称',
    style_image VARCHAR(500) COMMENT '款式主图',
    difficulty_level INT DEFAULT 1 COMMENT '难度等级',
    duration_minutes INT DEFAULT 60 COMMENT '预计耗时',
    priority INT DEFAULT 5 COMMENT '主推顺序',
    style_price DECIMAL(15,2) DEFAULT 0 COMMENT '售价',
    required_products VARCHAR(500) COMMENT '配套单品ID',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_package_style (package_id, style_id),
    INDEX idx_style_id (style_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='款式包关联';

-- ============================================
-- 6. 款式浏览收藏日志
-- ============================================
DROP TABLE IF EXISTS biz_style_browse_log;
CREATE TABLE biz_style_browse_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    style_id BIGINT NOT NULL COMMENT '款式ID',
    action_type VARCHAR(16) DEFAULT 'view' COMMENT '行为类型',
    source VARCHAR(32) DEFAULT 'app' COMMENT '行为来源',
    dwell_time INT DEFAULT 0 COMMENT '停留时长(秒)',
    session_id VARCHAR(64) COMMENT '会话ID',
    action_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '行为时间',
    ip_address VARCHAR(64) COMMENT 'IP地址',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member_id (member_id),
    INDEX idx_style_id (style_id),
    INDEX idx_action_type (action_type),
    INDEX idx_action_time (action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='款式浏览收藏日志';

-- ============================================
-- 7. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('crm:style:library', '款式库管理', 'crm', 'menu', 1, 0, NOW()),
    ('crm:style:preference', '客户款式偏好', 'crm', 'menu', 1, 0, NOW()),
    ('stock:material:package', '材料包管理', 'stock', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 8. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/crm' AND deleted = 0 LIMIT 1),
    'CrmStylePackage', '/crm/style-package', '款式偏好', 'Picture', 6, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/crm/style-package' AND deleted = 0
);

-- ============================================
-- 9. 示例数据
-- ============================================
INSERT INTO biz_style_library (style_code, style_name, style_type, style_tag, color_scheme, difficulty_level, duration_minutes, base_price, is_new, is_hot, status, deleted, create_time)
VALUES
    ('STY001', '经典法式', 'nail_art', '法式', '["裸色","白色"]', 1, 45, 80, 0, 1, 1, 0, NOW()),
    ('STY002', '星空渐变', 'nail_art', '梦幻', '["蓝色","紫色","银色"]', 3, 90, 180, 1, 1, 1, 0, NOW()),
    ('STY003', '樱花渐变', 'nail_art', '甜美', '["粉色","白色"]', 2, 60, 120, 1, 1, 1, 0, NOW()),
    ('STY004', '猫眼闪粉', 'nail_art', 'ins风', '["金色","银色"]', 2, 50, 150, 0, 1, 1, 0, NOW()),
    ('STY005', '韩式美睫', 'eyelash', '韩系', '["黑色"]', 2, 90, 280, 1, 1, 1, 0, NOW());

INSERT INTO biz_material_package (package_code, package_name, package_type, purpose, sale_price, discount_rate, final_price, style_count, serve_count, status, stock_quantity, deleted, create_time)
VALUES
    ('PKG001', '美甲新手开店包', 'starter', '新店开业必备材料,内含80+款式所需', 2880, 0.85, 2448.00, 80, 100, 1, 50, 0, NOW()),
    ('PKG002', '经典法式套装', 'kit', '包含法式美甲全套耗材', 380, 0.9, 342.00, 1, 30, 1, 100, 0, NOW()),
    ('PKG003', '梦幻星空组合', 'combo', '星空渐变材料+饰品', 580, 0.8, 464.00, 5, 20, 1, 30, 0, NOW()),
    ('PKG004', '夏季清凉礼包', 'seasonal', '夏季清凉色系组合', 268, 0.85, 227.80, 10, 25, 1, 50, 0, NOW());