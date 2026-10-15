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
