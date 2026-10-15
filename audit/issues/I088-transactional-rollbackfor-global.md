# I088 · 全后端 @Transactional 未指定 rollbackFor（199 处遗留）

> **Issue ID**：I088
> **严重度**：P2（技术债 · 全局一致性 · 事务语义）
> **发现时间**：2026-09-28 00:00（Phase 2.17 只读扫描）
> **Feature**：跨模块（多个 Feature 共同持有）
> **Fix Phase**：Post-Audit 三期

---

## 一、问题描述

全后端 `backend/src/main/java` 下共有 **208 处** `@Transactional` 注解未指定 `rollbackFor`，默认仅对 `RuntimeException` / `Error` 自动回滚。

若业务代码抛出 **受检异常（checked exception）** 如 `IOException`、`SQLException`、`BizException` 等，事务**不会回滚**，导致：

- 部分数据落库、部分未落库（数据不一致）
- 幂等保障失效（重试后重复写入）
- 审计日志与业务状态不一致

---

## 二、已修复范围（Phase 2.17）

| Issue | 文件 | 行号 | 方法 | 状态 |
|---|---|---|---|---|
| I009 | `service/CrmIntegrationService.java` | 86 | `syncNow` | ✅ 已修复（commit `5b48e70`） |
| I009 | `service/IntlBookingService.java` | 75,123,157,211,275,325,406 | `googleCallback / instagramCallback / pullBookings / updateStatus / googleWebhook / instagramWebhook / convertBooking` | ✅ 已修复（commit `d5b0d87`） |
| I009 | `service/PluginPersistenceService.java` | 43 | `recordState` | ✅ 已修复（commit `52b4714`） |
| I034 | `controller/YouzanWebhookController.java` | 80 | `handleYouzan` | ✅ 已修复（Phase 2.12，commit `977f58e`） |

**已修复小计：10 处**

---

## 三、遗留范围（199 处 · Post-Audit 三期）

### Top 20 高频文件

| # | 文件 | 命中数 | 主要业务 |
|---|---|---|---|
| 1 | `service/impl/DcepHardwareWalletServiceImpl.java` | 12 | DCEP 硬件钱包 |
| 2 | `service/LbsDispatchService.java` | 11 | LBS 派单 |
| 3 | `service/impl/CrmFollowUpTaskServiceImpl.java` | 10 | CRM 跟进任务 |
| 4 | `service/P3EcosystemSuiteService.java` | 10 | P3 生态套件 |
| 5 | `service/PriceListService.java` | 9 | 价格表 |
| 6 | `service/DataHubService.java` | 9 | 数据中枢 |
| 7 | `service/P1SuiteService.java` | 8 | P1 套件 |
| 8 | `service/impl/SubscriptionServiceImpl.java` | 8 | 订阅服务 |
| 9 | `service/impl/DcepSubWalletServiceImpl.java` | 7 | DCEP 子钱包 |
| 10 | `service/impl/CouponServiceImpl.java` | 6 | 优惠券 |
| 11 | `service/MemberDayService.java` | 6 | 会员日 |
| 12 | `service/P2ComplianceSuiteService.java` | 5 | P2 合规套件 |
| 13 | `service/impl/PointServiceImpl.java` | 5 | 积分服务 |
| 14 | `service/KioskService.java` | 5 | 自助终端 |
| 15 | `service/NoShowPredictionService.java` | 4 | NoShow 预测 |
| 16 | `service/DianpingSyncService.java` | 4 | 大众点评同步 |
| 17 | `service/impl/DcepSmartContractServiceImpl.java` | 4 | 智能合约 |
| 18 | `service/LtvTrainingService.java` | 3 | LTV 训练 |
| 19 | `service/SharedPackageService.java` | 3 | 共享套餐 |
| 20 | `service/CareSuggestionService.java` | 3 | 护理建议 |

### 建议修复批次（按写密度 / 资金风险排序）

#### 第一批（高写密度 · 优先修）
DCEP 套件（HardwareWallet/SubWallet/SmartContract）+ Point + Coupon + Subscription
约 **48 处**

#### 第二批（中写密度）
PriceList + DataHub + LBS + Kiosk + NoShow + DianpingSync + Ltv/ChurnTraining
约 **55 处**

#### 第三批（低写密度 · 后台任务）
Suite 套件 + Care/Complication + Journey/IoT + Plugin/Dcep/Material 等
约 **96 处**

---

## 四、修复方案

每处统一改为：
```java
// 单行
@Transactional(rollbackFor = Exception.class)

// 或多行（如果方法签名跨行）
@Transactional(
    rollbackFor = Exception.class
)
```

**改动量：+1 行 / 处，净改动 +199 行**

**风险：低**（仅扩大回滚范围，不改变业务逻辑）

---

## 五、为什么这次不动（Phase 2.17 决策）

1. **语义风险**：部分遗留处可能有业务层故意利用"不自动回滚 checked exception"的语义（如部分成功、部分失败的状态上报）
2. **回归验证不全**：199 处涉及 20+ 业务域，一次性改动 + 验证成本高
3. **分批可控**：Post-Audit 三期每批约 50 处，每批独立 commit + 回归

---

## 六、验证方法

```powershell
Get-ChildItem backend/src/main/java -Recurse -Filter *.java |
  Select-String '@Transactional\b' |
  Where-Object { $_.Line -notmatch 'rollbackFor' }
```

期望（Phase 三期收口后）：输出为空

---

## 七、关联 Issue

| Issue | 内容 | 状态 |
|---|---|---|
| I009 | CRM/Intl/Plugin 9 处修复 | ✅ FIXED |
| I034 | YouzanWebhookController 1 处修复 | ✅ FIXED |
| **I088** | **剩余 199 处遗留（本期）** | **OPEN** |

---

**状态**：OPEN（Post-Audit 三期待清）
**优先级**：P2（技术债，但非 P1 阻塞上线）
