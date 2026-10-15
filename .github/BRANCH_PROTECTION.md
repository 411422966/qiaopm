# Branch Protection Rules（GitHub 仓库配置参考）

> **目的**：R2.0-E T-E03 中"PR 合入要求绿灯"的 GitHub 端强制实现。
>
> 以下为推荐配置，**必须在 GitHub 仓库 Settings → Branches → Branch protection rules 中手动添加**（GitHub 不支持通过 yml 直接配置 branch protection，必须在仓库 UI 操作）。

## 主分支（main）

**Branch name pattern**: `main`

| 选项 | 推荐值 |
|---|---|
| Require a pull request before merging | ✅ 开启 |
| Require approvals | ✅ 至少 1 个（PD/后端TL/前端TL 至少其一） |
| Dismiss stale pull request approvals when new commits are pushed | ✅ 开启 |
| Require review from Code Owners | ✅ 开启（需先创建 CODEOWNERS 文件） |
| Require status checks to pass before merging | ✅ 开启 |
| Require branches to be up to date before merging | ✅ 开启 |
| Required status checks | 勾选以下 3 个：<br>1. `Backend Build & Unit Test`（来自 ci.yml 的 `backend` Job）<br>2. `Frontend Build, Type Check & Unit Test`（来自 ci.yml 的 `frontend` Job）<br>3. `Integration Tests (PowerShell)`（来自 ci.yml 的 `integration` Job） |
| Require conversation resolution before merging | ✅ 开启 |
| Require linear history | ✅ 开启（推荐） |
| Include administrators | ✅ 开启（管理员也受约束） |

## develop / release 分支

同上但可放宽：

- Required approvals: 0（信任开发者直接 push）
- 但仍要 Required status checks 全部通过

---

## CODEOWNERS 文件（项目根目录）

在仓库根目录创建 `.github/CODEOWNERS`：

```
# R2.0-E T-E03：自动评审人制度
# 默认评审人
*                                   @pd-team @be-tl @fe-tl

# 后端
/backend/                            @be-tl
backend/pom.xml                     @be-tl

# 前端
/frontend/                           @fe-tl
frontend/meiyetec-web/package.json  @fe-tl

# 数据库 / 迁移
**/*.sql                            @be-tl @dba

# 部署 / CI
/.github/                            @sre-team
docker-compose*.yml                 @sre-team
*.yml                               @sre-team

# 文档
/docs/                              @pd-team

# 发布门禁
/docs/RELEASE-GATE-发布门禁checklist.md  @sre-team @cto
/docs/RELEASE-R2.0-上线包评审决议.md      @cto
```

---

## 配置落地步骤

1. 仓库管理员（owner）登录 GitHub
2. 进入 `Settings → Branches → Branch protection rules → Add rule`
3. 输入 Branch name pattern: `main`（+ `develop` 单独一条）
4. 按上表勾选所有推荐项
5. **Required status checks** 处输入框需先等 ci.yml 跑过一次 PR，候选 job 才会出现
6. 保存
7. 创建 PR 验证：未通过 3 个 CI job 的 PR 不能被 Merge 按钮

---

## 当前状态（2026-08-12）

⚠️ **仓库 Branch Protection 尚未配置**（仅文档定义）。配置负责人：SRE TL。
完成时间目标：R2.0 灰度发布前 24h 完成。

## 兜底方案（Branch Protection 缺失期）

- 所有 PR 必须由 Reviewer 在 `Run-All-Tests.ps1` 输出"ALL PASS"截图作为评论
- Reviewer 必须在 PR 描述里贴本地 CI 三段输出
- merge 前人工确认 CI 三段绿灯