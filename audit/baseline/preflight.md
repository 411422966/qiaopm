# Pre-flight Check锛圥hase -1 浜у嚭锛?

> 鏉ユ簮锛歚@project-audit.mdc` 瑙勫垯 16锛圥hase -1 蹇呴』鍏堜簬 Phase 0锛?
> 妯℃澘锛歚@CURSOR_AUDIT_GUIDE.md`銆屼簩鐐逛簲銆嶇珷鑺?
> 鍒涘缓鏃堕棿锛?026-09-12 21:00 (UTC+8)
> 閫傜敤鎵规锛歷3 绗竴鎵癸紙A1-A2 + B1-B7 + C1锛?
> 涓婁竴闃舵浜у嚭锛歚audit/baseline/{commit.txt, env.md, db-snapshot.sql}`

---

## 鈿狅笍 宸ヤ綔娴佸亸宸鏄?

鎸夌敤鎴锋寚浠わ紝绗竴姝ヨ姹?`git stash push -u -m "before-phase-minus-1"` 鍚庝繚鎸?working tree clean銆?
浣嗘墽琛?C1 Phase -1 鏃堕渶瑕佽鍙栦互涓嬩袱涓?**untracked 鏂囨。**锛屼簩鑰呭潎琚?stash -u 涓€璧峰甫璧帮細

- `docs/PROJECT_AUDIT_PLAYBOOK.md`锛?4,901 bytes锛?
- `鍦?Cursor 涓惤鍦拌繖濂楅獙鏀朵綋绯荤殑鎿嶄綔鎸囧崡`锛?46 琛岋紝鏍圭洰褰曪級

**宸查噰鍙栫殑琛ユ晳**锛氭墽琛?`git stash pop 'stash@{0}'` 鎭㈠ working tree 鍚庣户缁?Phase -1銆?
**缁撴灉**锛氭湰 Phase -1 鏈熼棿 working tree 涓?**dirty**锛堝惈鍏ㄩ儴 25+ 澶?EXT-*/MKT 鏀瑰姩 + preflight.md锛夈€?

**鏈‘璁ら」**锛氭槸鍚﹀湪 Phase -1 鎶ュ憡鍚庡啀娆?`git stash` 鏀跺熬銆?*寰呯敤鎴峰喅绛?*銆?

---

## 涓€銆丮CP 閰嶇疆鐘舵€?

| MCP | 鐘舵€?| 鏉ユ簮 |
|---|---|---|
| **MySQL MCP** | 鉁?**宸查厤缃?* | 鐢ㄦ埛 B2 闃舵纭瀹屾垚锛?閰嶇疆 MySQL MCP锛屽凡瀹屾垚"锛?|
| **Playwright MCP** | 鉂?**寰呯敤鎴风‘璁?* | 鏈浼氳瘽鏈槑纭€傛湰鏈?`mysqldump 8.0.46` / `git 2.53.0` / `node 24` 宸茬‘璁ゅ彲鐢?|

**鏈厤缃奖鍝?*锛?
- Phase 5锛圧untime Verification锛夐渶瑕佺湡娴忚鍣ㄤ氦浜掗獙璇?鈥?鏃?Playwright MCP 鏃堕檷绾т负锛?
  - 闈欐€佹姄 HTML 鍏冪礌
  - 璋?API + curl 妯℃嫙
  - 鐢?vue-tsc + Vitest 鍗曞厓娴嬭瘯鏇夸唬
- Phase 6锛圖B Verification锛夐渶瑕佺洿杩炵敓浜ф暟鎹簱鎵ц鏌ヨ 鈥?鏃?MySQL MCP 鏃堕檷绾т负锛?
  - 鐢ㄦ埛鎻愪緵鐨?SQL 鑷墽琛?
  - Cursor 涓嶄富鍔ㄨ繛鐢熶骇搴擄紙瑙勫垯 9 鑳藉姏杈圭晫锛?

---

## 浜屻€丳RD 鏄惁瀛樺湪

| 椤?| 鍊?|
|---|---|
| **璺緞** | `docs/PRD-缇庝笟SaaS绯荤粺鍔熻兘澶у叏.md`锛?4,190 bytes锛寁1.0锛?026-07-31锛?|
| **瑕嗙洊鑼冨洿** | 12 鏍囧噯妯″潡锛圡01-M12锛? 6 杩涢樁妯″潡锛圡13-M18锛夛紝P0/P1/P2/P3 鍒嗙骇 |
| **瑕嗙洊璇︾粏** | 棰勭害/鏀堕摱/浼氬憳/濂楅/鍌ㄥ€?椤圭洰/搴撳瓨/鍛樺伐/钀ラ攢/璇勪环/鎶ヨ〃/杩為攣 + 鏅鸿兘鎺掔彮/鍔ㄦ€佸畾浠?瀹㈡埛360/鑷姩鍖栬惀閿€/瑙嗚妗ｆ/鐢靛瓙鐭ユ儏鍚屾剰 |
| **鏀粯鏂瑰紡瑕嗙洊** | 寰俊/鏀粯瀹?閾惰仈浜戦棯浠?鏁板瓧浜烘皯甯?2.0锛堢‖閽卞寘/杞挶鍖?鏅鸿兘鍚堢害/绂荤嚎/瀛愰挶鍖咃級/鑺卞憲/Stripe/Square/PayPal/Gift Card/BNPL |

### 缂哄け閮ㄥ垎锛堜笉鍦?PRD 鍐呯殑鍐呭锛?

| 缂哄け鍐呭 | 瀹為檯浣嶇疆 | 褰卞搷 Phase |
|---|---|---|
| **楠屾敹鏍囧噯**锛圠0-L5 绛夌骇瀹氫箟锛?| `docs/PROJECT_AUDIT_PLAYBOOK.md` 瀹屾暣铻嶅悎鐗?| Phase 7 闇€瀵圭収 |
| **鏁版嵁妯″瀷 / 琛ㄧ粨鏋?* | `docs/TECH-缇庝笟SaaS绯荤粺鎶€鏈柟妗?md`锛?0KB锛? `docs/TECH-...v2.0.md`锛?9KB锛?| Phase 2 |
| **API 璁捐 / 鎺ュ彛濂戠害** | `docs/EXEC-*.md`锛?5+ 涓墽琛屾枃妗ｏ級 | Phase 2 / 7 |
| **闈炲姛鑳介渶姹傦紙NFR锛?* | **鏈壘鍒扮嫭绔?NFR 鏂囨。** | Phase 8 闇€鐢ㄦ埛琛ュ厖 |
| **鏉冮檺鐭╅樀** | `docs/SECURITY-鏉冮檺鐮佹槧灏勮〃.md`锛?,555 bytes锛?| Phase 7 |
| **閫€娆惧崟鐘舵€佹満** | `docs/REFUND-閫€娆惧崟鐘舵€佹満涓庣瓥鐣?md`锛?,672 bytes锛?| Phase 6 |
| **鍙戝竷闂ㄧ / 涓婄嚎 SOP** | `docs/RELEASE-GATE-鍙戝竷闂ㄧchecklist.md`锛?,705 bytes锛? `docs/DEPLOY-SOP.md`锛?,282 bytes锛?| Phase 8 |

**褰卞搷**锛歅hase 7锛圔usiness Verification锛夐渶鎶?PRD 妯″潡娓呭崟涓?PROJECT_AUDIT_PLAYBOOK 楠岃瘉绛夌骇**鍙屽悜浜ゅ弶瀵圭収**锛屼笉鍙崟鐙緷璧?PRD銆?

---

## 涓夈€丅LOCKER 鍒ゅ畾闃堝€?

鎸?浜岀偣浜?绔犺妭妯℃澘锛屽垪鍑洪粯璁よ鍒欙紝**鍏ㄩ儴寰呯敤鎴风‘璁?*锛?

| 鎯呭喌 | 鏄惁 BLOCKER锛堥粯璁わ級 | 闇€鐢ㄦ埛纭 |
|---|---|---|
| Mock 鏈竻鐞嗭紙鍚?fallback 閾捐矾锛?| 鉁?**鏄?* | 鏄惁璞佸厤鏌愪簺鍦烘櫙锛?|
| 杩佺Щ鑴氭湰鏈湪鐢熶骇璺戣繃 | 鉁?**鏄?* | dev 鐜璺戣繃涔熺畻"閫氳繃"鍚楋紵 |
| webhook 绔偣缂哄け锛堝凡鐭ュ钩鍙版湭瀵规帴锛?| 鉁?**鏄?* | 鏄惁鍏佽"宸茶鍒?宸插缓绌哄３"鍗犱綅锛?|
| 鏈窇 E2E 鐨勬牳蹇冧笟鍔?| 鉁?**鏄?* | "鏍稿績涓氬姟"杈圭晫鐢辫皝瀹氫箟锛?|

### 棰濆 BLOCKER 椤癸紙寤鸿鍔狅級

| 鍊欓€?| 榛樿 | 澶囨敞 |
|---|---|---|
| EXT-* Operational Notes 搂5 宸茬煡闂鍒楄〃涓殑"鍋囨垚鍔? / "濂戠害婕傜Щ" / "鏁版嵁琛€缂樼己澶? | 鉁?**鏄?* | 瑙勫垯 10 纭€ц姹?|
| 鍙椾繚鎶ら〉闈紙/dashboard, /mh/visual, /mh/consent锛夊洖褰掑け璐?| 鉁?**鏄?* | 鐢ㄦ埛宸插疄娴嬮€氳繃 |
| 4 寮?EXT-* 缂哄け琛紙瑙佺鍏锛?| 鈿狅笍 **鍙栧喅浜?Phase 1 缁撹** | Phase 1 瑙﹁揪 |
| `market_activity` 琛?DROP 杩佺Щ鏈窇 | 鈿狅笍 **鍙栧喅浜?Phase 1 缁撹** | DB 鐘舵€佷笌浠ｇ爜涓嶄竴鑷?|

---

## 鍥涖€乬it 鐘舵€侊紙stash 婕斿彉锛?

| 鏃堕棿鐐?| 鐘舵€?| 璇存槑 |
|---|---|---|
| T0锛堜細璇濆紑濮嬶級 | dirty | 25+ 澶?EXT-*/MKT 鏀瑰姩 |
| T1锛坓it stash push -u 鍚庯級 | **clean** | stash@{0}: before-phase-minus-1锛?4a8c1392a1513690b6e981be8aff22495cc00d4锛墊
| T2锛坓it stash pop 鍚庯級 | dirty | 鎭㈠鍏ㄩ儴鏀瑰姩锛泂tash@{0} 琚?drop |
| **T3锛圥hase -1 瀹屾垚鏃讹級** | **dirty** | T2 + 鏂板 `audit/baseline/preflight.md`锛坲ntracked锛墊

### 褰撳墠 working tree 缁熻锛坓it status --short锛?

```
M  .github/workflows/ci.yml
M  .gitignore
D  backend/src/main/java/com/meiyetec/controller/MarketActivityController.java
M  backend/src/main/java/com/meiyetec/controller/MktEffectController.java
M  backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java
M  backend/src/main/java/com/meiyetec/entity/EcommercePlatform.java
D  backend/src/main/java/com/meiyetec/entity/MarketActivity.java
D  backend/src/main/java/com/meiyetec/mapper/MarketActivityMapper.java
M  backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java
M  backend/src/main/java/com/meiyetec/service/EcommerceService.java
D  backend/src/main/java/com/meiyetec/service/MarketActivityService.java
M  backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java
M  backend/src/main/java/com/meiyetec/service/ReportV2Service.java
M  backend/src/main/java/com/meiyetec/service/impl/BusinessPolicyDispatchServiceImpl.java
D  backend/src/main/java/com/meiyetec/service/impl/MarketActivityServiceImpl.java
M  backend/src/test/java/com/meiyetec/service/MktEntityFieldsTest.java
M  frontend/meiyetec-web/playwright.config.ts
M  frontend/meiyetec-web/src/api/crm-integration.ts
M  frontend/meiyetec-web/src/api/ecommerce.ts
M  frontend/meiyetec-web/src/views/crm-integration/index.vue
M  frontend/meiyetec-web/src/views/ecommerce/index.vue
?? audit/                                                                       鈫?鏂板锛坴3 宸ヤ綔鐩綍锛?
?? backend/src/main/java/com/meiyetec/controller/{MktChannel,MktConversion,MktJourney,MktTemplate,MktTouchTask}Controller.java
?? backend/src/main/java/com/meiyetec/service/ReportExportFileCleanupService.java
?? backend/src/main/resources/db/sql/migrate_mkt_002_drop_market_activity.sql
?? backend/src/main/resources/db/sql/migrate_mkt_002_rollback.sql
?? docs/{LIMITATIONS,PROJECT_AUDIT_PLAYBOOK,README,ROLLBACK-6.2-mkt_ddl}.md
?? frontend/meiyetec-web/e2e/{crm-integration,mh-routes}.spec.ts
?? .cursor/rules/{project-audit,feature-id,no-touch-verified-pages,ext-19-*,ext-22-*,ext-23-*,ext-24-*}.mdc
?? 鍦?Cursor 涓惤鍦拌繖濂楅獙鏀朵綋绯荤殑鎿嶄綔鎸囧崡
?? 浠诲姟鎵ц娓呭崟锛堜慨姝ｇ増锛?
?? 椤圭洰寮€鍙戝畬鎴愬害涓庢渶缁堥獙鏀朵綋绯伙紙鏈€缁堣瀺鍚堢増锛?
```

### 涓存椂鏂囦欢

`docs/audit/` 鐩綍涓嬪瓨鍦?25+ 涓巻鍙?`audit_*.txt` 鏂囦欢锛坄audit_entities.txt`, `audit_services.txt`, ... `audit_view_paths.txt` 绛夛級鈥?杩欎簺鏄?**v1/v2 鍘嗗彶瀹¤ raw 鎵弿浜х墿**锛屼笉鍦?v3 浣撶郴鑼冨洿锛?*涓嶆竻鐞?*锛堟寜 `audit/README.md` 搂2 寮曠敤绾﹀畾淇濈暀鍘熶綅锛夈€?

### 闇€ commit 鎴?stash 椤?

鎸夌敤鎴峰喅绛?2锛?baseline 鏆備笉鎻愪氦 git锛岀瓑 Phase -1 瀹屾垚鍚庝竴璧?銆?*Phase -1 鎶ュ憡瀹屾垚鍚庡緟鐢ㄦ埛鍐崇瓥锛?*

- [ ] 閫夐」 A锛氱洿鎺?commit锛坆aseline + preflight.md + 25+ 澶勬敼鍔ㄤ竴娆℃€у叆搴擄級
- [ ] 閫夐」 B锛氭媶 commit锛坆aseline 鍗曠嫭 + MKT 閲嶆瀯 + EXT-19/22/23/24 鍒嗘壒锛?
- [ ] 閫夐」 C锛氬啀 stash 涓€娆★紙Phase -1 瀹屾垚鍚庝繚鎸?working tree clean锛?

---

## 浜斻€佸彈淇濇姢椤甸潰娓呭崟

鏉ユ簮锛歚.cursor/rules/no-touch-verified-pages.mdc`锛堢敤鎴峰疄娴嬮€氳繃锛?

| # | 璺敱 | 鍓嶇鏂囦欢 | 鍚庣鎺ュ彛 | 鐘舵€?|
|---|---|---|---|---|
| 1 | `/dashboard` | `frontend/meiyetec-web/src/views/dashboard/**` | 锛堝緟 Phase 2 鍒楀嚭锛?| 鉁?宸查獙璇?|
| 2 | `/mh/visual` | `frontend/meiyetec-web/src/views/mh/visual.vue` | 锛堝緟 Phase 2 鍒楀嚭锛?| 鉁?宸查獙璇?|
| 3 | `/mh/consent` | `frontend/meiyetec-web/src/views/mh/consent.vue` | 锛堝緟 Phase 2 鍒楀嚭锛?| 鉁?宸查獙璇?|

### 褰卞搷

- Phase 3锛圫tatic Gap Audit锛夛細鍙垪鍙枒鐐癸紝**涓嶄慨澶?*
- Phase 5锛圧untime Verification锛夛細鍙獙璇佽繍琛岋紝**涓嶅姩浠ｇ爜**
- Phase 6锛圖B Verification锛夛細鍙 schema/鏁版嵁锛?*涓嶈皟鍐?*
- 鍗充娇鍙戠幇 Bug / 椋庢牸涓嶄竴鑷?/ dead code 鈫?浠呭垪鍏?Issue Register

### 渚嬪

- 鐢ㄦ埛鏄庣‘璇?淇?X"涓?X 鎸囧悜杩?3 涓〉闈?鈫?鍙姩鎵?
- 鐢ㄦ埛鏄庣‘璇?杩樺師/鍥為€€"瀵瑰畠浠殑鏌愭淇敼 鈫?鍙姩鎵?

---

## 鍏€佸畧闂ㄨ剼鏈槸鍚﹁繘 CI

### 褰撳墠 CI 鏂囦欢

`.github/workflows/ci.yml`锛?*宸蹭慨鏀?*鈥斺€攇it status 鏄剧ず `M .github/workflows/ci.yml`锛?

### CI 鐜版湁 jobs

| Job | 瑙﹀彂 | 鍐呭 |
|---|---|---|
| **backend** | push / PR | `mvn test` + `mvn package`锛圝DK 17 + MySQL 8.0 service锛墊
| **frontend** | push / PR | `npm ci` + `vue-tsc --noEmit` + Vitest + `npm run build`锛圢ode 20锛墊
| **integration** | push / PR | 鍚姩 MySQL 鈫?`init.sql` 鈫?鍚庣 鈫?`Run-All-Tests.ps1`锛圥owerShell锛學indows runner锛墊
| **e2e** | push / PR | Playwright chromium + Mobile Chrome + runtime-smoke锛岄檮甯?Playwright HTML report artifact |

### 宸插寘鍚殑鑴氭湰

- 鉁?Maven Surefire锛堝崟鍏冩祴璇曪級
- 鉁?Vitest锛堝墠绔崟鍏冩祴璇曪級
- 鉁?TypeScript strict check锛坄vue-tsc --noEmit`锛?
- 鉁?Playwright锛圗2E锛? 涓?spec锛歯ew-modules, business-flow, crm-integration, mh-routes锛?
- 鉂?**audit/scripts/ 瀹堥棬鑴氭湰** 鈥斺€?**鏈紩鐢?*

### 缂哄け鐨勮剼鏈?

`audit/README.md` 搂1 鏍囪 `audit/scripts/` 涓?v3 搂5 瑕佹眰鐨?8 涓畧闂ㄨ剼鏈洰褰曪細

| 缂栧彿 | 鑴氭湰鍚嶏紙棰勬湡锛?| 鐘舵€?|
|---|---|---|
| g1 | `g1-scope.sh` | 鉂?鏈疄鐜帮紙鐩綍涓嶅瓨鍦級 |
| g2 | `g2-*.sh` | 鉂?鏈疄鐜?|
| g3 | `g3-*.sh` | 鉂?鏈疄鐜?|
| g4 | `g4-*.sh` | 鉂?鏈疄鐜?|
| g5 | `g5-*.sh` | 鉂?鏈疄鐜?|
| g6 | `g6-*.sh` | 鉂?鏈疄鐜?|
| g7 | `g7-*.sh` | 鉂?鏈疄鐜?|
| g8 | `g8-pre-deploy.sh` | 鉂?鏈疄鐜?|

### 椋庨櫓

- 瑙勫垯 15锛?鑷姩鍖栧畧闂ㄨ剼鏈瘡娆?PR 蹇呴』璺? 鈥?**褰撳墠 CI 涓嶅畧闂?v3 浣撶郴瑙勫垯**
- PR 鍙粫杩囧璁¤鍒欏悎鍏?
- **Phase 0 蹇呴』纭**锛氬畧闂ㄨ剼鏈己澶辨槸鍚︾畻 BLOCKER锛熻繕鏄?Phase 1/2 鏈熼棿琛ワ紵

---

## 涓冦€佸綋鍓嶆壒娆?

| 椤?| 鍊?|
|---|---|
| **鎵规鍙?* | **v3 绗竴鎵?*锛圓1-A2 + B1-B7 + C1锛?|
| **瑙﹀彂鍘熷洜** | 鐢ㄦ埛寤虹珛 v3 楠屾敹浣撶郴锛堥」鐩紑鍙戝畬鎴愬害涓庢渶缁堥獙鏀朵綋绯?823 琛岃瀺鍚堢増锛墊
| **娑夊強妯″潡** | 瑙佷笅琛?|
| **鏄惁閲嶆瀯鎵规** | 鉁?**鏄?*锛堝競鍦烘椿鍔ㄦā鍧楁暣浣撻噸鍐?+ 钀ラ攢妯″潡鎷嗗垎锛墊

### 娑夊強妯″潡娓呭崟

| 妯″潡 | EXT-* | 涓昏鏀瑰姩 | 椋庨櫓 |
|---|---|---|---|
| 澶氳瑷€棰勭害纭 | **EXT-19** | `OverseasBooking.miniProgramOpenId` 瀛楁 + 6 涓?locale i18n template + 5 涓?channel sender | 琛?`overseas_booking` 缂哄け |
| Google Maps Place Details + 鏈夎禐绛惧悕 | **EXT-22** | `enablePlaceDetails` + `ecommerce_platform.{appId,kdtId,signSecret}` + 绛惧悕宸ュ叿 | 琛?`google_maps_config` 缂哄け |
| Plugin Runtime锛坧f4j 3.10.0锛墊 **EXT-23** | `MeiyetecPlugin` SPI + PluginRuntimeService + PluginPersistenceService + PluginTenantService + PluginSecurityPolicy | 琛?`plugin_runtime_state` 缂哄け锛涙矙绠卞悎浣滃紡 |
| CRM 闆嗘垚 | **EXT-24** | `CrmAdapter` + `SalesforceCrmAdapter` + `MockCrmAdapter` + `CrmSyncScheduler` + `objectMappings` JSON + `crm_contact_map` | 琛?`crm_contact_map` 缂哄け锛汬UBSPOT/ZOHO 鏈疄鐜?|
| 钀ラ攢妯″潡閲嶆瀯 | **鏈壒娆?* | `MarketActivity` 鍒犻櫎 + `MktChannel/MktJourney/MktTemplate/MktTouchTask/MktConversion/MktEffect` 6 鎺у埗鍣ㄦ媶鍑?| 琛?`market_activity` DROP 杩佺Щ鏈窇锛涘洖婊氳剼鏈凡鍐?|

### 閲嶆瀯绫诲彉鏇存彁绀?

鎸夎鍒?14锛?閲嶆瀯绫诲彉鏇村繀椤诲厛璺?Phase 3.5 Diff Audit"銆?
**鏈壒娆″寘鍚噸鏋?* 鈫?Phase 3.5 涓嶅彲璺宠繃銆?

### 鎵规鍛藉悕绾﹀畾

v3 浠诲姟娓呭崟 A 娈电敤 A1-A2銆丅 娈电敤 B1-B7銆丆 娈电敤 C1-C2銆丏 娈电敤 D1-D2 椤哄簭鎵ц銆傛湰鎵规瀹屾垚 C1 鍚庤繘鍏?C2 / D 娈点€?

---

## 鍏€佸凡鐭ョ害鏉熸眹鎬?

### 8.1 EXT-* 琛ㄧ己澶憋紙Phase 1 瑙﹁揪锛?

| # | 缂哄け琛?| 鎵€灞?EXT-* | Operational Notes 寮曠敤 | 鎺ㄦ祴鍘熷洜 |
|---|---|---|---|---|
| 1 | `crm_contact_map` | EXT-24 | 搂0.3 known-ids 鍘婚噸閾捐矾 | 瀹炰綋绫诲彲鑳芥湭寤?/ Flyway migration 鏈啓 |
| 2 | `overseas_booking` | EXT-19 | 搂0.4 StoreLookupService + 搂5 鍗犱綅绗?| DDL 鏈窇 / 瀹炰綋绫绘湭寤?|
| 3 | `google_maps_config` | EXT-22 | 搂0.2 / 搂3 / 搂4 瀛楁鏄犲皠 | 鍚屼笂 |
| 4 | `plugin_runtime_state` | EXT-23 | 搂0.5a `PluginPersistenceService` 鎸佷箙鍖栬〃 | 鍚屼笂 |

**娉?*锛氫笂杩?4 寮犺〃鍦?`audit/baseline/db-snapshot.sql`锛坢ysqldump schema-only dump, 417KB, 270 寮犺〃锛変腑**鍧囦笉瀛樺湪**銆傞渶 Phase 1 杩涗竴姝ュ垽鏂細
- 琛ㄤ笉瀛樺湪 鈫?瀹炰綋绫诲瓨鍦ㄥ悧锛烣lyway migration 鏈夊悧锛熻繍琛屾椂鏄惁 NPE锛?
- 杩欏叧绯诲埌 F-绾у埆锛團eature锛夌殑鏈€缁堢姸鎬佸垽瀹氾紙MISSING / BROKEN / SHELL-ABANDONED / MOCK-FAKE锛?

### 8.2 market_activity 琛ㄤ粛鍦紙杩佺Щ鏈窇锛?

| 椤?| 鐘舵€?|
|---|---|
| `market_activity` 琛?| 鉁?**瀛樺湪**锛圖B snapshot 涓?CREATE TABLE 鍙锛?|
| `migrate_mkt_002_drop_market_activity.sql` | 宸插啓浣?*鏈窇**锛?? untracked锛墊
| `migrate_mkt_002_rollback.sql` | 宸插啓浣?*鏈窇**锛?? untracked锛墊
| `MarketActivity.java` 瀹炰綋 | 宸?*鍒犻櫎**锛圖 鐘舵€侊級|
| `MarketActivityController.java` | 宸?*鍒犻櫎**锛圖 鐘舵€侊級|
| `MarketActivityService.java` + `MarketActivityServiceImpl.java` | 宸?*鍒犻櫎**锛圖 鐘舵€侊級|
| `MarketActivityMapper.java` | 宸?*鍒犻櫎**锛圖 鐘舵€侊級|
| `docs/ROLLBACK-6.2-mkt_ddl.md` | 宸插啓锛?,121 bytes锛寀ntracked锛墊

**椋庨櫓**锛?
- **浠ｇ爜涓?DB 涓嶄竴鑷?*锛欽ava 绔棤 `MarketActivity` 绫伙紝浣?DB 浠嶆湁 `market_activity` 琛?鈥?鍚姩鏈?MyBatis-Plus 鎵弿 mapper 涓嶄細鎶ラ敊锛堝洜涓?mapper 涔熷垹浜嗭級锛屼絾濡傛灉鏈夐仐鐣?SQL / 娴嬭瘯 / 鍏跺畠鏈嶅姟杩樺湪鏌ユ琛紝浼氳繍琛屾椂 NPE銆?
- **DROP 杩佺Щ椋庨櫓**锛氳窇 `migrate_mkt_002_drop_market_activity.sql` 涓嶄細褰卞搷浠ｇ爜锛屼絾**濡傛灉鏈夊叾瀹冩湭杩借釜浠ｇ爜寮曠敤姝よ〃 鈫?涓婄嚎鍚?500**銆?
- **鍥炴粴鑴氭湰灏辩华**锛歊OLLBACK-6.2-mkt_ddl.md 宸插啓锛屽洖婊氳矾寰勫凡瑙勫垝銆?

### 8.3 Redis 鏈獙璇侊紙鐢ㄦ埛鍐崇瓥 4锛?

| 椤?| 鐘舵€?|
|---|---|
| 閰嶇疆浣嶇疆 | `application.yml` 鈫?`spring.data.redis.{host=localhost, port=6379, database=0, timeout=3000ms}` |
| 瀹為檯杩為€氭€?| 鉂?**鏈獙璇?*锛堟寜鐢ㄦ埛鍐崇瓥 4 涓嶆帰娴嬶級|
| Phase 5/6 褰卞搷 | 闇€鐢ㄦ埛鎵嬪姩纭锛涘惁鍒欒繍琛屾椂 NPE 椋庨櫓 |

### 8.4 Playwright MCP 鏈槑纭?

- 鏈浼氳瘽鏈槑纭槸鍚﹀凡閰嶇疆
- 瑙?搂1 褰卞搷

### 8.5 瀹堥棬鑴氭湰鏈疄鐜?

- 瑙?搂6 椋庨櫓

### 8.6 working tree 浠?dirty

- 瑙?搂4 / 椤堕儴宸ヤ綔娴佸亸宸?
- 寰呯敤鎴峰喅绛栵細commit 绛栫暐

### 8.7 鍘嗗彶瀹¤娌夋穩涓嶅湪 v3 鐩綍

- `docs/audit/` 25+ 涓?`audit_*.txt` + `BEAUTY_SAAS/` 瀛愮洰褰曪紙28+ 鎶ュ憡锛夆€?鍘嗗彶 v1/v2 浜х墿
- 涓嶈縼绉伙紙鎸?`audit/README.md` 搂2锛?
- Phase 7 寮曠敤鏃朵娇鐢ㄧ浉瀵硅矾寰?`../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`

### 8.8 EXT-* Operational Notes 宸插姞杞?

鏈細璇濆凡鍔犺浇浠ヤ笅 4 浠?EXT-* Operational Notes锛?

- EXT-19锛堝璇█棰勭害纭锛夆€?`.cursor/rules/ext-19-operational-notes.mdc`
- EXT-22锛圙oogle Maps + 鏈夎禐锛夆€?`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`
- EXT-23锛圥lugin Runtime锛夆€?`.cursor/rules/ext-23-plugin-runtime.mdc`
- EXT-24锛圕RM 闆嗘垚锛夆€?`.cursor/rules/ext-24-crm-integration.mdc`

瑙勫垯 11锛?瀹¤缁撹涓?EXT-* 鍐茬獊鏃讹紝浠?EXT-* 涓哄噯骞舵爣 ISSUE" 鈥?**鏈壒娆″璁″潎鍙楄繖 4 浠界害鏉?*銆?

---

## 涔濄€丳hase -1 鍑嗗叆纭锛堣嚜妫€锛?

| 妫€鏌ラ」 | 缁撴灉 |
|---|---|
| commit hash 鏂囦欢宸茶惤鐩?| 鉁?`audit/baseline/commit.txt` = `656caa7鈥 |
| DB snapshot 宸茶惤鐩?| 鉁?`audit/baseline/db-snapshot.sql`锛?17KB, 270 琛? schema-only锛墊
| env.md 宸茶惤鐩?| 鉁?`audit/baseline/env.md`锛?,951 bytes, 235 琛岋級|
| 鍙椾繚鎶ら〉闈㈣鍒欏凡鍔犺浇 | 鉁?`.cursor/rules/no-touch-verified-pages.mdc` |
| Feature ID 瑙勫垯宸插姞杞?| 鉁?`.cursor/rules/feature-id.mdc` |
| 涓昏鍒欏凡鍔犺浇 | 鉁?`.cursor/rules/project-audit.mdc` |
| 浣撶郴鍏ㄦ枃宸茶惤鐩?| 鉁?`docs/PROJECT_AUDIT_PLAYBOOK.md`锛?4,901 bytes锛墊
| Cursor 鎿嶄綔鎸囧崡宸茶惤鐩?| 鉁?`鍦?Cursor 涓惤鍦拌繖濂楅獙鏀朵綋绯荤殑鎿嶄綔鎸囧崡`锛?46 琛岋級|
| PRD 宸插瓨鍦?| 鉁?`docs/PRD-缇庝笟SaaS绯荤粺鍔熻兘澶у叏.md`锛坴1.0, 14,190 bytes锛墊
| EXT-* Operational Notes 宸插姞杞?| 鉁?4 浠斤紙EXT-19/22/23/24锛墊

鉁?**Phase -1 鍑嗗叆鏉′欢鍏ㄩ儴婊¤冻銆傚彲浠ヨ繘鍏?Phase 0銆?*

---

## 鍗併€佸緟鐢ㄦ埛鍐崇瓥娓呭崟锛堜笉闃诲 Phase 0锛屼絾寤鸿杩?Phase 0 鍓嶅洖绛旓級

| # | 鍐崇瓥椤?| 榛樿 | 褰卞搷 |
|---|---|---|---|
| 1 | Playwright MCP 鏄惁宸查厤缃紵 | 鉂?鏈‘璁?| Phase 5 闄嶇骇鏂规 |
| 2 | BLOCKER 闃堝€兼槸鍚︽帴鍙楋紵 | 鍏ㄩ儴榛樿 鉁?| Phase 3/5/7 鍒ゅ畾 |
| 3 | 瀹堥棬鑴氭湰缂哄け鏄惁绠?BLOCKER锛?| 鈿狅笍 寰呯敤鎴?| Phase 0 鈫?1 璺緞 |
| 4 | `market_activity` 琛?+ DROP 杩佺Щ鏈窇 椋庨櫓濡備綍澶勭悊锛?| 鈿狅笍 Phase 1 瑙﹁揪 | DB schema truth |
| 5 | 4 寮?EXT-* 缂哄け琛ㄩ闄╁浣曞鐞嗭紵 | 鈿狅笍 Phase 1 瑙﹁揪 | DB schema truth |
| 6 | Phase -1 鍚?commit 绛栫暐锛圓/B/C锛夛紵 | 鉂?鏈€?| git history |
| 7 | 鏄惁闇€瑕佸湪 Phase 0 涔嬪墠鍐?`git stash`锛?| 鉂?鏈€?| working tree clean 搴?|

---

**Pre-flight 瀹屾垚銆傜瓑寰呯敤鎴风‘璁?鈫?杩涘叆 Phase 0锛堝喕缁撹鍒欙級銆?*

---

> ⚠️ **DEPRECATED（2026-10-16）**：本文件中文内容已损坏（I112），不再维护。
> 保留仅供 commit hash、日期、文件路径检索。
