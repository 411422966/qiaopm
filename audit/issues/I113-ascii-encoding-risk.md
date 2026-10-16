# I113 — compile_impls.ps1 使用 -Encoding ascii 写文件

## 现象
`backend/compile_impls.ps1:14` 使用 `Out-File -Encoding ascii`，
会把非 ASCII 字符永久替换为 `?`，与 I112 同源风险。

## 影响
- 当前写的文件为 `javac_impls_args.txt`（内容纯路径，暂无中文）
- 若未来内容含中文，将不可逆损坏

## 处置
- [ ] 改为 -Encoding utf8
- [ ] 全仓库排查 -Encoding ascii 与裸 > 重定向

## 状态
待办

## 处置完成（2026-10-16）
- backend/compile_impls.ps1:14 `-Encoding ascii` → `-Encoding utf8`
- 全仓库扫描：仅此一处，无其他命中
- 新增守门脚本 audit/scripts/09-check-git-objects.ps1，并入 I110 每周巡检

## 状态
已修复

## 补充（2026-10-16）
- compile_impls.ps1 / compile_pos008_v2.bat 属 backend 一次性编译脚本，
  .gitignore 已 ignore（符合项目约定），修复仅在本地生效
- 守门脚本 audit/scripts/09-check-git-objects.ps1 属长期资产，已 track
- 顺带放开 audit/scripts 01~08 巡检脚本（原被 *.ps1 全局忽略误挡）
