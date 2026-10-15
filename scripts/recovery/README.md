# First Batch Recovery — Runbook

> Generated: 2026-09-06 by Disabled Controller Recovery Audit
> See: `DISABLED_CONTROLLER_RECOVERY_AUDIT.md` for full matrix

## Status: ✅ READY (NOT EXECUTED)

Two scripts created under `scripts/recovery/`:
- `recovery-first-batch.ps1` — DRY-RUN by default
- `recovery-first-batch-rollback.ps1` — DRY-RUN by default

Both scripts are **safe by default** (no `-Execute` flag = no changes).

---

## Workflow

### Step 1: Pre-flight (compile baseline)

Before running any recovery, fix the compile failure identified in audit:

```bash
cd d:\QiaoPM
mvn -pl backend clean compile -U
```

**If FAILED**: check `entity/CrmSyncLog.java` lines 42-47 — Lombok `@Data` not generating setters.
- Option A: `mvn clean compile -U` (force annotation re-processing)
- Option B: IDE → Invalidate Caches → Rebuild Project
- Option C: Replace `@Data` with explicit `@Setter @Getter` on `CrmSyncLog`

**If SUCCESS**: proceed to Step 2.

### Step 2: Dry-run preview

```powershell
cd d:\QiaoPM
.\scripts\recovery\recovery-first-batch.ps1
```

Output shows what **would** happen. No files changed.

### Step 3: Execute (irreversible!)

```powershell
.\scripts\recovery\recovery-first-batch.ps1 -Execute
```

Will:
1. Rename 6 files (`.java.disabled` → `.java`):
   - `AttendanceController.java.disabled`
   - `AttendanceConfigController.java.disabled`
   - `LeaveRequestController.java.disabled`
   - `CrmFollowUpTaskController.java.disabled`
   - `CrmReviewController.java.disabled`
   - `CrmReviewTemplateController.java.disabled`
2. Run `mvn -pl backend clean compile`
3. Run targeted unit tests (if any)

### Step 4: Backend restart + smoke test

```bash
cd d:\QiaoPM\backend
mvn spring-boot:run
```

Wait for `Started MeiyetecApplication`. Then:

```bash
TOKEN=<your-jwt>
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/attendance/today?employeeId=1
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/attendance/config/active
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/attendance/leave/pending-count?storeId=1
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/crm/care/follow/task/pending-count
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/crm/care/review/list
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/crm/care/review/tpl/list
```

### Step 5: Browser verification

Login as SUPER_ADMIN or ADMIN, visit:
- `/attendance`, `/attendance/records`, `/attendance/leave`, `/attendance/statistics`, `/attendance/config`
- `/crm/care/follow-task`, `/crm/care/review`, `/crm/care/review-tpl`

Pass criteria: pages render, lists load, no 404/500 in DevTools.

### Step 6: Rollback (if anything fails)

```powershell
.\scripts\recovery\recovery-first-batch-rollback.ps1 -Execute
```

Restores 6 files to `.java.disabled`. Run `mvn -pl backend clean compile` to verify back to baseline.

---

## What is NOT touched

- Feature flags: all 12 maintained at current state
- Database: no migrations run
- Disabled controllers not in this batch (51 controllers remain disabled)
- Code: no edits beyond `.disabled` → `.java` rename

## What IS touched (irreversible)

- File state of 6 controllers (renamed from disabled to enabled)
- Compilation state (must succeed after rename)
- Any side effects of having these endpoints live (DB reads via new endpoints, no DB writes triggered by startup alone)

---

## Next batches (NOT in this script)

After successful First Batch + 24h observation window, plan Second Batch:

**Second Batch candidates** (P1, A-class, no FE/FE compatibility confirmed):
- CommissionSimulationController
- CommissionConditionController
- CommissionTierController
- CareSuggestionEngineController
- CareSuggestionLogController
- CareSuggestionRuleController
- CareSuggestionTemplateController
- HairDetectionController
- NailDetectionController
- DetectionReportController
- DetectionSolutionController
- DetectionDiagnosisController
- ReportCaliberController
- ResourceUtilizationController
- BlockchainController
- CareRecipeController
- BizServiceItemController

(17 controllers; do NOT batch-rename without per-module planning)

---

## Status Snapshot

- [x] Audit report generated: `DISABLED_CONTROLLER_RECOVERY_AUDIT.md`
- [x] Recovery script ready (DRY-RUN safe): `scripts/recovery/recovery-first-batch.ps1`
- [x] Rollback script ready: `scripts/recovery/recovery-first-batch-rollback.ps1`
- [x] Runbook generated: `scripts/recovery/README.md` (this file)
- [ ] Phase 0 (compile baseline fix): **NOT EXECUTED — awaiting user**
- [ ] Phase 1 (rename 6 files): **NOT EXECUTED — awaiting user**
- [ ] Phase 2-5 (verify): **NOT EXECUTED — awaiting user**

User must explicitly invoke `recovery-first-batch.ps1 -Execute` to commit.
