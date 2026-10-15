# I110 — 每周 Git 仓库体检

## 目标
在 NTFS 损坏早期发现问题，避免重演 I109。

## 动作
- 每周执行：git fsck --full
- 输出追加到 audit/logs/git-fsck-YYYYMMDD.txt
- 出现 missing/broken 立即升级

## 状态
待办
