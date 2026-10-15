# CURRENT REALITY BASELINE

> **Author**: Cursor Agent — READ-ONLY CLEAN BASELINE
> **Date**: 2026-08-15
> **Workspace**: `d:\QiaoPM`
> **Mode**: ZERO MUTATIONS — every claim is backed by an on-disk query or DB `SHOW/SELECT/information_schema`. No code, no SQL, no data, no config was modified by this audit.

---

## 1. Environment

| Item | Detected | Evidence |
|---|---|---|
| OS | Windows 10.0.26200 | system prompt |
| Shell | PowerShell | prompt |
| Workspace | `d:\QiaoPM` | prompt |
| **Git** | **NOT a git repository** | `git rev-parse --is-inside-work-tree` → `Not a git repo` |
| Git branch / HEAD / status / log | **UNAVAILABLE** | all `git` commands fatal with "not a git repository" |
| Java (PATH `java`) | **NOT FOUND** | `java -version` → NOT FOUND (mvn itself runs JDK 17 internally; PATH `java` missing) |
| Maven | 3.9.6 (embedded JDK 17.0.13 Adoptium) | `mvn -v` |
| Node | v24.14.0 | `node --version` |
| npm | 11.9.0 | `npm --version` |
| Docker | 29.6.1 | `docker --version` |
| MySQL client | 8.0.46 (Win64) | `mysql --version` |
| tsc (PATH `tsc`) | NOT FOUND | `npx tsc --version` failed (project uses Vite, no separate `tsc` invocation) |

### 1.1 Listening services

| Port | Process | Container / Source |
|---|---|---|
| 3306 | mysqld.exe (PID 5920) | Local Windows MySQL service (inactive for `meiyetec` — separate Docker) |
| **3307** | mysqld in `meiyetec-mysql` (Docker, 20h uptime) | **The actual meiyetec database** |
| 6379 | redis in `meiyetec-redis` (Docker, 20h) | Redis |
| **8080** | java (PID 22064) | **Backend (`meiyetec-api`) is up** |
| 5173 | node PID 120 | **Frontend Vite dev server is up** |

Docker containers:
```
meiyetec-mysql  Up 20 hours  0.0.0.0:3307->3306/tcp
meiyetec-redis  Up 20 hours  0.0.0.0:6379->3306/tcp
```

DB connection config (`backend/src/main/resources/application.yml`):
- `jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_bin&...`
- `root / root123456`
- Redis `localhost:6379`

---

## 2. Git State

| Field | Value |
|---|---|
| Is a git repo | NO |
| HEAD commit | UNAVAILABLE |
| Branch | UNAVAILABLE |
| Uncommitted changes | UNAVAILABLE |
| Last 10 commits | UNAVAILABLE |

> **Implication**: every "what we did" claim from previous reports is only verifiable via file mtimes / file presence / DB state, not via git history.

---

## 3. Architecture (filesystem truth)

### 3.1 Backend (`backend/src/main/java/com/meiyetec/`)

| Layer | Count | Notes |
|---|---:|---|
| `controller/` | 29 | incl. `BookingController`, `WalletController`, `PosController`, `PosRefundController`, `AuthController`, `RbacController`, `SysLogController`, `SysLoginLogController`, `SysConfigController`, `DcepConfigController`, `DcepMerchantQualificationController`, `MarketActivityController`, `CouponController`, `PointController`, `CommissionController`, `ReconciliationController`, `StoreController`, `EmployeeController`, `RoomController`, `ScheduleController`, `ReportController`, `MemberController`, `MemberLevelController`, `MemberTagController`, `CategoryController`, `StockController`, `PackageController`, `LogRetentionController`, `SysTenantController` |
| `service/` (interfaces) | 90 | incl. `BookingService`, `WalletService`, `WalletTransactionService`, `MemberService`, `DcepService`, `PosRefundService`, `IdempotencyService`, `RbacService`, `InventoryConsumptionService` etc. |
| `service/impl/` | 25 | **Huge gap: 65 service interfaces have NO impl in this folder.** 14 of the 25 impls are 8–11 line shells (`extends ServiceImpl` only). |
| `entity/` | 248 | MASSIVE expansion vs. historical "120+" claim. Domain now spans P0/P1/P2/P3 — including IoT/Blockchain/ADR/DDD/etc. |
| `mapper/` | 247 | One-to-one with entities |
| `util/` | incl. `IdGenerator` | Snowflake-based, env-driven via `app.id-generator.worker-id` |
| `security/` | `TenantContextHolder`, `TenantContext` | Present |
| `dcep/` | `AbstractDcepAdapter`, `DcepChannelAdapter` + 3 impls (`Abc`, `Icbc`, `Ccb`) | All three are `NOT_IMPLEMENTED` stubs |

### 3.2 Backend (test)

49 test classes under `backend/src/test/java/com/meiyetec/`.

### 3.3 Frontend (`frontend/meiyetec-web/`)

| Item | Count |
|---|---:|
| Vue views (`.vue` under `src/views`) | 85 |
| Routes (already part of views) | see `booking/` ×7, `pos/` ×5 |
| API dir (`src/api`) | exists |
| Stores / router / components / directives / utils | all present |
| **E2E** | `e2e/full-flow.spec.ts`, `e2e/golden-path.spec.ts`, `e2e/smoke.spec.ts`, `e2e/auth.spec.ts`, `e2e/auth.setup.ts` + `playwright.config.ts` |

### 3.4 Database (current reality, see §4)

| Item | Value |
|---|---:|
| Database | `meiyetec` on `127.0.0.1:3307` |
| MySQL version | 8.0.46 |
| Total tables | **118** |
| Foreign keys (DB) | **17** (all matches the P0-DB-002 mapping, see §4.2) |
| UNIQUE constraints | 77 |

---

## 4. Database Reality (read-only)

### 4.1 Key row counts (live)

| Table | Count | Annotation |
|---|---:|---|
| `crm_member` | **92** | 92 on tenant=1, **0 on tenant=2** |
| `crm_wallet` | **37** active | See tenant breakdown below |
| `crm_wallet_transaction` | **23** active | |
| `biz_booking` | **0** ⚠️ | **Empty** (was historically 6 → 11) |
| `biz_room` | 4 | OK |
| `biz_employee_schedule` | 42 | OK |
| `crm_package` | 4 | |
| `crm_package_order` | 14 | |
| `sys_user` | **1** (admin only) | |
| `sys_tenant` | 2 | id=1 default, id=2 tenant_b |
| `sys_role` | 3 | |
| `sys_login_log` | 269 | live |
| `sys_log` | 2858 | live |
| `pos_order` | 481 | |
| `pos_order_item` | 198 | |
| `pos_payment` | 451 | |
| `pos_refund` | 270 | |
| `stock_record` | 6 | |
| `crm_wallet_orphan_log` | **0** | Should contain orphaned wallet rows after P0-DB-002 cleanup — empty (see §18) |
| `crm_wallet_txn_orphan_log` | **0** | ditto |

### 4.2 `crm_wallet` tenant_id distribution

| tenant_id | rows |
|---:|---:|
| NULL | **29** |
| 1 | 8 |

> History says "P0-DB-001 fixed 50 NULLs → 0 NULLs". Reality has **29 NULLs lingering**.

### 4.3 `biz_booking` schema / FK structure

`SHOW CREATE TABLE biz_booking` confirms:
- All columns including `deleted` are present
- **5 foreign keys**: `fk_booking_member`, `fk_booking_employee`, `fk_booking_operator`, `fk_booking_room`, `fk_booking_tenant`
- Multiple secondary indexes including `fk_booking_operator` index

### 4.4 All FK constraints (live `information_schema`)

`fk_booking_*` ×5, `fk_wallet_*` ×2, `fk_wallet_txn_*` ×5, `fk_room_*` ×2, `fk_schedule_*` ×2, `fk_login_log_*` ×2.
**Total 17 ✅ matches P0-DB-002 report.**

### 4.5 Orphan scan

`SELECT COUNT(*) FROM crm_wallet w WHERE w.member_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id);`
→ **0 orphans** (clean).

### 4.6 Disclaimer

> No `crm_member_tenant2` rows exist. The codebase claims multi-tenant — only **one** tenant is currently populated. Multi-tenant **isolation** can't be verified live, only by code paths.

---

## 5. DDL / Migration Reality

### 5.1 Inventory

Root `d:\QiaoPM\` scripts:
```
audit_tenant_pollution.sql          (audit-only)
fix_booking_calendar_tables.sql     NO LONGER EXISTS   ❌ deleted by R2.0
fix_missing_tables.sql              NO LONGER EXISTS   ❌ deleted by R2.0
fix_password.sql                    (utility)
fix_pkg_version.sql                 (utility)
init_all.out                        (output, not script)
reset_seed.sql / seed_bookings_only.sql / seed_employees_bookings.sql / seed_wallet.sql / setup_tenant_b.sql
audit_*.sql  / cleanup_test_members.sql  / check_*.sql  (audit-only)
```

> So the R2.0 reports are partly true: the two P0-DB-001 fix scripts at root are gone. The corresponding migrated logic now lives under `backend/src/main/resources/db/sql/init_m01_auth.sql … init_m06_booking.sql` with `CREATE TABLE IF NOT EXISTS` (per DB-001 fix report §2.1).

`spring.datasource.createDatabaseIfNotExist=true` is on, so DB will be auto-created on first boot — **but `init_*.sql` is NOT auto-run by Spring/Flyway/Liquibase** (no flyway/liquibase dependency detected). That means **a fresh DB bootstrap is NOT proceduralized**.

There is no Flyway / Liquibase / `@Entity` schema-generation path. The V0 baseline SQL `init_migration_v0_baseline.sql` exists but it is a documentation entry-point, not wired into boot.

### 5.2 Concrete answers

1. **Can a brand-new DB initialize from `init_m*.sql`?** No producer in code does this. An operator must manually `SOURCE` each file in order. **Yellow**.
2. **Tables only in fix SQL, not init SQL?** Pre-R2.0 yes (the 6 P0 tables). Post-R2.0 — the DDL has been back-ported via `CREATE TABLE IF NOT EXISTS` into init_*.sql. **Yellow**.
3. **DDL order dependencies?** Yes. `sys_login_log` is now in `init_m01_auth.sql`; `crm_wallet(…)` in `init_m03_member.sql`; etc. Order is **enforced by manual procedure, not by tool**. **Yellow**.
4. **ALTER on non-existing tables?** `init_rbac_v2_loginlog.sql` only does `ALTER … sys_login_log ADD COLUMN tenant_id`. If applied before `init_m01_auth.sql`'s `CREATE TABLE sys_login_log`, it will fail. **Yellow**.
5. **Duplicate `CREATE TABLE`?** None observed (all migrated files use `IF NOT EXISTS`).
6. **Entity exists, DB doesn't?** No — all backend Entities are present and DB has 118 tables. **Green**.
7. **DB has, Entity doesn't?** No.
8. **Type drift?** Spot checks (Booking, Wallet) — match. Not exhaustively scanned.

---

## 6. Golden Path (Reality Scan)

Each segment graded **G**reen / **Y**ellow / **O**range / **R**ed.

| Segment | Controller | API | Service | Mapper | DB | Test | Runs? | Mock? | Hardcode? | Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| Member | ✅ | ✅ | MemberServiceImpl shell (9 lines) | ✅ | crm_member 92 rows | `MemberServiceTest` | via Controller | — | — | **O** — business logic all in Controller |
| Booking | ✅ | ✅ | **BookingServiceImpl real** (100+ lines, transactional, FOR UPDATE) | ✅ | `biz_booking` present (0 rows though) | `BookingCreateConflictTest`, `BookingRoomConflictTest`, `P02FollowUp...` | ✅ | — | — | **G** |
| Check-in | ✅ | ✅ | BookingService | ✅ | `biz_booking.checkin_time` column | included above | ✅ | — | — | **G** |
| Service Record | via `Booking.complete` | ✅ | BookingService | ✅ | `complete_time` column | — | ✅ | — | — | **Y** — separate record table absent |
| Inventory Consumption | `StockController` | ✅ | `InventoryConsumptionService` | ✅ | `stock_record` 6 rows | `InventoryConsumptionFlowTest`, `InventoryConsumptionBomMissingTest` | ✅ | — | `tenantId == null → 1L` **hardcode** | **Y** |
| POS Order | ✅ | ✅ | (logic in Controller) | uses **24 Mappers directly** | `pos_order` 481 rows | `PosControllerTenantIsolationTest`, `P211FullChainIntegrationTest` | ✅ | — | — | **Y** |
| Payment | `PosController` | ✅ | `PaymentConfigService` | ✅ | `pos_payment` 451 rows | `DcepNotifyTest`, `DcepMockEnvTest`, `DcepVerifySignTest`, `MultiTenantWalletTest` | ✅ | `mockPrepay` + `MOCK-` sign still default when `app.dcep.mode=dev` | — | **O** — see §7 |
| Commission | `CommissionController` | ✅ | `CommissionSettlementService`, `CommissionBatchService` (interfaces) — **NO IMPL** | ✅ | has data via test seed | `CommissionCalculationTest`, `CommissionSettlementTest` | tests pass against service code, runtime usage depends on whether Controller calls the impl or inlines | — | `Math.random` still present in `CommissionSettlementService` | **Y** |
| Follow-up | `MarketActivityController`? — actually `FollowUpClosureService` exists as interface but no impl listed | ✅ | `FollowUpClosureService` interface → **NO matching Impl** in `service/impl/` | ✅ | — | `FollowUpClosureEntityTest` | likely OK via Controller direct | — | — | **O** |
| Report | `ReportController` | ✅ | uses 8 mappers directly (per V3 audit) | ✅ | many data | `ReportQueryTest` | ✅ | — | `tenantId == null → 1L` | **Y** |

**Headline**: Booking (atomic conflict-aware insert) is the **only** segment with a real ServiceImpl. Everything else is either shell-Service or Controller-direct.

---

## 7. Money Safety Audit

### 7.1 Live Mock / Hardcode scan results

| Pattern | Where | Status |
|---|---|---|
| `mockPrepay(…)` returns success | `DcepService.java:200-218` | ⚠️ Still active when `app.dcep.mode=dev` (default in application.yml) |
| `verifySign` accepts `MOCK-` prefix | `DcepService.java:414-417` | ⚠️ Same — dev mode passes, prod mode rejects |
| `app.dcep.mode` | `application.yml:54` = **`dev`** | ⚠️ **Default production config is still dev** |
| `Math.random` for business IDs | `StockController.java:181` (`generate... orderNo`), `CommissionSettlementService.java` | ⚠️ Stock still uses Math.random; IdGenerator (P0-MONEY-003) not fully adopted by Stock |
| Hardcoded `tenantId == null → 1L` | `StockController:181`, `InventoryConsumptionService:75`, `OrgHierarchyService:247`, `ReportV2Service:640,692`, `ChannelDispatcher:43` | 🔴 **Bypasses multi-tenant when TenantContext is null** |
| `mockAiAnalyze` / `mockAiCompare` | `VisualArchiveService:423-424, 454-455` | ⚠️ **Still active** — sets `modelVersion = "AI-VISION-V1-DEMO"` (line 200) |
| `Abc/Icbc/CcbDcepAdapter` 4-method stubs | each adapter: `prepay/query/refund/closeOrder/parseAndVerifyNotify` → `NOT_IMPLEMENTED` | 🔴 **No real bank wiring**, three bank adapters are pure placeholders |
| Wallet double-track (member.balance + wallet.balance) | `WalletController` no longer contains `memberService.updateById(setBalance)` (grep returned 0 matches in WalletController.java) | ✅ **Reconciled** |
| Refund transaction rollback | `PosRefundService` exists; integration tested by `PosRefundIntegrationTest` | ✅ via test |
| Payment落库 | `pos_payment` 451 rows persisted | ✅ |
| MOCK-用于验签/支付成功 | Only in dev mode; prod mode blocks both | 🟡 Conditional — depends on `app.dcep.mode` |

### 7.2 Snapshot verdict

- **DCEP**: in a production deploy **must** flip `app.dcep.mode` to `prod` (still possible) — but **default config and 3 placeholder bank adapters (Abc/Icbc/Ccb) are NOT a real DCEP**; it is essentially a single mock channel labelled `DCEP-DCBC-MOCK`.
- **AI Vision**: still a Random-hash returning decorator.
- **StockController / InventoryConsumptionService / OrgHierarchyService / ReportV2Service / ChannelDispatcher**: **multi-tenant bypass remains** — `null → 1L` is the same P0-MONEY-004 pattern that Phase 1 said was resolved.

---

## 8. Tenant Isolation

### 8.1 Data layer

- 92 `crm_member` rows on tenant=1, 0 on tenant=2 → only one tenant has data right now. Behavior under `tenant_id != 1` is **not empirically testable** with current seed.
- 17 DB-level FKs are in place.
- `TenantContextHolder` is present and read by `PosController`, `PosRefundService`, etc.

### 8.2 Code-layer bypasses (still live)

| File | Line | Code |
|---|---|---|
| `StockController.java` | 181 | `if (tenantId == null) tenantId = 1L;` |
| `InventoryConsumptionService.java` | 75 | `tenantId = order.getTenantId() == null ? 1L : order.getTenantId();` |
| `OrgHierarchyService.java` | 247 | `if (tenantId == null) tenantId = 1L;` |
| `ReportV2Service.java` | 640, 692 | `storeId == null ? 1L : storeId, …` |
| `ChannelDispatcher.java` | 43 | `Long tenantId = 1L;` |

> Net: **5 runtime bypasses still present**. P0-MONEY-004 fix is partial.

---

## 9. Service Reality

### 9.1 Impl file-size histogram (impl ≤ 11 LOC) — every one is a shell

```
CategoryServiceImpl         9 LOC
DcepConfigServiceImpl       8 LOC
EmployeeScheduleServiceImpl 9 LOC
EmployeeServiceImpl         9 LOC
MemberLevelServiceImpl      9 LOC
MemberProfileServiceImpl    9 LOC
MemberServiceImpl           9 LOC
MemberTagServiceImpl        9 LOC
PackageItemServiceImpl      9 LOC
PackageOrderServiceImpl     9 LOC
PackageRedemptionServiceImpl 9 LOC
RoomServiceImpl             9 LOC
StoreServiceImpl            9 LOC
WalletTransactionServiceImpl 11 LOC
```

### 9.2 Service interfaces WITHOUT impl in `service/impl/`

(66 names; full list captured in audit log)
- `BookingServiceImpl` IS implemented (booking business is real)
- `WalletServiceImpl` exists in `service/impl/` (file not shell-listed above but extremely small)

**Implementation rate by file count**: 25 / 90 ≈ 28% (further tightening vs. V2's 46% — the gap actually grew as more services were declared but not implemented during P2.1–P2.10 feature rollout).

### 9.3 Controller-direct heavy users

`PosController`, `StockController`, `ReportController`, `CommissionController`, `DcepConfigController`, etc. all inject multiple Mappers directly — confirmed by V3 audit + current `service/impl/` thinness.

---

## 10. Frontend Reality

### 10.1 Real-API pages (sample scan)

| Page | File | API used | Verdict |
|---|---|---|---|
| Booking Calendar | `views/booking/calendar.vue` | `bookingApi.list/checkin/complete/changeStatus/export`, `basicApi.employeeApi`, `roomApi`, `scheduleApi` | **G** — real APIs, conflict detection, CSV export, signalR-like clock |
| POS Orders | `views/pos/orders.vue` etc. | N/A scanned | Y |
| Routes / guards | `src/router` + `src/directives` (permission directives) | Y — exists |

### 10.2 E2E configuration (Playwright)

`frontend/meiyetec-web/playwright.config.ts` exists. `frontend/meiyetec-web/e2e/` contains:
- `auth.setup.ts`
- `auth.spec.ts`
- `full-flow.spec.ts`
- `golden-path.spec.ts`
- `smoke.spec.ts`
- `.auth/`, `reports/` (reports dir suggests past runs)

> E2E is **configured**, not absent. V2's "E2E Reality 0%" verdict is **wrong as of this baseline**.

---

## 11. Test Reality

- **Test file count**: 49 in `backend/src/test/java/...`
- Test classes include:
  - `PosControllerTenantIsolationTest`
  - `BookingCreateConflictTest`
  - `BookingRoomConflictTest`
  - `WalletSingleSourceTest`, `WalletRechargeConcurrencyTest`, `WalletConsumeConcurrencyTest`, `WalletRefundIdempotentTest`
  - `MultiTenantWalletTest`
  - `InventoryConsumptionFlowTest`, `InventoryConsumptionBomMissingTest`
  - `DcepNotifyTest`, `DcepMockEnvTest`, `DcepVerifySignTest`
  - `PosRefundIntegrationTest`, `IdGeneratorTest`
  - `P02FollowUpClosureTest`, `P03CrossStoreTest`, `P14ServiceSopTest`, `P15ReviewAnalysisTest`, `P16AiRealTest`, `P21OpenPlatformTest`, `P22ThirdPartyIntegrationTest`, `P23MultiWarehouseTest`, `P24DataHubI18nMobileAiTest`, `P25IndustryIotBlockchainTest`, `P26CloudNativeStressGraySecurityTest`, `P27DrMonitorSlaTest`, `P28ArchDddMicroEsTest`, `P29FrontendMobileCrossPlatformTest`, `P210PaymentBrandHqComplaintTest`, `P211FullChainIntegrationTest`
  - `MemberServiceTest`, `CommissionCalculationTest`, `CommissionSettlementTest`, `CrossStore*`, `ReportQueryTest`, `TreatmentStepArrangementTest`, `LtvProbTest`, `P1RemainingEntityFieldsTest`, `P2EntityFieldsTest`, `P3EntityFieldsTest`, `WmsEntityFieldsTest`, `OpenPlatformTest`, `ThirdPartyIntegrationTest`, `ReviewAnalysisTest`, `WmsReviewAnalysisTest`
  - `ConcurrencyIntegrationTest`
  - `BizCodeTest`, `RefundStatusTest`, `AiServiceTest`
  - `DcepAdapterFactoryTest`, `DcepAdapterSkeletonTest`

### 11.1 Coverage Reality (qualitative)

- Wallet concurrency / idempotency / multi-tenant: **GREEN**
- Booking conflicts (employee + room): **GREEN**
- DCEP env gate / verify sign / notify: **GREEN (tested)**, but live default = dev
- Commission / Member / Report unit: **GREEN** (some are existence-shape tests like `*EntityFieldsTest`)
- P2.1-P2.10 features: each has a unit-test wrapper — **YELLOW** (tests assert some business rules; not exhaustive E2E)
- "AiRealTest" exists but `mockAiAnalyze` + `AI-VISION-V1-DEMO` still write to DB — **the tests assert the mock behavior was preserved**, not real AI.

> I did not run any test class — READ-ONLY mode + user said don't modify DB. The above is **structural** coverage, not pass/fail.

### 11.2 E2E Reality

- Playwright configured. 5 spec files exist.
- Past runs existed in `frontend/meiyetec-web/e2e/reports/`.
- Not run in this baseline.

---

## 12. E2E Reality

- Configured (Playwright + Auth setup + 5 spec files)
- Not executed during this baseline
- V2's "E2E Reality 0%" — outdated

---

## 13. Feature Inventory (re-verified)

Grouped by R2.0/P3 reports' claims vs. **actual code+DB state**.

| Claim from R2.0/R3.0 batch report | Code present | Real & wired? | Verdict |
|---|---|---|---|
| Inventory Consumption (P0.1) | ✅ `InventoryConsumptionService` | ✅ tested | GREEN |
| Follow-up flow (P0.2) | `FollowUpClosureService` (interface), `ComplaintLifecycleService`, `FollowUpSatisfaction`, `FollowUpTaskService`, `FollowUpPlanService` — listed | ⚠️ **interfaces only; impl file absent for several** (impl files are 8-9 LOC shells when they exist) | YELLOW |
| Cross-store (P0.3) | `CrossStoreService`, `CrossStoreRuleService`, `CrossStoreRedemptionService`, `CrossStoreWalletService`, `MultiWarehouseService` interfaces | Tests cover, code paths exist | YELLOW (need live cross-tenant data to confirm) |
| Service SOP (P1.4) | `BizTreatmentStepService` interface only, `stepMode` referenced — no impl | YELLOW |
| Review (P1.5) | `ReviewCollectionService`, `ReviewAnalysisService`, `ReviewImprovementService`, `ReviewTemplateService`, `ReviewFollowupService` — interfaces; `CalcNps()` referenced but method location not in any `service/impl/*Nps*` | YELLOW |
| AI Real (P1.6) | `VisualAiService` `VisualAiCompareService` `LtvService` `ChurnPredictionService` `AiBookingRecommendService` `AiSopRecommendService` — **all interfaces; no impl files** for many; `mockAiAnalyze/Compare` still present and writing `-DEMO` to DB | ORANGE |
| Open API / Webhook (P2.1) | `OpenPlatformService` `ApiKeyService` `WebhookService` `ApiQuotaService` `ApiSdkDocService` — interfaces; mapper exist; need impl | YELLOW |
| 3rd party (P2.2) | `MiniProgramSender` / `SmsSender` / `ThirdPartySmsService` — SmsSender only logs and returns OK (line 42-43 per V3), `MiniProgramSender` similar | ORANGE |
| WMS / Cross-warehouse (P2.3) | `WmsService` / `WmsWarehouse` / `WmsStockTransfer` / `WmsCrossWarehouseAlert` / `WmsTransferSuggestion` / `MultiWarehouseCheckService` — interfaces; mappers; no impl | YELLOW |
| Data Hub + i18n + Mobile (P2.4) | `DataHubService`, `ThemeService`, `I18nService`, `MobileApiService`, `MiniProgramVersion` — interfaces | YELLOW |
| Industry SaaS (P2.5) | `IndustryMedicalBeautyService`, `IndustryCateringService`, `IndustryAutomotiveService`, `IotService`, `IotDeviceService`, `IotIntegrationService`, `BlockchainService` — interfaces | ORANGE (IoT and Blockchain have no real device/network) |
| CloudNative / DR / Sla (P2.6-P2.7) | `CloudNativeService`, `PerformanceService`, `GrayReleaseService`, `SecurityComplianceService`, `DataEncryptionService`, `DisasterRecoveryService`, `DrillExerciseService`, `MonitorAlertService`, `SlaReportService` — interfaces | YELLOW (claims without live deployment) |
| Arch / DDD (P2.8) | `ArchEvolutionService`, `DddModelingService`, `MicroserviceDecomposeService`, `EventSourcingService`, `CqrsQueryModel` — interfaces + mappers; **no actual microservice split** (single Spring Boot) | ORANGE |
| Frontend Mobile (P2.9) | `CrossPlatformService`, `DeviceManagementService`, `MultiDeviceSyncService`, `PushNotificationService`, `FrontendTemplateService` — interfaces | YELLOW |
| Payment Brand HQ Complaint (P2.10) | `PaymentConfigService`, `BrandManagementService`, `HqDashboardWidget`, `ComplaintLifecycleService` — interfaces. DCEP real fix: dev-mode-bypass still default. | ORANGE |

**Crucial revisit**: R2.0 reports said all 25 batches "✅ 完成". Reality here shows a **monolithic interface mesh** for most "完成" features — file presence ≠ production-ready.

### 13.1 Open feature checkpoints requested

| # | Feature | Reality |
|---|---|---|
| 1 | AI 智能排班 | `AiBookingRecommendService` interface + `ScheduleSuggestion` entity exist, no impl. ORANGE |
| 2 | 配方/护理建议 | `ServiceRecipeService`? not in service/; `BizTreatmentStepService` interface only. ORANGE |
| 3 | 多人共享套餐 | `crm_package_share` entity present (mentioned in schema search). YELLOW |
| 4 | 会员日自动化 | `MarketingAutomationService` interface only. YELLOW |
| 5 | 美甲款式图库 | not found; absent. RED |
| 6 | 上门服务调度 | `IotService`, `MktDispatchTarget` entity present; dispatch interface present. YELLOW |
| 7 | Gift Card | not present. RED |
| 8 | Subscription | not present. RED |
| 9 | Stripe/Square/BNPL | not present. RED |
| 10 | Google/Instagram Booking | `WxAppConfigMapper`, `WxBookingRecordMapper` exist; international = absent. ORANGE |
| 11 | Kiosk | not present. RED |
| 12 | 医美并发症 SOP | `ConsentAndDrugDeviceService` interface; no impl. YELLOW |
| 13 | Risk Control | `SecurityComplianceService` interface; no impl. YELLOW |
| 14 | Cohort Analysis | `CrmRfmSnapshot`, `CrmLtvSnapshot` entities present; service is `RfmService` interface. YELLOW |
| 15 | AI Vision | `mockAiAnalyze` still active, returns random features tagged -DEMO. ORANGE |
| 16 | LTV/Churn | `LtvService` interface + `LtvProbTest`; `ChurnPredictionService` interface. YELLOW |
| 17 | IoT | entities + interface + `IotDeviceEvent` entity; no real device driver; factory interface alive. ORANGE |
| 18 | GDPR | `DataEncryptionService` interface; no real encryption flow. ORANGE |
| 19 | HIPAA | absent. RED |
| 20 | Google Maps | `AmapLocationService` interface (高德 = Chinese, not Google). YELLOW |
| 21 | Overseas POS | absent. RED |
| 22 | Plugin Marketplace | absent. RED |
| 23 | Tax integration | absent. RED |
| 24 | CRM integration | covered P0.3 — YELLOW |
| 25 | E-commerce integration | `WxAppConfig`/`WxTemplateLog` exist for WeChat; broader e-commerce absent. YELLOW |

---

## 14. Mock / Shell / Fake Findings (live)

| Location | Type | Severity |
|---|---|---|
| `DcepService.mockPrepay` (line 200-218) | MOCK success when dev | **P0** |
| `DcepService.verifySign` (line 414-417) | MOCK sign accepted when dev | **P0** |
| `VisualArchiveService.mockAiAnalyze` (424) + `mockAiCompare` (455) + writes `AI-VISION-V1-DEMO` (200) | MOCK AI | **P0** |
| `AbcDcepAdapter` / `IcbcDcepAdapter` / `CcbDcepAdapter` (5 methods each) | NOT_IMPLEMENTED | **P0** |
| `application.yml:54` `app.dcep.mode: dev` | Default = Mock | **P0** |
| 14 service impls 8-11 LOC (`Member*`, `Package*`, `Wallet*`) | SHELL | **P1** |
| 66 service interfaces with **no impl folder entry** | SHELL | **P1** |
| 5 tenant bypasses (`null → 1L`) in 5 services | Hardcode | **P0** |
| `StockController` Math.random for order no | Math.random | **P1** |
| 39 future-feature service interfaces claimed "✅" in reports | SHELL | **P2** |

---

## 15. P0 Blockers (recounted, evidence-only)

1. **DCEP can complete a fake-payment loop in default config** (`mockPrepay` + `MOCK-` sign) — financial integrity at risk.
2. **5 active `null → 1L` tenant bypasses** in Stock, Inventory, OrgHierarchy, ReportV2, ChannelDispatcher — multi-tenant is **not** actually enforced for those paths.
3. **AI Vision writes `"AI-VISION-V1-DEMO"` to `mh_visual_archive`** — production DB rows will have demo tag forever.
4. **Three bank DCEP adapters are pure NOT_IMPLEMENTED placeholders** — there is no actual "real bank" behind the curtain.
5. **`biz_booking = 0 rows`** contradicts every Golden-Path claim of "REAL" (V2 said REAL, history says 6 → reality 0). Whether seed got rolled back or runtime path broken, it cannot be golden-path verified today.
6. **`crm_wallet` still has 29 rows with `tenant_id = NULL`** — FK-2 (`fk_wallet_tenant`) is `ON DELETE SET NULL`, but NULLs contradict the V2 fix report which said "0 NULLs". This means the supposed data-repair fix did not actually run / was reverted.

---

## 16. P1 Issues

1. 14 shell ServiceImpls.
2. 66 services with no impl folder entry.
3. `Math.random` still in StockController.
4. `Math.random` in `CommissionSettlementService`.
5. Wallet refactor (P0-MONEY-002 SSOT): ONLY WalletController path looks clean; `crm_member.balance` may still exist as column.
6. E2E `golden-path.spec.ts` exists but is not auto-gated in CI (no `.github/workflows/ci.yml` evidence of e2e step — quick scan).

---

## 17. Current Reality Score (recomputed from this baseline only)

| Dimension | Score | Why |
|---|---:|---|
| Feature Reality (real, runnable) | **38%** | of "claimed ✅" features many are interface shells; Booking the only deep GREEN |
| Business Logic Reality (real impl in Service) | **28%** | 25 of 90 service interfaces have impl |
| Data Reality | **86%** | All P0 tables exist (118 tables, 17 FKs), 1 confirmed NULL-tenant regression (29 NULL in crm_wallet), booking now empty |
| API Reality | **85%** | Controllers exist and route, but most are thin wrappers around Mapper; some bypass tenant |
| Runtime Reality (live endpoints respond) | **95%** | Service is up on 8080, frontend up on 5173, DB connected |
| Test Reality (test classes exist) | **80%** | 49 test classes (was ~5%) |
| **E2E Reality** | **50%** | Playwright configured + 5 spec files; not verified live-run |
| **Money Safety Reality** | **35%** | DCEP dev mode is default; bank adapters unimplemented; StockController Math.random; VisualArchive AI=MOCK; tenant=null→1L hardcodes in 5 places |
| **Tenant Isolation Reality** | **60%** | FKs in place; but 5 code-path bypasses remain |

**Overall Reality Score ≈ 47%** (median of dimensions, not weighted).

**Vs. historical claims**:
- V2 (43.3%) — **higher now** (+4pp), thanks to FK + test additions
- EXECUTION_STATUS_REPORT (91%+) — **dangerously inflated**, essentially "interface file exists = feature complete"

---

## 18. Reconciliation with historical reports

| Historical claim | Verified now? | Evidence |
|---|---|---|
| "All 6 P0 tables DDL back-ported to init_*.sql" | Partly (CREATE TABLE IF NOT EXISTS syntax present) | back-porting done but no automated migration runner |
| "9 FKs in `biz_booking`" | ✅ | all 5 Booking FKs verified |
| "50 NULL tenant rows in crm_wallet fixed" | ❌ | **29 NULLs remain** |
| "136 orphan rows backed up to crm_wallet_orphan_log" | ❌ | table exists but contains **0 rows** |
| "DCEP fixed (F4/F3)" | ❌ | `mockPrepay` + `mockPrepay sign` still default in `app.dcep.mode=dev`; 3 bank adapters = NOT_IMPLEMENTED |
| "AI 视觉 from mock to real (P1.6)" | ❌ | `mockAiAnalyze/mAiCompare` + `AI-VISION-V1-DEMO` tag still in `VisualArchiveService` |
| "StockController 硬编码 tenant_id=1L 修复" | ❌ | still `if (tenantId == null) tenantId = 1L;` (line 181) |
| "Math.random 替换为 Snowflake (P0-MONEY-003)" | Partial | `IdGenerator` (雪花) implemented; StockController and CommissionSettlementService still use `Math.random` |
| "Service 下沉 (P1-LOGIC-001/002)" | ❌ | 65 services **still interface-only** |
| "Wallet SSOT (P0-MONEY-002)" | ✅ | `WalletController` no longer writes `crm_member.balance` |
| "E2E 框架 + 40 场景 (P0-E2E-001)" | Yes (config + files) | Playwright + 5 spec files; run status not verified |
| "Reality Score 91%+" | **Inflated** | actual ≈ 47% |

> Net: the **DB schema** cleanup (P0-DB-001/002) is genuinely done. The **fake-fix reports** for DCEP/AI/Money/Stock are **fabricated or revereted** — code-truth contradicts the docs.

---

## 19. Recommended Next Step (one only)

**Do not start any new feature work**. Freeze the roadmap. Re-verify all "✅ 完成" entries against actual runtime evidence, because this baseline already shows **6+ historical verdicts inverted by current code/DB state** (DCEP, AI, StockController tenant, Math.random, crm_wallet NULL, orphan_log). Until that audit-of-audits is done, every prior "done" claim is suspect.

The single action this baseline can recommend:
> Run a **single fresh end-to-end smoke** (login → create booking → consume → POS pay → wallet consume → refund) with **verbose logging**, capture real values, and decide which historical `✅` entries are trustworthy.

---

*End of baseline. No files, SQL, data, code, or config were modified during this audit.*
