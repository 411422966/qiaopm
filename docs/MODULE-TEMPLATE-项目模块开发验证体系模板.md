# [项目名] · 模块化开发验证体系（项目模板）

> 版本：v1.0（模板版） | 适用：管理后台 / SaaS / 中后台业务系统
> 来源：基于美业 SaaS 项目实战沉淀，并参数化为通用模板
> 配套方法论：`METHOD-通用项目快速开发方法论.md`
> 核心理念：**每个最小模块都能独立验证，前端 + 后端 + 数据库全链路打通**

---

## 📌 模板使用说明

### 本文档是"模板"，不是"实例"

- `[方括号内容]` = **占位符**，新项目时**全局替换**
- `_下划线字段_` = **可选填字段**，根据项目实际情况决定是否填写
- `【参考示例】` = 来自美业 SaaS 的真实案例，**仅供参考，可删可改**

### 新项目 5 步上手

```
步骤 1：复制本文件 → 重命名为 MODULE-[你的项目名].md
步骤 2：全局替换所有占位符（[项目名]、[公司英文名]、[项目前缀_] 等）
步骤 3：填写"二、项目信息"和"三、模块清单"
步骤 4：按"十、执行清单"顺序执行
步骤 5：每个 Mxx 模块完成时，按"五、模块任务清单模板"复制一份独立维护
```

### 占位符清单（先全局替换这些）

| 占位符 | 含义 | 示例（美业） |
|---|---|---|
| `[项目名]` | 项目中文名 | 美业 SaaS |
| `[项目代号]` | 项目英文代号 / 简写 | meiyetec |
| `[公司英文名]` | 英文公司名（用作包名前缀） | meiyetec |
| `[项目前缀_]` | 数据库表名前缀 | crm_ / pos_ / mem_ |
| `[8080]` | 后端服务端口 | 8080 |
| `[前端端口]` | 前端开发端口 | 5173 |
| `[MySQL 密码]` | 数据库 root 密码 | root123456 |
| `[公司包名]` | Java 包名 | com.meiyetec |
| `[3.x]` | Spring Boot 版本 | 3.2 |
| `[3.x]` | Vue 版本 | 3.4 |

---

## 一、核心原则

### 1.1 最小可运行单元 (MRU - Minimum Runnable Unit)

```
每个模块必须包含：
┌─────────────────────────────────────────┐
│                                         │
│    ┌───────────┐    ┌───────────┐       │
│    │   前端    │◀──▶│  后端API  │       │
│    │   Vue3    │    │ SpringBoot│       │
│    └───────────┘    └─────┬─────┘       │
│                           │             │
│                     ┌─────▼─────┐       │
│                     │  MySQL    │       │
│                     │  数据库    │       │
│                     └───────────┘       │
│                                         │
│    ✅ 三者连通 = 模块验证完成            │
│                                         │
└─────────────────────────────────────────┘
```

### 1.2 模块开发流程

```
┌─────────────────────────────────────────────────────────┐
│                    模块开发流程                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ① 确定模块功能                                          │
│      │                                                  │
│      ▼                                                  │
│  ② 拆分最小任务                                          │
│      │                                                  │
│      ▼                                                  │
│  ③ 同步开发                                             │
│      ┌─────────┬─────────┬─────────┐                   │
│      │  数据库  │  后端API │  前端   │                   │
│      └────┬────┴────┬────┴────┬────┘                   │
│           │         │         │                         │
│           └─────────┼─────────┘                         │
│                     │                                   │
│                     ▼                                   │
│  ④ 全链路验证                                           │
│      ┌──────────────────────────────────────┐          │
│      │ 1. 数据库测试（SQL可正常读写）             │          │
│      │ 2. API测试（Postman/接口调试）           │          │
│      │ 3. 前端测试（页面功能正常）               │          │
│      │ 4. 联调测试（前后端联调通过）             │          │
│      └──────────────────────────────────────┘          │
│                     │                                   │
│                     ▼                                   │
│  ⑤ ✅ 验证通过 = 进入下一模块                            │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 1.3 4 阶段并行开发（加速手段）

```
┌──────────────────────────────────────────────────────────┐
│          4 阶段并行（一个模块内同时推进 4 类工作）         │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────────┐    ┌──────────────────┐           │
│  │ ① 数据库设计      │    │ ② API 设计与实现  │           │
│  │ (1 名后端)       │    │ (1-2 名后端)     │           │
│  └──────────────────┘    └──────────────────┘           │
│           │                       │                      │
│           └───────────┬───────────┘                      │
│                       ▼                                  │
│              ┌──────────────────┐                        │
│              │ ③ 前端开发        │                        │
│              │ (1-2 名前端)     │                        │
│              └──────────────────┘                        │
│                       │                                  │
│                       ▼                                  │
│              ┌──────────────────┐                        │
│              │ ④ 联调测试        │                        │
│              │ (前后端协作)      │                        │
│              └──────────────────┘                        │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 二、项目信息（必填）

> ⚠️ 新项目时本节是**最优先**填写内容，所有后续章节会引用这里定义的占位符。

### 2.1 项目基本信息

```
项目代号：[项目代号]
项目名称：[项目名]
项目类型：[_SaaS 多租户 / 单租户 / 内部系统 / 其他_]
行业领域：[_美业 / 电商 / 教育 / 医疗 / 通用 / 其他_]
目标用户：[_门店 / 个人 / 企业管理员 / 其他_]
商业模式：[_订阅 / 按量 / 一次性 / 免费 / 其他_]
```

### 2.2 技术栈

```
后端：
- 语言：Java 17
- 框架：Spring Boot [3.x]
- ORM：MyBatis-Plus [3.x] / JPA [_可选_]
- 数据库：MySQL [8.x]
- 缓存：Redis [7.x]
- 鉴权：JWT (jjwt [0.12.x])

前端：
- 框架：Vue 3 + Vite
- 语言：TypeScript
- UI 组件库：[_Element Plus / Ant Design Vue / Naive UI_]
- 状态管理：Pinia
- HTTP：Axios
- 路由：Vue Router 4
```

### 2.3 命名规范

```
包名：[公司包名]（如 com.xxx）
数据库：[项目代号]_db 或 [项目代号]
表前缀：[项目前缀_]（如 crm_、sys_、biz_）
端口：
- 后端：[8080]
- 前端：[前端端口]（默认 5173）
- MySQL：3306
- Redis：6379
代码仓库：[_Git 地址_]
```

### 2.4 团队与排期

```
团队规模：[_人_]
模块数量：[_个_]
预计周期：[_周_]
交付节奏：[_每周 1-2 个模块_]
```

---

## 三、模块清单（必填）

> **本节是新项目的"施工地图"**，列出本项目要做的所有模块及其优先级。
> 模板里已用 `Mxx` 占位，请按以下规则拆分。

### 3.1 模块拆分原则

```
通用模块（任何项目都需要，建议保留）：
M01 登录认证      ← 必选，所有项目都需要
M02 基础数据      ← 必选（用户 / 角色 / 权限 / 字典等）
M03 业务核心 A    ← 根据项目填
M04 业务核心 B    ← 根据项目填
...
```

### 3.2 本项目模块清单

```
【参考示例 - 美业 SaaS · 四维状态模型 R2.0-E】
M01 ✅ 登录认证       impl✅ api✅ e2e✅ release✅
M02 ✅ 基础数据       impl✅ api✅ e2e✅ release✅
M03 ✅ 会员管理       impl✅ api✅ e2e✅ release✅
M04 ✅ 预约服务       impl✅ api✅ e2e✅ release✅
M05 ✅ 套餐次卡       impl✅ api✅ e2e✅ release✅
M06 ✅ 收银结算       impl✅ api✅ e2e✅ release✅
M07 ✅ 库存管理       impl✅ api✅ e2e✅ release✅
M08 ⚠️ 财务报表       impl✅ api✅ e2e⚠️ release⚠️
M09 ⚠️ 营销活动       impl✅ api⚠️ e2e⚠️ release⚠️
M10 ⚠️ 数据看板       impl✅ api✅ e2e⚠️ release⚠️
M11 ⬜ 移动端 H5
M12 ✅ 系统设置       impl✅ api✅ e2e✅ release✅
```

### 3.2.1 四维状态模型（R2.0-E T-E01）

> 每个模块的"完成状态"由四个独立维度构成：
> - `implementationStatus`（impl）：Controller/Service/Mapper/Entity 落地，SQL 初始化脚本就绪
> - `apiTestStatus`（api）：每个接口至少 1 个 curl/PowerShell 用例通过
> - `e2eStatus`（e2e）：至少 1 个 happy path + 1 个异常路径 用例通过
> - `releaseStatus`（release）：通过发布门禁 checklist（参见 `RELEASE-GATE-发布门禁checklist.md`）
>
> 任何一个维度为 ⬜ 都意味着该模块**生产不可发布**。

### 3.3 本项目模块清单（请填写）

```
M01 ⬜ [必选] 登录认证          impl⬜ api⬜ e2e⬜ release⬜
M02 ⬜ [必选] 基础数据          impl⬜ api⬜ e2e⬜ release⬜
M03 ⬜ [业务核心 A 名称]        impl⬜ api⬜ e2e⬜ release⬜
M04 ⬜ [业务核心 B 名称]        impl⬜ api⬜ e2e⬜ release⬜
M05 ⬜ [业务核心 C 名称]        impl⬜ api⬜ e2e⬜ release⬜
M06 ⬜ [业务核心 D 名称]        impl⬜ api⬜ e2e⬜ release⬜
M07 ⬜ [报表 / 数据]            impl⬜ api⬜ e2e⬜ release⬜
M08 ⬜ [系统设置]              impl⬜ api⬜ e2e⬜ release⬜
M09 ⬜ [_可选模块_]
M10 ⬜ [_可选模块_]
...
```

---

## 四、模块开发 5 步闭环（通用，直接复用）

### 4.1 第 1 步：确定模块功能

每个模块开发前，必须明确：

```
□ 模块核心目标（解决什么问题）
□ 用户角色（谁会用）
□ 主要场景（做什么操作）
□ 输入输出（数据从哪里来到哪里去）
□ 边界（不做什么）
```

### 4.2 第 2 步：拆分最小任务

按 **DB → API → FE** 三端拆分，每个任务 0.5-2 天可完成：

```
Mxx 模块任务拆分（示例）
├── T01 数据库设计
│   ├── T01-1 建表 SQL
│   ├── T01-2 建索引
│   └── T01-3 初始化数据
├── T02 后端 API
│   ├── T02-1 Entity / DTO / VO
│   ├── T02-2 Mapper / Service
│   ├── T02-3 Controller
│   └── T02-4 单元测试
├── T03 前端开发
│   ├── T03-1 API 封装
│   ├── T03-2 列表页
│   ├── T03-3 表单页
│   └── T03-4 详情页
└── T04 联调测试
    ├── T04-1 全链路走通
    ├── T04-2 异常处理
    └── T04-3 验收清单核对
```

### 4.3 第 3 步：同步开发（DB / API / FE 并行）

**关键原则**：不要等所有 SQL 都写完再写 API，不要等所有 API 都写完再写前端。

```
推荐节奏：
- Day 1：DB 设计（1 人）+ API 骨架（1 人）+ 前端骨架（1 人）
- Day 2-3：DB 完成 → API 跟进 → 前端跟进
- Day 4：联调 + 修复
- Day 5：自测 + 验收
```

### 4.4 第 4 步：全链路验证（4 层必做）

```
┌─────────────────────────────────────────┐
│              4 层验证                   │
├─────────────────────────────────────────┤
│                                         │
│  Layer 1：数据库层                       │
│  □ SQL 脚本执行成功                       │
│  □ 能正常增删改查                         │
│  □ 索引生效                              │
│                                         │
│  Layer 2：API 层                         │
│  □ Postman/Apifox 单接口调通              │
│  □ 正常返回统一格式                       │
│  □ 错误码符合规范                         │
│                                         │
│  Layer 3：前端层                         │
│  □ 页面正常显示                           │
│  □ 表单校验通过                           │
│  □ 数据回显正确                           │
│                                         │
│  Layer 4：联调层                         │
│  □ 完整业务链路走通                       │
│  □ 异常分支处理（401/403/500 等）         │
│  □ 边界条件（空值/极值/并发）             │
│                                         │
│  ✅ 4 层全部通过 = 模块完成              │
└─────────────────────────────────────────┘
```

### 4.5 第 5 步：模块验收 + 进入下一模块

```
本模块验收清单：
□ 4 层验证全部通过
□ 代码已提交到 Git
□ 文档已更新（API 文档 / 模块说明）
□ 进入下一模块

下一模块开始前：
□ 复盘本模块耗时与问题
□ 更新整体排期
□ 通知团队
```

---

## 五、模块任务清单模板（每个模块复制一份）

> **使用方式**：每开始一个 Mxx 模块，复制本节作为独立小节填到文档下方（如 `### 5.x Mxx 模块任务清单`），完成后归档。

### 5.x Mxx 模块任务清单

```
Mxx [模块名] 模块
├─ 模块目标：[_一句话说清本模块要解决的问题_]
├─ 关联模块：[_依赖哪些上游模块（Mxx）_]
├─ 涉及角色：[_管理员 / 操作员 / 会员 等_]
│
├── 1. 数据库设计
│   □ 建表 SQL（_路径_）
│   □ 建索引
│   □ 初始化数据（_默认账号 / 字典 / 测试数据_）
│
├── 2. 后端 API
│   □ Entity / DTO / VO
│   □ Mapper / Service
│   □ Controller
│   □ 单元测试（_Service 层覆盖率 ≥ 70%_）
│
├── 3. 前端开发
│   □ API 封装（_user_api.ts_）
│   □ 列表页（_列表 + 分页 + 筛选_）
│   □ 表单页（_新增 / 编辑_）
│   □ 详情页（_展示 + 操作_）
│
└── 4. 联调测试
    □ 完整链路走通
    □ 异常处理（_401/403/404/500_）
    □ 边界条件（_空值/极值/并发_）

验收：
□ 数据库验收
□ API 验收
□ 前端验收
□ 联调验收
□ 文档更新
□ Git 提交
```

---

## 六、环境与代码骨架（保留通用代码片段）

> **本节是"通用代码骨架"**，新项目**复制即可使用**，仅替换项目相关信息。

### 6.1 Docker 一键启动

#### 6.1.1 docker-compose.yml

```yaml
# 路径: [项目根目录]/docker-compose.yml
# 适用: MySQL 8.0 + Redis 7
# 使用: docker-compose up -d

version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: [项目代号]-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: [MySQL 密码]
      MYSQL_DATABASE: [项目代号]
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./backend/[项目代号]-api/src/main/resources/db/sql:/docker-entrypoint-initdb.d
    command: --default-authentication-plugin=mysql_native_password --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:7-alpine
    container_name: [项目代号]-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

volumes:
  mysql-data:
  redis-data:
```

#### 6.1.2 启动命令

```bash
# 在项目根目录下执行
docker-compose up -d

# 验证服务
docker ps

# 查看日志
docker-compose logs -f
```

#### 6.1.3 连接配置

| 服务 | 主机地址 | 端口 | 密码 |
|------|----------|------|------|
| MySQL | localhost | 3306 | [MySQL 密码] |
| Redis | localhost | 6379 | 无密码 |

---

### 6.2 后端项目初始化

#### 6.2.1 创建项目目录

```bash
# 在项目根目录下执行
mkdir backend frontend
```

#### 6.2.2 pom.xml 核心依赖清单

```xml
<!-- 路径: backend/[项目代号]-api/pom.xml -->

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Data JPA（可选，可改为 MyBatis-Plus） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- Spring Data Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- MySQL 驱动 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>

    <!-- MyBatis-Plus（推荐） -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>[3.x]</version>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>[0.12.x]</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>[0.12.x]</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>[0.12.x]</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 6.2.3 application.yml 模板

```yaml
# 路径: backend/[项目代号]-api/src/main/resources/application.yml

spring:
  application:
    name: [项目代号]-api
  datasource:
    url: jdbc:mysql://localhost:3306/[项目代号]?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: [MySQL 密码]
    driver-class-name: com.mysql.cj.jdbc.Driver
  redis:
    host: localhost
    port: 6379
    password: [_Redis 密码，无则空_]
    database: 0
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect

server:
  port: [8080]
  servlet:
    context-path: /api

jwt:
  secret: [项目代号]-secret-key-change-in-production
  expiration: 86400000  # 24 小时（毫秒）
```

#### 6.2.4 主启动类模板

```java
// 路径: backend/[项目代号]-api/src/main/java/[公司包名]/[项目代号]Application.java

package [公司包名];

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class [项目代号]Application {

    public static void main(String[] args) {
        SpringApplication.run([项目代号]Application.class, args);
        System.out.println("========================================");
        System.out.println("  [项目名] API 启动成功");
        System.out.println("  地址: http://localhost:[8080]/api");
        System.out.println("========================================");
    }
}
```

---

### 6.3 前端项目初始化

#### 6.3.1 创建项目

```bash
cd frontend

# 使用 Vite 创建 Vue 3 + TS 项目
npm create vite@latest [项目代号]-web -- --template vue-ts

cd [项目代号]-web
npm install

# 安装核心依赖
npm install vue-router@4 pinia axios element-plus @element-plus/icons-vue
```

#### 6.3.2 axios 请求封装（任何项目通用）

```typescript
// 路径: frontend/[项目代号]-web/src/api/request.ts

import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:[8080]/api',
  timeout: 10000
})

// 请求拦截器 - 添加 Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器 - 统一处理错误
request.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    ElMessage.error(error.response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)

export default request
```

#### 6.3.3 环境变量模板

```bash
# 路径: frontend/[项目代号]-web/.env.development

VITE_API_BASE_URL=http://localhost:[8080]/api
VITE_APP_TITLE=[项目名]
```

#### 6.3.4 Vite 代理配置

```typescript
// 路径: frontend/[项目代号]-web/vite.config.ts

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: [前端端口],
    proxy: {
      '/api': {
        target: 'http://localhost:[8080]',
        changeOrigin: true
      }
    }
  }
})
```

---

### 6.4 通用数据库初始化 SQL（任何项目必含）

#### 6.4.1 创建数据库

```sql
-- 使用 root 用户登录后执行
CREATE DATABASE [项目代号] DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE [项目代号];
```

#### 6.4.2 系统必备表（用户 / 角色 / 权限）

> **任何后台系统都需要这三张表**，复制即可，按需调整字段。

```sql
-- 路径: backend/[项目代号]-api/src/main/resources/db/sql/init_sys_auth.sql

-- ========== 系统认证基础表（任何项目必含） ==========

-- 1. 用户表
CREATE TABLE `[项目前缀_]user` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) UNIQUE NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码(bcrypt)',
  `real_name` varchar(50) COMMENT '真实姓名',
  `phone` varchar(20) COMMENT '手机号',
  `avatar` varchar(255) COMMENT '头像',
  `status` tinyint DEFAULT 1 COMMENT '状态:0禁用 1启用',
  `last_login_at` datetime COMMENT '最后登录时间',
  `last_login_ip` varchar(50) COMMENT '最后登录IP',
  `tenant_id` bigint COMMENT '租户ID（多租户项目用）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='系统用户表';

CREATE INDEX idx_user_username ON [项目前缀_]user(username);
CREATE INDEX idx_user_phone ON [项目前缀_]user(phone);

-- 2. 角色表
CREATE TABLE `[项目前缀_]role` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `code` varchar(50) UNIQUE NOT NULL COMMENT '角色编码',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(255) COMMENT '描述',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='角色表';

-- 3. 用户角色关联表
CREATE TABLE `[项目前缀_]user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`)
) COMMENT='用户角色关联表';

-- 4. 权限表
CREATE TABLE `[项目前缀_]permission` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `parent_id` bigint DEFAULT 0 COMMENT '父级ID',
  `code` varchar(100) UNIQUE NOT NULL COMMENT '权限编码',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `type` tinyint NOT NULL COMMENT '类型:1菜单 2按钮 3接口',
  `path` varchar(255) COMMENT '路径',
  `icon` varchar(50) COMMENT '图标',
  `sort` int DEFAULT 0 COMMENT '排序',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='权限表';

-- 5. 角色权限关联表
CREATE TABLE `[项目前缀_]role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`)
) COMMENT='角色权限关联表';

-- 6. 初始化数据
-- 默认密码: admin123（bcrypt 加密，登录后请立即修改）
INSERT INTO `[项目前缀_]user` (`username`, `password`, `real_name`, `phone`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', '13800138000', 1);

INSERT INTO `[项目前缀_]role` (`code`, `name`, `description`) VALUES
('SUPER_ADMIN', '超级管理员', '拥有所有权限'),
('NORMAL_USER', '普通用户', '基础权限');

INSERT INTO `[项目前缀_]user_role` (`user_id`, `role_id`) VALUES (1, 1);
```

> **注**：以上 SQL 是**通用骨架**，业务表（如会员、商品、订单等）按各项目模块清单独立建表。

---

## 七、API 接口规范（通用）

### 7.1 统一响应格式

```json
{
  "code": 200,        // 状态码：200成功，400参数错误，401未授权，500服务器错误
  "message": "success", // 消息
  "data": {}           // 数据（可选）
}
```

### 7.2 状态码定义

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录 / Token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

### 7.3 分页响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],        // 数据列表
    "total": 100,      // 总数
    "page": 1,         // 当前页
    "pageSize": 10     // 每页条数
  }
}
```

---

## 八、代码规范

### 8.1 后端 Java 规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | UpperCamelCase | `SysUserController` |
| 方法名 | lowerCamelCase | `getUserById` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 包名 | 全小写 | `[公司包名].controller` |
| 数据库表 | 小写下划线 | `[项目前缀_]user` |
| 数据库字段 | 小写下划线 | `user_name` |

### 8.2 前端 Vue 规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件名 | PascalCase | `UserList.vue` |
| 组件文件 | PascalCase | `UserCard.vue` |
| API 文件 | 小写下划线 | `user_api.ts` |
| Store 文件 | 小写下划线 | `user_store.ts` |
| 常量 | UPPER_SNAKE_CASE | `API_BASE_URL` |
| 变量 | lowerCamelCase | `userName` |

---

## 九、Git 分支管理策略

### 9.1 分支命名规范

```
main                    # 主分支（生产环境）
├── develop             # 开发分支
│   ├── feature/M01-auth          # M01 功能分支
│   ├── feature/M02-basic         # M02 功能分支
│   ├── feature/M03-[模块名]      # M03 功能分支
│   └── ...
└── hotfix/*            # 紧急修复分支
```

### 9.2 开发流程

```bash
# 1. 从 develop 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feature/M01-auth

# 2. 开发完成后提交
git add .
git commit -m "feat(M01): 完成登录认证模块"

# 3. 推送到远程
git push -u origin feature/M01-auth

# 4. 合并到 develop
git checkout develop
git merge feature/M01-auth
git push origin develop

# 5. 删除功能分支
git branch -d feature/M01-auth
```

### 9.3 提交信息规范

```
feat: 新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式（不影响功能）
refactor: 重构
test: 测试相关
chore: 构建 / 工具相关
```

示例：
```
feat(M01): 添加用户登录接口
feat(M01): 实现 JWT Token 认证
fix(M01): 修复登录超时问题
```

完整格式：
```
<type>(<scope>): <subject>

<body>

<footer>
```

示例：
```
feat(M01): 实现用户登录功能

- 添加登录接口
- 实现 Token 存储
- 添加路由守卫

Closes #1
```

---

## 十、执行清单（按顺序执行）

> **本节是项目整体推进的"总控表"**，每个阶段复制一份作为本项目实际进度跟踪。
> 模板只展示通用骨架，**项目实际进度在各自的 `MODULE-[项目名].md` 文件中维护**。

### 第一阶段：环境搭建（预计 2-4 小时）

#### 步骤 1.1：安装 Docker Desktop

- [ ] 下载 Docker Desktop: https://www.docker.com/products/docker-desktop/
- [ ] 安装并启动 Docker
- [ ] 验证：`docker --version`

#### 步骤 1.2：启动 MySQL 和 Redis

- [ ] 在项目根目录创建 `docker-compose.yml`（文档 6.1.1 节）
- [ ] 执行：`docker-compose up -d`
- [ ] 验证：`docker ps` 看到 mysql 和 redis 容器

#### 步骤 1.3：安装开发工具

- [ ] 安装 JDK 17+: https://adoptium.net/
- [ ] 安装 Maven 3.8+: https://maven.apache.org/download.cgi
- [ ] 安装 IDEA: https://www.jetbrains.com/idea/download/
- [ ] 安装 VS Code: https://code.visualstudio.com/download/
- [ ] 安装 Git: https://git-scm.com/download/

#### 步骤 1.4：验证环境

```bash
java -version      # 期望: openjdk 17.x.x
mvn --version      # 期望: Apache Maven 3.8.x
git --version      # 期望: git version 2.x.x
docker ps          # 期望: 看到 mysql 和 redis 容器
```

---

### 第二阶段：项目初始化（预计 1-2 小时）

#### 步骤 2.1：创建目录结构

```bash
cd [项目根目录]
mkdir backend frontend
```

#### 步骤 2.2：初始化后端项目

- [ ] 在 `backend/` 下创建 `pom.xml`（参考 6.2.2 节）
- [ ] 创建包结构：`[公司包名]`
- [ ] 创建配置类：`CorsConfig.java`、`RedisConfig.java`
- [ ] 创建主启动类：`[项目代号]Application.java`（参考 6.2.4 节）
- [ ] 创建 `application.yml` 配置文件（参考 6.2.3 节）

#### 步骤 2.3：初始化前端项目

```bash
cd frontend
npm create vite@latest [项目代号]-web -- --template vue-ts
cd [项目代号]-web
npm install
npm install vue-router@4 pinia axios element-plus @element-plus/icons-vue
```

- [ ] 配置 `vite.config.ts`（代理，参考 6.3.4 节）
- [ ] 配置 `router/index.ts`
- [ ] 配置 Pinia store
- [ ] 配置 `api/request.ts`（参考 6.3.2 节）

#### 步骤 2.4：数据库初始化

- [ ] 执行 `init_sys_auth.sql`（参考 6.4.2 节）
- [ ] 验证：用 `admin / admin123` 登录测试

#### 步骤 2.5：跑通"登录 → 首页"空白链路

- [ ] 后端启动成功
- [ ] 前端启动成功
- [ ] 后端 `POST /api/auth/login` 返回 Token
- [ ] 前端能调用登录接口
- [ ] 登录成功后跳转首页

---

### 第三阶段：模块开发（按模块清单逐个推进）

> **每个 Mxx 模块独立完成**，按文档 "四、模块开发 5 步闭环" 执行。
> 完成后，在本文档下方追加 `### 5.x Mxx 模块任务清单` 独立章节。

#### 模块开发节奏建议

```
┌────────────────────────────────────────────┐
│  推荐排期（每个模块 3-5 天）                  │
├────────────────────────────────────────────┤
│  Day 1：DB 设计 + 后端 Entity/Mapper         │
│  Day 2：后端 Service/Controller              │
│  Day 3：前端 API 封装 + 列表页               │
│  Day 4：前端 表单 + 详情 + 联调              │
│  Day 5：自测 + 验收清单 + 文档               │
└────────────────────────────────────────────┘
```

---

### 第四阶段：联调与上线

#### 步骤 4.1：全链路冒烟测试

- [ ] 所有模块 4 层验证全部通过（参考 4.4 节）
- [ ] 跨模块联动测试通过（如：会员 → 套餐 → 收银）
- [ ] 权限测试通过（不同角色访问控制正确）

#### 步骤 4.2：非功能验收

- [ ] 性能：核心接口响应 < 500ms
- [ ] 安全：SQL 注入 / XSS 防护
- [ ] 并发：核心接口并发 ≥ 100 QPS
- [ ] 日志：关键操作有日志记录
- [ ] 事务：资金类操作有事务保护

#### 步骤 4.3：部署上线

- [ ] 打包后端 JAR / 前端 dist
- [ ] 配置生产环境 `application-prod.yml`
- [ ] 部署到测试环境 → 预发环境 → 生产环境
- [ ] 配置域名 / HTTPS
- [ ] 配置数据库备份策略

---

## 十一、常见问题排查

| 问题 | 解决方案 |
|------|----------|
| 后端启动报错端口被占用 | `netstat -ano \| findstr [8080]` 查找进程并结束 |
| 前端 `npm install` 失败 | 检查 Node.js 版本，建议 18+ |
| MySQL 连接失败 | 检查用户名密码，确认 docker 容器已启动 |
| Redis 连接失败 | 检查 `redis-server` 是否在运行 |
| 跨域报错 | 确认后端 `CorsConfig` 已配置 |
| Token 验证失败 | 确认前后端使用相同的 secret key |
| 占位符未替换 | 全局搜索 `[项目代号]` 检查是否漏改 |

---

## 十二、参考与附录

### 12.1 配套文档

- **方法论**：`METHOD-通用项目快速开发方法论.md`（讲"为什么"和"用什么原则"）
- **真实实例**：`MODULE-模块化开发验证体系.md`（美业 SaaS 的完整实例参考）

### 12.2 参考实例

> 想看 M01-M07 完整开发过程？查看美业 SaaS 实例：
> - 数据库表设计（参考实例 7.3.2 节）
> - 后端 CRUD（参考实例 M01-M03 各章节）
> - 前端页面（参考实例 Vue 组件章节）

### 12.3 模板维护

- **本文档更新日期**：2026-08-01
- **反馈**：使用中发现通用部分可补充 / 删减 / 优化时，记录到此文档维护日志

---

## 📋 文档维护日志

```
2026-08-01  v1.0  模板初版
            - 基于美业 SaaS MODULE 文档参数化
            - 保留通用代码骨架（docker-compose / pom / application.yml / axios / 系统表）
            - 业务模块清单参数化为占位符
            - 新增"使用说明"和"5 步上手"流程
```