# F064 — GoogleMapsService `/stores` + `/heatmap` + `/geocode/log`

**状态**：PARTIAL-NON-BLOCKER（真实 Shadow — `/geocode/log` 前端无消费）
**验证类型**：API-only + Static-Only
**测试时间**：2026-09-14 00:39 (UTC+8)

## 1. 端点 L0 静态

| 端点 | 方法 | 行号 | 数据源 |
|---|---|---|---|
| `GET /google-maps/stores` | 5 门店常量 | L356-358 | 内存 STORES L33-43 |
| `GET /google-maps/heatmap` | heatmap 数据 | L361-368 | 内存 mock（200 点） |
| `GET /google-maps/geocode/log` | 日志列表 | L377-379 | DB 表（I029 MISSING） |

## 2. API 实证

### 2.1 `/stores`（admin token）

**结果**：

```json
{
  "code":200,
  "message":"操作成功",
  "data":[
    {"id":1,"name":"北京三里屯店","lat":39.9376,"lng":116.4474,"address":"北京市朝阳区三里屯 SOHO","monthlyBookings":320},
    {"id":2,"name":"上海徐汇店","lat":31.1958,"lng":121.4365,"address":"上海市徐汇区港汇恒隆广场","monthlyBookings":285},
    {"id":3,"name":"深圳南山店","lat":22.5333,"lng":113.9361,"address":"深圳市南山区万象天地","monthlyBookings":240},
    {"id":4,"name":"纽约店 (USA)","lat":40.7128,"lng":-74.0060,"address":"Manhattan, NY","monthlyBookings":95},
    {"id":5,"name":"东京店 (JP)","lat":35.6762,"lng":139.6503,"address":"Shibuya, Tokyo","monthlyBookings":130}
  ]
}
```

✅ **5 门店常量完全匹配 STORES L33-43**（name/lat/lng/address/monthlyBookings）。

### 2.2 `/heatmap`（admin token）

**结果**：

```json
{"code":200,"message":"操作成功","data":{"points":[{"lng":116.48,"lat":39.69,"weight":0.437},...],"total":200}}
```

✅ **200 points heatmap**（服务随机生成）。

### 2.3 `/geocode/log`（admin token，I029 优先暴露）

**结果**：

```json
{"code":500,"message":"数据库异常","data":null,"traceId":"e143de9cc2f743a2a5784d88e008a07b"}
HTTP=200
```

→ **I029 MISSING 暴露**：表不存在，SQL 抛异常。

证据文件：
- `audit/evidence/F064-googlemaps-service-stores-heatmap-log/raw-stores.txt`
- `audit/evidence/F064-googlemaps-service-stores-heatmap-log/raw-heatmap.txt`
- `audit/evidence/F064-googlemaps-service-stores-heatmap-log/raw-geocode-log.txt`

## 3. N17 — `/geocode/log` 前端无消费（真实 Shadow）

### 3.1 前端静态

`frontend/meiyetec-web/src/views/google-maps/index.vue` 全文 175 行 — **没有调用** `/google-maps/geocode/log` 的代码：

```
grep "geocode/log" → 0 hits
```

→ 与 plan-batch-1.3.md §1 F064 一致："`/geocode/log` 端点无前端消费"。

### 3.2 与 EXT-22 §0.5 一致

EXT-22 Operational Notes §0.5 已标 "**`/geocode/log` 真实 Shadow**"。

## 4. 鉴权实证

无 token 调 `/stores` + `/geocode/log` 全部 **401**：
- raw-stores-noauth.txt：`{"code":401,"message":"未登?","data":null}` HTTP=401
- raw-geocode-log-noauth.txt：同上

→ 两个端点都需 Bearer token（与 `/config` 一致）。

## 5. 结论

| 端点 | 实测状态 | I029 暴露 | Shadow |
|---|---|---|---|
| `/stores` | ✅ 200 + 5 门店常量 | N/A（内存数据） | — |
| `/heatmap` | ✅ 200 + 200 points | N/A（内存数据） | — |
| `/geocode/log` | ❌ 500 I029 | ✅ | **N17 真实 Shadow** |

**最终状态**：**PARTIAL-NON-BLOCKER**
**负向证据**：
- N17：`/geocode/log` 后端有，前端无（D20 不加 UI，沿用 I006/I007 模式）
- I029：DB MISSING 暴露

**修复 Phase**：Post-Audit（建表后端点可达 + 决定是否补前端 tab）