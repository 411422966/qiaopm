# F138 — BusinessPolicyDispatchController 4 表 MISSING 实证

> **运行时间**：2026-09-16 22:55-23:18 (UTC+8)
> **环境**：后端 8080 + MySQL 3307 + admin token
> **关联 Issue**：I063 (CRITICAL-P2) — 4 张表 Entity + Mapper 已建但从未 CREATE
> **Phase 5B 增量**：补 4 个端点 × 5 次 curl 实证 + 4 表 information_schema 确认

## 一、4 张缺失表（information_schema 实证）

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'meiyetec' AND table_name IN (
    'mkt_invitation', 'mkt_invitation_rule', 'mkt_dispatch', 'mkt_dispatch_target'
);
-- → 0 rows（4 张表全部不存在）
```

DB 中 mkt_% 总表数（信息来自 Phase 5A 实测）：16 张，但 4 张 I063 表**不在内**。

## 二、BusinessPolicyDispatchController 端点清单（共 9 端点）

| 端点 | HTTP | 表 | Phase 5B 实证 |
|---|---|---|---|
| `POST   /m12/dispatch` | POST | INSERT mkt_dispatch | ✅ **500 BadSqlGrammarException** traceId=`80c37dc34991488b91e8c36cb48ed5e4` |
| `POST   /m12/dispatch/{id}/publish` | POST | SELECT mkt_dispatch + mkt_dispatch_target | ⚠ 未实测（依赖 POST /dispatch 先成功创建）|
| `POST   /m12/dispatch/{id}/publish-async` | POST | 同上 | ⚠ 未实测 |
| `POST   /m12/dispatch/{id}/cancel` | POST | SELECT mkt_dispatch | ⚠ 未实测 |
| `GET    /m12/dispatch/page` | GET | SELECT mkt_dispatch | ✅ **500 数据库异常** traceId=`0a2b13c1eaab4cb495358a3e762467e3` |
| `GET    /m12/dispatch/{id}` | GET | SELECT mkt_dispatch WHERE id=? | ✅ **500 数据库异常** traceId=`882abc61c06443a28ead17529f1aeeb9` |
| `GET    /m12/dispatch/{id}/targets` | GET | SELECT mkt_dispatch_target | ✅ **500 数据库异常** traceId=`45261b3710b444e49baef509ec8ba78e` |
| `GET    /m12/dispatch/{id}/logs` | GET | SELECT business_policy_dispatch_log（已建表）| ⚠ 未实测 |
| `GET    /m12/dispatch/stats` | GET | SELECT mkt_dispatch + aggregation | ✅ **500 数据库异常** traceId=`97cee2d41b284998aabe21e4c1c857a8` |

**5B 增量实证（vs Phase 5A 仅 1 个 POST 端点）**：4 个额外 GET 端点全部 500 数据库异常。
**累计实证端点**：5 / 9（55.6% — 其余 4 个 POST 写端点依赖前序成功才能测试，table 不存在链路下注定失败）

## 三、后端日志证据（traceId=00e729b8fec64115b9a7547caaab30ab — Phase 5A）

```
2026-09-16 23:01:21.047 [http-nio-8080-exec-8] ERROR c.m.exception.GlobalExceptionHandler [traceId=00e729b8fec64115b9a7547caaab30ab] [DBError]
org.springframework.jdbc.BadSqlGrammarException:
### Error updating database.  Cause: java.sql.SQLSyntaxErrorException: Table 'meiyetec.mkt_dispatch' doesn't exist
### The error may exist in com/meiyetec/mapper/MktDispatchMapper.java
### SQL: INSERT INTO mkt_dispatch  ( tenant_id, dispatch_no, dispatch_type, dispatch_subtype, source_id, source_version, published_version, title, target_type, status, success_count, fail_count, total_count, operator_id, operator_name, remark, create_time, update_time ) VALUES (  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )
### Cause: java.sql.SQLSyntaxErrorException: Table 'meiyetec.mkt_dispatch' doesn't exist
    at com.meiyetec.service.impl.BusinessPolicyDispatchServiceImpl.createDispatch(BusinessPolicyDispatchServiceImpl.java:70)
```

**调用链**：MktDispatchMapper.insert → IService.save → BusinessPolicyDispatchServiceImpl.createDispatch:70

## 四、I063 关联（CRITICAL-P2）

- I063 issue 登记：`backend/src/main/java/com/meiyetec/service/impl/P1RemainingService.java:100-181` + `P2FeaturesService.java:217-343` 通过 `JdbcTemplate.update("UPDATE mkt_xxx ...")` **直写 SQL**（不经过 MyBatis Mapper）
- Entity + Mapper 已建：`MktInvitation.java` / `MktDispatch.java` / `MktDispatchTarget.java` / `MktInvitationRule.java`
- 表从未 CREATE：`db-snapshot.sql` 0 匹配 + information_schema 0 匹配
- 实证影响范围：BusinessPolicyDispatchController 9 端点（已实证 5 端点全部 500）

## 五、F138 Result

| 项 | 值 |
|---|---|
| **Result** | **UNVERIFIED → BROKEN 升级** |
| **核心证据** | 5/9 端点 curl 实测全部 500 数据库异常 + 4 表 information_schema 0 匹配 |
| **代码 vs DB 状态** | 业务入口聚合路径完整（Service + Mapper + Controller 三层就绪），但 4 张底层表从未 CREATE |
| **用户决策** | Phase 4 已决策：登记不修，Post-Audit 统一跑迁移（Q3 第六次统一口径） |

## 六、Evidence 文件清单

| 文件 | 内容 |
|---|---|
| `db-4tables-missing.txt` | 4 表 SQL 触发 ERROR 1146 (Phase 5A) |
| `server-log-trace.txt` | traceId=00e729b8fec64115b9a7547caaab30ab (Phase 5A) |
| `raw-response.json` | POST /m12/dispatch 单点响应 (Phase 5A) |
| `runtime-evidence.md` | 本文件 (Phase 5B 增量补 4 个端点 × 5 次 curl 实证) |
| `db-information-schema-4-tables.txt` | information_schema 0 匹配 |
| `raw-response-5-endpoints.txt` | 5 个端点响应汇总 |
