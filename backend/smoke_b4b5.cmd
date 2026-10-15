@echo off
chcp 65001 >nul
set BASE=http://localhost:8080

echo === 拿 Token ===
curl.exe -s -X POST %BASE%/auth/login -H "Content-Type: application/json" --data-binary "@d:\QiaoPM\backend\login.json" > d:\QiaoPM\backend\login_resp.json
for /f "tokens=*" %%i in ('powershell -Command "(Get-Content d:\QiaoPM\backend\login_resp.json | ConvertFrom-Json).data.token"') do set TOKEN=%%i
echo Token: %TOKEN:~0,30%...
echo.

echo === 批次 4 10 个 API ===
for %%p in (
  kiosk/device/list
  complication/sop/list
  complication/dashboard
  cohort/dashboard
  gdpr/consent/list
  gdpr/data-request/list
  hipaa/key/list
  hipaa/dashboard
  pos-device/list
  plugin/market/list
  fiscal/list
  crm-integration/list
  crm-integration/dashboard
  ecommerce/platform/list
  ecommerce/dashboard
) do (
  for /f "tokens=*" %%r in ('curl.exe -s -o nul -w "%%{http_code}" -X GET "%BASE%/%%p" -H "Authorization: Bearer %TOKEN%"') do echo [GET %%p] HTTP %%r
)

echo.
echo === 批次 5 5 个 API ===
for %%p in (
  intl-payment/gateways
  intl-payment/charges
  intl-booking/channels
  intl-booking/list
  ai-scheduler/forecasts
  ai-scheduler/schedules
  ai-scheduler/staff-load
  risk-control/rule/list
  risk-control/alert/list
  risk-control/dashboard
  google-maps/config
  google-maps/stores
  google-maps/heatmap
) do (
  for /f "tokens=*" %%r in ('curl.exe -s -o nul -w "%%{http_code}" -X GET "%BASE%/%%p" -H "Authorization: Bearer %TOKEN%"') do echo [GET %%p] HTTP %%r
)
