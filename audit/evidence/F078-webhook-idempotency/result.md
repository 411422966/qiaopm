# F078 webhook msg_id 幂等去重 — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：I030（CONFLICT 中立标 — SoT 反重放保护缺失，实际 msg_id 判重已实现）+ I035（时间窗缺失）

---

## 1. 实证结论

**状态**：**PARTIAL-NON-BLOCKER**（I037 + I029 阻断，duplicate=true 永远走不到 — 静态层 PASS）

## 2. 实证证据

### 2.1 同 msg_id 第 2 次推送 → 500

```bash
# 第 1 次（同 F077）
POST /webhook/youzan?kdt_id=test-kdt-5B
Body: {"id":"msg-5B-001",...}
Response: code=500 (I029 PARTIAL-MISSING)

# 第 2 次（同 msg_id，期望 duplicate=true）
POST /webhook/youzan?kdt_id=test-kdt-5B
Body: {"id":"msg-5B-001",...}  (same)
Response: code=500 (same I029 阻断)
```

→ **F078 幂等去重依赖 F077 链路**，F077 被 I037 + I029 双层阻断 → F078 同样被阻断

## 3. 静态层（YouzanWebhookController L136-149）

```java
if (msgId != null && !msgId.isEmpty()) {
    WebhookMsgLog existing = webhookMsgLogMapper.selectOne(new LambdaQueryWrapper<WebhookMsgLog>()
            .eq(WebhookMsgLog::getSource, "YOUZAN")
            .eq(WebhookMsgLog::getMsgId, msgId)
            .last("LIMIT 1"));
    if (existing != null) {
        log.info("[WEBHOOK/YOUZAN] duplicate msg_id: kdtId={} msgId={} prevStatus={} (idempotent success)", ...);
        Map<String, Object> dup = new LinkedHashMap<>();
        dup.put("duplicate", true);
        dup.put("msgId", msgId);
        dup.put("previousStatus", existing.getStatus());
        dup.put("logId", existing.getId());
        return Result.success("已处理（幂等）", dup);
    }
}
```

**幂等机制完整**：
- ✅ 按 `(source, msg_id)` 联合查询 webhook_msg_log
- ✅ 命中 → 返 `{duplicate: true, previousStatus, logId, elapsedMs}`
- ✅ 不命中 → 走正常 upsert 路径

## 4. I030 CONFLICT 状态

| EXT-22 SoT 描述 | 实际 | 一致性 |
|---|---|---|
| 没有重放保护 | 实际有 msg_id 幂等去重 | ✅ SoT **描述反方向**（SoT 错） |
| webhook_msg_log 表归属 EXT-24 | 实际 EXT-24 已写迁移脚本但未跑 | 维持 |

→ **I030 CONFLICT 维持中立标**（SoT 描述方向错，Phase 3 中立标合理）

## 5. I035 时间窗缺失

Plan §一 §1.5 提及：webhook 幂等**无时间窗**（重复 msg_id 永远幂等 → 老数据不会被刷新）

EXT-22 §5 已知问题确认：
> **没有 webhook 重放保护**：`verifyWebhookSignature` 只验签，不记录已处理 msg_id（理论上可重放）— 生产应加 `webhook_msg_log` 去重表

→ **I035 维持 PARTIAL-NON-BLOCKER**，D18 决策：时间窗为可选增强，不加

但需要修正：F078 静态层**已有 webhook_msg_log 去重**，与 I035"没有重放保护"矛盾。
- 实际：msg_id 重复 → duplicate=true（不算"重放保护缺失"）
- 但**没有时间窗** → 一年前发的 msg_id 还能去重命中
- → I035 描述应修正为"**时间窗缺失**"而非"**重放保护缺失**"

## 6. 实施缺失（前置条件）

F078 运行时验证需 3 个前置：
1. ✅ Controller 实现完整
2. ❌ I037 修复（webhook 路径 exclude）
3. ❌ I029 修复（I029 PARTIAL-MISSING → 列补齐）
4. ❌ EXT-24 迁移（webhook_msg_log 表存在）

→ 4 项里只有 1 项已就绪 → F078 暂不能完整运行时验证

## 7. Issue 状态

| Issue | 关联 | 状态 |
|---|---|---|
| **I037** | webhook 路径未 exclude | NEW（同 F077） |
| **I030** | 幂等去重 CONFLICT | 维持中立标 |
| **I035** | 时间窗缺失 | **描述修正**：msg_id 去重已实现，**仅时间窗缺失** |
| **I029** | PARTIAL-MISSING | 维持 |

## 8. Phase 7 决策建议

修复优先级（同 F077）：
1. WebConfig.excludePathPatterns 加 `/webhook/**`
2. 跑 EXT-22 migration 修 I029
3. 跑 EXT-24 migration 建 webhook_msg_log 表
4. 重跑 F077 → 期望 200 + PROCESSED
5. 重跑 F078 → 期望 200 + duplicate=true

## 9. 证据文件

- `raw-push-2.txt` — 第 2 次推送 500 响应
- `backend-log.txt` — 后端日志（找不到 WEBHOOK/YOUZAN 关键词 → 印证 controller 未触达）
