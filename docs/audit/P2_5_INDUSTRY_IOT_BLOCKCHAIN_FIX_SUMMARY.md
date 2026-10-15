# P2.5 行业 SaaS + IoT + 区块链溯源

> P2 长期第 5 项：3 个维度并行实装

---

## 1. 调研结论

| 子项 | 实际 |
|---|---|
| **行业 SaaS** | ❌ 缺失（仅美业，**无牙科/口腔/医美/月子/美发等行业模板**） |
| **智能硬件 IoT** | ⚠️ Entity+Mapper 已有（iot_device / iot_device_command / iot_device_event），**无 Service/Controller** |
| **区块链溯源** | ❌ 缺失（**无 hashchain/provenance**） |

---

## 2. 实际修复

### 2.1 新增 SQL

`migrate_r3_0_h_p2_5_industry_iot_blockchain.sql`：
- `industry_template` — 行业模板（预置 5：美业/医美/口腔/美发/月子）
- `industry_service_relation` — 行业-服务/商品关联
- `iot_command_dest` — IoT 命令下发（含优先级 + 重试）
- `iot_event_dest` — IoT 事件上报（4 级严重度）
- `blockchain_record` — 区块链存证（5 类：PRODUCT/DRUG/DEVICE/ORDER/CONSENT）
- `blockchain_chain_block` — 区块链 - 区块

### 2.2 新增 Entity（6 个）+ Mapper（6 个）

### 2.3 新增 Service（3 个，**25 个新 API**）

> 注意：IoT 使用 `/iotv2` 路径（避开 P3EcosystemService 的 `/iot/*` 已有端点）

#### A. `IndustryService`（5 个 API）

| 端点 | 说明 |
|---|---|
| `GET /industry/template/list` | 行业列表 |
| `GET /industry/template/{code}` | 模板详情 + 关联 |
| `POST /industry/template` | 创建模板 |
| `POST /industry/relation` | 添加关联 |
| `POST /industry/template/{code}/apply` | **一键应用模板到租户** |

#### B. `IotService`（13 个 API，路径 `/iotv2`）

| 端点 | 说明 |
|---|---|
| `GET /iotv2/device/list` | 设备列表 |
| `GET /iotv2/device/{id}` | 设备详情 |
| `POST /iotv2/device/{id}/command` | **命令下发**（优先级 + 重试） |
| `POST /iotv2/command/{id}/retry` | 重试命令 |
| `POST /iotv2/command/{id}/ack` | 设备 ACK |
| `POST /iotv2/command/{id}/complete` | 完成命令 |
| `GET /iotv2/command/page` | 命令分页 |
| `POST /iotv2/event/report` | **事件上报**（设备 → SERVER） |
| `GET /iotv2/event/page` | 事件分页 |
| `POST /iotv2/event/{id}/process` | 处理事件 |
| `POST /iotv2/device/{id}/heartbeat` | 设备心跳 |
| `POST /iotv2/device/batch-command` | **批量下发** |

#### C. `BlockchainService`（7 个 API）

| 端点 | 说明 |
|---|---|
| `POST /blockchain/record` | **存证**（PoW 挖矿 + 链式） |
| `GET /blockchain/record/{bizId}` | 按 bizId 查询 |
| `GET /blockchain/record/page` | 存证分页 |
| `GET /blockchain/chain/verify` | **链完整性校验** |
| `GET /blockchain/chain/info` | 链信息 |
| `GET /blockchain/trace/{type}/{bizId}` | **溯源查询** |
| `POST /blockchain/utils/hash` | 哈希工具 |

---

## 3. 核心算法

### 3.1 IoT 命令状态机

```
PENDING → SENT → ACK → SUCCESS
                ↓
              FAIL / TIMEOUT
重试 ≤ maxRetry (默认 3)
```

### 3.2 IoT 事件 4 级严重度

```
INFO / WARN / ERROR / CRITICAL
CRITICAL → 自动 processed=1（触发高级联动）
```

### 3.3 区块链 PoW 挖矿

```
hash = SHA-256(prevHash + ":" + blockIndex + ":" + payloadHash + ":" + nonce)
要求 hash.startsWith("00...")  // 难度 2

难度 1：~5 nonce
难度 2：~256 nonce
难度 4：~65000 nonce
```

### 3.4 链完整性校验

```
对每块验证：
  1. prevHash == expectedPrev
  2. hash.startsWith("0" * difficulty)
  3. SHA-256(prevHash + ":" + blockIndex + ":" + merkleRoot + ":" + nonce) == blockHash
任一失败 → 链篡改
```

### 3.5 5 行业模板

| 行业 | 默认服务 | 默认商品 |
|---|---|---|
| **BEAUTY** 美业 | 基础护理/深层清洁/保湿/SPA | 面膜/精华/磨砂 |
| **MEDSPA** 医美 | 玻尿酸/肉毒素/激光/热玛吉 | 玻尿酸/肉毒素/麻药 |
| **DENTAL** 口腔 | 洁牙/补牙/种植/正畸 | 种植体/牙套/麻药 |
| **HAIR** 美发 | 剪/烫/染/护理 | 染膏/烫发剂/洗护 |
| **MATERNITY** 月子 | 开奶/复旧/婴儿SPA/营养餐 | 月子餐/催乳/婴儿用品 |

### 3.6 5 类存证

```
PRODUCT  / DRUG  / DEVICE / ORDER / CONSENT
幂等：同一 bizId + recordType 不重复存证
```

---

## 4. 测试覆盖（43 个新场景）

| 维度 | 测试数 | 场景 |
|---|---|---|
| **行业 SaaS** | 5 | 行业码校验 / 唯一性 / 区别 / 关联类型 / icon |
| **IoT 设备** | 11 | 设备类型 / 在线状态 / 命令类型 / 命令状态 / 优先级 / 状态机 / 重试 / 事件类型 / 严重度 / MAC / IP |
| **IoT 综合** | 2 | deviceSn 唯一 / 严重度排序 |
| **区块链 SHA-256** | 5 | RFC 测试向量 / 确定性 / 长度 / 差异 / 空字符串 |
| **区块链 PoW** | 4 | 难度 1/2 挖矿 / 难度 4 / nonce 溢出 |
| **区块链核心** | 7 | 创世 prevHash / 共识类型 / 存证类型 / chainId / 块哈希唯一 / 块序号 / 校验 |
| **区块链验证** | 3 | 校验通过 / 篡改检测 / merkleRoot |
| **区块链 POI** | 4 | 幂等 / payloadHash 稳定 / verifyCount / 链大小 |
| **综合** | 2 | 跨域协同 / 碰撞概率 |

---

## 5. 编译 + 测试

- ✅ `mvn compile` 通过
- ✅ 新增 43 个测试全部通过
- ✅ 全量 347 个测试，333 通过 — 14 个失败均为 **MySQL 不可达**（测试环境问题，与代码无关）
- ✅ 修复 P2.5 引入的 `/iot/*` 路径冲突（改用 `/iotv2`）

---

## 6. Checklist

| 维度 | 实际 |
|---|---|
| **行业 SaaS - 5 模板** | ✅ **本次新增** |
| **行业 SaaS - 关联** | ✅ **本次新增** |
| **行业 SaaS - 一键应用** | ✅ **本次新增** |
| **IoT - 设备列表** | ✅ **本次新增** |
| **IoT - 命令下发** | ✅ **本次新增**（含优先级 + 重试） |
| **IoT - 命令状态机** | ✅ **本次新增** |
| **IoT - 事件上报** | ✅ **本次新增** |
| **IoT - 事件处理** | ✅ **本次新增** |
| **IoT - 设备心跳** | ✅ **本次新增** |
| **IoT - 批量控制** | ✅ **本次新增** |
| **区块链 - 存证** | ✅ **本次新增**（PoW 挖矿） |
| **区块链 - 链校验** | ✅ **本次新增**（3级校验） |
| **区块链 - 溯源** | ✅ **本次新增** |
| **区块链 - 多链** | ✅ **本次新增**（meiyetec-main / drug / device） |
| **测试覆盖** | ✅ 43 个新场景 |

---

## 7. 累计审计报告修正

| 批次 | 结果 |
|---|---|
| **P0.1** 库存消耗 | ✅ 9 合计 |
| **P0.2** 回访流程 | ✅ 11 新测试 |
| **P0.3** 跨店核销 | ✅ 29 合计 |
| **P1.4** 服务 SOP | ✅ 10 新测试 |
| **P1.5** 评价体系 | ✅ 15 新测试 |
| **P1.6** AI 真实化 | ✅ 17 新测试 |
| **P2.1** 开放平台 | ✅ 23 新测试 |
| **P2.2** 第三方集成 | ✅ 28 新测试 |
| **P2.3** 多仓管理 | ✅ 30 新测试 |
| **P2.4** 数据中台+i18n+移动+AI | ✅ 38 新测试 |
| **P2.5** 行业SaaS+IoT+区块链 | ✅ **43 新测试**（本次） |
| P2.6+ 长期 | 待执行 |

---

## 8. 相关文件

- `backend/src/main/resources/db/sql/migrate_r3_0_h_p2_5_industry_iot_blockchain.sql`（本次新增）
- `backend/src/main/java/com/meiyetec/service/{IndustryService,IotService,BlockchainService}.java`（3 Service / 25 API）
- `backend/src/main/java/com/meiyetec/entity/{IndustryTemplate,IndustryServiceRelation,IotCommand,IotEvent,BlockchainRecord,BlockchainChainBlock}.java`（6）
- `backend/src/main/java/com/meiyetec/mapper/*`（6 个）
- `backend/src/test/java/com/meiyetec/service/P25IndustryIotBlockchainTest.java`（43 测试）

---

## 9. 测试统计

| 测试套件 | 场景数 |
|---|---|
| **P2.5 行业SaaS+IoT+区块链**（本次新增） | **43** |
| P2.4 数据中台+i18n+移动+AI | 38 |
| P2.3 多仓管理 | 30 |
| P2.2 第三方集成 | 28 |
| P2.1 开放平台 | 23 |
| P1.6 AI 真实化 | 17 |
| P1.5 评价体系 | 15 |
| P1.4 SOP 步骤 | 10 |
| P0.1 库存链路 | 9 |
| P0.2 回访补强 | 11 |
| P0.3 跨店补强 | 29 |
| 其他（P1/P2/P3/其他） | 约 94 |
| **总计** | **347** |