# batch1 观察期记录（2026-10-26 → 2026-11-02）

## 说明
batch1 权限对齐已上线。观察期内日常使用 5 个账号，发现异常记录到下方表格。
观察期结束（2026-11-02）统一分析，决定是否补进 batch2。

## 账号

测试账号统一密码见：`backend/src/main/resources/db/sql/seed_test_roles.sql`（4 个测试账号 + admin 均为 `123456`）

| 账号 | 角色 |
|---|---|
| admin | SUPER_ADMIN + ADMIN |
| boss01 | BOSS |
| manager01 | MANAGER |
| clerk01 | CLERK |
| operator01 | OPERATOR |

## 问题记录

| 日期 | 账号 | 路由 | 现象 | HTTP状态 | 怀疑原因 | 截图 | 处理 |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## 观察期结束后的结论（2026-11-02 填）
- [ ] 0 条问题 → batch1 完全成功
- [ ] 少量问题 → 分类：batch1 漏了 / 范围外
- [ ] 大量问题 → 暂停 batch2，先修 batch1