# F143 — multipart 文件上传 500（I138）证据包

> **Feature**：F143 multipart 上传链路
> **Issue**：I138（`audit/issues/I138-multipart-upload-500.md`）
> **验证时间**：2026-11-06 23:44 – 23:57 (+08:00)
> **验证环境**：容器 `meiyetec-api`（镜像 `meiyetec/api:dev`，23:44 重建后 healthy）｜DB `meiyetec@127.0.0.1:3307`（MySQL 8.0.46）｜env 见 `audit/evidence/env.md`、快照见 `audit/evidence/db-snapshot.md`
> **结论**：`MultipartException` 计数 **0**，三种上传场景 + JSON 回归全部符合预期

---

## 1. 证据文件清单

| 文件 | 类型 | 内容 |
|---|---|---|
| `before-01-multipart-exception-stack.txt` | 修复前 | 容器日志完整堆栈：`MultipartException: Failed to parse multipart servlet request`，关键帧 `at com.meiyetec.log.LogContextFilter.doFilterInternal(LogContextFilter.java:54)`（修复前行号），traceId `32ad5bcd1353414d9648b332b01c9248` |
| `after-01-empty-file.json` | 修复后 | 上传 0 字节文件 → `code=400 / 请求参数错误: 请上传文件`（`file.isEmpty()` 早于解析返回，无 ERROR 日志） |
| `after-02-not-excel.json` | 修复后 | 上传文本内容伪装 `.xlsx` → `code=400 / 请求参数错误: 文件格式错误，请使用 .xlsx 模板文件上传`，对应 WARN 日志 `[Import] 服务项目导入解析失败 ... not a valid OOXML (Office Open XML) file` |
| `after-03-valid-template.json` | 修复后 | 上传真实模板 → `code=200 / 导入完成，成功 2 条，失败 0 条`（`total=2, success=2, failed=0`） |
| `after-04-json-regression.json` | 回归 | `POST /biz/service/item` 普通 JSON → `code=200 创建成功`（id=40），证明变更 A 未误伤 JSON body 缓存 |
| `after-05-json-over-256kb.json` | 回归 | `POST /biz/service/item`，请求体 **300 146 字节**（> 256KB 上限）→ `code=200 创建成功`（id=41），证明变更 B 生效：body 不再被截断 |
| `after-05b-json-over-256kb-field-too-long.json` | 回归 | 同上但 300KB 写进 `description varchar(500)` → `code=500 数据库异常`；说明请求体已完整到达业务/DB 层（而非被截断后解析失败），失败原因是字段长度约束，与本次修复无关 |
| `after-06-container-logs.txt` | 修复后 | 容器全量日志：`MultipartException` 出现次数 **0**；`[Import]` WARN 仅出现在"非 Excel 文件"场景 |
| `after-07-before-after-compare.json` | 汇总 | 根因链、修复前后逐场景对比、回归结论、安全折衷的结构化 JSON |
| `README.md` | 说明 | 本文件：证据索引 + 复现步骤 |

---

## 2. 复现步骤

### 2.1 前置

```powershell
cd d:\QiaoPM
docker compose ps          # api / mysql / redis 均需 healthy
docker compose up -d api   # 若镜像未重建
```

### 2.2 取 admin token

```powershell
curl.exe -s -X POST http://localhost:8080/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"admin","password":"<env.md 中的口令>"}' -o tmp-login-resp.json
```

token 落盘到 `tmp-admin-token.txt`，后续命令用 `$token` 引用。

### 2.3 场景一：空文件

```powershell
[System.IO.File]::WriteAllBytes("$PWD\tmp-empty-f143.xlsx", @())
curl.exe -s -H "Authorization: Bearer $token" `
  -F "file=@tmp-empty-f143.xlsx;filename=tmp-empty-f143.xlsx" `
  http://localhost:8080/biz/service/item/import
# → {"code":400,"message":"请求参数错误: 请上传文件", ...}
```

### 2.4 场景二：非 Excel 内容

```powershell
[System.IO.File]::WriteAllText("$PWD\tmp-notexcel-f143.xlsx",
  "this is definitely not an excel file`n", (New-Object System.Text.UTF8Encoding($false)))
curl.exe -s -H "Authorization: Bearer $token" `
  -F "file=@tmp-notexcel-f143.xlsx;filename=tmp-notexcel-f143.xlsx" `
  http://localhost:8080/biz/service/item/import
# → {"code":400,"message":"请求参数错误: 文件格式错误，请使用 .xlsx 模板文件上传", ...}
```

### 2.5 场景三：真实模板

模板由系统自身导出，再把示例编码改成**未使用过的唯一值**（导入按 `service_code` 唯一校验）：

```powershell
curl.exe -s -H "Authorization: Bearer $token" `
  http://localhost:8080/biz/service/item/template -o tmp-template.xlsx
```

用 **.NET `ZipArchive` 以 Create 模式重建 zip**（不能用 `Update` 模式：Update 模式重写后的 zip Apache POI 读不出来，会误报 `not a valid OOXML file` —— 这是验证工具的坑，不是产品缺陷），在重建时替换 `xl/worksheets/sheet1.xml` 内的示例编码：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$zin  = [System.IO.Compression.ZipFile]::OpenRead("$PWD\tmp-template.xlsx")
$fs   = [System.IO.File]::Open("$PWD\tmp-template-f143.xlsx",[System.IO.FileMode]::CreateNew)
$zout = New-Object System.IO.Compression.ZipArchive($fs,[System.IO.Compression.ZipArchiveMode]::Create)
foreach ($e in $zin.Entries) {
  $ne = $zout.CreateEntry($e.FullName,[System.IO.Compression.CompressionLevel]::Optimal)
  $ne.LastWriteTime = $e.LastWriteTime
  $os = $ne.Open()
  if ($e.FullName -eq 'xl/worksheets/sheet1.xml') {
    $sr = New-Object System.IO.StreamReader($e.Open(),[System.Text.Encoding]::UTF8)
    $t  = $sr.ReadToEnd(); $sr.Close()
    $t  = $t.Replace('SV001','F143SV003').Replace('SV002','F143SV004')
    $sw = New-Object System.IO.StreamWriter($os,(New-Object System.Text.UTF8Encoding($false)))
    $sw.Write($t); $sw.Flush(); $sw.Close()
  } else { $is = $e.Open(); $is.CopyTo($os); $is.Close() }
  $os.Close()
}
$zout.Dispose(); $fs.Close(); $zin.Dispose()
```

```powershell
curl.exe -s -H "Authorization: Bearer $token" `
  -F "file=@tmp-template-f143.xlsx;filename=tmp-template-f143.xlsx" `
  http://localhost:8080/biz/service/item/import
# → {"code":200,"message":"导入完成，成功 2 条，失败 0 条",
#    "data":{"total":2,"success":2,"failed":0,"errors":[]}, ...}
```

### 2.6 JSON 回归（验证变更 A 未误伤）

```powershell
$json = '{"serviceCode":"F143JSON001","serviceName":"F143 JSON回归","unitPrice":88.00,"duration":45,"unit":"次","status":1}'
[System.IO.File]::WriteAllText("$PWD\tmp-f143-json.json",$json,(New-Object System.Text.UTF8Encoding($false)))
curl.exe -s -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  --data-binary "@tmp-f143-json.json" http://localhost:8080/biz/service/item
# → code=200 创建成功
```

### 2.7 >256KB 报文回归（验证变更 B 不再截断）

```powershell
$big = '{"serviceCode":"F143JSONBIG003","serviceName":"F143 JSON大报文回归2","unitPrice":88.00,"duration":45,"unit":"次","status":1,"bigNote":"' + ('X' * 300000) + '"}'
[System.IO.File]::WriteAllText("$PWD\tmp-f143-jsonbig2.json",$big,(New-Object System.Text.UTF8Encoding($false)))
curl.exe -s -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  --data-binary "@tmp-f143-jsonbig2.json" http://localhost:8080/biz/service/item
# → code=200 创建成功（请求体 300 146 字节）
```

### 2.8 日志核对

```powershell
docker compose logs api --no-color | Select-String "MultipartException"   # → 无输出（计数 0）
docker compose logs api --no-color | Select-String "\[Import\]"           # → 仅"非 Excel 文件"场景的 WARN
```

---

## 3. 验证数据清理

本次验证产生的服务项目（`F143SV001/002/003/004`、`F143JSON001`、`F143JSONBIG003`）已通过业务接口 `DELETE /biz/service/item/{id}` 软删除。

⚠️ `biz_service_item.uk_tenant_code` 唯一键为 `(tenant_id, service_code)`，**不含 `deleted`** → 软删后编码仍被占用，再次导入同编码会在 DB 层报 `Duplicate entry`（应用层唯一性校验因逻辑删除过滤而查不到）。复现本证据时请使用**全新的编码**。

---

## 4. 本证据包**不**覆盖的范围

- `POST /biz/treatment/plan/import`（`BizTreatmentService.importPlan`）与 `POST /biz/service/item/import` 共用 `XlsxExportUtil.importRows`，改动完全同构，但本证据包只对前者做了 curl 验证；
- `POST /member/import`（CSV 解析异常未捕获，仍会落到 500 兜底）、`DcepMerchantQualificationController` 上传（无格式校验）—— 属 I138 §8 遗留项，本次未修；
- chunked 传输（`Content-Length` 未知）场景**未做运行时验证**：当前 HTTP 客户端与 nginx 默认都带 `Content-Length`，该分支仅经代码路径推演（见 I138 §6 安全折衷），状态为 **UNVERIFIED**。
