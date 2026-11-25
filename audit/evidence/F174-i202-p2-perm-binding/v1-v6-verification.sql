-- ============================================================
-- F174 / I202-P2 A-mid — V1~V6 验证查询（只读 SELECT，无写操作）
-- 用途：验证 26 条 sys_role_permission 补绑的落地状态
-- 执行方式：docker exec meiyetec-mysql mysql -uroot -p*** --default-character-set=utf8mb4 meiyetec -t < 本文件
-- 执行时间：2026-11-26
-- ============================================================

-- ---------- V1 目标码绑定数快照（期望：iot:link:execute=0 / crm:care:recipe:list=3 / 其余见下）----------
SELECT 'V1' AS v, p.permission_code, p.id AS permission_id, COUNT(rp.id) AS binds
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN ('spa:scene:list','spa:scene:edit',
       'iot:link:list','iot:link:edit','iot:link:execute',
       'iot:rule:list','iot:rule:edit',
       'iot:alert:list','iot:alert:edit',
       'iot:data:list','iot:data:edit','crm:care:recipe:list')
GROUP BY p.permission_code, p.id ORDER BY p.permission_code;

-- ---------- V2 目标 26 行逐条核对（期望 26 行）----------
SELECT 'V2' AS v, rp.role_id, rp.permission_id, p.permission_code
FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
WHERE (rp.role_id, rp.permission_id) IN (
  (4,1564),(5,1564),(6,1564),(8,1564),
  (5,1565),(8,1565),
  (5,1479),(6,1479),(8,1479),
  (5,1552),(6,1552),(8,1552),
  (5,1548),(6,1548),(8,1548),
  (5,1550),(6,1550),(8,1550),
  (5,1553),(8,1553),
  (5,1549),(8,1549),
  (5,1551),(8,1551),
  (5,1480),(8,1480)
) ORDER BY rp.role_id, rp.permission_id;

-- ---------- V3 分角色总数（期望 1=118 2=47 4=55 5=314 6=229 7=105 8=425）----------
SELECT 'V3' AS v, rp.role_id, r.role_code, COUNT(*) AS cnt
FROM sys_role_permission rp LEFT JOIN sys_role r ON r.id = rp.role_id
GROUP BY rp.role_id, r.role_code ORDER BY rp.role_id;

-- ---------- V4 总数 + 交叉校验（期望 1293，且 118+47+0+55+314+229+105+425 = 1293）----------
SELECT 'V4' AS v, COUNT(*) AS total FROM sys_role_permission;
SELECT 'V4-cross' AS v, 118+47+0+55+314+229+105+425 AS cross_sum,
       (118+47+0+55+314+229+105+425) = (SELECT COUNT(*) FROM sys_role_permission) AS matches_total;

-- ---------- V5 孤儿行 / 重复组（期望 orphan=3 基线既有未动，dup_groups=0）----------
SELECT 'V5-orphan' AS v, COUNT(*) AS orphan_rows FROM sys_role_permission rp
LEFT JOIN sys_permission p ON p.id = rp.permission_id WHERE p.id IS NULL;
SELECT 'V5-dup' AS v, COUNT(*) AS dup_groups FROM (
  SELECT role_id, permission_id FROM sys_role_permission
  GROUP BY role_id, permission_id HAVING COUNT(*) > 1) t;

-- ---------- V6 负向对照：未被本批触碰的码（期望 iot:link:execute=0 / crm:care:recipe:list=3）----------
SELECT 'V6' AS v, p.permission_code, COUNT(rp.id) AS binds
FROM sys_permission p LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN ('iot:link:execute','crm:care:recipe:list')
GROUP BY p.permission_code;

-- ---------- V7 本批 26 行计数断言（期望 26）----------
SELECT 'V7-assert' AS v, COUNT(*) AS inserted_should_be_26 FROM sys_role_permission
WHERE (role_id, permission_id) IN (
  (4,1564),(5,1564),(6,1564),(8,1564),
  (5,1565),(8,1565),
  (5,1479),(6,1479),(8,1479),
  (5,1552),(6,1552),(8,1552),
  (5,1548),(6,1548),(8,1548),
  (5,1550),(6,1550),(8,1550),
  (5,1553),(8,1553),
  (5,1549),(8,1549),
  (5,1551),(8,1551),
  (5,1480),(8,1480)
);
