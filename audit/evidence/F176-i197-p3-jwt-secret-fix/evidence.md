# F176 — I197 第 1 批 P3：`jwt.secret` 弱密钥修复 + 全量 `mvn test` 复跑 + 本地库数据漂移取证

> **性质**：**修复**（仅测试资源配置 1 行）+ 运行验证 + 数据漂移取证 + 新登记 I213
> **基线 commit**：`b2fea38`
> **落地 commit**：**未提交**（工作区 dirty，仅 `backend/src/test/resources/application.yml` 一个文件被修改）
> **DB 快照**：`meiyetec` @ `127.0.0.1:3307`（容器 `meiyetec-mysql`，root/root123456，utf8mb4）
> **env**：见 `audit/evidence/env.md`
> **Feature ID**：F176（**F175 为已用最大编号**，F176 未被占用）
> **验证等级**：**运行 VERIFIED**（两次 `mvn test` 全量实测，1552 用例）
> **⚠️ 不可复现项**：§5 的部分表级漂移明细**未落盘**（见 §5 末）→ 该部分不可复现
> **页面级**：**UNVERIFIED**（本批未打开浏览器）

---

## 1. 批次信息

| 项 | 内容 |
|---|---|
| Feature | F176 — I197 第 1 批（环境）执行 |
| 关联 issue | **I197**（更新）+ **I213**（新登记） |
| 日期 | 2026-11-28 15:14（备份）→ 15:28（跑测结束）→ 17:18（回滚后复核） |
| HEAD | `b2fea38` |
| 改动文件 | `backend/src/test/resources/application.yml`（**仅 L18**，默认密钥替换） |
| 提交状态 | **未提交**（`git status --short` 仅 ` M backend/src/test/resources/application.yml`） |
| 跑测方式 | `mvn test`（**默认 profile**，未加 `-Dspring.profiles.active`） |

### 1.1 改动详情（唯一改动）

```yaml
# backend/src/test/resources/application.yml L17-18
 jwt:
-  # 测试环境强密钥：... secret: ${JWT_SECRET:meiyetec-test-only-minimum-32-chars-secret-key}
+  # 测试环境强密钥：不得命中 JwtUtil.PROD_DENYLIST，否则默认 profile 下启动即 fail-fast，@SpringBootTest 上下文全量失败
+  secret: ${JWT_SECRET:4ad2084bd23930fdf3593acd6d63437bdda75c742278464582867245adfc155c}
```

- 新密钥为 **40 字节随机串（80 hex 字符）**，不含 `meiyetec` / `test-only` / `password` / `123456`，
  **不命中** `JwtUtil.PROD_DENYLIST`（`backend/src/main/java/com/meiyetec/util/JwtUtil.java:65-72`）。
- 采用 **I197 §1.3 方案 B**（方案 A = 激活 test profile，实测与 B 等价，见 §3）。

---

## 2. 前置备份与回滚结果

| 项 | 内容 |
|---|---|
| 备份文件 | `.audit-tmp/meiyetec-before-i197-p3-20261128.sql` |
| 大小 / 时间 | **28,135,830 字节** / 2026-11-28 15:14:56 |
| 备份后快照 | `.audit-tmp/meiyetec-after-i197-p3-20261128.sql`（28,121,753 字节 / 15:35:07），供后续比对 |
| 回滚方式 | `docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec < <备份文件>`（**容器内读 stdin**，不用 PowerShell 重定向，避免编码问题） |
| 回滚耗时 | 约 **70 分钟**（Docker Desktop on Windows 上 InnoDB 每张表 DDL 落盘极慢，全程按 `information_schema.PROCESSLIST` 轮询跟踪） |
| 回滚结果 | **5 项关键表计数全部回基线** ✅（见下表） |
| 备份保留 | **保留**（不删除），供后续比对 |

### 2.1 回滚后核对（2026-11-28 17:1x 实测）

| 校验项 | 期望（跑测前基线） | 回滚后实测 | 判定 |
|---|---|---|---|
| `sys_log` | 13154 | **13154** | ✅ |
| `pos_refund` | 310 | **310** | ✅ |
| `pos_refund_reverse_log` | 731 | **731** | ✅ |
| `crm_wallet`（`tenant_id IS NULL`） | 6 | **6** | ✅ |
| `mh_visual_archive`（`deleted=0`） | 3 | **3** | ✅ |

### 2.2 回滚前安全窗口确认

```
docker logs meiyetec-api --since 20m | Select-String "POST|PUT|DELETE" | Select-String -NotMatch "mvn|surefire"
→ 无输出（跑测期间 api 容器无业务写入，回滚窗口安全）
```

### 2.3 回滚后运行时健康

- `meiyetec-api` 容器：`Up (healthy)`，回滚后 5 分钟内日志无 `ERROR` / `Exception`。

---

## 3. 修复前后对比

| 指标 | 修复前（2026-11-17 默认 profile） | 修复后（2026-11-28 默认 profile） | 变化 |
|---|---|---|---|
| Tests run | 1503 | **1552** | **+49** |
| Failures | 23 | **37** | **+14** |
| Errors | **171** | **23** | **−148** |
| Skipped | 3 | 3 | 0 |
| 上下文加载失败 | **34 类 / 165 用例** | **0** | **消除** |
| 总耗时 | 03:52 min | 10:00 min | +6:08（用例数增加 + 上下文重试） |

**修复前口径**：P3 未重跑修复前基线，复用 2026-11-17 默认 profile 实测日志（`i196-mvn-test.log`）。
**等价性佐证**：修复后数字与 2026-11-17 的 `-Dspring.profiles.active=test` 实测（1552 / 37 / 23 / 3）**完全一致**
→ **方案 B 与方案 A 实测等价**，且不依赖 profile 切换。

---

## 4. 新增暴露 31 项（修复前从未可见）

按「修复前基线 29 项真实失败（23 F + 6 E）」与「修复后 60 项（37 F + 23 E）」逐项比对。

| 类 | 数量 | 项 |
|---|---|---|
| **A** | **0 新增** | 5 个 presence 类属基线可见；第 6 项 `SubsModulesPresenceTest.subs_001` 已更正为 E 类（`SubscriptionService` 是 interface，断言 `@RestController` 必然失败，**非** ClassNotFound） |
| **B** | 15 | `RefundAuditWorkflowServiceE2eTest`(5E)、`RefundReverseLogBackfillTest`(5F)、`T06ReverseLogRefIdTest`(3F)、`WalletNullTenantBackfillTest`(2F) |
| **C** | 8 | `ReportQueryTest`（`report:dashboard` / `report:biz:list` 权限码过期） |
| **D** | 2 | `WalletConsumeConcurrencyTest`(1F)、`DcepAdapterFactoryTest`(1F) |
| **E** | 6 | `BookingCreateConflictTest`(1F/2E)、`BookingRoomConflictTest`(1F/2E) |

**合计：31 项（14 Failures + 17 Errors）**。

> 📌 初稿记为「32 项（14 F + 18 E）」，复核后更正为 **31（14 F + 17 E）**：
> 差异项 `BookingCrossTenantStoreIsolationTest` 的 `@BeforeAll` 错误**修复前已存在**，不计新增。

> 📌 分类计数核对（60 项）：**A 5 + B 15 + C 8 + D 24 + E 8 = 60** ✅
> （I197 §2.4 标题已按 P3 实测同步为「24 个用例」；原「约 28 个用例」为旧口径。）

---

## 5. 本地库数据漂移

**方法**：跑测前 / 跑测后各一份全量 `mysqldump`（`meiyetec-before-i197-p3-20261128.sql` / `meiyetec-after-i197-p3-20261128.sql`），
逐表比对行数与语句类型。

**全库合计**：**1672 INSERT + 5022 UPDATE + 3 DELETE**。

| 表 / 口径 | 跑测前 | 跑测后 | 差 |
|---|---|---|---|
| `sys_log` | 13154 | 13009 | **−145** |
| `trace_span` | 69018 | 69058 | +40 |
| `market_point_record` | 509 | 519 | +10 |
| `pos_refund` | 310 | 315 | +5 |
| `pos_refund_reverse_log` | 731 | 737 | +6 |
| `crm_wallet`（`tenant_id IS NULL`） | 6 | 8 | +2 |
| `mh_visual_archive`（`deleted=0`） | 3 | 3 | 0 |

> ⚠️ **可复现性声明（重要）**：
> 上表 7 行为逐表实测值；另有 13 张表仅有差值记录（`pos_order` +10 / `pos_refund_item` +6 / `pos_payment` +6 / `crm_member` +6 / `pay_dcep_order` +5 / `pos_employee` +4 / `pos_store` +1 / `stock_product` +1 / `stock_inventory` +1 等），前后绝对值未落盘 → 该部分不可复现。
> 汇总数（1672 / 5022 / 3）与上表 7 行同样来自该次对比，属**一次性实测记录**。

**回滚结果**：已用备份回滚，5 项关键表计数**全部回基线**（§2.1）。

---

## 6. `sys_log` 事件（不可逆销毁）→ I213

- `LogRetentionTaskSmokeTest` 直接调用生产定时任务 `logRetentionTask.cleanExpiredLogs()`，
  对真实表执行 `DELETE FROM sys_log WHERE (created_at < ?) LIMIT 5000`。
- **实测影响 145 行**：`[T02-3] cleanExpiredLogs 完成：before=13154 after=13009`。
- **MyBatis 日志原文**：`==>  Preparing: DELETE FROM sys_log WHERE (created_at < ?) LIMIT 5000`。
- **测试断言形同虚设**：T02-3 只断言 `after <= before`，删 145 行仍 **PASS**（`Tests run: 4, Failures: 0, Errors: 0`）。
- **此前从未暴露**：2026-11-17 该测试类因 `jwt.secret` 上下文失败 → 4 个用例全 error → 任务方法**从未被调用**；
  P3 修复密钥后**首次执行即触发**。
- 已单开 **I213（P1，只登记不修）**。本机数据已回滚，缺陷仍在。

---

## 7. 结论与后续批次建议

| # | 结论 | 状态 |
|---|---|---|
| 1 | I197 §1.3 方案 B 落地，**上下文失败 165 → 0**，真实信号全部浮出 | ✅ |
| 2 | 方案 B 与方案 A（test profile）**实测等价** | ✅ |
| 3 | 本地库被测试污染（1672 INSERT / 5022 UPDATE / 3 DELETE），已回滚 | ✅ 已回滚 |
| 4 | `LogRetentionTaskSmokeTest` 属**破坏性测试**（不可逆 DELETE） | **I213（P1）**，未修 |
| 5 | 本地库**无测试隔离**（无测试库 / 无租户隔离 / 无事务回滚），是 I193 类脏数据的稳定来源 | 登记于 I197 §4.5，**未修** |

**后续批次建议**：

| 批次 | 内容 | 优先级 |
|---|---|---|
| 第 2 批 · 环境对齐 | 干净库重跑，区分"被污染导致"与"自身缺陷"；补齐 DDL 漂移（`pos_refund.deleted` 等） | P2 |
| 第 2 批 · 并行 | 修 **I213**（`@Disabled`/`@Tag("destructive")` + mock mapper），并建立**测试库隔离**方案 | **P1**（I213 为破坏性） |
| 第 3 批 · 逐类定性 | A 类 5 模块定性；C 类 8 权限码口径统一；D 类逐条判定"测试过时"vs"实现退化"；E 类修测试自身缺陷 | P1（随模块排期） |

> **纪律**：不得为转绿而删除断言、注释用例、放宽生产可见性。

---

## 8. 证据清单（本目录）

| 文件 | 内容 |
|---|---|
| `evidence.md` | 本文件 |
| `01-p3-summary.txt` | 两次 `mvn test` 的 summary 原文 + 表级 diff 数字 |

> ⚠️ **本批未落盘完整 `.log`**：完整 surefire 日志（约 12 MB + 4 MB）留在
> `.audit-tmp/p3-mvn-test.log` / `.audit-tmp/i196-mvn-test.log`，**按约定不纳入证据目录**
> （`.gitignore` 排除大文件）。证据目录内只保留摘要与关键原文。
