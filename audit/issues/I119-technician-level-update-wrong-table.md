
## 修复 + 数据补齐（2026-10-20）
- 代码修复：TechnicianLevelServiceImpl 晋升 UPDATE（表名 sys_user→pos_employee，参数 nextLevelCode→nextLevelId）
- 数据补齐：biz_technician_level 补 5 行（L4/L3/L2/L1/T1）
- 编译 EXIT=0
- 等级表验证：5 行数据，HEX(level_name) 确认为正确 UTF-8（E9A696E5B8ADE68A80E5B888 = 首席技师）
- PowerShell 5.1 显示乱码是本地显示 bug，非数据问题，API 读取正常

## 状态
已修复（代码 + 数据），运行时晋升验证待补
