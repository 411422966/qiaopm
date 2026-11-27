# I212 — createCompare 的 (BigDecimal) 硬转与 VisualAiService 返回 Double 契约漂移 → /mh/visual「对比 / 开始对比」按钮在被对比档案含 imageUrl 时恒 500（HTTP 200 + code 500 假成功）

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**
> **P1 理由**：与 **I211 同型** —— `/mh/visual` 的「对比」（`views/mh/visual.vue:337` → `doCompare` → L1308）与「开始对比」（L394 → `runBatchCompare` → L1365）**两个按钮调用同一端点 `POST /mh/visual/compare`**，在**被对比的两张档案均含 `imageUrl`**（即命中真实模型分支）时**必然抛 `ClassCastException`** → 返回 **HTTP 200 + `body.code=500`**（假成功）。前端只判 HTTP 状态码会把失败当成功，用户侧表现为「点了没反应 / 结果为空」。
> **发现日期**：2026-11-27（I209#2 修复验证过程中偶发命中同一端点）
> **登记人**：Cursor
> **绑定基线**：HEAD `10d7981`（`fix(mh): I209#2 createCompare 补 tenant 校验 + I208 两条 Mapper SQL 补 status=1`）
> **验证等级**：**运行期 VERIFIED**（容器 `meiyetec-api` 实测 `HTTP 200 + body.code=500` + 原始堆栈已取证，堆栈定位到 `VisualArchiveService.java:363`）+ **静态 VERIFIED**（类型契约漂移定位到具体行）
> **关联**：I211（同型假成功：`ClassCastException` + HTTP 200）、I206（鉴权注解补齐后才暴露的运行时缺陷）、D-3-37（HTTP 断言须双判 `body.code`）
> **登记状态**：**待登记**（`issue-register.md` 写入需用户逐字确认，见 I198 §D-3.36 / D-3-38）

---

## 0. 一句话描述

`POST /mh/visual/compare` 把 `VisualAiService.compareArchives` 返回的 Map 中 `similarity` / `diffScore` **硬转为 `BigDecimal`**
（`VisualArchiveService.java:363-364`），但该 Map 在**真实模型分支**下装的是 `Double`
（`VisualAiService.buildComparisonResponse` → `CvModelClient.CvComparisonResult.similarity / diffScore` 声明即为 `Double`），
在**确定性降级分支**下装的才是 `BigDecimal` —— 两条分支的类型契约不一致，硬转在真实模型分支必然抛 `ClassCastException`。

---

## 1. 事实

### 1.1 硬转点

`backend/src/main/java/com/meiyetec/service/VisualArchiveService.java:363-364`

```java
c.setSimilarity((BigDecimal) diffResult.get("similarity"));
c.setDiffScore((BigDecimal) diffResult.get("diffScore"));
```

### 1.2 两个分支的产出类型

**分支 A（真实模型）** —— `backend/src/main/java/com/meiyetec/service/VisualAiService.java:225-228`

```java
private Map<String, Object> buildComparisonResponse(CvModelClient.CvComparisonResult r) {
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("similarity", r.getSimilarity());   // ← Double
    res.put("diffScore", r.getDiffScore());      // ← Double
```

类型声明源头 `backend/src/main/java/com/meiyetec/ai/CvModelClient.java:107-117`：

```java
class CvComparisonResult {
    private Double similarity;
    ...
    private Double diffScore;
```

`DefaultCvModelClient.buildComparison`（`backend/src/main/java/com/meiyetec/ai/DefaultCvModelClient.java:344-349`）显式 `.doubleValue()` 装箱回 `Double`。

**分支 B（确定性降级）** —— `backend/src/main/java/com/meiyetec/service/VisualAiService.java:196-200`

```java
BigDecimal diffScore = BigDecimal.valueOf(improvement * 10 + 5).setScale(2, RoundingMode.HALF_UP);
BigDecimal similarity = BigDecimal.valueOf(cosSim).setScale(4, RoundingMode.HALF_UP);
res.put("similarity", similarity);   // ← BigDecimal
res.put("diffScore", diffScore);     // ← BigDecimal
```

### 1.3 分支选择条件

`VisualAiService.compareArchives`（L106-133）：`cvModelClient != null`（`DefaultCvModelClient` 是 `@Component`，**默认必然注入**）且 `before.getImageUrl()` / `after.getImageUrl()` **均非空** → 走分支 A。

→ 即：**只要被对比的两张档案都带 `imageUrl`，就必然进入分支 A 并触发硬转失败**；无 `imageUrl` 的档案走分支 B，正常。

### 1.4 实测

- `POST /mh/visual/compare`（同租户真实 id，两档案均带 `imageUrl`）→ **HTTP 200 + `body.code=500`**，堆栈定位 `at VisualArchiveService.java:363`；
- 判定口径：HTTP 状态码与业务码**双判**（D-3-37）—— 两者任一表示失败即判 FAIL。

### 1.5 与本次修复提交的关系

**既有缺陷，非 `10d7981` 引入。** `git diff -U0 10d7981^ 10d7981 -- backend/src/main/java/com/meiyetec/service/VisualArchiveService.java` 仅触及 **L345**（新增 `Long tenantId = ...`）与 **L347**（原单行 if 改为多行 tenant 校验），**未触及 L363-364** 的硬转语句。

---

## 2. 影响

- `/mh/visual` 的「对比」与「开始对比」两个按钮，**在被对比档案带图的场景下必然失败**；
- 失败以 HTTP 200 + `body.code=500` 返回 → 前端 `http.post` 的 `try/finally` 只做 `compLoading` 复位，**不弹错误提示** → 用户侧表现为「点击后无结果」；
- 已写入 `mh_visual_compare` 表的记录不受影响（异常发生在 `compareMapper.insert` 之前，`@Transactional(rollbackFor = Exception.class)` 亦无脏写）。

---

## 3. 修复方向（**二选一，需产品/技术负责人拍板**）

- **方案 A**：把 `VisualAiService.buildComparisonResponse` 的产出改为 `BigDecimal`（`BigDecimal.valueOf(r.getSimilarity())` 等）→ **统一到分支 B 的契约**，消除类型分叉；
- **方案 B**：把 `createCompare` 的硬转改为宽容转换（`BigDecimal.valueOf(((Number) v).doubleValue())`）→ **消费侧兼容两种类型**，但保留「同一 Map 值类型随分支变化」的隐患；
- 建议 **方案 A**（契约在生产侧统一，消费侧不引入 `Number` 强转噪音），但**两者都需改 `backend/` 业务代码**，本条**仅登记不修**。

---

## 4. 遗留标注

本条**只登记，不修**，未修改任何业务代码，未触达 `views/mh/visual.vue`。

页面级影响（按钮在浏览器中的真实表现）**未实测 → UNVERIFIED**；本条结论基于后端 HTTP 层实测与源码类型链。

---

## 5. 关联与交叉引用

- `backend/src/main/java/com/meiyetec/service/VisualArchiveService.java:363-364`
- `backend/src/main/java/com/meiyetec/service/VisualAiService.java:106-133` / `:196-200` / `:225-228`
- `backend/src/main/java/com/meiyetec/ai/CvModelClient.java:107-117`
- `backend/src/main/java/com/meiyetec/ai/DefaultCvModelClient.java:65-82` / `:344-349`
- `frontend/meiyetec-web/src/views/mh/visual.vue:337`（对比按钮）/ `:394`（开始对比按钮）/ `:1301-1312`（doCompare）/ `:1354-1371`（runBatchCompare）
- `audit/issues/I211-mh-visual-batch-ids-classcastexception.md`（同型假成功）
- `audit/issues/I206-mh-visual-consent-complication-no-backend-auth.md`
