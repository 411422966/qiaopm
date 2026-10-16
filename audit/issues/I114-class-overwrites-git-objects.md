# I114 — Java class 覆盖 .git/objects 的根因排查

## 现象（来自 I109 现场取证）
- `.git/objects/26/28c688cee2bbe2fd0509ed428740432af10c82` 文件头为
  `CA FE BA BE 00 00 00 3D`，即 Java .class 魔数
- 说明某个编译/构建脚本把 .class 写进了 `.git/objects/` 而非 target/
- 该对象在 a414f95 重建后已弃，但触发者可能仍在

## 影响
- 若再次触发，会污染新仓库的 objects
- 与 I113（-Encoding ascii 乱写文件）疑似同类：脚本输出路径失控
- 可能与 I105（docker-compose.yml 被 SQLite 头覆盖）同源

## 排查方向
- [ ] 所有产出 .class 的脚本（javac / mvn / mvn test）
- [ ] 工作目录依赖：是否有 cd 后写相对路径
- [ ] pom.xml / build.gradle 的 outputDirectory 配置
- [ ] mybatis-generator / codegen 的输出路径
- [ ] .gitignore 是否覆盖 .class / target/

## 处置
- [ ] 定位触发脚本
- [ ] 修复输出路径
- [ ] 加守门检查：.git/objects 内不得出现非 git 格式文件

## 状态
待办
