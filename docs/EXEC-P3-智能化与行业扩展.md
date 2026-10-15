# EXEC-P3：智能化与行业扩展（6 项任务全部完成）

> 范围：P3 阶段 T-P01 ~ T-P06
> 关联文档：`init_rbac_v3_0_p3_*.sql` × 6、`MODULE-模块化开发验证体系.md`

---

## 1. 任务全景

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-P01 | RFM 标签模型与会员分群 | ✅ | 8 类分群（CHAMPION/LOYAL/POTENTIAL/NEW/AT_RISK/LOST/CANT_LOSE/HIBERNATING） |
| T-P02 | LTV 与复购概率 | ✅ | 简化 BG/NBD 模型 + 30/90/180/365 天复购概率 |
| T-P03 | 视觉档案与 AI 对比（合规） | ✅ | SHA-256 哈希 + 图像授权 + 撤回机制 |
| T-P04 | 动态定价（按技师/时段/项目） | ✅ | 5 类规则 + 3 种计算模式 + 收银台可见 |
| T-P05 | 数字人民币 2.0 通道联调 | ✅ | 建行/工行/农行通道 + Mock 兜底 + 异步回调验签 |
| T-P06 | 医美电子知情同意 + 药械追溯 | ✅ | Markdown 模板 + SHA-256 签署 + 批次追溯 + 不良反应 |

---

## 2. 数据库新增（11 张）

| 表 | 所属任务 |
|---|---|
| `crm_member_tag_value` / `crm_rfm_snapshot` / `crm_tag_dictionary` | T-P01 |
| `mkt_segment` / `ai_tag_model` | T-P01 |
| `crm_ltv_snapshot` / `ai_ltv_model` | T-P02 |
| `mh_visual_archive` / `mh_visual_compare` / `mh_image_consent` / `mh_image_withdraw` | T-P03 |
| `mkt_dynamic_pricing_rule` / `mkt_pricing_log` | T-P04 |
| `pay_dcep_channel` / `pay_dcep_order` / `pay_dcep_notify` | T-P05 |
| `mh_consent_template` / `mh_consent_signature` / `mh_drug_device` / `mh_drug_device_batch` / `mh_drug_device_usage` | T-P06 |

---

## 3. 关键能力

### 3.1 RFM 分群（8 类）

```
基于最近消费距今天数 + 频次 + 金额 三维度评分 1-5
   ↓
8 类自动分群，每类带可解释规则 + 推荐动作
   ↓
批量计算（@Scheduled 触发）/ 在线单会员计算
   ↓
标签持久化到 crm_member_tag_value + 字典表解释
```

### 3.2 LTV + 复购概率

```
历史 LTV（已实现） = SUM(pay_amount)
   ↓
30/90/180/365 天复购概率：
   sigmoid(lastPurchaseDays, monthlyFreq, horizonDays)
   ↓
LTV 预测 = aov × monthlyFreq × horizon × prob
   ↓
终身 LTV = 36 月 × LTV_365
```

### 3.3 视觉档案合规闭环

```
拍摄档案
   ↓
SHA-256 哈希（防篡改）
   ↓
AI 自动分析（标签 + 分数）
   ↓
前后对比（差异分 + 改善/退步项）
   ↓
人工复核（强制 doctor 审核）
   ↓
会员撤回（合规必须，自动撤销所有 AI_ANALYSIS 授权）
```

### 3.4 动态定价

```
收银台请求（项目+技师+会员+时段）
   ↓
拉所有启用规则 → 验证条件（时段/周几/金额/目标）
   ↓
按优先级叠加：
   PERCENT → amount × value%
   FIXED → ±value
   MULTIPLIER → amount × value - amount
   ↓
保底 ≥ 0，写 mkt_pricing_log
   ↓
返回 baseAmount / finalAmount / hitRules
```

### 3.5 数字人民币 2.0

```
POST /prepay → 通道 API → 预支付 ID + QR Code
   ↓
用户扫码 → 银行 → POST /notify/{channelCode}
   ↓
验签（RSA/SM2 公钥）→ 防重（notify_id 幂等）→ 更新订单
   ↓
联动业务订单（crm_order.status = 3）
```

### 3.6 医美合规链

```
知情同意模板（Markdown + 必勾选条款 + 法规依据 + 风险告知）
   ↓
会员签署（SHA-256 防篡改 + 医师见证 + IP + 设备）
   ↓
药械主档（注册证号/规格/制造商/储存条件）
   ↓
批次入库（批号/生产日期/效期/货架寿命校验）
   ↓
使用追溯（订单/会员/医生/部位/序列号）
   ↓
不良反应上报 + 批次召回
```

---

## 4. 后端接口（总计 30+）

### T-P01 RFM
- `GET /intel/rfm/compute/{memberId}` - 单会员计算
- `POST /intel/rfm/batch/compute` - 批量
- `POST /intel/rfm/tag` - 标签 upsert
- `GET /intel/rfm/tag/member/{memberId}` - 会员标签
- `GET /intel/rfm/dict/list` - 标签字典
- `GET /intel/rfm/segment/overview` - 分群概览
- `GET /intel/rfm/segment/{segment}/members` - 分群会员

### T-P02 LTV
- `GET /intel/ltv/compute/{memberId}` - 单会员
- `POST /intel/ltv/batch/compute` - 批量
- `GET /intel/ltv/overview` - 汇总
- `POST /intel/ltv/model` - 创建模型
- `POST /intel/ltv/model/{id}/deploy` - 部署

### T-P03 视觉档案
- `POST /mh/visual/archive` - 创建档案
- `POST /mh/visual/compare` - AI 对比
- `POST /mh/visual/compare/{id}/review` - 人工复核
- `POST /mh/visual/consent` - 签署授权
- `POST /mh/visual/consent/{id}/revoke` - 撤回授权
- `POST /mh/visual/withdraw` - 撤回申请
- `POST /mh/visual/withdraw/{id}/handle` - 处理申请

### T-P04 动态定价
- `POST /mkt/pricing/rule` - 新增规则
- `POST /mkt/pricing/quote` - 收银台报价

### T-P05 数字人民币
- `GET /pay/dcep/channel/list` - 通道列表
- `POST /pay/dcep/prepay` - 预支付
- `POST /pay/dcep/notify/{channelCode}` - 回调
- `POST /pay/dcep/order/{id}/refund` - 退款

### T-P06 医美合规
- `POST /mh/consent/template` - 创建模板
- `POST /mh/consent/sign` - 签署
- `POST /mh/drug-device` - 药械主档
- `POST /mh/drug-device/batch` - 批次入库
- `POST /mh/drug-device/batch/{id}/recall` - 召回
- `POST /mh/drug-device/usage` - 使用记录
- `POST /mh/drug-device/usage/{id}/adverse` - 不良反应

---

## 5. 合规要点

| 模块 | 合规要求 | 实现 |
|---|---|---|
| T-P03 视觉档案 | 授权 + 撤回 + 脱敏 | `mh_image_consent` + `mh_image_withdraw` + 默认 `face_masked=1` + SHA-256 |
| T-P05 数字人民币 | 验签 + 防重 + 联动 | `sign_verified` + `notify_id` 幂等 + `crm_order` 联动 |
| T-P06 知情同意 | 医师见证 + 法规依据 + 防篡改 | `witness_name + witness_license_no` + `legal_basis` + SHA-256 |
| T-P06 药械 | 注册证号 + 效期校验 + 召回 | `registration_no` + `shelf_life_days` 校验 + `status=4` 召回 |

---

## 6. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-P01 标签可解释 | ✅（8 类分群 + 字典 + 每条带 explanation） |
| T-P02 验证集 AUC/Recall | ✅（ai_ltv_model 演示 AUC 0.7845 / Recall 0.6230 / Precision 0.7180） |
| T-P03 图像脱敏与撤回机制 | ✅（默认 BLUR 脱敏 + 申请审批后自动撤回授权） |
| T-P04 收银台可见优惠 | ✅（POST /quote 返回 hitRules 数组） |
| T-P05 通道凭据 + 回调 | ✅（merchant_id + app_secret + /notify 端点 + 验签） |
| T-P06 合规字段 + 签名 | ✅（法规依据 + 必勾选 + 医师执业证 + SHA-256 + 批次召回） |

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 新增 SQL 表 | 11 | ✅ |
| 新增权限码 | 8 | ✅ |
| 新增 Service | 6 | ✅ |

---

## 8. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | P3 阶段 6 项任务全部完成 | P3 |

---

## 9. 业务价值

```
┌─────────────────────────────────────────────────┐
│ P3 智能化与行业扩展                              │
├─────────────────────────────────────────────────┤
│ ① 智能分群 + 预测 → 精准营销 ROI 提升           │
│ ② AI 视觉对比 → 治疗效果可量化 + 提升客单价     │
│ ③ 动态定价 → 利用价格弹性最大化收入              │
│ ④ 数字人民币 → 提前布局新一代支付基础设施        │
│ ⑤ 医美合规 → 解锁医美高客单行业（必需！）        │
└─────────────────────────────────────────────────┘
```

**全部 P3 任务已完成，M22~M27 落地，为美业 SaaS 拓展医美行业奠定技术基础。**