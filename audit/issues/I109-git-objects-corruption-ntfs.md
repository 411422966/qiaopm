# I109 — Git objects/30 NTFS 损坏导致仓库重建

## 现象
- .git/objects/30/ 目录 NTFS 级损坏
- 5 个 blob 损坏 + 3 个 tree 断链 + pack rev-index 损坏
- Windows 事件 ID 98：卷 D: 需要扫描

## 时间
- 首次发现：2026-10-15 22:55:37
- 重建：2026-10-16（commit a414f95）

## 根因
D 盘（ST2000DM006-2DM164，2TB 机械盘）NTFS 文件系统损坏
磁盘 SMART 健康，非硬件坏道

## 处置
- 从工作区重建仓库
- 丢失：git commit 历史、git tag、git reflog

## 教训
1. 仓库必须配远程（防本地损坏）
2. 每周 git fsck 检测早期损坏
3. 每月 chkdsk D: /SCAN
4. Git 仓库应放 SSD
