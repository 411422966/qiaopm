-- =============================================
-- I128 战役：早期字符集问题导致的中文乱码修复
-- 生成日期：2026-11-05
-- 数据库：meiyetec @ localhost:3307 (MySQL 8.0.46)
-- 覆盖范围：9 张表 / 核心字典与主数据
--
-- ★★★ 执行前置条件（务必遵守）★★★
--  1. 本文件必须保存为 UTF-8 **无 BOM**
--     （MySQL 客户端遇 BOM 会报 ERROR 1064 / Unknown command '\U'）
--  2. 必须通过 cmd 执行，禁用 PowerShell 的 | 管道或 < 重定向（会重编码，破坏中文）：
--       cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot123456 -P3307 meiyetec < backup\i128\fix-i128-charset.sql"
--  3. 执行后立刻跑 fix-i128-verify.sql
--  4. 建议先在备份库演练
--
-- ★ 乱码分类与修复策略
--  - A 类（GBK 误解码）：字节完整，理论上可程序化逆转
--  - B 类（字面 ? 替换）：字节已丢失为 0x3F，不可逆
--  - 本脚本对 A 类同样使用权威源硬编码重填，而非 CONVERT 反转，
--    规避二次转码风险；权威值已与 CONVERT 反转结果交叉验证逐字一致
--
-- ★ 权威源对照
--  - seed_test_roles.sql:36-39
--  - migrate_r3_0_c_menu_role.sql:13-17, 23, 26, 29, 32
--  - migrate_pos_008_refund_audit_chain.sql
--  - init_technician_level.sql / migrate_p0_2_p0_5_rbac_registration.sql
--  - migrate_r3_0_h_p2_10_payment_brand_hq_complaint.sql:221-262
--  - init_attendance.sql:97
--
-- ★ 覆盖 9 张表
--    1. sys_user                 (4 行)
--    2. sys_role                 (5 行)
--    3. sys_role_data_scope      (4 行)
--    4. sys_permission           (11 行)
--    5. pos_refund_audit_rule    (21 行)
--    6. complaint_category       (8 行)
--    7. payment_channel          (8 行)
--    8. hq_dashboard_widget      (6 行)
--    9. attendance_config        (1 行)
-- =============================================

USE meiyetec;

START TRANSACTION;

-- =============================================
-- 1. sys_user.real_name —— 4 个测试账号（B 类：字面 ?，不可逆）
--    坏值 hex：3F3F3F3F3F3F
--    前置检查确认：clerk01/manager01/boss01/operator01 均仅 tenant_id=1
--    权威：seed_test_roles.sql:36-39
-- =============================================
UPDATE sys_user SET real_name = '测试店员' WHERE username = 'clerk01'    AND tenant_id = 1;
UPDATE sys_user SET real_name = '测试店长' WHERE username = 'manager01' AND tenant_id = 1;
UPDATE sys_user SET real_name = '测试老板' WHERE username = 'boss01'     AND tenant_id = 1;
UPDATE sys_user SET real_name = '测试运营' WHERE username = 'operator01' AND tenant_id = 1;

-- =============================================
-- 2. sys_role.role_name + description —— 5 个角色（B 类）
--    权威：migrate_r3_0_c_menu_role.sql:13-17
--    WHERE 用 role_code（唯一键）
-- =============================================
UPDATE sys_role SET role_name = '店员',     description = '一线操作员：收银/预约/会员/库存/基础资料' WHERE role_code = 'CLERK';
UPDATE sys_role SET role_name = '店长',     description = '门店管理者：店员 + 提成/报表/营销/医美合规'   WHERE role_code = 'MANAGER';
UPDATE sys_role SET role_name = '老板',     description = '企业主：店长 + 客户运营/组织架构/系统设置'   WHERE role_code = 'BOSS';
UPDATE sys_role SET role_name = '运营',     description = '运营专员：数据报表/营销工具/客户运营'         WHERE role_code = 'OPERATOR';
UPDATE sys_role SET role_name = '系统管理员', description = '技术管理员：全部菜单，含研发工具'         WHERE role_code = 'ADMIN';

-- =============================================
-- 3. sys_role_data_scope.remark —— 4 行（B 类）
--    权威：migrate_r3_0_c_menu_role.sql:23, 26, 29, 32
--    WHERE 用 role_id + remark LIKE '%?%'（remark 字段本身就是乱码，
--    不依赖 data_scope 值，避免未来修改 data_scope 导致漏匹配）
--    role_id 5=MANAGER 6=BOSS 7=OPERATOR 8=ADMIN
-- =============================================
UPDATE sys_role_data_scope SET remark = '店长仅当前门店数据' WHERE role_id = 5 AND remark LIKE '%?%';
UPDATE sys_role_data_scope SET remark = '老板可见全部门店'   WHERE role_id = 6 AND remark LIKE '%?%';
UPDATE sys_role_data_scope SET remark = '运营可见全部门店'   WHERE role_id = 7 AND remark LIKE '%?%';
UPDATE sys_role_data_scope SET remark = '管理员跨租户'       WHERE role_id = 8 AND remark LIKE '%?%';

-- =============================================
-- 4. sys_permission.permission_name —— 11 行（A+B 混合）
--    权威：migrate_pos_008_refund_audit_chain.sql
--          init_technician_level.sql / migrate_p0_2_p0_5_rbac_registration.sql
--    WHERE 用 permission_code（唯一键）
--
--    4.1 含字面 ? 的 6 行（B 类，不可逆）
-- =============================================
UPDATE sys_permission SET permission_name = '退款审核-店长'     WHERE permission_code = 'pos:refund:audit:l1';
UPDATE sys_permission SET permission_name = '退款审核-区域经理' WHERE permission_code = 'pos:refund:audit:l2';
UPDATE sys_permission SET permission_name = '退款审核-财务总监' WHERE permission_code = 'pos:refund:audit:l3';
UPDATE sys_permission SET permission_name = '退款审核规则'       WHERE permission_code = 'pos:refund:audit:rule';
UPDATE sys_permission SET permission_name = '技师等级'           WHERE permission_code = 'employee:level';
UPDATE sys_permission SET permission_name = '耗材绑定-新增'     WHERE permission_code = 'material:binding:add';

--    4.2 纯 GBK 误解码的 5 行（A 类，可逆，但统一用权威源硬编码重填）
--    坏值不含 ?，仅按 '%?%' 扫描会漏掉，必须靠 CONVERT 比对发现
--    权威：migrate_p0_2_p0_5_rbac_registration.sql:15,16,49,61,69
--      绛夌骇鏉冪泭  -> 等级权益      (employee:level:benefit, sort_order=92, path=/technician/benefit)
--      绛夌骇璇勫畾  -> 等级评定      (employee:level:eval,    sort_order=93, path=/technician/evaluation)
--      椤圭洰鑰楁潗閰嶇疆 -> 项目耗材配置 (material:binding,   sort_order=80, path=/material/service-material)
--      鑰楁潗缁戝畾-缂栬緫 -> 耗材绑定-编辑 (material:binding:edit)
--      鑰楁潗缁戝畾-鍒犻櫎 -> 耗材绑定-删除 (material:binding:del)
-- =============================================
UPDATE sys_permission SET permission_name = '等级权益'     WHERE permission_code = 'employee:level:benefit';
UPDATE sys_permission SET permission_name = '等级评定'     WHERE permission_code = 'employee:level:eval';
UPDATE sys_permission SET permission_name = '项目耗材配置' WHERE permission_code = 'material:binding';
UPDATE sys_permission SET permission_name = '耗材绑定-编辑' WHERE permission_code = 'material:binding:edit';
UPDATE sys_permission SET permission_name = '耗材绑定-删除' WHERE permission_code = 'material:binding:del';

-- =============================================
-- 5. pos_refund_audit_rule.audit_level_name —— 21 行（A 类：GBK 误解码，可逆）
--    权威：migrate_pos_008_refund_audit_chain.sql
--    坏值在 7 个租户（1 / 2 / 900007 / 900010 / 900013 / 900016 / 900019）
--    下完全一致，hex 分别为：
--      level1 搴楅暱      E690B4E6A585E69AB1
--      level2 鍖哄煙缁忕悊  E98D96E59384E78599E7BC81E5BF95E6828A
--      level3 璐㈠姟鎬荤洃  E79290E388A0E5A79FE98EACE88DA4E6B483
--    WHERE 用 audit_level + 旧值双重条件，保证只改乱码行
-- =============================================
UPDATE pos_refund_audit_rule SET audit_level_name = '店长'
 WHERE audit_level = 1 AND audit_level_name = '搴楅暱';
UPDATE pos_refund_audit_rule SET audit_level_name = '区域经理'
 WHERE audit_level = 2 AND audit_level_name = '鍖哄煙缁忕悊';
UPDATE pos_refund_audit_rule SET audit_level_name = '财务总监'
 WHERE audit_level = 3 AND audit_level_name = '璐㈠姟鎬荤洃';

-- =============================================
-- 6. complaint_category.category_name —— 8 行（A+B 混合）
--    权威：migrate_r3_0_h_p2_10_payment_brand_hq_complaint.sql:234
--    WHERE 用 category_code + tenant_id（唯一键）
-- =============================================
UPDATE complaint_category SET category_name = '服务态度' WHERE category_code = 'SERVICE'     AND tenant_id = 1;
UPDATE complaint_category SET category_name = '服务效果' WHERE category_code = 'QUALITY'     AND tenant_id = 1;
UPDATE complaint_category SET category_name = '账单争议' WHERE category_code = 'BILLING'     AND tenant_id = 1;
UPDATE complaint_category SET category_name = '环境卫生' WHERE category_code = 'ENVIRONMENT' AND tenant_id = 1;
UPDATE complaint_category SET category_name = '设备故障' WHERE category_code = 'EQUIPMENT'   AND tenant_id = 1;
UPDATE complaint_category SET category_name = '产品质量' WHERE category_code = 'PRODUCT'     AND tenant_id = 1;
UPDATE complaint_category SET category_name = '隐私投诉' WHERE category_code = 'PRIVACY'     AND tenant_id = 1;
UPDATE complaint_category SET category_name = '其他'     WHERE category_code = 'OTHER'       AND tenant_id = 1;

-- =============================================
-- 7. payment_channel.channel_name —— 8 行（A+B 混合）
--    权威：migrate_r3_0_h_p2_10_payment_brand_hq_complaint.sql:221
--    WHERE 用 channel_code（UNIQUE 键）
-- =============================================
UPDATE payment_channel SET channel_name = '微信支付'   WHERE channel_code = 'WECHAT';
UPDATE payment_channel SET channel_name = '支付宝'     WHERE channel_code = 'ALIPAY';
UPDATE payment_channel SET channel_name = '银联'       WHERE channel_code = 'UNIONPAY';
UPDATE payment_channel SET channel_name = '数字人民币' WHERE channel_code = 'DCEP';
UPDATE payment_channel SET channel_name = '银行转账'   WHERE channel_code = 'BANK';
UPDATE payment_channel SET channel_name = '现金'       WHERE channel_code = 'CASH';
UPDATE payment_channel SET channel_name = '会员储值'   WHERE channel_code = 'WALLET';
UPDATE payment_channel SET channel_name = '预付卡'     WHERE channel_code = 'CARD';

-- =============================================
-- 8. hq_dashboard_widget.widget_name —— 6 行（A+B 混合）
--    权威：migrate_r3_0_h_p2_10_payment_brand_hq_complaint.sql:256
--    WHERE 用 widget_code + tenant_id
-- =============================================
UPDATE hq_dashboard_widget SET widget_name = '门店总数' WHERE widget_code = 'TOTAL_STORES'  AND tenant_id = 1;
UPDATE hq_dashboard_widget SET widget_name = '总营收'   WHERE widget_code = 'TOTAL_REVENUE' AND tenant_id = 1;
UPDATE hq_dashboard_widget SET widget_name = '总订单'   WHERE widget_code = 'TOTAL_ORDERS'  AND tenant_id = 1;
UPDATE hq_dashboard_widget SET widget_name = '会员总数' WHERE widget_code = 'TOTAL_MEMBERS' AND tenant_id = 1;
UPDATE hq_dashboard_widget SET widget_name = '新增会员' WHERE widget_code = 'NEW_MEMBERS'   AND tenant_id = 1;
UPDATE hq_dashboard_widget SET widget_name = '门店排行' WHERE widget_code = 'TOP_STORES'    AND tenant_id = 1;

-- =============================================
-- 9. attendance_config.config_name —— 1 行（A 类）
--    权威：init_attendance.sql:97
--    WHERE 用 id + store_id IS NULL + tenant_id
-- =============================================
UPDATE attendance_config SET config_name = '通用考勤配置'
 WHERE id = 1 AND store_id IS NULL AND tenant_id = 1;

COMMIT;
