-- ============================================================
-- 非正式工具（NOT a formal tool / 非守门脚本）
-- 用途：考勤 / 提成权限码绑定时间线（raw/13-perm-binding-timeline-20261117.txt）的只读回放
-- 场景：I183 / I184 核查收尾 —— 复核「11-16 23:34:53 临时授权撤销后，基线绑定关系回到原状」
-- 参数：MySQL 只读账号；执行方：mysql -h127.0.0.1 -P3307 -uroot -p*** --batch --raw meiyetec < 本文件
-- 退出码：0 = 全部查询成功；非 0 = 连接错误或语法错误
-- 失败处理：连接失败则不出结论，标 UNVERIFIED
-- 数据安全：**全部 SELECT / SHOW**，无任何写操作；不修改任何数据
-- 关联：audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md
--        audit/issues/I183-attendance-perm-codes-unbound.md
--        audit/issues/I184-commission-perm-codes-unbound.md
-- ============================================================
SET NAMES utf8mb4;

SELECT '=== A. sys_role_permission 总行数（基线 1164；夹具态 2047）===' AS s;
SELECT COUNT(*) AS rp_total FROM sys_role_permission;

SELECT '=== B. role 5 (MANAGER) 的绑定行数（基线 278；污染备份中 290 = 278 + 12 条临时授权）===' AS s;
SELECT r.id, r.role_code, r.role_name, COUNT(rp.id) AS binding_count
FROM sys_role r LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
WHERE r.id = 5 GROUP BY r.id, r.role_code, r.role_name;

SELECT '=== B2. 3 个夹具角色的绑定行数（污染备份中 291 / 290 / 290；当前库应全为 0）===' AS s;
SELECT rp.role_id, COUNT(*) AS binding_count
FROM sys_role_permission rp
WHERE rp.role_id IN (900004, 900005, 900006)
GROUP BY rp.role_id ORDER BY rp.role_id;

SELECT '=== C. 11-16 23:34:53 临时授权的 12 条权限码当前绑定数（期望全为 0）===' AS s;
SELECT p.id, p.permission_code, p.permission_name, p.permission_type,
       COUNT(rp.id) AS binding_count,
       GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS bound_role_ids
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.id IN (1451,1454,1455,1459,1330,1332,1333,1334,1335,1338,1342,1345)
GROUP BY p.id, p.permission_code, p.permission_name, p.permission_type
ORDER BY p.id;

SELECT '=== D. attendance 9 码（I183 死码）当前绑定数（期望全为 0）===' AS s;
SELECT p.id, p.permission_code, p.permission_type,
       COUNT(rp.id) AS binding_count,
       GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS bound_role_ids
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code LIKE 'attendance:%'
GROUP BY p.id, p.permission_code, p.permission_type
ORDER BY p.id;

SELECT '=== E. commission 全部码当前绑定数（I184：共 6 个码有绑定 = 4 个只读列表码 + 2 个 batch/reversal）===' AS s;
SELECT p.id, p.permission_code, p.permission_type,
       COUNT(rp.id) AS binding_count,
       GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS bound_role_ids
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code LIKE 'commission:%'
GROUP BY p.id, p.permission_code, p.permission_type
ORDER BY p.id;

SELECT '=== F. I186 验证夹具残留（期望全为 0）===' AS s;
SELECT COUNT(*) AS fixture_users FROM sys_user WHERE username LIKE 'i186fix%';
SELECT COUNT(*) AS fixture_roles FROM sys_role WHERE role_code LIKE 'I186FIX%';
SELECT COUNT(*) AS fixture_role_perm FROM sys_role_permission WHERE role_id >= 900000;
SELECT COUNT(*) AS fixture_user_role FROM sys_user_role WHERE user_id >= 99999700;
SELECT COUNT(*) AS fixture_role_scope FROM sys_role_data_scope WHERE role_id >= 900000;

SELECT '=== G. role 5 当前绑定的权限码清单（应仅含 4 个只读列表码 + 菜单码）===' AS s;
SELECT rp.permission_id, p.permission_code
FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
WHERE rp.role_id = 5 ORDER BY rp.permission_id;

SELECT '=== H. 近 30 天内对 sys_role_permission 的写入痕迹（binlog 侧核对用；表内无时间戳列）===' AS s;
SELECT 'sys_role_permission 表结构无 create_time/update_time 列 -> 写入时间只能由 binlog 核对'
       AS note, '参见 raw/13 §3（binlog.000139 / 000140 / 000141 解码）' AS ref;

SELECT '=== I. 备份文件污染计数（对备份 SQL 做文本统计，不连库）===' AS s;
SELECT '注意：sys_role_permission 的 INSERT 是「单行多元组」，用 Measure-Object 统计行数只会得到 1，' AS pitfall_1;
SELECT '必须按元组正则计数，下面给出可用命令与期望输出。' AS pitfall_2;
SELECT '【期望输出】11-17 污染备份：总元组 2047 / role 5 = 290 / 900004 = 291 / 900005 = 290 / 900006 = 290；' AS expected_20261117;
SELECT '              11-16 基线备份：总元组 1164 / role 5 = 278 / 三个夹具角色 = 0' AS expected_20261116;

SELECT '--- PowerShell 命令（逐个备份文件执行）---' AS cmd_section;
SELECT '$l=(Select-String -Path backup/meiyetec_20261117_020004.sql -Pattern ''INSERT INTO `sys_role_permission`'' | Select-Object -First 1).Line;' AS cmd_1;
SELECT '$r = foreach ($t in [regex]::Matches($l, ''[(]([0-9]+),([0-9]+),([0-9]+),''''([^'']*)''''[)]'')) { [pscustomobject]@{ role_id=[int]$t.Groups[2].Value; permission_id=[int]$t.Groups[3].Value; create_time=$t.Groups[4].Value } }' AS cmd_2;
SELECT '$r.Count;                                            # 期望 2047（11-17）/ 1164（11-16）' AS cmd_3;
SELECT '$r | Group-Object role_id | Where-Object { $_.Name -in 5,900004,900005,900006 } | ForEach-Object { "{0} -> {1}" -f $_.Name, $_.Count };' AS cmd_4;
SELECT '$r | Where-Object { $_.create_time -like ''2026-11-17 01:17:*'' } | Group-Object create_time;   # 870 + 1' AS cmd_5;
SELECT '$r | Where-Object { $_.create_time -eq ''2026-11-16 23:34:53'' } | Measure-Object | % Count;          # 期望 12（role 5 临时授权）' AS cmd_6;

SELECT '--- 夹具账号 / 角色 ID 的正确取值（勿混用 role_id 与 user_id）---' AS ids_section;
SELECT 'role_id : 900004 (I186FIX_S1) / 900005 (I186FIX_S5) / 900006 (I186FIX_MULTI)' AS role_ids;
SELECT 'user_id : 99999704 (i186fix_s1) / 99999705 (i186fix_s5) / 99999706 (i186fix_multi)' AS user_ids;
SELECT '来源：backup/meiyetec_20261117_020004.sql 的 sys_role / sys_user INSERT 元组' AS ids_src;

SELECT '=== J. 编号域对照（夹具 role_id 与种子账号 user_id 数值重叠，对象不同）===' AS s;
SELECT '⚠️ 以下 4 个数字在 sys_user 中是**种子账号**，而在 sys_role 中是**夹具角色**（现已删除）：' AS warning;
SELECT '  sys_user.id=900003 -> clerk01（种子账号） | sys_role.id=900003 -> 不存在（夹具段 900004~900006）' AS pair_900003;
SELECT '  sys_user.id=900004 -> manager01（种子账号）| sys_role.id=900004 -> I186FIX_S1（夹具）' AS pair_900004;
SELECT '  sys_user.id=900005 -> boss01（种子账号）   | sys_role.id=900005 -> I186FIX_S5（夹具）' AS pair_900005;
SELECT '  sys_user.id=900006 -> operator01（种子账号）| sys_role.id=900006 -> I186FIX_MULTI（夹具）' AS pair_900006;
SELECT '=> 种子 sys_role 只有 id 1~8（SUPER_ADMIN/STORE_ADMIN/EMPLOYEE/CLERK/MANAGER/BOSS/OPERATOR/ADMIN），' AS role_seed_range;
SELECT '   id >= 900000 的**只有夹具角色**；而 sys_user 里 id >= 900000 的是**正常种子账号** —— 两个编号段重叠但表不同。' AS collision_note;
SELECT '=> WHERE role_id=900004 命中夹具角色；WHERE user_id=900004 命中 manager01（正常账号），不是夹具账号' AS conclusion;
SELECT '=> 夹具账号只能按 user_id IN (99999704,99999705,99999706) 或 username LIKE ''i186fix%'' 定位' AS how_to_find;

SELECT '=== K. 回放实测记录（2026-11-18 21:5x 第三轮，docker exec meiyetec-mysql mysql --batch --raw）===' AS s;
SELECT '当前库（撤销后基线态）实际输出：' AS scope;
SELECT '  A. rp_total = 1164' AS k_a;
SELECT '  B. role 5 -> binding_count = 278' AS k_b;
SELECT '  B2. 900004/900005/900006 -> 无任何行（即三者均为 0）' AS k_b2;
SELECT '  C. 12 个临时授权码 binding_count 全为 0，bound_role_ids 全为 NULL' AS k_c;
SELECT '  D. attendance 9 码 binding_count 全为 0' AS k_d;
SELECT '  E. commission 有绑定的码恰为 6 个：rule:list / record:list / salary:list / settlement:list / batch / reversal（各 3 条，role 5,6,8）' AS k_e;
SELECT '  F. fixture_users=0 / fixture_roles=0 / fixture_role_perm=0 / fixture_user_role=0 / fixture_role_scope=0' AS k_f;
SELECT '⚠️ 口径提示：291/290/290 与 290 是**污染备份文件**里的数字（§I 的文本解析）；' AS tip_1;
SELECT '   本文件 §A~§H 连库执行得到的是**当前库**数字（1164 / 278 / 0）—— 两者不是同一口径，勿混用。' AS tip_2;
