# I110 — 每周 Git 仓库体检

## 目标
在 NTFS 损坏早期发现问题，避免重演 I109。

## 动作
- 每周执行：git fsck --full
- 本地留档到 audit/evidence/git-fsck/YYYYMMDD.txt（不入库）
- 出现 missing/broken 立即升级

## 状态
待办

## 执行记录
- 2026-10-16：git fsck --full，输出为空（仓库干净），无 missing/broken/dangling

## 附加守门（2026-10-16）
- 新增 `audit/scripts/09-check-git-objects.ps1`
- 用途：检测 .git/objects 内非标准格式文件（I113/I114 防护）
- 并入每周巡检，与 git fsck --full 一起跑
