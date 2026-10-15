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

## 远程备份
- 远程仓库：https://github.com/411422966/qiaopm.git
- 首次推送 commit：16c8fe6
- 验证：git clone 成功（3276 文件），git fsck --full 干净，2026-10-16

## 状态
已缓解（2026-10-16，远程备份已建立并验证）
- [x] 远程仓库已推送并验证克隆
- [x] git fsck --full 通过
- [ ] chkdsk D: /scan 无错误
- [ ] 仓库迁移 SSD 或每日备份
- [ ] 事件 ID 98 不再出现
