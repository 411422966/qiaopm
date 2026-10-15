# I111 — 每月 chkdsk D: /scan

## 目标
提前发现 D 盘 NTFS 异常（Event ID 98）。

## 动作
- 每月执行：chkdsk D: /scan
- 记录输出到 audit/logs/chkdsk-YYYYMMDD.txt
- 有错误先备份，再评估 /spotfix

## 状态
待办
