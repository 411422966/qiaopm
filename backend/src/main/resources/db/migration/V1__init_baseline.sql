-- ============================================================
-- 缇庝笟 SaaS - V0 Baseline ALL-in-One 鏁村悎鑴氭湰
-- 鍒涘缓鏃ユ湡锛?026-08-14
-- 鐢ㄩ€旓細鎶?init_migration_v0_baseline.sql 鍏ㄩ儴 SOURCE 灞曞紑涓哄崟鏂囦欢
-- 閫傞厤锛歅owerShell 绠￠亾锛坄Get-Content xxx.sql | mysql ...`锛?-- 娉ㄦ剰锛氬幓鎺変簡閲嶅 init_m05_package_part2.sql / init_m05_seed.sql
--       淇濈暀 init_m05_package.sql锛堝凡鍚?4 濂楅 + 1 璁㈠崟 + 1 鏍搁攢锛?--       淇濈暀 init_m09_commission_v2.sql锛堝凡鍚?settlement 涓?record 涓よ〃锛?-- ============================================================


-- ============================================================
-- 1. 鐢ㄦ埛/瑙掕壊/鏉冮檺 + sys_login_log
-- 鏉ヨ嚜锛歩nit_m01_auth.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '鐢ㄦ埛ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '鐢ㄦ埛鍚?,
    password VARCHAR(100) NOT NULL COMMENT '瀵嗙爜锛圔Crypt鍔犲瘑锛?,
    real_name VARCHAR(50) COMMENT '鐪熷疄濮撳悕',
    phone VARCHAR(20) COMMENT '鎵嬫満鍙?,
    email VARCHAR(100) COMMENT '閭',
    avatar VARCHAR(255) COMMENT '澶村儚URL',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0绂佺敤 1鍚敤',
    store_id BIGINT COMMENT '鎵€灞為棬搴桰D',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT DEFAULT 0 COMMENT '鍒犻櫎鏍囪锛?鏈垹闄?1宸插垹闄?,
    INDEX idx_username (username),
    INDEX idx_store_id (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绯荤粺鐢ㄦ埛琛?;

CREATE TABLE IF NOT EXISTS `sys_role` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '瑙掕壊ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '瑙掕壊浠ｇ爜',
    role_name VARCHAR(50) NOT NULL COMMENT '瑙掕壊鍚嶇О',
    description VARCHAR(255) COMMENT '鎻忚堪',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0绂佺敤 1鍚敤',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT DEFAULT 0 COMMENT '鍒犻櫎鏍囪',
    INDEX idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绯荤粺瑙掕壊琛?;

CREATE TABLE IF NOT EXISTS `sys_permission` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '鏉冮檺ID',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '鏉冮檺浠ｇ爜',
    permission_name VARCHAR(50) NOT NULL COMMENT '鏉冮檺鍚嶇О',
    permission_type VARCHAR(20) DEFAULT 'button' COMMENT '鏉冮檺绫诲瀷锛歮enu鎸夐挳',
    parent_id BIGINT DEFAULT 0 COMMENT '鐖剁骇ID',
    path VARCHAR(255) COMMENT '璺敱璺緞',
    icon VARCHAR(50) COMMENT '鍥炬爣',
    sort_order INT DEFAULT 0 COMMENT '鎺掑簭',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0绂佺敤 1鍚敤',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT DEFAULT 0 COMMENT '鍒犻櫎鏍囪',
    INDEX idx_permission_code (permission_code),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏉冮檺琛?;

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '鐢ㄦ埛ID',
    role_id BIGINT NOT NULL COMMENT '瑙掕壊ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛瑙掕壊鍏宠仈琛?;

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    role_id BIGINT NOT NULL COMMENT '瑙掕壊ID',
    permission_id BIGINT NOT NULL COMMENT '鏉冮檺ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='瑙掕壊鏉冮檺鍏宠仈琛?;

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '鏃ュ織ID',
    user_id BIGINT DEFAULT NULL COMMENT '鐢ㄦ埛ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '鐢ㄦ埛鍚?,
    tenant_id BIGINT DEFAULT NULL COMMENT '鎵€灞炵鎴稩D',
    login_type VARCHAR(20) DEFAULT 'password' COMMENT '鐧诲綍绫诲瀷',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0澶辫触 1鎴愬姛',
    message VARCHAR(255) DEFAULT NULL COMMENT '澶囨敞/閿欒淇℃伅',
    ip VARCHAR(50) DEFAULT NULL COMMENT '瀹㈡埛绔疘P',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'UA',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐧诲綍鏃ュ織琛?;

INSERT INTO sys_role (role_code, role_name, description) VALUES
('SUPER_ADMIN', '瓒呯骇绠＄悊鍛?, '绯荤粺瓒呯骇绠＄悊鍛橈紝鎷ユ湁鎵€鏈夋潈闄?),
('STORE_ADMIN', '闂ㄥ簵绠＄悊鍛?, '闂ㄥ簵绠＄悊鍛橈紝绠＄悊闂ㄥ簵鎵€鏈変笟鍔?),
('EMPLOYEE', '鍛樺伐', '鏅€氬憳宸ワ紝鎷ユ湁鍩虹鎿嶄綔鏉冮檺');

INSERT INTO sys_user (username, password, real_name, phone, status) VALUES
('admin', '$2a$10$6I0.9H0hK9mbKFPnSClLBukD9oElGsqRJuDeop.XJzOVNHxWM7ZP2', '绯荤粺绠＄悊鍛?, '13800138000', 1);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN';

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, sort_order) VALUES
('dashboard', '棣栭〉', 'menu', 0, '/dashboard', 1),
('member', '浼氬憳绠＄悊', 'menu', 0, '/member', 2),
('booking', '棰勭害绠＄悊', 'menu', 0, '/booking', 3),
('pos', '鏀堕摱缁撶畻', 'menu', 0, '/pos', 4),
('inventory', '搴撳瓨绠＄悊', 'menu', 0, '/inventory', 5),
('employee', '鍛樺伐绠＄悊', 'menu', 0, '/employee', 6),
('marketing', '钀ラ攢宸ュ叿', 'menu', 0, '/marketing', 7),
('report', '鏁版嵁鎶ヨ〃', 'menu', 0, '/report', 8),
('setting', '绯荤粺璁剧疆', 'menu', 0, '/setting', 9);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order) VALUES
('member:list', '鏌ョ湅浼氬憳', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 1),
('member:add', '鏂板浼氬憳', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 2),
('member:edit', '缂栬緫浼氬憳', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 3),
('member:delete', '鍒犻櫎浼氬憳', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 4);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

-- ============================================================
-- 2. 闂ㄥ簵/鍛樺伐/绛夌骇/鍒嗙被 + sys_log
-- 鏉ヨ嚜锛歩nit_m02_basic.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `pos_store` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '闂ㄥ簵ID',
    store_name VARCHAR(100) NOT NULL COMMENT '闂ㄥ簵鍚嶇О',
    store_code VARCHAR(50) UNIQUE COMMENT '闂ㄥ簵缂栫爜',
    contact_person VARCHAR(50) COMMENT '鑱旂郴浜?,
    contact_phone VARCHAR(20) COMMENT '鑱旂郴鐢佃瘽',
    address VARCHAR(255) COMMENT '璇︾粏鍦板潃',
    province VARCHAR(50) COMMENT '鐪佷唤',
    city VARCHAR(50) COMMENT '鍩庡競',
    district VARCHAR(50) COMMENT '鍖哄幙',
    business_hours VARCHAR(100) COMMENT '钀ヤ笟鏃堕棿',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0鍋滀笟 1钀ヤ笟',
    logo_url VARCHAR(255) COMMENT '闂ㄥ簵Logo',
    description TEXT COMMENT '闂ㄥ簵绠€浠?,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT DEFAULT 0 COMMENT '鍒犻櫎鏍囪',
    INDEX idx_store_code (store_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闂ㄥ簵琛?;

CREATE TABLE IF NOT EXISTS `pos_employee` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '鍛樺伐ID',
    employee_code VARCHAR(50) UNIQUE COMMENT '鍛樺伐宸ュ彿',
    employee_name VARCHAR(50) NOT NULL COMMENT '鍛樺伐濮撳悕',
    phone VARCHAR(20) UNIQUE COMMENT '鎵嬫満鍙?,
    password VARCHAR(100) COMMENT '鐧诲綍瀵嗙爜',
    gender TINYINT COMMENT '鎬у埆锛?濂?1鐢?,
    id_card VARCHAR(20) COMMENT '韬唤璇佸彿',
    avatar_url VARCHAR(255) COMMENT '澶村儚',
    position VARCHAR(50) COMMENT '鑱屼綅',
    department VARCHAR(50) COMMENT '閮ㄩ棬/缁勫埆',
    store_id BIGINT NOT NULL COMMENT '鎵€灞為棬搴桰D',
    entry_date DATE COMMENT '鍏ヨ亴鏃ユ湡',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0绂昏亴 1鍦ㄨ亴 2浼戝亣',
    salary decimal(10,2) COMMENT '鍩烘湰宸ヨ祫',
    commission_rate DECIMAL(5,4) COMMENT '鎻愭垚姣斾緥',
    role_ids VARCHAR(255) COMMENT '瑙掕壊ID鍒楄〃锛堥€楀彿鍒嗛殧锛?,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT DEFAULT 0 COMMENT '鍒犻櫎鏍囪',
    INDEX idx_employee_code (employee_code),
    INDEX idx_phone (phone),
    INDEX idx_store_id (store_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍛樺伐琛?;

CREATE TABLE IF NOT EXISTS `mem_level` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '绛夌骇ID',
    level_code VARCHAR(50) NOT NULL UNIQUE COMMENT '绛夌骇缂栫爜',
    level_name VARCHAR(50) NOT NULL COMMENT '绛夌骇鍚嶇О',
    level_icon VARCHAR(255) COMMENT '绛夌骇鍥炬爣',
    min_points INT DEFAULT 0 COMMENT '鏈€浣庣Н鍒嗚姹?,
    max_points INT COMMENT '鏈€楂樼Н鍒嗚姹?,
    discount_rate DECIMAL(4,2) DEFAULT 1.00 COMMENT '鎶樻墸鐜囷紙0-1锛?,
    birthday_gift VARCHAR(100) COMMENT '鐢熸棩绀肩墿',
    birthday_discount DECIMAL(4,2) DEFAULT 1.00 COMMENT '鐢熸棩鎶樻墸',
    points_multiplier DECIMAL(3,2) DEFAULT 1.00 COMMENT '绉垎鍊嶆暟',
    level_color VARCHAR(20) DEFAULT '#409EFF' COMMENT '绛夌骇棰滆壊',
    sort_order INT DEFAULT 0 COMMENT '鎺掑簭',
    description TEXT COMMENT '绛夌骇璇存槑',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0绂佺敤 1鍚敤',
    tenant_id BIGINT DEFAULT NULL COMMENT '鎵€灞炵鎴稩D',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT DEFAULT 0 COMMENT '鍒犻櫎鏍囪',
    INDEX idx_level_code (level_code),
    INDEX idx_sort_order (sort_order),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浼氬憳绛夌骇琛?;

CREATE TABLE IF NOT EXISTS `sys_category` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '鍒嗙被ID',
    category_code VARCHAR(50) COMMENT '鍒嗙被缂栫爜',
    category_name VARCHAR(50) NOT NULL COMMENT '鍒嗙被鍚嶇О',
    parent_id BIGINT DEFAULT 0 COMMENT '鐖剁骇ID',
    level INT DEFAULT 1 COMMENT '灞傜骇',
    path VARCHAR(255) COMMENT '璺緞锛堥€楀彿鍒嗛殧锛?,
    sort_order INT DEFAULT 0 COMMENT '鎺掑簭',
    icon VARCHAR(50) COMMENT '鍥炬爣',
    category_type VARCHAR(20) DEFAULT 'goods' COMMENT '绫诲瀷锛歡oods鏈嶅姟椤圭洰 product鍟嗗搧',
    description VARCHAR(255) COMMENT '鎻忚堪',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0绂佺敤 1鍚敤',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    deleted TINYINT DEFAULT 0 COMMENT '鍒犻櫎鏍囪',
    INDEX idx_parent_id (parent_id),
    INDEX idx_category_type (category_type),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍒嗙被琛?;

CREATE TABLE IF NOT EXISTS sys_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '鏃ュ織ID',
    user_id BIGINT DEFAULT NULL COMMENT '鎿嶄綔鐢ㄦ埛ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '鎿嶄綔鐢ㄦ埛鍚?,
    tenant_id BIGINT DEFAULT NULL COMMENT '鎵€灞炵鎴稩D',
    module VARCHAR(50) DEFAULT NULL COMMENT '妯″潡',
    action VARCHAR(50) DEFAULT NULL COMMENT '鍔ㄤ綔',
    description VARCHAR(500) DEFAULT NULL COMMENT '鎿嶄綔鎻忚堪',
    request_url VARCHAR(255) DEFAULT NULL COMMENT '璇锋眰URL',
    request_method VARCHAR(10) DEFAULT NULL COMMENT '璇锋眰鏂规硶',
    request_params TEXT DEFAULT NULL COMMENT '璇锋眰鍙傛暟',
    ip VARCHAR(50) DEFAULT NULL COMMENT '瀹㈡埛绔疘P',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'UA',
    cost_ms INT DEFAULT NULL COMMENT '鑰楁椂(ms)',
    status TINYINT DEFAULT 1 COMMENT '鐘舵€侊細0澶辫触 1鎴愬姛',
    error_msg TEXT DEFAULT NULL COMMENT '閿欒淇℃伅',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_module (module),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鎿嶄綔鏃ュ織琛?;

INSERT INTO pos_store (store_name, store_code, contact_person, contact_phone, address, province, city, district, business_hours, status) VALUES
('缇庝笟SaaS绀鸿寖搴?, 'STORE001', '绠＄悊鍛?, '13800138001', '鍖椾含甯傛湞闃冲尯寤哄浗璺?8鍙?, '鍖椾含甯?, '鍖椾含甯?, '鏈濋槼鍖?, '09:00-21:00', 1);

INSERT INTO mem_level (level_code, level_name, min_points, max_points, discount_rate, level_color, sort_order, description) VALUES
('LEVEL_PURPLE', '绱捇浼氬憳', 0, 999, 0.95, '#9B59B6', 1, '鏂版敞鍐屼細鍛橈紝浜彈95鎶樹紭鎯?),
('LEVEL_GOLD', '閲戝崱浼氬憳', 1000, 4999, 0.90, '#F1C40F', 2, '绱娑堣垂1000绉垎锛屼韩鍙?鎶樹紭鎯?),
('LEVEL_BLACK', '榛戦噾浼氬憳', 5000, 9999, 0.85, '#2C3E50', 3, '绱娑堣垂5000绉垎锛屼韩鍙?5鎶樹紭鎯?),
('LEVEL_DIAMOND', '閽荤煶浼氬憳', 10000, NULL, 0.80, '#3498DB', 4, '绱娑堣垂10000绉垎锛屼韩鍙?鎶樹紭鎯狅紝鐢熸棩鍙屽€嶇Н鍒?);

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) VALUES
('GOODS', '鏈嶅姟椤圭洰', 0, 1, '0', 1, 'goods'),
('PRODUCT', '鍟嗗搧', 0, 1, '0', 2, 'product');

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'HAIR', '缇庡彂', id, 2, CONCAT('0,', id), 1, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'NAIL', '缇庣敳', id, 2, CONCAT('0,', id), 2, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'SKIN', '缇庡鎶よ偆', id, 2, CONCAT('0,', id), 3, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'COSMETICS', '鍖栧鍝?, id, 2, CONCAT('0,', id), 1, 'product' FROM sys_category WHERE category_code = 'PRODUCT';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'SKIN_CARE', '鎶よ偆鍝?, id, 2, CONCAT('0,', id), 2, 'product' FROM sys_category WHERE category_code = 'PRODUCT';

-- ============================================================
-- 3. 浼氬憳/鏍囩/妗ｆ + crm_wallet + crm_wallet_transaction
-- 鏉ヨ嚜锛歩nit_m03_member.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `crm_member_tag` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_name VARCHAR(50) NOT NULL,
    tag_color VARCHAR(20) DEFAULT '#409EFF',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag_name (tag_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浼氬憳鏍囩';

CREATE TABLE IF NOT EXISTS `crm_member` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    gender TINYINT DEFAULT 0,
    birthday DATE DEFAULT NULL,
    level_id BIGINT DEFAULT NULL,
    store_id BIGINT DEFAULT NULL,
    balance DECIMAL(12,2) DEFAULT 0.00,
    points INT DEFAULT 0,
    total_consume DECIMAL(12,2) DEFAULT 0.00,
    avatar VARCHAR(255) DEFAULT NULL,
    address VARCHAR(255) DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    join_date DATE DEFAULT (CURRENT_DATE),
    last_visit DATETIME DEFAULT NULL,
    tenant_id BIGINT DEFAULT NULL COMMENT '鎵€灞炵鎴稩D',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_phone (phone, deleted),
    UNIQUE KEY uk_member_no (member_no, deleted),
    KEY idx_name (name),
    KEY idx_level (level_id),
    KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浼氬憳';

CREATE TABLE IF NOT EXISTS `crm_member_profile` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    skin_type VARCHAR(50) DEFAULT NULL,
    hair_type VARCHAR(50) DEFAULT NULL COMMENT '鍙戣川绫诲瀷锛堢編鍙戝瓙琛屼笟鐢诲儚锛?026-09 M03 鎵╁睍锛?,
    nail_type VARCHAR(50) DEFAULT NULL COMMENT '鐢查潰绫诲瀷锛堢編鐢插瓙琛屼笟鐢诲儚锛?026-09 M03 鎵╁睍锛?,
    allergy VARCHAR(500) DEFAULT NULL,
    hobby VARCHAR(500) DEFAULT NULL,
    job VARCHAR(100) DEFAULT NULL,
    wechat VARCHAR(50) DEFAULT NULL,
    emergency_name VARCHAR(50) DEFAULT NULL,
    emergency_phone VARCHAR(20) DEFAULT NULL,
    consume_pref TEXT DEFAULT NULL,
    visit_record TEXT DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member (member_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浼氬憳妗ｆ';

CREATE TABLE IF NOT EXISTS `crm_member_tag_rel` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_tag (member_id, tag_id),
    KEY idx_member (member_id),
    KEY idx_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浼氬憳鏍囩鍏宠仈';

CREATE TABLE IF NOT EXISTS crm_wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '閽卞寘ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '绉熸埛ID',
    member_id BIGINT NOT NULL COMMENT '浼氬憳ID',
    balance DECIMAL(12,2) DEFAULT 0.00 COMMENT '鍙敤浣欓',
    total_recharge DECIMAL(12,2) DEFAULT 0.00 COMMENT '绱鍏呭€?,
    total_consume DECIMAL(12,2) DEFAULT 0.00 COMMENT '绱娑堣垂',
    frozen_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '鍐荤粨閲戦',
    version INT DEFAULT 0 COMMENT '涔愯閿佺増鏈彿',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
    UNIQUE KEY uk_member (member_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浼氬憳閽卞寘琛?;

CREATE TABLE IF NOT EXISTS crm_wallet_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '娴佹按ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '绉熸埛ID',
    member_id BIGINT NOT NULL COMMENT '浼氬憳ID',
    type TINYINT NOT NULL COMMENT '绫诲瀷锛?鍏呭€?2娑堣垂 3閫€娆?4璧犻€?5鍐荤粨 6瑙ｅ喕',
    amount DECIMAL(12,2) NOT NULL COMMENT '鍙樺姩閲戦锛堟鏁帮級',
    balance_before DECIMAL(12,2) DEFAULT NULL,
    balance_after DECIMAL(12,2) DEFAULT NULL,
    gift_amount DECIMAL(12,2) DEFAULT 0.00,
    pay_method VARCHAR(20) DEFAULT NULL,
    order_id BIGINT DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    remark VARCHAR(255) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閽卞寘娴佹按琛?;

INSERT INTO crm_member_tag (tag_name, tag_color, sort_order) VALUES
('VIP瀹㈡埛',    '#F56C6C', 1),
('鏁忔劅鑲?,    '#E6A23C', 2),
('鍥炲ご瀹?,    '#67C23A', 3),
('鏂板鎴?,    '#909399', 4),
('楂樻秷璐?,    '#409EFF', 5);

INSERT INTO crm_member (member_no, name, phone, gender, level_id, store_id, balance, points, total_consume, address, remark, tenant_id) VALUES
('M202607001', '鏉庨泤鐞?, '13800138001', 2, 1, 1, 5800.00, 2860, 12680.00, '鍖椾含甯傛湞闃冲尯鏈涗含SOHO', '蹇犲疄瀹㈡埛', 1),
('M202607002', '鐜嬫檽褰?, '13800138002', 2, 2, 1, 2200.00, 1200, 4580.00,  '鍖椾含甯傛捣娣€鍖轰腑鍏虫潙', '鏁忔劅鑲?,   1),
('M202607003', '寮犳晱鍗?, '13800138003', 2, 3, 2,  500.00,  350,  1200.00, '鍖椾含甯傛湞闃冲尯鍥借锤',   '',         1),
('M202607004', '闄堜附鍚?, '13800138004', 2, 1, 1,12800.00, 5200, 28900.00, '鍖椾含甯備笢鍩庡尯鐜嬪簻浜?, '楂樻秷璐筕IP', 1),
('M202607005', '鍒樿帋鑾?, '13800138005', 2, 2, 2, 3500.00, 1800,  7200.00, '鍖椾含甯傝タ鍩庡尯瑗垮崟',   '',         1),
('M202607006', '璧垫檽鐕?, '13800138006', 2, 1, 1,15600.00, 6800, 35800.00, '鍖椾含甯傛湞闃冲尯涓夐噷灞?, '鍥炲ご瀹?,   1),
('M202607007', '瀛欑編涓?, '13800138007', 2, 3, 1,  800.00,  480,  2400.00, '鍖椾含甯備赴鍙板尯鏂瑰簞',   '鏂板鎴?,   1),
('M202607008', '鍛ㄩ洩姊?, '13800138008', 2, 2, 2, 4500.00, 2200,  9800.00, '鍖椾含甯傛湞闃冲尯CBD',   '',         1);

INSERT INTO crm_member_profile (member_id, skin_type, allergy, hobby, job, wechat) VALUES
(1, '骞叉€ц倢鑲?,     '鑺辩矇', '鐟滀冀/鏃呮父', '澶栦紒缁忕悊', 'lyl_888'),
(2, '鏁忔劅鎬ц倢鑲?, '娴烽矞', '闃呰/鐢靛奖', '璁捐甯?,   'wxt_design'),
(3, '娣峰悎鎬ц倢鑲?, '鏃?,   '鍋ヨ韩',     '鏁欏笀',     'zmh_teacher'),
(4, '骞叉€ц倢鑲?,     '閰掔簿', '楂樺皵澶?绾㈤厭', '鎶曡祫浜?,   'clj_vip');

INSERT INTO crm_member_tag_rel (member_id, tag_id) VALUES
(1, 1), (1, 3),
(2, 2),
(3, 4),
(4, 1), (4, 5),
(6, 3), (6, 5),
(7, 4);

-- ============================================================
-- 4. 濂楅 + 璁㈠崟 + 鏍搁攢
-- 鏉ヨ嚜锛歩nit_m05_package.sql锛堝惈 4 濂楅 + 1 璁㈠崟 + 1 鏍搁攢锛?-- 娉細init_m05_package_part2.sql / init_m05_seed.sql 鍐呭閲嶅锛屽凡鐪佺暐
-- ============================================================
CREATE TABLE IF NOT EXISTS `crm_package` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) DEFAULT NULL,
    category VARCHAR(50) DEFAULT '閫氱敤',
    cover VARCHAR(255) DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    total_times INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    cost_price DECIMAL(12,2) DEFAULT 0.00,
    valid_days INT DEFAULT 365,
    sale_count INT DEFAULT 0,
    redeem_count INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_name (name),
    KEY idx_category (category),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='濂楅';

CREATE TABLE IF NOT EXISTS `crm_package_order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    order_no VARCHAR(32) NOT NULL,
    member_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    package_name VARCHAR(100) NOT NULL,
    total_times INT NOT NULL,
    remaining_times INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    pay_method VARCHAR(20) DEFAULT '寰俊',
    operator_id BIGINT DEFAULT NULL,
    buy_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME DEFAULT NULL,
    status TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no, deleted),
    KEY idx_member (member_id),
    KEY idx_package (package_id),
    KEY idx_status (status),
    KEY idx_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='濂楅璐拱璁板綍';

CREATE TABLE IF NOT EXISTS `crm_package_redemption` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    order_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    times INT DEFAULT 1,
    service_item VARCHAR(100) DEFAULT NULL,
    beautician_id BIGINT DEFAULT NULL,
    note VARCHAR(500) DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    redeem_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order (order_id),
    KEY idx_member (member_id),
    KEY idx_package (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='濂楅鏍搁攢璁板綍';

INSERT INTO crm_package (tenant_id, name, code, category, description, total_times, price, cost_price, valid_days, status) VALUES
(1, '闈㈤儴鍩虹鎶ょ悊 5 娆″崱',   'PKG-FACE-05', '闈㈤儴鎶ょ悊', '娣卞眰娓呮磥 + 淇濇箍鎶ょ悊锛? 娆′竴鐤楃▼',     5,   599.00,  200.00,  180, 1),
(1, '韬綋缁忕粶鐤忛€?10 娆″崱', 'PKG-BODY-10', '韬綋鎶ょ悊', '鍏ㄨ韩缁忕粶鐤忛€氭寜鎽╋紝10 娆″椁?,         10, 1980.00,  600.00,  365, 1),
(1, '鑲╅鑸掔紦 20 娆″勾鍗?,   'PKG-NECK-20', '鑲╅鎶ょ悊', '閫傚悎涓婄彮鏃忥紝缂撹В鑲╅鍍电‖',           20, 3580.00, 1200.00,  365, 1),
(1, 'VIP 灏婁韩鏃犻檺娆″勾鍗?,   'PKG-VIP-999', 'VIP灏婁韩', '鍏ㄥ勾涓嶉檺娆℃姢鐞嗭紙鎸囧畾椤圭洰锛夛紝鍚笓灞為【闂?, 999, 18800.00, 6000.00, 365, 1);

INSERT INTO crm_package_order
(tenant_id, order_no, member_id, package_id, package_name, total_times, remaining_times, price, pay_method, operator_id, buy_time, expire_time, status, remark)
SELECT 1, 'PKG202608010001', m.id, p.id, p.name, p.total_times, p.total_times, p.price, '寰俊', 1,
       NOW(), DATE_ADD(NOW(), INTERVAL p.valid_days DAY), 1, '娴嬭瘯鐢ㄨ喘涔拌褰?
FROM crm_member m, crm_package p
WHERE m.phone = '13800138001' AND p.code = 'PKG-FACE-05'
LIMIT 1;

INSERT INTO crm_package_redemption
(tenant_id, order_id, member_id, package_id, times, service_item, beautician_id, note, operator_id)
SELECT 1, o.id, o.member_id, o.package_id, 1, '闈㈤儴娓呮磥', 1, '绗竴娆″埌搴楁牳閿€', 1
FROM crm_package_order o
WHERE o.order_no = 'PKG202608010001'
LIMIT 1;

UPDATE crm_package_order o
SET o.remaining_times = o.remaining_times - 1
WHERE o.order_no = 'PKG202608010001';

UPDATE crm_package p
JOIN crm_package_order o ON o.package_id = p.id
SET p.sale_count = p.sale_count + 1, p.redeem_count = p.redeem_count + 1
WHERE o.order_no = 'PKG202608010001';

-- ============================================================
-- 5. 棰勭害/鎴块棿/鎺掔彮
-- 鏉ヨ嚜锛歩nit_m06_booking.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT NULL,
    store_id BIGINT DEFAULT NULL,
    room_name VARCHAR(50) NOT NULL,
    room_type VARCHAR(20) DEFAULT NULL,
    capacity INT DEFAULT 1,
    equipment VARCHAR(255) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    sort_no INT DEFAULT 0,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_tenant (tenant_id),
    INDEX idx_store (store_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鎴块棿琛?;

CREATE TABLE IF NOT EXISTS biz_employee_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT NULL,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(50) DEFAULT NULL,
    work_date DATE NOT NULL,
    shift_type TINYINT DEFAULT 4,
    start_time TIME DEFAULT NULL,
    end_time TIME DEFAULT NULL,
    max_bookings INT DEFAULT 8,
    status TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_employee (employee_id),
    INDEX idx_date (work_date),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鎶€甯堟帓鐝〃';

CREATE TABLE IF NOT EXISTS biz_booking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT NULL,
    booking_no VARCHAR(32) NOT NULL,
    member_id BIGINT NOT NULL,
    member_name VARCHAR(50) DEFAULT NULL,
    member_phone VARCHAR(20) DEFAULT NULL,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(50) DEFAULT NULL,
    service_item VARCHAR(100) DEFAULT NULL,
    room_id BIGINT DEFAULT NULL,
    room_name VARCHAR(50) DEFAULT NULL,
    booking_date DATE DEFAULT NULL,
    start_time DATETIME DEFAULT NULL,
    end_time DATETIME DEFAULT NULL,
    status TINYINT DEFAULT 1,
    source TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    checkin_time DATETIME DEFAULT NULL,
    complete_time DATETIME DEFAULT NULL,
    cancel_time DATETIME DEFAULT NULL,
    cancel_reason VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_member (member_id),
    INDEX idx_employee (employee_id),
    INDEX idx_room (room_id),
    INDEX idx_date (booking_date),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='棰勭害琛?;


INSERT INTO biz_room (tenant_id, store_id, room_name, room_type, capacity, equipment, status, sort_no) VALUES
(1, 1, 'VIP1鍙锋埧',  'VIP',     1, '鎸夋懇搴?娴寸几+闊冲搷', 1, 1),
(1, 1, 'VIP2鍙锋埧',  'VIP',     1, '鎸夋懇搴?娴寸几',      1, 2),
(1, 1, '鏍囧噯3鍙锋埧', '鍗曚汉闂?,  1, '鎸夋懇搴?,           1, 3),
(1, 1, '鏍囧噯4鍙锋埧', '鍗曚汉闂?,  1, '鎸夋懇搴?,           1, 4);

INSERT INTO biz_employee_schedule
  (tenant_id, employee_id, employee_name, work_date, shift_type, start_time, end_time, max_bookings, status)
SELECT 1, e.id, e.employee_name,
  DATE_ADD(CURDATE(), INTERVAL d.n DAY),
  4, '09:00:00', '18:00:00', 8, 1
FROM pos_employee e
JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) d
WHERE e.deleted = 0 AND e.status = 1;

INSERT INTO biz_booking
  (tenant_id, booking_no, member_id, member_name, member_phone, employee_id, employee_name,
   service_item, room_id, room_name, booking_date, start_time, end_time, status, source, remark, operator_id)
VALUES
(1, 'BK20260801100001', 1, '鏉庨泤鐞?, '13800138001', 4, '鐜嬮洩姊?, '闈㈤儴娓呮磥', 1, 'VIP1鍙锋埧', CURDATE(),
   TIMESTAMP(CURDATE(),'10:00:00'), TIMESTAMP(CURDATE(),'11:00:00'), 4, 1, '宸插畬鎴愮殑鏈嶅姟', 1),
(1, 'BK20260801110001', 1, '鏉庨泤鐞?, '13800138001', 5, '璧垫檽褰?, '鑲╅鎸夋懇', 2, 'VIP2鍙锋埧', CURDATE(),
   TIMESTAMP(CURDATE(),'11:00:00'), TIMESTAMP(CURDATE(),'12:00:00'), 3, 1, '宸茬鍒版湭瀹屾垚', 1),
(1, 'BK20260801140001', 1, '鏉庨泤鐞?, '13800138001', 6, '瀛欎附鍗?, '韬綋缁忕粶', 3, '鏍囧噯3鍙锋埧', CURDATE(),
   TIMESTAMP(CURDATE(),'14:00:00'), TIMESTAMP(CURDATE(),'15:00:00'), 2, 1, '宸茬‘璁ら绾?, 1),
(1, 'BK20260801150001', 2, '寮犻洦娑?, '13800138002', 4, '鐜嬮洩姊?, '闈㈤儴鎶ょ悊', 1, 'VIP1鍙锋埧', DATE_ADD(CURDATE(), INTERVAL 1 DAY),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY),'15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY),'16:00:00'), 1, 1, '鏄庡ぉ涓嬪崍', 1),
(1, 'BK20260801090001', 3, '闄堟€濈惇', '13800138003', 7, '鍛ㄦ晱',   '瓒抽儴鎶ょ悊', 4, '鏍囧噯4鍙锋埧', CURDATE(),
   TIMESTAMP(CURDATE(),'09:00:00'), TIMESTAMP(CURDATE(),'10:00:00'), 5, 2, '瀹㈡埛涓存椂鍙栨秷', 1),
(1, 'BK20260802090001', 4, '榛勬ⅵ鐟?, '13800138004', 4, '鐜嬮洩姊?, '闈㈤儴娓呮磥', 2, 'VIP2鍙锋埧', DATE_ADD(CURDATE(), INTERVAL 2 DAY),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY),'09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY),'10:00:00'), 1, 1, '鏂颁汉浣撻獙', 1);

-- ============================================================
-- 6. POS
-- 鏉ヨ嚜锛歩nit_m07_pos.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `biz_service` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint,
  `service_code` varchar(64),
  `service_name` varchar(128) NOT NULL,
  `category_id` bigint,
  `unit_price` decimal(10,2) NOT NULL,
  `member_price` decimal(10,2),
  `unit` varchar(16) DEFAULT '娆?,
  `duration` int DEFAULT 0,
  `description` varchar(500),
  `image_url` varchar(500),
  `is_package` tinyint DEFAULT 0,
  `status` tinyint DEFAULT 1,
  `sort_order` int DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_biz_service_store` (`store_id`),
  INDEX `idx_biz_service_cat` (`category_id`)
) COMMENT='鏈嶅姟/浜у搧琛?;

CREATE TABLE IF NOT EXISTS `pos_order` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `order_no` varchar(64) UNIQUE NOT NULL,
  `member_id` bigint,
  `member_name` varchar(64),
  `member_phone` varchar(20),
  `booking_id` bigint,
  `type` tinyint NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) DEFAULT 0,
  `final_amount` decimal(12,2) NOT NULL,
  `paid_amount` decimal(12,2) DEFAULT 0,
  `cashier_id` bigint NOT NULL,
  `cashier_name` varchar(64),
  `remark` varchar(255),
  `status` tinyint DEFAULT 1,
  `pay_time` datetime,
  `refund_time` datetime,
  `refund_amount` decimal(12,2) DEFAULT 0,
  `refund_reason` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_order_store` (`store_id`),
  INDEX `idx_pos_order_member` (`member_id`),
  INDEX `idx_pos_order_date` (`store_id`, `create_time`)
) COMMENT='鏀堕摱璁㈠崟琛?;

CREATE TABLE IF NOT EXISTS `pos_order_item` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `order_id` bigint NOT NULL,
  `service_id` bigint,
  `service_name` varchar(128) NOT NULL,
  `quantity` int DEFAULT 1,
  `unit_price` decimal(10,2) NOT NULL,
  `total_price` decimal(12,2) NOT NULL,
  `discount_amount` decimal(10,2) DEFAULT 0,
  `final_price` decimal(12,2) NOT NULL,
  `package_order_id` bigint,
  `package_times` int DEFAULT 0,
  `employee_id` bigint,
  `employee_name` varchar(64),
  `remark` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_item_order` (`order_id`)
) COMMENT='璁㈠崟鏄庣粏琛?;

CREATE TABLE IF NOT EXISTS `pos_payment` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `order_id` bigint NOT NULL,
  `payment_no` varchar(64) UNIQUE NOT NULL,
  `method` varchar(32) NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` tinyint DEFAULT 0,
  `transaction_id` varchar(128),
  `pay_time` datetime,
  `refund_time` datetime,
  `error_msg` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_payment_order` (`order_id`)
) COMMENT='鏀粯璁板綍琛?;

INSERT INTO `biz_service` (`service_code`, `service_name`, `unit_price`, `member_price`, `unit`, `duration`, `status`, `sort_order`) VALUES
('SV0001', '鍩虹闈㈤儴鎶ょ悊', 280.00, 198.00, '娆?, 60, 1, 1),
('SV0002', '娣卞眰琛ユ按鎶ょ悊', 480.00, 358.00, '娆?, 90, 1, 2),
('SV0003', '鑲╅鑸掔紦鎸夋懇', 180.00, 128.00, '娆?, 45, 1, 3),
('SV0004', '鍏ㄨ韩绮炬补SPA', 880.00, 698.00, '娆?, 120, 1, 4),
('SV0005', '缇庣潾瀚佹帴', 380.00, 288.00, '娆?, 90, 1, 5);

CREATE TABLE IF NOT EXISTS `sys_sequence` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `seq_name` varchar(64) UNIQUE,
  `current_val` bigint DEFAULT 0,
  `prefix` varchar(16),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='涓氬姟搴忓垪鍙疯〃';

-- ============================================================
-- 7. 搴撳瓨
-- 鏉ヨ嚜锛歩nit_m08_stock.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `stock_product` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_code` varchar(64) UNIQUE NOT NULL,
  `product_name` varchar(128) NOT NULL,
  `category` varchar(50) DEFAULT '鑰楁潗',
  `spec` varchar(100),
  `unit` varchar(16) DEFAULT '鐡?,
  `cost_price` decimal(12,2) DEFAULT 0,
  `sale_price` decimal(12,2) DEFAULT 0,
  `min_stock` int DEFAULT 10,
  `max_stock` int DEFAULT 1000,
  `enable_batch` tinyint DEFAULT 0,
  `status` tinyint DEFAULT 1,
  `remark` varchar(500),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_product_store` (`store_id`),
  INDEX `idx_stock_product_cat` (`category`)
) COMMENT='鍟嗗搧/鑰楁潗琛?;

CREATE TABLE IF NOT EXISTS `stock_inventory` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `batch_no` varchar(64) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT 0,
  `production_date` date,
  `expiry_date` date,
  `supplier` varchar(100),
  `last_in_time` datetime,
  `last_out_time` datetime,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_inv_store_product` (`store_id`, `product_id`),
  INDEX `idx_stock_inv_batch` (`batch_no`),
  INDEX `idx_stock_inv_expiry` (`expiry_date`)
) COMMENT='搴撳瓨琛紙鏀寔鎵规锛?;

CREATE TABLE IF NOT EXISTS `stock_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `batch_no` varchar(64),
  `type` tinyint NOT NULL,
  `quantity` int NOT NULL,
  `before_qty` int DEFAULT 0,
  `after_qty` int DEFAULT 0,
  `unit_price` decimal(12,2) DEFAULT 0,
  `total_amount` decimal(12,2) DEFAULT 0,
  `supplier` varchar(100),
  `order_id` bigint,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64),
  `remark` varchar(500),
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_record_product` (`product_id`),
  INDEX `idx_stock_record_type` (`type`),
  INDEX `idx_stock_record_time` (`record_time`)
) COMMENT='鍑哄叆搴撹褰?;

CREATE TABLE IF NOT EXISTS `stock_check` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `check_no` varchar(64) UNIQUE NOT NULL,
  `check_date` date NOT NULL,
  `total_items` int DEFAULT 0,
  `diff_items` int DEFAULT 0,
  `profit_qty` int DEFAULT 0,
  `loss_qty` int DEFAULT 0,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64),
  `status` tinyint DEFAULT 1,
  `remark` varchar(500),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_check_store` (`store_id`)
) COMMENT='搴撳瓨鐩樼偣鍗?;

INSERT INTO `stock_product` (tenant_id, store_id, product_code, product_name, category, spec, unit, cost_price, sale_price, min_stock, max_stock, enable_batch, status) VALUES
(1, 1, 'SP-BOT-001', '鐜诲翱閰哥簿鍗庢恫', '鑰楁潗', '30ml/鐡?,  '鐡?,  80.00, 198.00,  20,  500, 1, 1),
(1, 1, 'SP-MSK-002', '缇庣櫧琛ユ按闈㈣啘', '鑰楁潗', '25g*5鐗?鐩?, '鐩?, 35.00,  98.00,  30,  600, 1, 1),
(1, 1, 'SP-CRM-003', '淇濇箍闈㈤湝',     '鑰楁潗', '50g/鐡?,  '鐡?,  60.00, 158.00,  25,  400, 0, 1),
(1, 1, 'SP-OIL-004', '绮炬补鎸夋懇娌?,   '鑰楁潗', '100ml/鐡?, '鐡?, 45.00, 128.00,  15,  300, 0, 1),
(1, 1, 'SP-TOW-005', '涓€娆℃€ф瘺宸?,   '鑰楁潗', '80g/鏉?,  '鏉?,   2.00,   5.00, 100, 2000, 0, 1),
(1, 1, 'SP-CUP-006', '涓€娆℃€ф澂瀛?,   '鑰楁潗', '250ml/涓?, '涓?,   0.50,   2.00, 200, 3000, 0, 1);

INSERT INTO `stock_inventory` (tenant_id, store_id, product_id, batch_no, quantity, production_date, expiry_date, supplier, last_in_time)
SELECT 1, 1, id, CONCAT('BATCH-', DATE_FORMAT(NOW(), '%Y%m%d'), '-001'), 100, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), '骞垮窞缇庡渚涘簲鍟?, NOW()
FROM stock_product WHERE deleted = 0;

INSERT INTO `stock_record` (tenant_id, store_id, product_id, batch_no, type, quantity, before_qty, after_qty, unit_price, total_amount, supplier, operator_id, operator_name, remark)
SELECT 1, 1, id, CONCAT('BATCH-', DATE_FORMAT(NOW(), '%Y%m%d'), '-001'), 1, 100, 0, 100, cost_price, cost_price * 100, '骞垮窞缇庡渚涘簲鍟?, 1, 'admin', '鍒濆鍏ュ簱'
FROM stock_product WHERE deleted = 0;

INSERT INTO `stock_check` (tenant_id, store_id, check_no, check_date, total_items, operator_id, operator_name, status, remark)
VALUES (1, 1, CONCAT('CHK', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')), CURDATE(), 6, 1, 'admin', 1, '鏈堝垵鐩樼偣');

-- ============================================================
-- 8. 鎻愭垚锛坴2锛屽惈 settlement + record 涓よ〃锛?-- 鏉ヨ嚜锛歩nit_m09_commission_v2.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `commission_rule` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `calculation_method` varchar(16) NOT NULL,
  `rate` decimal(7,4) DEFAULT NULL,
  `fixed_amount` decimal(12,2) DEFAULT NULL,
  `priority` int NOT NULL DEFAULT 0,
  `effective_date` date NOT NULL,
  `expiry_date` date DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  INDEX `idx_comm_rule_match` (`tenant_id`, `store_id`, `business_type`, `target_id`, `status`),
  INDEX `idx_comm_rule_effective` (`effective_date`, `expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍛樺伐鎻愭垚瑙勫垯';

CREATE TABLE IF NOT EXISTS `commission_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `rule_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `source_type` varchar(20) NOT NULL,
  `source_id` bigint NOT NULL,
  `source_item_id` bigint NOT NULL,
  `order_no` varchar(64),
  `service_id` bigint,
  `service_name` varchar(128),
  `base_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `calculation_method` varchar(16) NOT NULL,
  `rate` decimal(7,4),
  `fixed_amount` decimal(12,2),
  `commission_amount` decimal(12,2) NOT NULL,
  `business_time` datetime NOT NULL,
  `salary_month` char(7) NOT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `invalid_reason` varchar(255),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_comm_source_item` (`source_type`, `source_item_id`),
  INDEX `idx_comm_record_employee_month` (`employee_id`, `salary_month`, `status`),
  INDEX `idx_comm_record_order` (`source_id`, `order_no`),
  INDEX `idx_comm_record_settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍛樺伐鎻愭垚璁板綍';

CREATE TABLE IF NOT EXISTS `commission_settlement` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `settlement_no` varchar(64) NOT NULL,
  `salary_month` char(7) NOT NULL,
  `employee_count` int NOT NULL DEFAULT 0,
  `total_commission` decimal(12,2) NOT NULL DEFAULT 0,
  `total_adjustment` decimal(12,2) NOT NULL DEFAULT 0,
  `total_payable` decimal(12,2) NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  `confirm_time` datetime,
  `confirm_by` bigint,
  `pay_time` datetime,
  `pay_by` bigint,
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  INDEX `idx_settlement_month` (`salary_month`, `store_id`),
  INDEX `idx_settlement_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鎻愭垚缁撶畻鍗?;

CREATE TABLE IF NOT EXISTS `commission_settlement_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL,
  `commission_record_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `base_salary` decimal(12,2) NOT NULL DEFAULT 0,
  `commission_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `adjustment_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `payable_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_settlement_commission` (`settlement_id`, `commission_record_id`),
  INDEX `idx_settlement_employee` (`settlement_id`, `employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缁撶畻鍗曚笌鎻愭垚璁板綍鍏宠仈';

INSERT INTO `commission_rule`
(`tenant_id`, `store_id`, `rule_name`, `business_type`, `target_id`, `calculation_method`, `rate`, `priority`, `effective_date`, `status`, `remark`)
SELECT 1, 1, 'service default 10%', 'SERVICE', NULL, 'RATE', 0.1000, 10, '2000-01-01', 1, 'm09 default'
WHERE NOT EXISTS (
  SELECT 1 FROM `commission_rule`
  WHERE `tenant_id`=1 AND `store_id`=1 AND `business_type`='SERVICE' AND `target_id` IS NULL AND `deleted`=0
);

-- ============================================================
-- 9. P0-DB-001 鏁版嵁淇锛堝鍎块噸娲?+ NULL tenant 鍥炲～锛?-- 鏉ヨ嚜锛歱atch_p0_db_001_data_repair.sql
-- 骞傜瓑锛氬凡淇鐨勬暟鎹簱鍐嶆鎵ц 0 褰卞搷琛?-- ============================================================
-- 淇 A: crm_wallet_transaction.operator_id 瀛ゅ効
UPDATE crm_wallet_transaction wt
INNER JOIN sys_user u ON u.username = 'admin'
SET wt.operator_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM sys_user u2 WHERE u2.id = wt.operator_id)
  AND wt.operator_id IS NOT NULL;

-- 淇 B: biz_booking.operator_id 瀛ゅ効
UPDATE biz_booking b
INNER JOIN sys_user u ON u.username = 'admin'
SET b.operator_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM sys_user u2 WHERE u2.id = b.operator_id)
  AND b.operator_id IS NOT NULL;

-- 淇 C: crm_wallet.tenant_id NULL 鍥炲～
UPDATE crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
SET w.tenant_id = m.tenant_id
WHERE w.tenant_id IS NULL
  AND m.tenant_id IS NOT NULL;

-- ============================================================
-- 鏈€缁堥獙鏀?-- ============================================================
-- SELECT '=== V0 Baseline ALL 杩佺Щ瀹屾垚 ===' AS step;

-- 6 寮?P0 琛?SELECT 'crm_wallet' AS tbl, COUNT(*) AS cnt FROM crm_wallet
-- UNION ALL SELECT 'crm_wallet_transaction', COUNT(*) FROM crm_wallet_transaction
-- UNION ALL SELECT 'biz_booking',              COUNT(*) FROM biz_booking
-- UNION ALL SELECT 'biz_room',                 COUNT(*) FROM biz_room
-- UNION ALL SELECT 'biz_employee_schedule',    COUNT(*) FROM biz_employee_schedule
-- UNION ALL SELECT 'sys_login_log',            COUNT(*) FROM sys_login_log
-- UNION ALL SELECT 'sys_log',                  COUNT(*) FROM sys_log;

-- 瀛ゅ効妫€鏌?SELECT '=== FK-5 wallet_txn orphans (鏈熸湜 0) ===' AS step;
-- SELECT COUNT(*) AS orphans FROM crm_wallet_transaction wt
-- WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = wt.operator_id)
  -- AND wt.operator_id IS NOT NULL;

-- SELECT '=== FK-10 booking orphans (鏈熸湜 0) ===' AS step;
-- SELECT COUNT(*) AS orphans FROM biz_booking b
-- WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id)
  -- AND b.operator_id IS NOT NULL;

-- NULL tenant
-- SELECT '=== crm_wallet NULL tenant (鏈熸湜 0) ===' AS step;
-- SELECT COUNT(*) AS null_tenants FROM crm_wallet WHERE tenant_id IS NULL;

-- 璺ㄧ鎴锋薄鏌?SELECT '=== cross-tenant contamination (鏈熸湜 0) ===' AS step;
-- SELECT COUNT(*) AS mismatches
-- FROM crm_wallet w
-- INNER JOIN crm_member m ON m.id = w.member_id
-- WHERE w.tenant_id IS NOT NULL
  -- AND m.tenant_id IS NOT NULL
  -- AND w.tenant_id <> m.tenant_id;
