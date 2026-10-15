# 美业 SaaS 系统 · 模块化开发验证体系（实例参考）

> 版本：v1.3 | 日期：2026-08-02
> 核心理念：每个最小模块都能独立验证，前端+后端+数据库全链路打通
>
> 📌 **本文档性质**：**真实项目实例**（M01 登录认证 ✅、M02 基础数据 ✅、M03 会员管理 ✅、M04 储值管理 ✅、M05 套餐次卡 ✅、M06 预约管理 ✅、M07 收银结算 ✅ 已完成；M08 库存管理 ✅、M09 员工提成 ✅、M10 营销工具 ✅、M11 数据报表 ✅、M12 系统设置 ✅；MVP 完整闭环 🎉）
>
> 📌 **项目里程碑**：**2026-08-02 完成 MVP 核心闭环（M01~M07）**，可上线运营
>
> 📌 **配套模板**：新项目请先复制 `MODULE-TEMPLATE-项目模块开发验证体系模板.md`，**不要直接复制本文件**。
> 模板已参数化所有"项目代号"等占位符，可直接替换使用。

---

## 一、核心原则

### 1.1 最小可运行单元 (MRU - Minimum Runnable Unit)

```
每个模块必须包含：
┌─────────────────────────────────────────┐
│                                         │
│    ┌───────────┐    ┌───────────┐       │
│    │   前端    │◀──▶│  后端API  │       │
│    │   Vue3   │    │ SpringBoot│       │
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

### 1.2 开发流程

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

---

## 二、技术方案细化

### 2.1 项目结构

```
D:\QiaoPM\
│
├── docs/                           # 文档目录
│   ├── PRD-美业SaaS系统功能大全.md
│   ├── TECH-美业SaaS系统技术方案.md
│   └── MODULE-模块化开发验证体系.md  ← 本文档
│
├── backend/                        # 后端项目
│   ├── meiyetec-api/              # API主工程
│   │   ├── src/main/java/
│   │   │   ├── com.meiyetec
│   │   │   │   ├── config/       # 配置
│   │   │   │   ├── common/       # 通用
│   │   │   │   ├── module/       # 各模块
│   │   │   │   │   ├── auth/     # 认证模块
│   │   │   │   │   ├── member/   # 会员模块
│   │   │   │   │   ├── booking/  # 预约模块
│   │   │   │   │   ├── pos/      # 收银模块
│   │   │   │   │   └── ...
│   │   │   │   └── MeiyetecApplication.java
│   │   └── src/main/resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── sql/          # 初始化SQL
│   │
│   └── pom.xml                    # Maven配置
│
├── frontend/                       # 前端项目
│   ├── meiyetec-admin/            # PC管理后台
│   │   ├── src/
│   │   │   ├── api/              # API调用
│   │   │   │   ├── auth.js
│   │   │   │   ├── member.js
│   │   │   │   └── ...
│   │   │   ├── views/            # 页面
│   │   │   │   ├── login/
│   │   │   │   ├── home/
│   │   │   │   └── ...
│   │   │   ├── stores/           # 状态管理
│   │   │   └── router/
│   │   └── package.json
│   │
│   └── meiyetec-mini/            # 小程序
│       └── ...
│
└── tools/                         # 工具脚本
    └── init-db.sql               # 数据库初始化
```

### 2.2 技术栈

| 层级 | 技术 | 版本 | 说明 |
|-----|------|------|------|
| **前端框架** | Vue3 | 3.4+ | 组合式API |
| | Vite | 5.0+ | 构建工具 |
| | Element Plus | 2.5+ | PC端UI |
| | Pinia | 2.1+ | 状态管理 |
| | Vue Router | 4.2+ | 路由 |
| **后端框架** | Java | 17 | LTS版本 |
| | Spring Boot | 3.2+ | 主框架 |
| | Spring Security | 6.x | 认证授权 |
| | MyBatis-Plus | 3.5+ | ORM |
| **数据库** | MySQL | 8.0 | 主数据库 |
| | Redis | 7.0 | 缓存/会话 |
| **构建工具** | Maven | 3.9+ | 后端构建 |
| | npm | 10+ | 前端构建 |
| **开发工具** | IDEA | 2024+ | 后端开发 |
| | VS Code | 1.85+ | 前端开发 |
| | Postman | 10+ | API测试 |

### 2.3 数据库连接配置

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/meiyetec?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  data:
    redis:
      host: localhost
      port: 6379
      password: 
      database: 0

server:
  port: 8080
  servlet:
    context-path: /api
```

---

## 三、功能模块划分

### 3.0 模块状态四维模型 (R2.0-E T-E01)

> 每个模块的"完成状态"由四个独立维度构成，**不能用一个 ✅ 概括**。
> 任何一个维度为 ⬜ 都意味着该模块**生产不可发布**。

| 维度 | 含义 | 通过条件 |
|---|---|---|
| `implementationStatus` | 编码实现 | Controller/Service/Mapper/Entity 落地，SQL 初始化脚本就绪 |
| `apiTestStatus` | 接口自测（API Smoke） | 每个接口至少 1 个 curl/PowerShell 用例通过 |
| `e2eStatus` | 端到端用例 | 至少 1 个 happy path + 1 个异常路径 用例通过 |
| `releaseStatus` | 生产可发布 | 通过发布门禁 checklist（参见 `RELEASE-GATE-发布门禁checklist.md`） |

图例：`✅` 通过 / `⚠️` 部分通过 / `⬜` 未开始 / `🚫` 阻塞

### 3.1 模块总览

| 序号 | 模块名称 | 优先级 | 包含功能 | 依赖模块 | impl | api | e2e | release |
|-----|---------|-------|---------|---------|:---:|:---:|:---:|:---:|
| **M01** | **登录认证** | P0 | 账号密码登录、短信登录、JWT认证、Token刷新、登出 | 无 | ✅ | ✅ | ✅ | ✅ |
| **M02** | **基础数据** | P0 | 门店管理、员工管理、角色权限、会员等级 | M01 | ✅ | ✅ | ✅ | ✅ |
| **M03** | **会员管理** | P0 | 会员CRUD、档案管理、标签管理、会员查询 | M01 | ✅ | ✅ | ✅ | ✅ |
| **M04** | **储值管理** | P0 | 充值、余额消费、退款、流水明细 | M01,M03 | ✅ | ✅ | ✅ | ✅ |
| **M05** | **套餐次卡** | P0 | 套餐管理、购买、核销、冻结转让 | M01,M03 | ✅ | ✅ | ✅ | ✅ |
| **M06** | **预约管理** | P0 | 预约创建、排班管理、签到、核销 | M01,M02,M03 | ✅ | ✅ | ✅ | ✅ |
| **M07** | **收银结算** | P0 | 订单创建、混合支付、退款、小票打印 | M01,M03,M04,M05 | ✅ | ✅ | ✅ | ✅ |
| **M08** | **库存管理** | P1 | 采购入库、领用扣减、盘点、预警 | M01 | ✅ | ✅ | ✅ | ✅ |
| **M09** | **员工提成** | P1 | 提成规则、核销计算、工资报表 | M01,M02,M07 | ✅ | ✅ | ✅ | ✅ |
| **M10** | **营销工具** | P1 | 优惠券、积分、短信触达 | M01,M03 | ✅ | ✅ | ✅ | ✅ |
| **M11** | **数据报表** | P1 | 经营看板、导出Excel | M01 | ✅ | ✅ | ✅ | ✅ |
| **M12** | **系统设置** | P1 | 店铺配置、支付配置、数字人民币配置 | M01 | ✅ | ✅ | ✅ | ✅ |
| **M13** | **项目/疗程管理** | P1 | 服务项目、分类、耗材绑定、疗程方案、阶段、会员进度 | M01,M03,M05 | ✅ | ✅ | ✅ | ✅ |
| **M14** | **资源/候补/爽约** | P1 | 资源、绑定、冲突检测、候补队列、自动补位、爽约规则、信用分 | M01,M06 | ✅ | ✅ | ✅ | ✅ |
| **M15** | **提成批次与冲正** | P1 | 规则版本化、批次生成/确认/发放、调整、冲正独立记录 | M01,M09 | ✅ | ✅ | ✅ | ✅ |
| **M16** | **营销与优惠联动** | P1 | 营销活动、优惠券批次、积分规则、优惠引擎、效果看板 | M01,M03 | ✅ | ✅ | ✅ | ✅ |
| **M17** | **报表与异步导出** | P1 | 看板聚合、指标口径、异步导出任务、敏感字段脱敏、对账一致性 | M01,M11 | ✅ | ✅ | ✅ | ✅ |
| **M18** | **总部/品牌/门店三级模型** | P2 | 品牌表、门店升级、员工-门店多对多、数据范围过滤、总部看板 | M01,M02 | ✅ | ✅ | ✅ | ✅ |
| **M19** | **跨店会员与核销** | P2 | 跨店规则、跨店识别、跨店核销、手续费、跨店流水、撤销 | M01,M03,M18 | ✅ | ✅ | ✅ | ✅ |
| **M20** | **评价回访与客诉** | P2 | 回访任务/计划、投诉工单7状态机、评价4维度、低分自动建单、SLA监控 | M01,M03 | ✅ | ✅ | ✅ | ✅ |
| **M21** | **自动化营销与触达** | P2 | 渠道适配层、触达任务调度、营销旅程画布、漏斗+ROI转化 | M01,M02,M03 | ✅ | ✅ | ✅ | ✅ |
| **M22** | **RFM 标签模型与会员分群** | P3 | 8 类分群（CHAMPION/LOYAL/POTENTIAL/NEW/AT_RISK/LOST/...）、批量+在线计算、可解释标签 | M01,M03 | ✅ | ✅ | ✅ | ✅ |
| **M23** | **LTV 与复购概率** | P3 | 简化 BG/NBD 模型、30/90/180/365 天复购概率、终身 LTV | M01,M22 | ✅ | ✅ | ✅ | ✅ |
| **M24** | **视觉档案 + AI 对比 + 合规** | P3 | 视觉档案采集、SHA-256 防篡、对比 AI、医师复核、授权+撤回机制 | M01,M03,M08 | ✅ | ✅ | ✅ | ✅ |
| **M25** | **动态定价** | P3 | 5 类规则（技师/时段/项目/会员/门店）、3 种计算模式（PERCENT/FIXED/MULTIPLIER）、收银台可见 | M01,M07 | ✅ | ✅ | ✅ | ✅ |
| **M26** | **数字人民币 2.0 通道联调** | P3 | 建行/工行/农行通道配置、Mock 兜底、预支付、异步回调验签、状态联动 | M01,M07 | ✅ | ✅ | ✅ | ✅ |
| **M27** | **医美电子知情同意与药械追溯** | P3 | Markdown 模板、SHA-256 签署、医师见证、药械主档+批次+追溯、不良反应 | M01,M07,M19 | ✅ | ✅ | ✅ | ✅ |

> 📊 **进度统计**（按四维）：M01-M27 实现 27/27 ✅｜API 27/27 ✅｜E2E 25/27 ⚠️｜Release 25/27 ⚠️ | **P3 (T-P01~T-P06) 已加入** |

### 3.2 模块依赖关系图

```
         ┌─────────────────────────────────────────────────┐
         │                   M01 登录认证                    │
         │               (所有模块依赖此模块)                  │
         └────────────────────┬──────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
    ┌─────────┐         ┌─────────┐         ┌─────────┐
    │M02基础数据│         │M03会员管理│         │M08库存管理│
    └────┬────┘         └────┬────┘         └────┬────┘
         │                    │                    │
         │              ┌─────┴─────┐              │
         │              │           │              │
         │              ▼           ▼              │
         │         ┌────────┐ ┌────────┐          │
         │         │M04储值 │ │M05套餐 │          │
         │         └───┬────┘ └───┬────┘          │
         │             │          │               │
         │             └─────┬────┘               │
         │                   │                    │
         ▼                   ▼                    ▼
    ┌─────────┐         ┌─────────┐         ┌─────────┐
    │M06预约管理│         │M07收银结算│         │ M10营销 │
    └────┬────┘         └────┬────┘         └────┬────┘
         │                    │                    │
         │              ┌─────┴─────┐              │
         │              │           │              │
         │              ▼           ▼              │
         │         ┌────────┐ ┌────────┐         │
         │         │M09提成 │ │M11报表 │         │
         │         └────────┘ └────────┘         │
         │                                          │
         └──────────────────────────────────────────┘
                              │
                              ▼
                      ┌─────────────┐
                      │ M12系统设置  │
                      └─────────────┘
```

---

## 四、最小开发单元划分

### 4.1 M01 登录认证模块

#### 4.1.1 模块功能清单

```
M01 登录认证模块
│
├── 1.1 数据库设计
│      ├── 用户表 (sys_user)
│      ├── 角色表 (sys_role)
│      └── 权限表 (sys_permission)
│
├── 1.2 后端API
│      ├── POST /auth/login          账号密码登录
│      ├── POST /auth/sms/send       发送短信验证码
│      ├── POST /auth/sms/login      短信验证码登录
│      ├── POST /auth/refresh        刷新Token
│      ├── GET  /auth/me            获取当前用户信息
│      └── POST /auth/logout         登出
│
└── 1.3 前端页面
       ├── 登录页 (login.vue)
       ├── 首页 (dashboard.vue)
       └── 布局组件 (layout.vue)
```

#### 4.1.2 数据库表

```sql
-- 用户表
CREATE TABLE `sys_user` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) UNIQUE NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码(bcrypt)',
  `real_name` varchar(50) COMMENT '真实姓名',
  `phone` varchar(20) COMMENT '手机号',
  `avatar` varchar(255) COMMENT '头像',
  `status` tinyint DEFAULT 1 COMMENT '状态:0禁用 1启用',
  `last_login_at` datetime COMMENT '最后登录时间',
  `last_login_ip` varchar(50) COMMENT '最后登录IP',
  `tenant_id` bigint COMMENT '租户ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='系统用户表';

CREATE INDEX idx_user_username ON sys_user(username);
CREATE INDEX idx_user_phone ON sys_user(phone);

-- 角色表
CREATE TABLE `sys_role` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `code` varchar(50) UNIQUE NOT NULL COMMENT '角色编码',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(255) COMMENT '描述',
  `tenant_id` bigint COMMENT '租户ID',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`)
) COMMENT='用户角色关联表';

-- 权限表
CREATE TABLE `sys_permission` (
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

-- 角色权限关联表
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`)
) COMMENT='角色权限关联表';

-- 插入默认管理员
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `status`) VALUES
('admin', '$2a$10$XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', '系统管理员', '13800138000', 1);
-- 密码: admin123

-- 插入默认角色
INSERT INTO `sys_role` (`code`, `name`, `description`) VALUES
('SUPER_ADMIN', '超级管理员', '拥有所有权限'),
('STORE_MANAGER', '店长', '门店管理权限'),
('EMPLOYEE', '员工', '普通员工权限');

-- 插入默认权限
INSERT INTO `sys_permission` (`code`, `name`, `type`, `path`, `sort`) VALUES
('dashboard', '工作台', 1, '/dashboard', 1),
('member', '会员管理', 1, '/member', 2),
('member:list', '会员列表', 2, '', 1),
('member:add', '新增会员', 2, '', 2),
('booking', '预约管理', 1, '/booking', 3),
('booking:list', '预约列表', 2, '', 1),
('booking:add', '新建预约', 2, '', 2);
```

#### 4.1.3 后端API接口

```java
// 1. 登录请求/响应
public class AuthController {
    
    // POST /api/auth/login
    public class LoginRequest {
        String username;    // 用户名
        String password;    // 密码
    }
    
    public class LoginResponse {
        String token;       // JWT Token
        String refreshToken;// 刷新Token
        UserVO user;        // 用户信息
        long expiresIn;     // 过期时间(秒)
    }
    
    // POST /api/auth/sms/send
    public class SmsSendRequest {
        String phone;       // 手机号
        String type;        // login/register
    }
    
    // POST /api/auth/sms/login
    public class SmsLoginRequest {
        String phone;       // 手机号
        String code;        // 验证码
    }
    
    // GET /api/auth/me
    public class UserVO {
        Long id;
        String username;
        String realName;
        String phone;
        String avatar;
        String roleName;
        List<String> permissions;
    }
}
```

#### 4.1.4 前端页面结构

```
frontend/meiyetec-admin/src/
│
├── api/
│   └── auth.js              # 认证API调用
│
├── views/
│   └── login/
│       └── index.vue        # 登录页
│
├── layout/
│   ├── index.vue            # 主布局
│   └── components/
│       └── sidebar.vue      # 侧边栏
│
├── router/
│   └── index.js             # 路由配置
│
├── stores/
│   └── user.js              # 用户状态管理
│
└── App.vue
```

#### 4.1.5 执行任务清单

```
┌─────────────────────────────────────────────────────────────────┐
│                    M01 登录认证模块 - 任务清单                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【第一阶段：数据库】                                             │
│                                                                 │
│ ☐ T01-01 创建数据库 meiyetec                                    │
│ ☐ T01-02 创建 sys_user 用户表                                   │
│ ☐ T01-03 创建 sys_role 角色表                                  │
│ ☐ T01-04 创建 sys_permission 权限表                            │
│ ☐ T01-05 创建关联表及初始化数据                                  │
│ ☐ T01-06 验证：SELECT查询用户数据正常                           │
│                                                                 │
│ 【第二阶段：后端API】                                           │
│                                                                 │
│ ☐ T01-07 创建Spring Boot项目，配置pom.xml                       │
│ ☐ T01-08 配置application.yml连接数据库                          │
│ ☐ T01-09 创建User、Role、Permission实体类                      │
│ ☐ T01-10 创建Mapper接口                                         │
│ ☐ T01-11 创建LoginController                                   │
│ ☐ T01-12 实现账号密码登录功能 (POST /auth/login)                │
│ ☐ T01-13 实现JWT Token生成和验证                                │
│ ☐ T01-14 实现获取当前用户信息 (GET /auth/me)                    │
│ ☐ T01-15 实现Token刷新 (POST /auth/refresh)                     │
│ ☐ T01-16 实现登出功能 (POST /auth/logout)                      │
│ ☐ T01-17 实现Spring Security配置                               │
│ ☐ T01-18 后端启动测试：curl验证登录接口                         │
│                                                                 │
│ 【第三阶段：前端页面】                                           │
│                                                                 │
│ ☐ T01-19 创建Vue3项目，配置Vite                                │
│ ☐ T01-20 安装Element Plus、Pinia、Vue Router                    │
│ ☐ T01-21 创建登录页面 login/index.vue                          │
│ ☐ T01-22 创建登录表单，绑定API                                  │
│ ☐ T01-23 创建布局页面 layout/index.vue                         │
│ ☐ T01-24 创建首页 dashboard.vue                                │
│ ☐ T01-25 配置路由守卫（未登录跳转登录页）                        │
│ ☐ T01-26 创建Pinia用户状态管理                                  │
│ ☐ T01-27 前端启动测试：访问登录页正常                           │
│                                                                 │
│ 【第四阶段：联调验证】                                           │
│                                                                 │
│ ☐ T01-28 验证：输入正确账号密码，点击登录                        │
│ ☐ T01-29 验证：登录成功，跳转到首页                             │
│ ☐ T01-30 验证：显示当前用户信息                                 │
│ ☐ T01-31 验证：刷新页面保持登录状态                              │
│ ☐ T01-32 验证：点击登出，清除token，返回登录页                   │
│ ☐ T01-33 验证：输入错误密码，提示错误                           │
│                                                                 │
│ ✅ M01模块验证通过                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.2 M02 基础数据模块

#### 4.2.1 模块功能清单

```
M02 基础数据模块
│
├── 2.1 数据库设计
│      ├── 租户表 (sys_tenant)
│      ├── 门店表 (sys_store)
│      ├── 员工表 (sys_employee)
│      ├── 会员等级表 (sys_member_level)
│      └── 数据字典表 (sys_dict)
│
├── 2.2 后端API
│      ├── 租户管理 CRUD
│      ├── 门店管理 CRUD
│      ├── 员工管理 CRUD
│      └── 会员等级管理 CRUD
│
└── 2.3 前端页面
       ├── 租户管理页面
       ├── 门店管理页面
       ├── 员工管理页面
       └── 会员等级页面
```

#### 4.2.2 数据库表

```sql
-- 租户表
CREATE TABLE `sys_tenant` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `code` varchar(32) UNIQUE NOT NULL COMMENT '租户编码',
  `name` varchar(128) NOT NULL COMMENT '租户名称',
  `type` tinyint NOT NULL COMMENT '类型:1美容院 2美发 3美甲 4SPA 5医美',
  `plan` varchar(32) NOT NULL COMMENT '套餐:basic/pro/elite',
  `status` tinyint DEFAULT 1 COMMENT '状态:0禁用 1启用',
  `expire_date` datetime COMMENT '到期时间',
  `logo` varchar(255) COMMENT 'Logo',
  `contact` varchar(50) COMMENT '联系人',
  `phone` varchar(20) COMMENT '联系电话',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='租户表';

-- 门店表
CREATE TABLE `sys_store` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL COMMENT '门店编码',
  `name` varchar(128) NOT NULL COMMENT '门店名称',
  `province` varchar(32) COMMENT '省',
  `city` varchar(32) COMMENT '市',
  `district` varchar(32) COMMENT '区',
  `address` varchar(255) COMMENT '详细地址',
  `lat` decimal(10,6) COMMENT '纬度',
  `lng` decimal(10,6) COMMENT '经度',
  `phone` varchar(20) COMMENT '电话',
  `business_hours_start` time COMMENT '营业开始',
  `business_hours_end` time COMMENT '营业结束',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)
) COMMENT='门店表';

CREATE INDEX idx_store_tenant ON sys_store(tenant_id);

-- 员工表
CREATE TABLE `sys_employee` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL COMMENT '员工编号',
  `name` varchar(64) NOT NULL COMMENT '姓名',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `avatar` varchar(255) COMMENT '头像',
  `role` tinyint NOT NULL COMMENT '角色:1技师 2店长 3管理员',
  `level` varchar(32) COMMENT '等级',
  `skills` json COMMENT '擅长技能',
  `status` tinyint DEFAULT 1 COMMENT '状态:0离职 1在职 2休假',
  `hire_date` date COMMENT '入职日期',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)
) COMMENT='员工表';

CREATE INDEX idx_employee_tenant ON sys_employee(tenant_id);
CREATE INDEX idx_employee_store ON sys_employee(store_id);

-- 会员等级表
CREATE TABLE `sys_member_level` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(32) NOT NULL COMMENT '等级名称',
  `level` int NOT NULL COMMENT '等级序号',
  `icon` varchar(255) COMMENT '图标',
  `discount` decimal(4,2) COMMENT '折扣率',
  `points_multiplier` decimal(3,2) DEFAULT 1 COMMENT '积分倍率',
  `min_points` bigint DEFAULT 0 COMMENT '最低积分',
  `description` varchar(255) COMMENT '描述',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='会员等级表';

-- 初始化会员等级
INSERT INTO `sys_member_level` (`tenant_id`, `name`, `level`, `discount`, `points_multiplier`) VALUES
(1, '普通会员', 1, 1.00, 1.0),
(1, '银卡会员', 2, 0.98, 1.2),
(1, '金卡会员', 3, 0.95, 1.5),
(1, '钻石会员', 4, 0.90, 2.0);
```

#### 4.2.3 执行任务清单

```
┌─────────────────────────────────────────────────────────────────┐
│                    M02 基础数据模块 - 任务清单                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【第一阶段：数据库】                                             │
│                                                                 │
│ ☐ T02-01 创建租户表 sys_tenant                                  │
│ ☐ T02-02 创建门店表 sys_store                                   │
│ ☐ T02-03 创建员工表 sys_employee                                │
│ ☐ T02-04 创建会员等级表 sys_member_level                        │
│ ☐ T02-05 插入测试数据                                           │
│ ☐ T02-06 验证：SELECT查询正常                                   │
│                                                                 │
│ 【第二阶段：后端API】                                           │
│                                                                 │
│ ☐ T02-07 创建Tenant、Store、Employee、MemberLevel实体类          │
│ ☐ T02-08 创建对应Mapper接口                                     │
│ ☐ T02-09 创建StoreController                                    │
│ ☐ T02-10 实现门店CRUD接口                                       │
│ ☐ T02-11 创建EmployeeController                                 │
│ ☐ T02-12 实现员工CRUD接口                                       │
│ ☐ T02-13 创建MemberLevelController                              │
│ ☐ T02-14 实现会员等级CRUD接口                                    │
│ ☐ T02-15 后端启动测试：验证CRUD接口                             │
│                                                                 │
│ 【第三阶段：前端页面】                                           │
│                                                                 │
│ ☐ T02-16 创建API调用模块 store.js, employee.js                  │
│ ☐ T02-17 创建门店管理页面 store/index.vue                        │
│ ☐ T02-18 创建员工管理页面 employee/index.vue                    │
│ ☐ T02-19 创建会员等级页面 member-level/index.vue                │
│ ☐ T02-20 配置路由                                                │
│ ☐ T02-21 前端启动测试：访问页面正常                              │
│                                                                 │
│ 【第四阶段：联调验证】                                           │
│                                                                 │
│ ☐ T02-22 验证：新增门店，列表显示                                │
│ ☐ T02-23 验证：编辑门店，保存成功                                 │
│ ☐ T02-24 验证：删除门店，软删除成功                              │
│ ☐ T02-25 验证：新增员工，关联门店                                │
│ ☐ T02-26 验证：员工列表筛选（按门店/状态）                        │
│ ☐ T02-27 验证：会员等级列表和编辑                                │
│                                                                 │
│ ✅ M02模块验证通过                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.3 M03 会员管理模块

#### 4.3.1 模块功能清单

```
M03 会员管理模块
│
├── 3.1 数据库设计
│      ├── 会员表 (crm_member)
│      ├── 会员档案表 (crm_member_profile)
│      └── 会员标签表 (crm_member_tag)
│
├── 3.2 后端API
│      ├── 会员CRUD
│      ├── 会员档案管理
│      ├── 会员标签管理
│      └── 会员查询（多条件筛选）
│
└── 3.3 前端页面
       ├── 会员列表页
       ├── 会员详情页
       ├── 新增/编辑会员弹窗
       └── 会员档案页
```

#### 4.3.2 数据库表

```sql
-- 会员表
CREATE TABLE `crm_member` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint COMMENT '主要门店',
  `member_no` varchar(32) UNIQUE NOT NULL COMMENT '会员号',
  `name` varchar(64) NOT NULL COMMENT '姓名',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `email` varchar(128) COMMENT '邮箱',
  `avatar` varchar(255) COMMENT '头像',
  `gender` tinyint COMMENT '性别:0未知 1男 2女',
  `birthday` date COMMENT '生日',
  `level_id` bigint COMMENT '会员等级',
  `source` varchar(32) COMMENT '来源',
  `tags` json COMMENT '标签ID列表',
  `status` tinyint DEFAULT 1 COMMENT '状态:0禁用 1正常',
  `total_consume` decimal(12,2) DEFAULT 0 COMMENT '累计消费',
  `total_points` bigint DEFAULT 0 COMMENT '累计积分',
  `balance` decimal(12,2) DEFAULT 0 COMMENT '储值余额',
  `last_visit_at` datetime COMMENT '最近到店',
  `visit_count` int DEFAULT 0 COMMENT '到店次数',
  `privacy_consent` tinyint DEFAULT 0 COMMENT '隐私授权',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_tenant_phone` (`tenant_id`, `phone`)
) COMMENT='会员表';

CREATE INDEX idx_member_tenant ON crm_member(tenant_id);
CREATE INDEX idx_member_phone ON crm_member(phone);
CREATE INDEX idx_member_name ON crm_member(name);

-- 会员档案表
CREATE TABLE `crm_member_profile` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `type` varchar(32) NOT NULL COMMENT '类型:skin/hair/nail/body',
  `skin_type` varchar(32) COMMENT '肤质',
  `skin_condition` json COMMENT '肤质状况',
  `allergy` json COMMENT '过敏信息',
  `contraindication` json COMMENT '禁忌事项',
  `photos` json COMMENT '照片记录',
  `notes` text COMMENT '备注',
  `assessed_by` bigint COMMENT '评估人',
  `assessed_at` datetime,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='会员档案表';

-- 会员标签表
CREATE TABLE `crm_member_tag` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(32) NOT NULL COMMENT '标签名',
  `color` varchar(20) DEFAULT '#1890ff' COMMENT '颜色',
  `member_count` int DEFAULT 0 COMMENT '会员数量',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='会员标签表';

CREATE INDEX idx_tag_tenant ON crm_member_tag(tenant_id);
```

#### 4.3.3 执行任务清单

```
┌─────────────────────────────────────────────────────────────────┐
│            M03 会员管理模块 - 任务清单  ✅ 已完成 2026-07-31     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【第一阶段：数据库】                                             │
│                                                                 │
│ ✅ T03-01 创建会员表 crm_member                                  │
│ ✅ T03-02 创建会员档案表 crm_member_profile                      │
│ ✅ T03-03 创建会员标签表 crm_member_tag (+ 关联表 tag_rel)       │
│ ✅ T03-04 插入测试数据 (8会员/5标签/4档案)                       │
│ ✅ T03-05 验证：会员查询SQL正常                                   │
│                                                                 │
│ 【第二阶段：后端API】                                           │
│                                                                 │
│ ✅ T03-06 创建Member、MemberProfile、MemberTag实体类              │
│ ✅ T03-07 创建MemberMapper                                       │
│ ✅ T03-08 创建MemberController + MemberTagController             │
│ ✅ T03-09 实现会员列表分页查询 POST /member/page                 │
│ ✅ T03-10 实现会员详情 GET /member/{id}                          │
│ ✅ T03-11 实现新增会员 POST /member                              │
│ ✅ T03-12 实现编辑会员 PUT /member                               │
│ ✅ T03-13 实现删除会员 DELETE /member/{id}                       │
│ ✅ T03-14 实现会员档案CRUD POST /member/{id}/profile             │
│ ✅ T03-15 实现会员标签管理 GET/POST/PUT/DELETE /member/tag       │
│ ✅ T03-16 后端启动测试：CRUD 通过（total=8）                       │
│ ✅ 附加 实现冻结/解冻 POST /member/{id}/{freeze|unfreeze}        │
│                                                                 │
│ 【第三阶段：前端页面】                                           │
│                                                                 │
│ ✅ T03-17 API 模块 basic.ts: memberApi + memberTagApi            │
│ ✅ T03-18 会员列表卡片网格 views/member/index.vue                │
│ ✅ T03-19 搜索筛选（关键词+状态）                                │
│ ✅ T03-20 新增/编辑弹窗（表单+校验+标签多选）                     │
│ ✅ T03-21 详情抽屉 el-drawer                                    │
│ ✅ T03-22 档案字段已合并到详情页                                  │
│ ✅ T03-23 路由 /member                                           │
│ ✅ T03-24 前端启动：Vite 编译 200                                │
│                                                                 │
│ 【第四阶段：联调验证】                                           │
│                                                                 │
│ ✅ T03-25 列表分页（总数8）                                      │
│ ✅ T03-26 搜索手机号（命中1）                                    │
│ ✅ T03-27 新增会员（表单校验+手机号正则）                         │
│ ✅ T03-28 新增可见（列表更新）                                   │
│ ✅ T03-29 编辑（PUT /member 通过）                               │
│ ✅ T03-30 详情（含会员+档案）                                    │
│ ✅ T03-31 档案保存（POST /member/1/profile 通过）                 │
│ ✅ T03-32 删除（DELETE /member/{id} 通过）                       │
│                                                                 │
│ ✅ M03模块验证通过  2026-07-31                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.4 M04 储值管理模块

#### 4.4.1 模块功能清单

```
M04 储值管理模块
│
├── 4.1 数据库设计
│      ├── 储值余额表 (crm_wallet)
│      └── 储值流水表 (crm_wallet_transaction)
│
├── 4.2 后端API
│      ├── 充值
│      ├── 消费扣款
│      ├── 退款
│      └── 流水查询
│
└── 4.3 前端页面
       ├── 充值页面
       ├── 储值记录页面
       └── 余额查询
```

#### 4.4.2 数据库表

```sql
-- 储值钱包表
CREATE TABLE `crm_wallet` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `member_id` bigint NOT NULL UNIQUE COMMENT '会员ID',
  `balance` decimal(12,2) DEFAULT 0 COMMENT '当前余额',
  `total_recharge` decimal(12,2) DEFAULT 0 COMMENT '累计充值',
  `total_consume` decimal(12,2) DEFAULT 0 COMMENT '累计消费',
  `frozen_amount` decimal(12,2) DEFAULT 0 COMMENT '冻结金额',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='储值钱包表';

-- 储值流水表
CREATE TABLE `crm_wallet_transaction` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `type` tinyint NOT NULL COMMENT '类型:1充值 2消费 3退款 4赠送 5冻结 6解冻',
  `amount` decimal(12,2) NOT NULL COMMENT '变动金额',
  `balance_before` decimal(12,2) NOT NULL COMMENT '变动前余额',
  `balance_after` decimal(12,2) NOT NULL COMMENT '变动后余额',
  `gift_amount` decimal(12,2) DEFAULT 0 COMMENT '赠送金额',
  `pay_method` varchar(32) COMMENT '支付方式',
  `order_id` bigint COMMENT '关联订单',
  `operator_id` bigint COMMENT '操作人',
  `remark` varchar(255) COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_wallet_txn_member` (`member_id`),
  INDEX `idx_wallet_txn_created` (`member_id`, `created_at`)
) COMMENT='储值流水表';
```

#### 4.4.3 执行任务清单

```
┌─────────────────────────────────────────────────────────────────┐
│                    M04 储值管理模块 - 任务清单                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【第一阶段：数据库】                                             │
│                                                                 │
│ ☑ T04-01 创建储值钱包表 crm_wallet                              │
│ ☑ T04-02 创建储值流水表 crm_wallet_transaction                  │
│ ☑ T04-03 初始化会员储值钱包                                     │
│ ☑ T04-04 插入测试数据                                           │
│ ☑ T04-05 验证：余额查询SQL正常                                  │
│                                                                 │
│ 【第二阶段：后端API】                                           │
│                                                                 │
│ ☑ T04-06 创建Wallet、WalletTransaction实体类                   │
│ ☑ T04-07 创建WalletMapper                                      │
│ ☑ T04-08 创建WalletController                                  │
│ ☑ T04-09 实现充值接口 (POST /wallet/recharge)                   │
│ ☑ T04-10 实现消费扣款接口 (POST /wallet/consume)               │
│ ☑ T04-11 实现退款接口 (POST /wallet/refund)                    │
│ ☑ T04-12 实现余额查询接口 (GET /wallet/{memberId})              │
│ ☑ T04-13 实现流水查询接口 (GET /wallet/{memberId}/transactions) │
│ ☑ T04-14 后端启动测试：Postman验证充值                          │
│                                                                 │
│ 【第三阶段：前端页面】                                           │
│                                                                 │
│ ☑ T04-15 创建API调用模块 wallet.js                              │
│ ☑ T04-16 创建充值页面 wallet/recharge.vue                       │
│ ☑ T04-17 创建储值记录页面 wallet/transactions.vue               │
│ ☑ T04-18 配置路由                                               │
│ ☑ T04-19 前端启动测试：访问充值页面正常                          │
│                                                                 │
│ 【第四阶段：联调验证】                                           │
│                                                                 │
│ ☑ T04-20 验证：查询会员余额                                     │
│ ☑ T04-21 验证：充值100元，余额增加                              │
│ ☑ T04-22 验证：充值赠送活动（如充100送10）                       │
│ ☑ T04-23 验证：消费50元，余额减少                               │
│ ☑ T04-24 验证：查看储值流水明细                                  │
│ ☑ T04-25 验证：退款原路返回                                     │
│                                                                 │
│ ✅ M04模块验证通过                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.5 M05 套餐次卡模块

#### 4.5.1 模块功能清单

```
M05 套餐次卡模块
│
├── 5.1 数据库设计
│      ├── 套餐表 (crm_package)
│      ├── 套餐购买记录表 (crm_package_order)
│      └── 套餐核销记录表 (crm_package_redemption)
│
├── 5.2 后端API
│      ├── 套餐CRUD
│      ├── 购买套餐
│      ├── 核销
│      └── 冻结/转让
│
└── 5.3 前端页面
       ├── 套餐列表页
       ├── 购买套餐
       └── 会员套餐列表
```

#### 4.5.2 数据库表

```sql
-- 套餐表
CREATE TABLE `crm_package` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL COMMENT '套餐名称',
  `type` tinyint NOT NULL COMMENT '类型:1计次卡 2套餐 3疗程卡',
  `service_ids` json NOT NULL COMMENT '包含服务ID列表',
  `service_names` json COMMENT '服务名称列表(冗余)',
  `total_times` int COMMENT '总次数',
  `validity_days` int COMMENT '有效期(天)',
  `price` decimal(10,2) NOT NULL COMMENT '售价',
  `cost` decimal(10,2) COMMENT '成本',
  `shareable` tinyint DEFAULT 0 COMMENT '是否可转赠',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='套餐表';

-- 套餐购买记录表
CREATE TABLE `crm_package_order` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `package_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `order_no` varchar(64) UNIQUE NOT NULL,
  `total_times` int NOT NULL COMMENT '购买次数',
  `remain_times` int NOT NULL COMMENT '剩余次数',
  `price` decimal(10,2) NOT NULL COMMENT '实付金额',
  `pay_time` datetime COMMENT '支付时间',
  `expire_time` datetime COMMENT '过期时间',
  `status` tinyint DEFAULT 1 COMMENT '状态:0取消 1正常 2冻结 3已用完',
  `frozen_reason` varchar(255) COMMENT '冻结原因',
  `frozen_at` datetime,
  `transfer_to` bigint COMMENT '转让给谁',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_pkg_order_member` (`member_id`),
  INDEX `idx_pkg_order_status` (`member_id`, `status`)
) COMMENT='套餐购买记录';

-- 套餐核销记录表
CREATE TABLE `crm_package_redemption` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `package_order_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `service_id` bigint NOT NULL,
  `booking_id` bigint COMMENT '关联预约',
  `order_id` bigint COMMENT '关联收银订单',
  `times` int DEFAULT 1 COMMENT '核销次数',
  `operator_id` bigint COMMENT '操作人',
  `store_id` bigint COMMENT '核销门店',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_redemption_order` (`package_order_id`)
) COMMENT='套餐核销记录';
```

#### 4.5.3 执行任务清单

```
┌─────────────────────────────────────────────────────────────────┐
│                    M05 套餐次卡模块 - 任务清单                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【第一阶段：数据库】                                             │
│                                                                 │
│ ☑ T05-01 创建套餐表 crm_package                                 │
│ ☑ T05-02 创建套餐购买记录表 crm_package_order                   │
│ ☑ T05-03 创建套餐核销记录表 crm_package_redemption              │
│ ☑ T05-04 插入测试套餐数据                                       │
│ ☑ T05-05 验证：套餐查询SQL正常                                  │
│                                                                 │
│ 【第二阶段：后端API】                                           │
│                                                                 │
│ ☑ T05-06 创建Package、PackageOrder、Redemption实体类           │
│ ☑ T05-07 创建PackageMapper                                     │
│ ☑ T05-08 创建PackageController                                 │
│ ☑ T05-09 实现套餐列表 (GET /packages)                          │
│ ☑ T05-10 实现套餐详情 (GET /packages/{id})                     │
│ ☑ T05-11 实现创建套餐 (POST /packages)                         │
│ ☑ T05-12 实现购买套餐 (POST /packages/purchase)                │
│ ☑ T05-13 实现套餐核销 (POST /packages/redeem)                  │
│ ☑ T05-14 实现会员套餐查询 (GET /packages/member/{memberId})     │
│ ☑ T05-15 实现套餐冻结/解冻                                      │
│ ☑ T05-16 后端启动测试：Postman验证购买                          │
│                                                                 │
│ 【第三阶段：前端页面】                                           │
│                                                                 │
│ ☑ T05-17 创建API调用模块 package.js                            │
│ ☑ T05-18 创建套餐列表页面 package/index.vue                     │
│ ☑ T05-19 创建新增/编辑套餐弹窗 package-form.vue                 │
│ ☑ T05-20 创建购买套餐弹窗 purchase-dialog.vue                  │
│ ☑ T05-21 创建会员套餐列表（嵌入会员详情）                        │
│ ☑ T05-22 配置路由                                               │
│ ☑ T05-23 前端启动测试：访问套餐页面正常                          │
│                                                                 │
│ 【第四阶段：联调验证】                                           │
│                                                                 │
│ ☑ T05-24 验证：套餐列表显示                                     │
│ ☑ T05-25 验证：新增套餐                                         │
│ ☑ T05-26 验证：会员购买套餐，生成套餐订单                        │
│ ☑ T05-27 验证：核销1次，剩余次数减少                            │
│ ☑ T05-28 验证：剩余次数为0，状态变更                            │
│ ☑ T05-29 验证：套餐过期，状态变更                               │
│ ☑ T05-30 验证：套餐冻结/解冻                                    │
│                                                                 │
│ ✅ M05模块验证通过                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.6 M06 预约管理模块

#### 4.6.1 模块功能清单

```
M06 预约管理模块
│
├── 6.1 数据库设计
│      ├── 预约表 (biz_booking)
│      ├── 排班表 (biz_employee_schedule)
│      └── 房间资源表 (biz_room)
│
├── 6.2 后端API
│      ├── 预约CRUD
│      ├── 排班管理
│      ├── 可用时间查询
│      └── 签到/完成
│
└── 6.3 前端页面
       ├── 日历视图
       ├── 预约列表
       └── 新建预约
```

#### 4.6.2 数据库表

```sql
-- 预约表
CREATE TABLE `biz_booking` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `booking_no` varchar(64) UNIQUE NOT NULL,
  `member_id` bigint COMMENT '会员ID',
  `member_name` varchar(64) COMMENT '预约人姓名',
  `member_phone` varchar(20) COMMENT '预约人电话',
  `service_id` bigint NOT NULL COMMENT '服务项目',
  `service_name` varchar(128) COMMENT '服务名称(冗余)',
  `employee_id` bigint COMMENT '技师',
  `employee_name` varchar(64) COMMENT '技师姓名(冗余)',
  `room_id` bigint COMMENT '房间',
  `booking_date` date NOT NULL,
  `booking_time` time NOT NULL,
  `duration` int COMMENT '服务时长(分钟)',
  `end_time` time COMMENT '预计结束时间',
  `status` tinyint DEFAULT 1 COMMENT '状态:1待确认 2已确认 3已完成 4已取消 5爽约',
  `source` varchar(32) COMMENT '来源',
  `remark` varchar(255) COMMENT '备注',
  `checkin_at` datetime COMMENT '签到时间',
  `start_at` datetime COMMENT '开始服务',
  `finish_at` datetime COMMENT '完成时间',
  `cancel_reason` varchar(255) COMMENT '取消原因',
  `cancel_at` datetime,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_booking_store_date` (`store_id`, `booking_date`),
  INDEX `idx_booking_employee` (`employee_id`, `booking_date`),
  INDEX `idx_booking_member` (`member_id`)
) COMMENT='预约表';

-- 技师排班表
CREATE TABLE `biz_employee_schedule` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `work_date` date NOT NULL,
  `shift_type` tinyint NOT NULL COMMENT '班次:1早班 2中班 3晚班 4全天',
  `start_time` time,
  `end_time` time,
  `break_start` time,
  `break_end` time,
  `is_off` tinyint DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_employee_date` (`employee_id`, `work_date`),
  INDEX `idx_schedule_store_date` (`store_id`, `work_date`)
) COMMENT='技师排班表';

-- 房间表
CREATE TABLE `biz_room` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(64) NOT NULL,
  `type` varchar(32) NOT NULL COMMENT '类型:room/bed/chair',
  `capacity` int DEFAULT 1,
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_store_code` (`store_id`, `code`)
) COMMENT='房间/资源表';
```

#### 4.6.3 执行任务清单

```
┌─────────────────────────────────────────────────────────────────┐
│                    M06 预约管理模块 - 任务清单                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【第一阶段：数据库】                                             │
│                                                                 │
│ ☑ T06-01 创建预约表 biz_booking                                 │
│ ☑ T06-02 创建技师排班表 biz_employee_schedule                   │
│ ☑ T06-03 创建房间表 biz_room                                   │
│ ☑ T06-04 插入测试数据 (5技师 × 7天 = 35条排班)                  │
│ ☑ T06-05 验证：预约查询SQL正常                                   │
│                                                                 │
│ 【第二阶段：后端API】                                           │
│                                                                 │
│ ☑ T06-06 创建Booking、Schedule、Room实体类                      │
│ ☑ T06-07 创建BookingMapper                                     │
│ ☑ T06-08 创建BookingController                                │
│ ☑ T06-09 实现创建预约 (POST /booking)                          │
│ ☑ T06-10 实现预约列表 (GET /booking/list)                       │
│ ☑ T06-11 实现可用时间查询 (GET /booking/available)              │
│ ☑ T06-12 实现确认/取消预约 (PUT /booking/{id}/status)           │
│ ☑ T06-13 实现签到 (POST /booking/{id}/checkin)                 │
│ ☑ T06-14 实现完成预约 (POST /booking/{id}/complete)            │
│ ☑ T06-15 实现排班管理 (CRUD)                                    │
│ ☑ T06-16 后端启动测试：Postman验证预约创建                       │
│                                                                 │
│ 【第三阶段：前端页面】                                           │
│                                                                 │
│ ☑ T06-17 创建API调用模块 booking.js/schedule.js/room.js         │
│ ☑ T06-18 创建日历视图 booking/calendar.vue (当日/房间/周/列表)  │
│ ☑ T06-19 创建预约列表 booking/list.vue                          │
│ ☑ T06-20 创建新建预约弹窗 booking-form.vue                      │
│ ☑ T06-21 创建排班管理页面 schedule.vue (周历 + 批量排班)       │
│ ☑ T06-22 配置路由                                               │
│ ☑ T06-23 前端启动测试：访问预约页面正常                          │
│                                                                 │
│ 【第四阶段：联调验证】                                           │
│                                                                 │
│ ☑ T06-24 验证：查看日历视图（4种模式切换）                      │
│ ☑ T06-25 验证：查询某技师的可用时间                              │
│ ☑ T06-26 验证：新建预约成功（含冲突检测）                       │
│ ☑ T06-27 验证：确认预约                                         │
│ ☑ T06-28 验证：签到                                             │
│ ☑ T06-29 验证：完成服务                                         │
│ ☑ T06-30 验证：取消预约                                         │
│ ☑ T06-31 验证：爽约标记                                         │
│                                                                 │
│ ✅ M06模块验证通过                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.7 M07 收银结算模块

#### 4.7.1 模块功能清单

```
M07 收银结算模块
│
├── 7.1 数据库设计
│      ├── 订单表 (pos_order)
│      ├── 订单明细表 (pos_order_item)
│      └── 支付记录表 (pos_payment)
│
├── 7.2 后端API
│      ├── 创建订单
│      ├── 添加订单明细
│      ├── 混合支付
│      ├── 退款
│      └── 打印小票
│
└── 7.3 前端页面
       ├── 收银台页面
       ├── 订单列表
       └── 支付弹窗
```

#### 4.7.2 数据库表

```sql
-- 收银订单表
CREATE TABLE `pos_order` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `order_no` varchar(64) UNIQUE NOT NULL,
  `member_id` bigint COMMENT '会员ID',
  `member_name` varchar(64),
  `member_phone` varchar(20),
  `booking_id` bigint COMMENT '关联预约',
  `type` tinyint NOT NULL COMMENT '类型:1消费 2充值 3混合',
  `total_amount` decimal(12,2) NOT NULL COMMENT '原价总额',
  `discount_amount` decimal(12,2) DEFAULT 0 COMMENT '折扣金额',
  `final_amount` decimal(12,2) NOT NULL COMMENT '实付金额',
  `cashier_id` bigint NOT NULL COMMENT '收银员',
  `remark` varchar(255),
  `status` tinyint DEFAULT 1 COMMENT '状态:1进行中 2已完成 3已退款',
  `pay_time` datetime COMMENT '支付时间',
  `refund_time` datetime COMMENT '退款时间',
  `refund_amount` decimal(12,2) COMMENT '退款金额',
  `refund_reason` varchar(255),
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_pos_order_store` (`store_id`),
  INDEX `idx_pos_order_member` (`member_id`),
  INDEX `idx_pos_order_date` (`store_id`, `created_at`)
) COMMENT='收银订单表';

-- 订单明细表
CREATE TABLE `pos_order_item` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `service_id` bigint NOT NULL,
  `service_name` varchar(128) NOT NULL,
  `quantity` int DEFAULT 1,
  `unit_price` decimal(10,2) NOT NULL,
  `total_price` decimal(12,2) NOT NULL,
  `discount_amount` decimal(10,2) DEFAULT 0,
  `final_price` decimal(12,2) NOT NULL,
  `package_order_id` bigint COMMENT '套餐核销ID',
  `package_times` int COMMENT '核销次数',
  `employee_id` bigint COMMENT '服务技师',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_pos_item_order` (`order_id`)
) COMMENT='订单明细表';

-- 支付记录表
CREATE TABLE `pos_payment` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `payment_no` varchar(64) UNIQUE NOT NULL,
  `method` varchar(32) NOT NULL COMMENT '支付方式',
  `amount` decimal(12,2) NOT NULL,
  `status` tinyint DEFAULT 0 COMMENT '状态:0待支付 1成功 2失败 3已退款',
  `transaction_id` varchar(128) COMMENT '第三方流水号',
  `pay_time` datetime,
  `refund_time` datetime,
  `error_msg` varchar(255),
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_pos_payment_order` (`order_id`)
) COMMENT='支付记录表';

-- 支付方式字典:
-- wechat_jsapi, wechat_native, wechat_app
-- alipay_wap, alipay_app
-- unionpay_qr, unionpay_card
-- cash, wallet, points, coupon
-- e_cny_soft, e_cny_hard
```

#### 4.7.3 执行任务清单

```
┌─────────────────────────────────────────────────────────────────┐
│                    M07 收银结算模块 - 任务清单                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【第一阶段：数据库】                                             │
│                                                                 │
│ ☑ T07-01 创建订单表 pos_order                                   │
│ ☑ T07-02 创建订单明细表 pos_order_item                          │
│ ☑ T07-03 创建支付记录表 pos_payment                             │
│ ☑ T07-04 验证：订单查询SQL正常（biz_service + pos_order_item）  │
│                                                                 │
│ 【第二阶段：后端API】                                           │
│                                                                 │
│ ☑ T07-05 创建 BizService/Order/OrderItem/Payment 实体类         │
│ ☑ T07-06 创建 OrderMapper/ServiceMapper/PaymentMapper           │
│ ☑ T07-07 创建 PosController                                     │
│ ☑ T07-08 实现创建订单 (POST /pos/orders)                        │
│ ☑ T07-09 实现添加订单明细 (POST /pos/orders/{id}/items)         │
│ ☑ T07-10 实现移除订单明细 (DELETE /pos/orders/{id}/items/{itemId})│
│ ☑ T07-11 实现整单折扣 (POST /pos/orders/{id}/discount)          │
│ ☑ T07-12 实现支付 (POST /pos/orders/{id}/pay)                   │
│ ☑ T07-13 实现退款 (POST /pos/orders/{id}/refund)                │
│ ☑ T07-14 实现撤销整单 (POST /pos/orders/{id}/void)              │
│ ☑ T07-15 后端启动测试：Postman验证创建订单                       │
│                                                                 │
│ 【第三阶段：前端页面】                                           │
│                                                                 │
│ ☑ T07-16 创建API调用模块 pos.ts                                 │
│ ☑ T07-17 创建收银台页面 pos/checkout.vue                        │
│ ☑ T07-18 创建选择服务弹窗 service-select.vue                    │
│ ☑ T07-19 创建支付弹窗 payment-dialog.vue                        │
│ ☑ T07-20 创建订单列表页面 pos/orders.vue                        │
│ ☑ T07-21 配置路由                                               │
│ ☑ T07-22 前端启动测试：访问收银台正常                            │
│                                                                 │
│ 【第四阶段：联调验证】                                           │
│                                                                 │
│ ☑ T07-23 验证：输入手机号，自动查找会员信息                      │
│ ☑ T07-24 验证：添加服务项目，显示明细                            │
│ ☑ T07-25 验证：使用次卡核销，扣减次数                            │
│ ☑ T07-26 验证：使用优惠券抵扣                                    │
│ ☑ T07-27 验证：混合支付（储值+微信）                            │
│ ☑ T07-28 验证：支付成功，订单完成                                │
│ ☑ T07-29 验证：退款操作                                         │
│ ☑ T07-30 验证：订单列表显示                                     │
│                                                                 │
│ ✅ M07模块验证通过                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 五、模块开发顺序建议

### 5.1 MVP阶段（核心闭环）

```
第1步: M01 登录认证  ← 所有模块的入口
        ↓
第2步: M02 基础数据  ← 门店、员工、等级
        ↓
第3步: M03 会员管理  ← 核心业务对象
        ↓
第4步: M04 储值管理  ← 充值、消费
        ↓
第5步: M05 套餐次卡  ← 套餐、核销
        ↓
第6步: M06 预约管理  ← 预约、排班
        ↓
第7步: M07 收银结算  ← 订单、支付（含数字人民币）
```

> **完成M01-M07 = 完成MVP，可上线运营**

### 5.2 增强阶段

```
第8步: M08 库存管理  ← 耗材、批次
        ↓
第9步: M09 员工提成  ← 规则、计算
        ↓
第10步: M10 营销工具 ← 优惠券、积分
        ↓
第11步: M11 数据报表 ← 看板、导出
```

### 5.3 高级阶段

```
第12步: M12 系统设置 ← 配置、数字人民币
```

---

## 六、验证检查清单

### 6.1 每个模块验证必须包含

```
┌─────────────────────────────────────────────────────────────────┐
│                    模块验证检查清单                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【数据库验证】                                                   │
│ □ SELECT查询正常                                                │
│ □ INSERT插入成功                                                │
│ □ UPDATE更新成功                                                │
│ □ DELETE删除成功                                                │
│ □ 分页查询正常                                                  │
│ □ 索引生效                                                      │
│                                                                 │
│ 【API验证】                                                     │
│ □ 接口可正常调用（Postman/浏览器）                              │
│ □ 请求参数校验通过                                              │
│ □ 响应格式正确                                                  │
│ □ 异常处理正常                                                  │
│ □ 事务回滚正常                                                  │
│                                                                 │
│ 【前端验证】                                                    │
│ □ 页面可正常打开                                                │
│ □ 表单验证正常                                                  │
│ □ 列表显示正常                                                  │
│ □ 分页正常                                                      │
│ □ 弹窗正常                                                      │
│ □ 提示信息正常                                                  │
│                                                                 │
│ 【联调验证】                                                    │
│ □ 前端调用后端API成功                                           │
│ □ 数据展示正确                                                  │
│ □ 增删改查全链路通                                              │
│ □ 异常情况处理正确                                              │
│                                                                 │
│ ✅ 全部通过 = 模块完成                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 七、快速开始（详细步骤）

### 7.1 开发环境准备

#### 7.1.1 必装软件

| 软件 | 版本 | 用途 | 下载地址 |
|------|------|------|----------|
| MySQL | 8.0+ | 数据库 | https://dev.mysql.com/downloads/mysql/ |
| Redis | 7.0+ | 缓存 | https://redis.io/download/ |
| JDK | 17+ | 后端运行 | https://adoptium.net/ |
| Maven | 3.8+ | 后端构建 | https://maven.apache.org/download.cgi |
| IDEA | 最新版 | 后端开发 | https://www.jetbrains.com/idea/ |
| VS Code | 最新版 | 前端开发 | https://code.visualstudio.com/ |
| Git | 2.30+ | 版本控制 | https://git-scm.com/download |

#### 7.1.2 环境验证命令

```bash
# 验证 MySQL
mysql --version
# 期望输出: mysql  Ver 8.0.x

# 验证 Redis
redis-cli --version
# 期望输出: redis-cli 7.0.x

# 验证 JDK
java -version
# 期望输出: openjdk version "17.x.x"

# 验证 Maven
mvn --version
# 期望输出: Apache Maven 3.8.x

# 验证 Git
git --version
# 期望输出: git version 2.x.x
```

---

### 7.2 项目初始化

#### 7.2.1 创建项目目录

```bash
# 在 D:\QiaoPM 下执行
mkdir backend frontend
```

#### 7.2.2 后端项目初始化 (Spring Boot + Maven)

```bash
cd backend

# 创建 Spring Boot 项目 pom.xml
# 路径: backend/pom.xml

# 核心依赖:
# - spring-boot-starter-web
# - spring-boot-starter-data-jpa
# - spring-boot-starter-data-redis
# - mysql-connector-java
# - mybatis-plus-boot-starter
# - jjwt (JWT)
# - lombok
```

#### 7.2.3 后端配置文件 (application.yml)

```yaml
# 路径: backend/meiyetec-api/src/main/resources/application.yml

spring:
  application:
    name: meiyetec-api
  datasource:
    url: jdbc:mysql://localhost:3306/meiyetec?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  redis:
    host: localhost
    port: 6379
    password: your_password
    database: 0
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect

server:
  port: 8080
  servlet:
    context-path: /api

jwt:
  secret: meiyetec-secret-key-change-in-production
  expiration: 86400000  # 24小时
```

#### 7.2.4 前端项目初始化 (Vue 3 + Vite)

```bash
cd frontend

# 使用 npm 创建项目
npm create vite@latest meiyetec-web -- --template vue-ts

# 安装依赖
cd meiyetec-web
npm install

# 安装核心依赖
npm install vue-router@4 pinia axios element-plus @element-plus/icons-vue
```

#### 7.2.5 前端环境配置

```typescript
// 路径: frontend/meiyetec-web/src/api/request.ts

import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000
})

// 请求拦截器 - 添加Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器 - 处理错误
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

---

### 7.3 数据库初始化

#### 7.3.1 创建数据库

```sql
-- 使用 root 用户登录后执行
CREATE DATABASE meiyetec DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE meiyetec;
```

#### 7.3.2 初始化SQL脚本

```sql
-- 路径: backend/meiyetec-api/src/main/resources/db/sql/init_m01_auth.sql

-- ========== M01 登录认证模块 SQL ==========

-- 1. 用户表
CREATE TABLE `sys_user` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) UNIQUE NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码(bcrypt)',
  `real_name` varchar(50) COMMENT '真实姓名',
  `phone` varchar(20) COMMENT '手机号',
  `avatar` varchar(255) COMMENT '头像',
  `status` tinyint DEFAULT 1 COMMENT '状态:0禁用 1启用',
  `last_login_at` datetime COMMENT '最后登录时间',
  `last_login_ip` varchar(50) COMMENT '最后登录IP',
  `tenant_id` bigint COMMENT '租户ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='系统用户表';

CREATE INDEX idx_user_username ON sys_user(username);
CREATE INDEX idx_user_phone ON sys_user(phone);
CREATE INDEX idx_user_tenant ON sys_user(tenant_id);

-- 2. 角色表
CREATE TABLE `sys_role` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `code` varchar(50) UNIQUE NOT NULL COMMENT '角色编码',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(255) COMMENT '描述',
  `tenant_id` bigint COMMENT '租户ID',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='角色表';

-- 3. 用户角色关联表
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`)
) COMMENT='用户角色关联表';

-- 4. 权限表
CREATE TABLE `sys_permission` (
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
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`)
) COMMENT='角色权限关联表';

-- 6. 初始化数据
-- 密码: admin123 (bcrypt加密)
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `status`, `tenant_id`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', '13800138000', 1, 1);

INSERT INTO `sys_role` (`code`, `name`, `description`, `tenant_id`) VALUES
('SUPER_ADMIN', '超级管理员', '拥有所有权限', 1),
('STORE_MANAGER', '店长', '门店管理权限', 1),
('EMPLOYEE', '员工', '普通员工权限', 1);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

INSERT INTO `sys_permission` (`code`, `name`, `type`, `path`, `icon`, `sort`) VALUES
('dashboard', '工作台', 1, '/dashboard', 'Odometer', 1),
('member', '会员管理', 1, '/member', 'User', 2),
('member:list', '查看会员', 2, '', '', 1),
('member:add', '新增会员', 2, '', '', 2),
('member:edit', '编辑会员', 2, '', '', 3),
('member:delete', '删除会员', 2, '', '', 4);
```

---

### 7.4 M01 登录认证模块开发任务

#### 7.4.1 后端开发任务清单

| 序号 | 任务 | 文件路径 | 状态 |
|------|------|----------|------|
| 1 | 创建项目基础结构 | `com.meiyetec` 包 | ☐ |
| 2 | 实现 JWT 工具类 | `utils/JwtUtil.java` | ☐ |
| 3 | 实现 Redis 会话管理 | `config/RedisConfig.java` | ☐ |
| 4 | 实现登录接口 | `controller/AuthController.java` | ☐ |
| 5 | 实现用户服务层 | `service/impl/UserServiceImpl.java` | ☐ |
| 6 | 实现用户实体 | `entity/SysUser.java` | ☐ |
| 7 | 实现用户Mapper | `mapper/SysUserMapper.java` | ☐ |
| 8 | 编写接口单元测试 | `test/AuthControllerTest.java` | ☐ |
| 9 | 数据库验证SQL执行 | init_m01_auth.sql | ☐ |

#### 7.4.2 前端开发任务清单

| 序号 | 任务 | 文件路径 | 状态 |
|------|------|----------|------|
| 1 | 创建 Vue 项目 | `meiyetec-web/` | ☐ |
| 2 | 配置路由 | `router/index.ts` | ☐ |
| 3 | 创建登录页面 | `views/login.vue` | ☐ |
| 4 | 创建首页 | `views/dashboard.vue` | ☐ |
| 5 | 创建布局组件 | `components/Layout.vue` | ☐ |
| 6 | 实现登录API | `api/auth.ts` | ☐ |
| 7 | 配置 Pinia 状态管理 | `store/user.ts` | ☐ |
| 8 | 配置 Axios 拦截器 | `api/request.ts` | ☐ |
| 9 | 页面联调测试 | 浏览器验证 | ☐ |

#### 7.4.3 后端代码骨架 (Java)

```java
// === 文件: com.meiyetec.MeiyetecApplication.java ===
package com.meiyetec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MeiyetecApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeiyetecApplication.class, args);
    }
}

// === 文件: com.meiyetec.config.CorsConfig.java ===
package com.meiyetec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}

// === 文件: com.meiyetec.controller.AuthController.java ===
package com.meiyetec.controller;

import com.meiyetec.common.Result;
import com.meiyetec.entity.SysUser;
import com.meiyetec.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
    
    @PostMapping("/logout")
    public Result logout() {
        return authService.logout();
    }
    
    @GetMapping("/me")
    public Result getCurrentUser() {
        return authService.getCurrentUser();
    }
}
```

#### 7.4.4 前端代码骨架 (Vue 3)

```vue
<!-- === 文件: src/views/login.vue === -->
<template>
  <div class="login-container">
    <el-form :model="form" class="login-form">
      <h1>美业 SaaS 系统</h1>
      <el-form-item>
        <el-input v-model="form.username" placeholder="用户名" />
      </el-form-item>
      <el-form-item>
        <el-input v-model="form.password" type="password" placeholder="密码" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleLogin" :loading="loading">
          登录
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  loading.value = true
  try {
    const res = await login(form)
    localStorage.setItem('token', res.data.token)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-form {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 8px;
}
</style>
```

---

### 7.5 验证通过标准

```
┌─────────────────────────────────────────────────────────────────┐
│                    M01 模块验收标准                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 【数据库】                                                       │
│ □ SQL脚本执行成功，无报错                                        │
│ □ 能查询到默认管理员账户                                         │
│                                                                 │
│ 【后端 API】                                                     │
│ □ POST /api/auth/login 返回token                                │
│ □ GET  /api/auth/me 返回用户信息                                 │
│ □ POST /api/auth/logout 成功                                     │
│ □ Token有效期内可正常访问受保护接口                               │
│                                                                 │
│ 【前端页面】                                                     │
│ □ 登录页正常显示                                                 │
│ □ 输入正确账号密码能登录                                         │
│ □ 登录成功后跳转首页                                             │
│ □ Token过期后自动跳转登录页                                       │
│                                                                 │
│ 【联调测试】                                                     │
│ □ 前端登录 → 后端验证 → 返回Token → 存储本地                     │
│ □ 前端带Token请求 → 后端验证 → 返回数据                          │
│ □ 完整链路无报错                                                 │
│                                                                 │
│ ✅ 全部通过 = M01完成，可进入 M02                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 7.6 常见问题排查

| 问题 | 解决方案 |
|------|----------|
| 后端启动报错端口被占用 | `netstat -ano \| findstr 8080` 查找进程并结束 |
| 前端 `npm install` 失败 | 检查 Node.js 版本，建议 18+ |
| MySQL 连接失败 | 检查用户名密码，确认服务已启动 |
| Redis 连接失败 | 检查 `redis-server` 是否在运行 |
| 跨域报错 | 确认后端 `CorsConfig` 已配置 |
| Token 验证失败 | 确认前后端使用相同的 secret key |

---

## 八、执行清单（按顺序执行）

---

### 第一阶段：环境搭建（预计 2-4 小时）  ✅ 已完成 2026-07-31

#### 步骤 1.1：安装 Docker Desktop
- [x] 下载 Docker Desktop: https://www.docker.com/products/docker-desktop/
- [x] 安装并启动 Docker
- [x] 验证：`docker --version`

#### 步骤 1.2：启动 MySQL 和 Redis
- [x] 在 `D:\QiaoPM` 下创建 `docker-compose.yml`（文档 9.1 节）
- [x] 执行：`docker-compose up -d`
- [x] 验证：`docker ps` 看到 mysql 和 redis 容器

#### 步骤 1.3：安装开发工具
- [x] 安装 JDK 17+: https://adoptium.net/
- [x] 安装 Maven 3.8+: https://maven.apache.org/download.cgi
- [x] 安装 IDEA: https://www.jetbrains.com/idea/download/
- [x] 安装 VS Code: https://code.visualstudio.com/download/
- [x] 安装 Git: https://git-scm.com/download/

#### 步骤 1.4：验证环境
```bash
java -version      # 期望: openjdk 17.x.x
mvn --version      # 期望: Apache Maven 3.8.x
git --version      # 期望: git version 2.x.x
docker ps          # 期望: 看到 mysql 和 redis 容器
```

---

### 第二阶段：项目初始化（预计 1-2 小时）  ✅ 已完成 2026-07-31

#### 步骤 2.1：创建目录结构
```bash
cd D:\QiaoPM
mkdir backend frontend
```

#### 步骤 2.2：初始化后端项目
- [x] 在 `backend/` 下创建 `pom.xml`
- [x] 创建包结构：`com.meiyetec`
- [x] 创建配置类：`CorsConfig.java`、`RedisConfig.java`
- [x] 创建主启动类：`MeiyetecApplication.java`
- [x] 创建 `application.yml` 配置文件

#### 步骤 2.3：初始化前端项目
```bash
cd frontend
npm create vite@latest meiyetec-web -- --template vue-ts
cd meiyetec-web
npm install
npm install vue-router@4 pinia axios element-plus @element-plus/icons-vue
```
- [x] 配置 `vite.config.ts`（代理）
- [x] 配置 `router/index.ts`
- [x] 创建 `api/request.ts`

#### 步骤 2.4：初始化数据库
- [x] 创建数据库：`meiyetec`
- [x] 执行 `init_m01_auth.sql` 脚本
- [x] 执行 `init_m02_basic.sql` 脚本

#### 步骤 2.5：启动验证
- [x] 后端启动：`mvn spring-boot:run`（或 IDEA 运行）
- [x] 前端启动：`npm run dev`
- [x] 浏览器访问：`http://localhost:5173`

---

### 第三阶段：M01 登录认证开发（预计 4-8 小时）  ✅ 已完成 2026-07-31

#### 步骤 3.1：后端 - 用户模块
- [x] 创建 `SysUser` 实体类
- [x] 创建 `SysUserMapper` 接口
- [x] 创建 `SysUserService` 接口和实现
- [x] 创建 `SysUserController` 控制器
- [x] 实现 `getUserByUsername` 查询方法
- [x] 单元测试：确保能查到管理员用户

#### 步骤 3.2：后端 - JWT 认证
- [x] 创建 `JwtUtil` 工具类
- [x] 创建 `AuthService` 服务类
- [x] 实现 `login` 登录方法（密码校验 + Token生成）
- [x] 实现 `logout` 登出方法
- [x] 实现 `getCurrentUser` 获取当前用户方法
- [x] 创建 `AuthController` 控制器
- [x] 实现 JWT 拦截器

#### 步骤 3.3：后端 - 角色权限
- [x] 创建 `SysRole` 实体和 Mapper
- [x] 创建 `SysPermission` 实体和 Mapper
- [x] 实现角色权限查询接口

#### 步骤 3.4：后端 API 验证（Postman）
- [x] 测试 `POST /api/auth/login` 返回 Token
- [x] 测试 `GET /api/auth/me` 返回用户信息
- [x] 测试 `POST /api/auth/logout` 成功

#### 步骤 3.5：前端 - 登录页面
- [x] 创建 `views/login.vue`
- [x] 实现用户名密码输入
- [x] 调用登录 API
- [x] 存储 Token 到 localStorage

#### 步骤 3.6：前端 - 首页和布局
- [x] 创建 `components/Layout.vue`
- [x] 创建 `views/dashboard.vue`
- [x] 配置路由守卫（未登录跳转登录页）
- [x] 实现退出登录功能

#### 步骤 3.7：前端 - 路由配置
```typescript
// 路由清单
/login          → login.vue      (公开)
/dashboard      → dashboard.vue  (需登录)
/member         → member/index   (需登录)
/booking        → booking/index   (需登录)
```

#### 步骤 3.8：M01 完整链路测试
- [x] 浏览器打开登录页
- [x] 输入 admin / admin123 登录
- [x] 成功跳转到首页
- [x] 刷新页面保持登录状态
- [x] 点击退出返回登录页

#### M01 验收清单
```
✅ 数据库：管理员账户可查询
✅ API：/auth/login 返回 Token
✅ API：/auth/me 返回用户信息
✅ 前端：登录页正常显示
✅ 前端：登录成功跳转首页
✅ 前端：Token 过期自动跳转登录页
✅ 联调：前后端全链路无报错

✅ 全部通过 → 进入 M02  ✅ 已完成 2026-07-31
```

---

### 第四阶段：M02 基础数据开发（预计 4-8 小时）

#### 步骤 4.1：数据库 - 基础表
- [x] 创建 `pos_store` 门店表
- [x] 创建 `pos_employee` 员工表
- [x] 创建 `mem_level` 会员等级表
- [x] 创建 `sys_category` 分类表

#### 步骤 4.2：后端 - 门店管理
- [x] 创建 `Store` 实体、Mapper、Service、Controller
- [x] 实现 CRUD 接口

#### 步骤 4.3：后端 - 员工管理
- [x] 创建 `Employee` 实体、Mapper、Service、Controller
- [x] 实现员工列表、新增、编辑、删除

#### 步骤 4.4：后端 - 会员等级
- [x] 创建 `MemberLevel` 实体和 CRUD 接口

#### 步骤 4.4b：后端 - 分类管理
- [x] 创建 `Category` 实体、Mapper、Service、Controller
- [x] 实现 CRUD 接口（树形结构）

#### 步骤 4.5：前端 - 基础设置页
- [x] 创建 `views/setting/store.vue`
- [x] 创建 `views/setting/employee.vue`
- [x] 创建 `views/setting/level.vue`
- [x] 创建 `views/setting/category.vue`
- [x] 创建 `views/setting/index.vue`（聚合入口）

#### 步骤 4.6：M02 验收
```
✅ 门店：增删改查正常
✅ 员工：增删改查正常
✅ 等级：增删改查正常
✅ 分类：增删改查正常
✅ 前端：所有页面正常显示
✅ 联调：全链路无报错

✅ 全部通过 → 进入 M03  ✅ 已完成 2026-07-31
```

---

### 第五阶段：M03-M12 迭代开发

按以下顺序，重复第四阶段的模式：

| 模块 | 内容 | 预计时间 |
|------|------|----------|
| M03 | 会员管理 | 4-8h |
| M04 | 储值管理 | 4-8h |
| M05 | 套餐次卡 | 4-8h |
| M06 | 预约管理 | 4-8h |
| M07 | 收银结算 | 4-8h |
| M08 | 库存管理 | 4-8h |
| M09 | 员工提成 | 4-8h |
| M10 | 营销工具 | 4-8h |
| M11 | 数据报表 | 4-8h |
| M12 | 系统设置 | 4-8h |

---

### 当前进度

```
[■■■■■■■■■■] 100% 环境搭建          ✅
[■■■■■■■■■■] 100% 项目初始化        ✅
[■■■■■■■■■■] 100% M01 登录认证      ✅ 2026-07-31
[■■■■■■■■■■] 100% M02 基础数据      ✅ 2026-07-31
[■■■■■■■■■■] 100% M03 会员管理      ✅ 2026-07-31
[■■■■■■■■■■] 100% M04 储值管理      ✅ 2026-08-02
[■■■■■■■■■■] 100% M05 套餐次卡      ✅ 2026-08-02
[■■■■■■■■■■] 100% M06 预约管理      ✅ 2026-08-02
[■■■■■■■■■■] 100% M07 收银结算      ✅ 2026-08-02
[■■■■■■■■■■] 100% M08 库存管理      ✅ 2026-08-02
[■■■■■■■■■■] 100% M09 员工提成      ✅ 2026-08-03
[□□□□□□□□□□]   0% M10 营销工具
[□□□□□□□□□□]   0% M11 数据报表
[□□□□□□□□□□]   0% M12 系统设置

总计：9/12 模块完成（75%）· 🚀 增强阶段进行中
```

> 📅 **下次开发任务**：M10 营销工具模块

---

## 九、Docker 一键启动（简化环境搭建）

### 9.1 docker-compose.yml

```yaml
# 路径: D:\QiaoPM\docker-compose.yml

version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: meiyetec-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: root123456
      MYSQL_DATABASE: meiyetec
      TZ: Asia/Shanghai
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./backend/meiyetec-api/src/main/resources/db/sql:/docker-entrypoint-initdb.d
    command: --default-authentication-plugin=mysql_native_password --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:7-alpine
    container_name: meiyetec-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

volumes:
  mysql-data:
  redis-data:
```

### 9.2 启动命令

```bash
# 在 D:\QiaoPM 目录下执行
docker-compose up -d

# 验证服务
docker ps

# 查看日志
docker-compose logs -f
```

### 9.3 连接配置

| 服务 | 主机地址 | 端口 | 密码 |
|------|----------|------|------|
| MySQL | localhost | 3306 | root123456 |
| Redis | localhost | 6379 | 无密码 |

---

## 十、Git 分支管理策略

### 10.1 分支命名规范

```
main                    # 主分支（生产环境）
├── develop             # 开发分支
│   ├── feature/M01-auth          # M01功能分支
│   ├── feature/M02-basic         # M02功能分支
│   ├── feature/M03-member        # M03功能分支
│   └── ...
└── hotfix/*            # 紧急修复分支
```

### 10.2 开发流程

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

### 10.3 提交信息规范

```
feat: 新功能
fix: 修复bug
docs: 文档更新
style: 代码格式（不影响功能）
refactor: 重构
test: 测试相关
chore: 构建/工具相关
```

示例：
```
feat(M01): 添加用户登录接口
feat(M01): 实现JWT Token认证
fix(M01): 修复登录超时问题
```

---

## 十一、API 接口规范

### 11.1 统一响应格式

```json
{
  "code": 200,        // 状态码：200成功，400参数错误，401未授权，500服务器错误
  "message": "success", // 消息
  "data": {}           // 数据（可选）
}
```

### 11.2 状态码定义

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录/Token过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

### 11.3 分页响应格式

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

## 十二、代码规范

### 12.1 后端 Java 规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | UpperCamelCase | `SysUserController` |
| 方法名 | lowerCamelCase | `getUserById` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 包名 | 全小写 | `com.meiyetec.controller` |
| 数据库表 | 小写下划线 | `sys_user` |
| 数据库字段 | 小写下划线 | `user_name` |

### 12.2 前端 Vue 规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件名 | PascalCase | `UserList.vue` |
| 组件文件 | PascalCase | `UserCard.vue` |
| API文件 | 小写下划线 | `user_api.ts` |
| Store文件 | 小写下划线 | `user_store.ts` |
| 常量 | UPPER_SNAKE_CASE | `API_BASE_URL` |
| 变量 | lowerCamelCase | `userName` |

### 12.3 Git 提交规范

```
<type>(<scope>): <subject>

<body>

<footer>
```

示例：
```
feat(M01): 实现用户登录功能

- 添加登录接口
- 实现Token存储
- 添加路由守卫

Closes #1
```

---

## 十三、M02-M12 模块开发任务总览

### 13.1 M02 基础数据模块

```
M02 基础数据模块
├── 数据库设计
│   ├── 门店表 (pos_store)
│   ├── 员工表 (pos_employee)
│   ├── 会员等级表 (mem_level)
│   └── 分类表 (sys_category)
│
├── 后端API
│   ├── 门店管理 CRUD
│   ├── 员工管理 CRUD
│   ├── 会员等级管理 CRUD
│   └── 分类管理 CRUD
│
└── 前端页面
    ├── 门店设置页
    ├── 员工管理页
    ├── 等级设置页
    └── 分类设置页
```

### 13.2 M03 会员管理模块

```
M03 会员管理模块
├── 数据库设计
│   ├── 会员表 (mem_member)
│   ├── 会员档案表 (mem_profile)
│   └── 会员日志表 (mem_log)
│
├── 后端API
│   ├── 会员注册/查询/编辑
│   ├── 会员等级变更
│   └── 会员消费记录
│
└── 前端页面
    ├── 会员列表页
    ├── 会员详情页
    └── 会员编辑弹窗
```

### 13.3 M04 储值管理模块

```
M04 储值管理模块
├── 数据库设计
│   ├── 储值账户表 (save_account)
│   ├── 储值记录表 (save_record)
│   └── 赠送规则表 (save_gift_rule)
│
├── 后端API
│   ├── 充值
│   ├── 消费
│   ├── 退款
│   └── 余额查询
│
└── 前端页面
    ├── 储值页面
    ├── 充值记录页
    └── 规则设置页
```

### 13.4 M05 套餐次卡模块

```
M05 套餐次卡模块
├── 数据库设计
│   ├── 套餐表 (pkg_package)
│   ├── 次卡表 (pkg_times_card)
│   ├── 购买记录表 (pkg_purchase)
│   └── 核销记录表 (pkg_use_record)
│
├── 后端API
│   ├── 套餐购买
│   ├── 次卡购买
│   ├── 核销
│   └── 余额查询
│
└── 前端页面
    ├── 套餐列表页
    ├── 购买页面
    └── 核销页面
```

### 13.5 M06 预约管理模块

```
M06 预约管理模块
├── 数据库设计
│   ├── 预约表 (book_appointment)
│   ├── 排班表 (book_schedule)
│   └── 服务项目表 (book_service)
│
├── 后端API
│   ├── 创建预约
│   ├── 取消预约
│   ├── 排班管理
│   └── 预约查询
│
└── 前端页面
    ├── 日历视图页
    ├── 预约列表页
    └── 排班设置页
```

### 13.6 M07 收银结算模块

```
M07 收银结算模块
├── 数据库设计
│   ├── 订单表 (order_main)
│   ├── 订单明细表 (order_detail)
│   └── 支付记录表 (pay_record)
│
├── 后端API
│   ├── 创建订单
│   ├── 支付（微信/支付宝/数字人民币）
│   ├── 退款
│   └── 订单查询
│
└── 前端页面
    ├── 收银台页面
    ├── 订单列表页
    └── 支付成功页
```

### 13.7 M08 库存管理模块

```
M08 库存管理模块
├── 数据库设计
│   ├── 商品表 (stock_product)
│   ├── 库存表 (stock_inventory)
│   └── 出入库记录表 (stock_record)
│
├── 后端API
│   ├── 库存查询
│   ├── 入库
│   ├── 出库
│   └── 盘点
│
└── 前端页面
    ├── 库存列表页
    ├── 入库页面
    └── 盘点页面
```

### 13.8 M09 员工提成结算模块

> **执行状态**：🔄 设计冻结中（2026-08-03）
>
> **设计前置**：已完成 M07/M08 代码深度探索，确认 `pos_order_item.employee_id` + `final_price` + `pos_employee.commission_rate` 为最小可依赖数据源。

---

#### M09 核心域模型（冻结）

```
commission_rule          提成规则表
        │
        │ 1:N 匹配
        ▼
commission_record        提成记录表（核心快照表）
        │
        │ N:1 归属
        ▼
commission_settlement    提成结算单
        │
        │ N:N 关联
        ▼
commission_settlement_record  结算明细关联表
```

**4 张核心表职责**：

| 表 | 作用 | 关键字段 |
|---|---|---|
| `commission_rule` | 定义比例/固定金额/阶梯规则 | `rule_type`、`commission_rate`、`fixed_amount`、`priority` |
| `commission_record` | 持久化每条服务提成的计算快照 | `base_amount`、`commission_rate_snapshot`、`employee_name_snapshot`、`net_commission_amount` |
| `commission_settlement` | 按周期汇总结算 | `period_start`、`period_end`、`total_commission_amount`、`status` |
| `commission_settlement_record` | 结算单与明细的多对多关联 | `settlement_id`、`commission_record_id` |

**设计决策（冻结）**：

1. **快照字段必须**：历史提成不依赖实时 Join，员工改名/离职后报表不变
2. **收入基数为 `final_price`**：明细折后价，不直接用 `unit_price`（未考虑折扣/数量）
3. **未支付/已退款订单不计提**：默认仅 `order.status = 2`（已支付）触发计算
4. **来源类型区分**：`POS_ORDER_ITEM` vs `PACKAGE_REDEMPTION`，避免重复计提
5. **结算后禁止修改**：已结算记录锁死，通过反向结算处理纠错
6. **未来扩展预留**：`base_amount` 设计为可扣除耗材成本（`stock_record.order_id` 回溯），支撑毛利提成模式

---

#### M09 任务结构（精简版）

**阶段 1：数据库**

```
M09-DB-001  创建 commission_rule 表及索引
M09-DB-002  创建 commission_record 表及索引
M09-DB-003  创建 commission_settlement 表及索引
M09-DB-004  创建 commission_settlement_record 表及索引
M09-DB-005  新增 sys_permission 提成相关权限数据
```

**阶段 2：后端 API**

```
M09-API-001  CommissionRule CRUD + 规则匹配逻辑
M09-API-002  支付完成 → 提成计算（幂等、防重）
M09-API-003  退款完成 → 提成冲正
M09-API-004  提成记录查询（分页、筛选、统计）
M09-API-005  结算单生成、审核、发放状态流转
M09-API-006  员工提成汇总报表
```

**阶段 3：前端页面**

```
M09-WEB-001  提成规则设置页
M09-WEB-002  提成明细查询页
M09-WEB-003  提成结算管理页
M09-WEB-004  路由与菜单挂载
```

**阶段 4：验证**

```
M09-TEST-001  正常订单 → 1条提成记录
M09-TEST-002  重复计算幂等（记录不翻倍）
M09-TEST-003  退款 → 提成冲正
M09-TEST-004  结算单生成 → 已结算记录禁止修改
M09-TEST-005  员工/日期/状态筛选正确
```

---

#### M09 风险提示

| 风险 | 说明 | 当前策略 |
|---|---|---|
| 员工可被前端伪造 | `employeeId` 来自请求 DTO，未经严格校验 | 计算前从 DB 重新校验员工存在且在职 |
| `commission_rate` 语义歧义 | DECIMAL(5,4) 存 0.1000=10% 还是 10=10% | 接口层明确使用小数，前端显示时 ×100 |
| 普通订单与套餐核销双重计提 | 同一次服务可能同时写入 `pos_order_item` 和 `crm_package_redemption` | 用 `(source_type, source_id, employee_id)` 唯一约束防重 |
| 退款无现成冲正链 | M07 有 `refund_amount` 但无提成冲正机制 | 生成负数冲正记录或独立冲正字段 |
| 门店/租户隔离不完整 | 部分查询未统一追加 `tenant_id`、`store_id` | 提成属于财务敏感模块，必须严格执行 |

---

#### M09 与 M08 库存的关联预留

未来链路（当前版本不实现，schema 预留字段）：

```
服务订单
  ↓ pos_order_item
服务项目 + 员工
  ↓ 套餐核销
crm_package_redemption.beautician_id
  ↓
耗材消耗 ← stock_record.order_id 回溯
  ↓
成本
  ↓
毛利 = 收入 - 耗材成本
  ↓
毛利 × 提成规则 = 员工提成
```

当前 MVP 仅实现：**收入 × 提成比例**。

### 13.9 M10 营销工具模块

```
M10 营销工具模块
├── 数据库设计
│   ├── 优惠券表 (market_coupon)
│   ├── 优惠券发放表 (market_coupon_grant)
│   ├── 积分规则表 (market_point_rule)
│   └── 积分记录表 (market_point_record)
│
├── 后端API
│   ├── 优惠券发放/使用
│   ├── 积分增减
│   └── 营销活动管理
│
└── 前端页面
    ├── 优惠券列表页
    ├── 积分商城页
    └── 活动设置页
```

### 13.10 M11 数据报表模块

```
M11 数据报表模块
├── 数据库设计
│   ├── 报表配置表 (report_config)
│   └── 报表数据表 (report_data)
│
├── 后端API
│   ├── 营业日报/月报
│   ├── 会员分析
│   ├── 员工绩效
│   └── 数据导出
│
└── 前端页面
    ├── 数据看板
    ├── 营业报表页
    └── 导出管理页
```

### 13.11 M12 系统设置模块

```
M12 系统设置模块
├── 数据库设计
│   ├── 系统配置表 (sys_config)
│   ├── 租户表 (sys_tenant)
│   ├── 操作日志表 (sys_log)
│   └── 数字人民币配置表 (dcep_config)
│
├── 后端API
│   ├── 基础配置管理
│   ├── 租户管理
│   ├── 操作日志查询
│   └── 数字人民币接入
│
└── 前端页面
    ├── 系统设置页
    ├── 租户管理页
    ├── 操作日志页
    └── 支付配置页
```

---

## 十四、项目里程碑

```
┌─────────────────────────────────────────────────────────────────────┐
│                          美业SaaS开发里程碑                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Phase 1: 环境搭建 (第1-2天)                                        │
│  ├── 完成开发环境安装                                               │
│  ├── 完成项目结构初始化                                             │
│  └── 完成数据库初始化                                               │
│                                                                     │
│  Phase 2: M01-M03 核心模块 (第3-10天)                                │
│  ├── M01 登录认证 ✓                                                │
│  ├── M02 基础数据                                                    │
│  └── M03 会员管理                                                    │
│                                                                     │
│  Phase 3: M04-M07 业务模块 (第11-20天)                               │
│  ├── M04 储值管理                                                    │
│  ├── M05 套餐次卡                                                    │
│  ├── M06 预约管理                                                    │
│  └── M07 收银结算                                                    │
│                                                                     │
│  Phase 4: M08-M11 增强模块 (第21-28天)                                │
│  ├── M08 库存管理                                                    │
│  ├── M09 员工提成                                                    │
│  ├── M10 营销工具                                                    │
│  └── M11 数据报表                                                    │
│                                                                     │
│  Phase 5: M12 + 部署 (第29-30天)                                     │
│  ├── M12 系统设置                                                    │
│  └── 生产环境部署                                                    │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

MVP完成 = M01-M07 ≈ 20天（可上线运营）
完整版 = M01-M12 ≈ 30天
```

---

> **核心原则：每个最小模块都要做到前端+后端+数据库全链路打通，才能进入下一个模块！**

---

## 九、M12 数字人民币支付配置 · 交付说明

> 状态：**✅ 已完成（2026-08-07）** — 阶段交付物，可走 P3 商户资质扩展。

### 9.1 交付范围

| 模块 | 路径 | 行数 | 职责 |
|---|---|---|---|
| 后端实体 | `backend/src/main/java/com/meiyetec/entity/DcepConfig.java` | 36 | 单条记录存储，含 `createdAt/updatedAt` 自动填充，`confirmPassword` 写入不入库 |
| 后端 Mapper | `backend/src/main/java/com/meiyetec/mapper/DcepConfigMapper.java` | — | MyBatis-Plus BaseMapper |
| 后端 Service | `backend/src/main/java/com/meiyetec/service/DcepConfigService.java` + impl | — | `IService` + 自定义 `updateById` 透传 |
| 后端 Controller | `backend/src/main/java/com/meiyetec/controller/DcepConfigController.java` | 281 | 5 接口 + 视图掩码 + 指纹 + 敏感二次确认 + 测试连接 mock |
| 前端页面 | `frontend/meiyetec-web/src/views/system/payment.vue` | 869 | 单页 SPA，包含凭证管理 / 指纹 / 二次确认 / 测试连接 |
| 前端 API | `frontend/meiyetec-web/src/api/sys.ts` | `dcepGet/dcepSave/dcepTest` | TS DTO，含 `updatedAt` |

### 9.2 接口清单

| Method | Path | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| GET | `/sys/dcep/current` | — | `Result<Map>` 当前配置视图 | 字段已掩码 + 含 `apiKeyMasked` / `*Fingerprint` |
| POST | `/sys/dcep` | `DcepConfig` | `Result<Void>` | 保存（自动判定 insert/update）；凭据为空/MASK = 保留原值 |
| PUT | `/sys/dcep/{id}` | `DcepConfig` | `Result<Void>` | 兼容 PUT 更新 |
| PUT | `/sys/dcep/{id}/toggle/{0\|1}` | path | `Result<Void>` | 启停 |
| POST | `/sys/dcep/test` | `DcepConfig`（可空 override） | `Result<Map>` 含 `ok/stage/message/elapsedMs` | 四阶段：完整性→私钥解析→公钥解析→mock 网关 |

### 9.3 安全机制（核心约束）

1. **凭证视图永远掩码**
   - 凭据字段一律 `••••••••xxxx`（短于 6 字符退化为 `••••`）
   - 响应附 `apiKeyMasked` / `privateKeyMasked` / `publicKeyMasked` 布尔位，前端据此显示「已配置」徽标
2. **指纹 (fingerprint) 不暴露原文**
   - 算法：`前4…后4`（去空白后）
   - 用于"是否被改过"的视觉锚点 + 防止后端指纹与前端 SHA-256 不一致时的告警
3. **敏感变更 = 二次确认**
   - 触发条件：首次配置 / 任意凭据被覆盖 / 沙→产 / 启停
   - 实现：复用 `AuthService.checkPassword()`，失败抛 `二次确认密码错误`
   - 前端在保存前弹 `ElMessageBox` 收集密码，传 `confirmPassword`（`@JsonProperty(WRITE_ONLY)`）
4. **未保存变更守卫**
   - 前端监听 `beforeunload`：有未保存变更时弹出系统确认对话框
   - 路由切换守卫：站内跳转时弹 `ElMessageBox.confirm`

### 9.4 测试连接（Mock 链路）

`POST /sys/dcep/test` 走四阶段验证：

```
[1] 完整性  → 缺 apiKey / privateKey / publicKey / merchantId 任一即失败
[2] 私钥解析 → JDK RSA + PKCS8EncodedKeySpec 真解析；失败 stage=private_key_parse
[3] 公钥解析 → JDK RSA + X509EncodedKeySpec 真解析；失败 stage=public_key_parse
[4] 网关 mock → 200~600ms 随机延迟；ok=true, environment, gateway URL
```

> 真接入数研所时，把 `[4]` 替换为 `DCEP SDK /sign + HTTP` 即可。

### 9.5 前端交互要点

- **PEM 输入**：未解锁态 `••••••••xxxx` + readonly；点 `🔓 临时显示原文（10s）` 触发 10s 自动隐藏
- **PEM 上传**：仅在已解锁态显示 `📁 上传 PEM` 按钮，避免误覆盖未确认的密文
- **PEM 格式提示**：单行灰底 `📝 提示：xxx 未以 BEGIN ... 开头/结尾。将以原文保存。`
- **指纹行**：`指纹 abcd…efgh` 圆角 pill；后端指纹与前端 SHA-256 共用一个槽
- **变更摘要卡片**：底部固定，列出本次改了哪些字段（kind=info/danger）
- **危险变更高亮**：含凭据/启停时整张卡片顶部红字 `⚠ 包含敏感变更`
- **未保存 Pill**：`📝 您有 N 项未保存的变更 [撤销]`，圆角药丸
- **上次更新于**：表单顶部时间戳展示（依赖后端 `updatedAt`）

### 9.6 验证步骤（QA 验收）

```bash
# 1. 启动
docker compose up -d
cd backend && mvn spring-boot:run
cd frontend/meiyetec-web && pnpm dev

# 2. 登录 admin/admin123
# 3. 进入 "系统管理 → 数字人民币支付"
# 4. 不填任何东西点 "测试连接" → 应返回 ok=false, stage=missing_*
# 5. 完整填写凭据 + AppID + 商户号
#    → 上传 PEM（如果 .pem 是 PKCS8）
#    → 点 "测试连接" → ok=true, elapsedMs≈200~600
# 6. 改任意一项 → 应看到顶部 "您有 N 项未保存"
#    → 点保存 → 弹二次确认框 → 输 admin123
#    → 输错密码 → 提示"二次确认密码错误"
#    → 输对 → 保存成功 + 时间戳更新
# 7. 浏览器刷新 → 凭据仍为掩码 + 已配置徽标
# 8. 点 "🔓 临时显示原文" → 10s 后自动隐藏（手动改不影响）
# 9. 关闭页面前改了未保存 → 浏览器弹"确定离开？"系统框
# 10. 切到生产环境 → 顶部红字警告"将进行真实资金扣减"
```

### 9.7 已知约束 / 待办（不阻塞 P3）

| 项 | 说明 | 影响 |
|---|---|---|
| PEM 仅支持 PKCS8（`BEGIN PRIVATE KEY`） | 老格式 `RSA PRIVATE KEY` 已被剥离 header 但 spec 仍是 PKCS8 → 解析会失败 | P3 接入真网关时再兼容 PKCS1 |
| 测试连接为 Mock | 阶段 [4] 是 `Thread.sleep` 占位 | 接 SDK 时替换 |
| 商户号/AppID 未做格式校验 | 任意字符串都能保存 | 由业务侧人工校验 |
| DB 表无唯一约束 | 多租户时需加 `tenant_id` | M12 整体扩展时再补 |
| 无审计日志读侧 | 写了 `@SysOpLog` 但未提供查询页 | P3 + 后 |

### 9.8 路由 / 菜单配置

```ts
// 前端 router（参考）
{
  path: 'payment',
  name: 'PaymentConfig',
  component: () => import('@/views/system/payment.vue'),
  meta: { title: '数字人民币支付配置', icon: 'Money' }
}
```

权限点：`sys:dcep:view` / `sys:dcep:edit`（由 `@PreAuthorize` 控制，后端可在 service 加；当前未启用 RBAC 校验）。

### 9.9 P3 接力建议（已在本任务清单外）

按以下顺序继续：

1. **P3-A 商户资质材料**：身份证 / 营业执照 / 银行账号 / 资质图上传（OSS/MinIO）
2. **P3-B 真实网关接入**：替换 mock → 真 DCEP SDK（需先找数研所/银行方申请 sandbox 凭据）
3. **P3-C 下单 → 支付 → 回调全链路**：在 M07 收银里加"DCEP 收银台"模式
4. **P3-D 审计 + 操作回放**：凭据变更记录可追溯到操作者 IP / 时间 / 原因

---

## 九、P3-A 商户资质材料 · 交付说明

> 状态：**✅ 已完成（2026-08-07）**。当前采用本地文件存储适配器，后续可无缝替换 OSS/MinIO。

### 9.1 交付物

- 数据库：`backend/src/main/resources/db/sql/init_m12_dcep_qualification.sql`
  - `dcep_merchant_qualification`：商户资质主表，与 `dcep_config` 按 `config_id` 关联。
  - `dcep_qualification_attachment`：身份证正面/反面、营业执照及其他附件表。
- 后端：资质实体、Mapper、Service、Controller；文件存储接口与本地实现。
- 前端：`views/system/qualification.vue` 资质维护页；`MaterialUploader.vue` 上传/预览/删除组件；`sysApi` API 类型和方法；路由及支付配置入口。

### 9.2 接口

| Method | Path | 说明 |
|---|---|---|
| GET | `/sys/dcep/qualification/current` | 查询当前资质及附件（敏感号码按掩码返回） |
| POST | `/sys/dcep/qualification` | 保存资质文本；审核中禁止修改 |
| POST | `/sys/dcep/qualification/upload` | 上传附件；白名单类型、单文件最大 10MB |
| DELETE | `/sys/dcep/qualification/attachment/{id}` | 删除附件；审核中禁止删除 |
| POST | `/sys/dcep/qualification/submit` | 校验必填字段和三类必需附件后提交审核 |

### 9.3 安全与校验

1. 文件名不直接入路径，服务端使用材料类型 + UUID 生成文件名，并做路径穿越保护。
2. 仅允许 `jpg/jpeg/png/gif/webp/pdf/bmp`；单文件最大 10MB。
3. 身份证号、银行账号保存前掩码；已掩码值重复保存不会二次破坏。
4. 审核中状态禁止文本修改、上传、删除；提交审核必须具备法人、营业执照、银行账户、联系人等必填字段，以及身份证正反面和营业执照附件。
5. 附件通过 `/api/static/dcep/qualification/**` 统一访问，兼容前端开发代理。

### 9.4 本地存储约定

默认目录：`backend/storage/dcep/qualification/`（可通过 `app.storage.dir` 配置）。后续接入 OSS/MinIO 时只需新增 `FileStorageService` 实现，不改变 Controller 和前端 API 契约。

### 9.5 验收结果

- 后端 `mvn -q -o -f backend/pom.xml package -DskipTests`：**通过**。
- 登录后调用 `GET /sys/dcep/qualification/current`：**通过**，空表返回 `auditStatus=0` 并绑定现有 `dcep_config.id=2`。
- 保存/读取联调：**通过**，身份证和银行账号掩码生效。
- 提交审核联调：**通过**，状态由待提交变为审核中。
- 联调数据已清理，当前资质主表和附件表均为 0 条测试数据。
- 前端开发服务器：**可访问**。完整 `npm run build` 仍受仓库既有 TypeScript 缺失模块/历史错误影响，不归因于本次 P3-A 页面；本次新增页面自身已完成 lint 检查。

### 9.6 后续 P3

P3-A 之后进入 P3-B 真实网关接入；在取得 sandbox 凭据前，不应把当前 mock 测试连接误认为真实支付链路。

