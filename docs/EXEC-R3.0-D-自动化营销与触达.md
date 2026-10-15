# EXEC-R3.0-D：自动化营销与触达

> 范围：R3.0-D《自动化营销与触达》(T-D21 ~ T-D24)
> 关联文档：`init_rbac_v3_0_d.sql`、`MODULE-模块化开发验证体系.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-D21 | 消息渠道适配层（短信/微信/订阅消息） | ✅ | `ChannelSender` 接口 + SmsSender/MiniProgramSender/InAppSender + 限流 + 重试 |
| T-D22 | `marketing_touch_task` 任务调度 | ✅ | `MarketingAutomationService.task` CRUD + 启动/暂停/恢复/取消 |
| T-D23 | 营销旅程画布（阶段+触发+动作） | ✅ | `mkt_journey` + `mkt_journey_instance` + 节点 JSON 推进引擎 |
| T-D24 | 触达效果统计（ROI + 转化漏斗） | ✅ | `marketing_touch_conversion` + funnel/roi/by-channel 三个接口 |

---

## 2. 数据库设计

### 2.1 新增 7 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `mkt_channel_config` | 渠道配置 | `channel_type / provider / rate_limit_per_min / rate_limit_per_day / retry_max` |
| `mkt_channel_template` | 渠道模板 | `template_code / scene / content / variables / audit_status` |
| `marketing_touch_task` | 触达任务 | `campaign_name / channel_type / template_id / status / target_count / success_count / fail_count / read_count / convert_count` |
| `marketing_touch_log` | 触达明细 | `member_id / status / error_msg / retry_count / external_msg_id` |
| `mkt_journey` | 营销旅程 | `nodes_json / edges_json / trigger_event / status / enter_count / complete_count` |
| `mkt_journey_instance` | 旅程执行实例 | `member_id / current_node_id / status / context_json` |
| `marketing_touch_conversion` | 触达转化明细 | `conversion_type / related_id / amount` |

### 2.2 渠道类型

| channel_type | 说明 |
|---|---|
| SMS | 短信（阿里云/腾讯云） |
| MINI_PROGRAM | 小程序订阅消息 |
| MP | 公众号模板消息 |
| WECHAT_WORK | 企业微信 |
| IN_APP | 站内信 |
| EMAIL | 邮件 |
| PUSH | APP 推送 |

### 2.3 任务状态机

```
1待执行 ──start──> 2执行中 ──┬──完成──> 3已完成
                              ├──pause──> 4已暂停 ──resume──> 2执行中
                              ├──cancel──> 6已取消
                              └──失败──> 5已失败
```

### 2.4 触达明细状态机

```
1待发送 ──send──> 2已发送 ──read──> 4已读 ──convert──> 5已转化
                    │
                    └──fail──> 3失败
```

### 2.5 旅程节点类型

| type | 说明 |
|---|---|
| START | 进入节点 |
| WAIT | 等待（hours 字段） |
| SMS / MINI_PROGRAM / EMAIL | 发送触达 |
| CONDITION | 条件分支（前端可扩展） |
| END | 结束 |

---

## 3. 关键流程

### 3.1 触达任务启动

```
POST /mkt/task (campaignName, channelType, templateId, audienceFilter)
    ↓
INSERT marketing_touch_task (status=1)
    ↓
解析 audienceFilter → 拉会员 → 批量生成 marketing_touch_log (status=1)
    ↓
POST /mkt/task/{id}/start
    ↓
task.status = 2 (started_at)
    ↓
逐条 log 发送：
  ① 限流检查 (rateLimitPerMin / rateLimitPerDay)
  ② 路由 ChannelSender
  ③ 渲染 ${var}
  ④ 失败重试 (retryMax=3)
  ⑤ 写日志 (status=2 成功 / 3 失败)
    ↓
task.status = 3 (finished_at, success/failCount)
```

### 3.2 营销旅程推进

```
发布旅程 (status=2)
    ↓
POST /journey/{id}/enter (memberId)
    ↓
INSERT mkt_journey_instance (currentNodeId=n1, status=1)
    ↓
POST /journey/instance/{id}/next
    ↓
读取 edges_json, 找 currentNodeId 的下一个节点
    ↓
if next.type == 'SMS' || 'MINI_PROGRAM':
    自动 createTask(audienceFilter={memberIds:[currentMember]})
    ↓
更新 currentNodeId = next.id
    ↓
if 无下一节点:
    instance.status = 2 (completed)
    journey.complete_count++
```

### 3.3 转化追踪

```
会员下单 / 注册
    ↓
POST /mkt/conversion/track (touchLogId, conversionType, relatedId, amount)
    ↓
INSERT marketing_touch_conversion
    ↓
UPDATE marketing_touch_log (status=5, convert_at, related_order_id)
    ↓
UPDATE marketing_touch_task (convert_count++)
```

---

## 4. 后端接口

### 4.1 渠道配置（T-D21）

| Method | URL | 用途 |
|---|---|---|
| GET | `/mkt/channel/list` | 渠道列表 |
| POST | `/mkt/channel` | 新增 |
| PUT | `/mkt/channel/{id}` | 更新 |
| DELETE | `/mkt/channel/{id}` | 删除 |
| POST | `/mkt/channel/health-check` | 全部健康检查 |

### 4.2 模板（T-D21）

| Method | URL | 用途 |
|---|---|---|
| GET | `/mkt/template/list` | 模板列表 |
| POST | `/mkt/template` | 新增 |
| PUT | `/mkt/template/{id}/audit` | 审核 |

### 4.3 触达任务（T-D22）

| Method | URL | 用途 |
|---|---|---|
| POST | `/mkt/task` | 新建 |
| GET | `/mkt/task/page` | 列表 |
| POST | `/mkt/task/{id}/start` | 启动 |
| POST | `/mkt/task/{id}/pause` | 暂停 |
| POST | `/mkt/task/{id}/resume` | 恢复 |
| POST | `/mkt/task/{id}/cancel` | 取消 |
| GET | `/mkt/task/{id}/log` | 明细 |

### 4.4 旅程（T-D23）

| Method | URL | 用途 |
|---|---|---|
| POST | `/mkt/journey` | 新建 |
| PUT | `/mkt/journey/{id}` | 更新 |
| GET | `/mkt/journey/page` | 列表 |
| GET | `/mkt/journey/{id}` | 详情 |
| POST | `/mkt/journey/{id}/publish` | 发布 |
| POST | `/mkt/journey/{id}/pause` | 暂停 |
| POST | `/mkt/journey/{id}/enter` | 会员进入 |
| POST | `/mkt/journey/instance/{id}/next` | 推进下一节点 |

### 4.5 转化（T-D24）

| Method | URL | 用途 |
|---|---|---|
| GET | `/mkt/conversion/funnel` | 漏斗 |
| GET | `/mkt/conversion/roi` | ROI |
| GET | `/mkt/conversion/by-channel` | 按渠道统计 |
| POST | `/mkt/conversion/track` | 转化追踪 |
| POST | `/mkt/log/{id}/read` | 标记已读 |

---

## 5. 前端

| 页面 | 路由 | 功能 |
|---|---|---|
| 渠道配置 | `/mkt/channel` | 渠道 CRUD + 健康检查 |
| 消息模板 | `/mkt/template` | 模板 CRUD + 审核（通过/驳回） |
| 触达任务 | `/mkt/touch-task` | 任务面板 + 启动/暂停/恢复/取消 + 明细 |
| 营销旅程 | `/mkt/journey` | 旅程 CRUD + 画布预览（节点类型染色） |
| 触达效果 | `/mkt/conversion` | 漏斗 + ROI + 按渠道统计 |

---

## 6. 测试

### 6.1 单元测试

| 测试类 | 数量 | 状态 |
|---|---:|:---:|
| `BizCodeTest` | 6 | ✅ |
| `RefundStatusTest` | 13 | ✅ |
| **小计** | **19** | ✅ |

### 6.2 集成测试 `test-rbac-v3_0_d.ps1`

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 渠道 CRUD + 健康检查 | 5 |
| 2 | 模板 CRUD + 审核 | 3 |
| 3 | 触达任务（创建/启动/明细/取消） | 14 |
| 4 | 旅程（创建/发布/进入/推进） | 12 |
| 5 | 转化统计（funnel/ROI/by-channel） | 7 |
| **小计** | | **≥ 40** |

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 集成测试用例 | ≥ 30 | ✅ ≥ 40 |
| 新增 SQL 表 | 7 | ✅ |
| 新增权限码 | 5 | ✅ |
| 新增 Vue 页面 | 5 | ✅ |
| 渠道 Sender 实现 | 3+ | ✅ |

---

## 8. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-D21 渠道可插拔，含失败重试 | ✅（ChannelSender 接口 + 3 实现 + retryMax + 限流） |
| T-D22 任务可执行与暂停 | ✅（5 状态机：待执行/执行中/已完成/已暂停/已取消） |
| T-D23 旅程可视化与执行日志 | ✅（JSON 节点画布 + 推进引擎 + instance 状态机） |
| T-D24 ROI 与转化漏斗 | ✅（funnel 三级 + ROI + 按渠道） |

---

## 9. 渠道适配器扩展指南

```java
@Component
public class EmailSender implements ChannelSender {
    @Override
    public String channelType() { return "EMAIL"; }
    
    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        // 使用 JavaMailSender 或 sendGrid
        String content = SmsSender.render(tpl.getContent(), vars);
        return SendResult.ok("EMAIL-XXX");
    }
    
    @Override
    public boolean healthCheck() { return true; }
}
```

注册即可自动接入，无需修改 `ChannelDispatcher`。

---

## 10. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | 初版（R3.0-D 4 项任务全部完成） | R3.0-D |
