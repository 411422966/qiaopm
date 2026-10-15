# 美业 SaaS 系统 · 技术方案设计

> 版本：v1.0 | 日期：2026-07-31
> 基于：PRD-美业SaaS系统功能大全.md

---

## 1. 背景与目标

### 1.1 业务背景

面向美业（美容院/美发/美甲美睫/SPA/医美）商家的 SaaS 管理系统，解决：

- 多门店统一管理难
- 会员数据分散
- 预约/排班资源冲突
- 储值/次卡核销复杂
- 营销触达效率低

### 1.2 设计目标

| 目标 | 说明 |
|-----|------|
| **高可用** | 99.9% 可用性，支持高峰时段（周末/节假日） |
| **高并发** | 单店 500并发，连锁 5000并发 |
| **安全合规** | PIPL合规，支付数据PCI-DSS，数字人民币智能合约 |
| **快速迭代** | 模块独立开发，独立部署 |
| **多租户** | 支持 SaaS 多商户，数据隔离 |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              客户端层                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ 微信小程序 │  │  H5网页  │  │ PC管理后台│  │  APP端  │  │  抖音/点评 │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘ │
└───────┼─────────────┼─────────────┼─────────────┼─────────────┼───────┘
        │             │             │             │             │
        └─────────────┴─────────────┴──────┬──────┴─────────────┘
                                          │
                              ┌────────────▼────────────┐
                              │       API 网关层         │
                              │  (Kong / Nginx)         │
                              │  鉴权/限流/路由/监控     │
                              └────────────┬────────────┘
                                          │
┌─────────────────────────────────────────┼─────────────────────────────┐
│                                        │        服务层               │
│  ┌─────────────┐  ┌─────────────┐  ┌────▼────┐  ┌─────────────┐       │
│  │   用户服务   │  │   预约服务   │  │  收银服务 │  │   会员服务   │       │
│  │  (auth)    │  │ (booking)  │  │ (pos)   │  │ (member)   │       │
│  └─────────────┘  └─────────────┘  └─────────┘  └─────────────┘       │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────┐  ┌─────────────┐       │
│  │   套餐服务   │  │   库存服务   │  │ 营销服务 │  │   报表服务   │       │
│  │ (package)  │  │(inventory) │  │(marketing)│ │ (report)   │       │
│  └─────────────┘  └─────────────┘  └─────────┘  └─────────────┘       │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────┐  ┌─────────────┐       │
│  │   员工服务   │  │   支付服务   │  │ 文件服务 │  │   消息服务   │       │
│  │ (employee) │  │ (payment)  │  │ (file)  │  │ (message)  │       │
│  └─────────────┘  └─────────────┘  └─────────┘  └─────────────┘       │
├──────────────────────────────────────────────────────────────────────┤
│                              数据层                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │  MySQL   │  │   Redis   │  │ MongoDB  │  │ Elasticsearch│        │
│  │  主数据库  │  │  缓存/会话  │  │ 文档存储  │  │  日志/搜索  │        │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘             │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                         │
│  │   OSS    │  │   MQ     │  │   定时任务 │                        │
│  │ 文件存储  │  │ 消息队列  │  │  Scheduler│                        │
│  └──────────┘  └──────────┘  └──────────┘                         │
├──────────────────────────────────────────────────────────────────────┤
│                              第三方集成                                │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐  │
│  │微信支付 │ │支付宝  │ │数字人民币│ │短信服务 │ │邮件服务 │ │地图服务 │  │
│  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘ └────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 技术选型

| 层级 | 组件 | 选型 | 理由 |
|-----|------|------|------|
| **前端框架** | 小程序 | UniApp + Vue3 | 一套代码多端（微信/抖音/支付宝） |
| | PC管理后台 | Vue3 + Element Plus | 后台管理系统成熟方案 |
| | H5 | Vue3 + Vant | 移动端H5 |
| **后端框架** | 主语言 | Java 17 + Spring Boot 3 | 生态成熟，稳定，适合企业级 |
| | 微服务框架 | Spring Cloud Alibaba | 国内云原生事实标准 |
| | ORM | MyBatis-Plus | 国产，文档友好 |
| **数据库** | 主库 | MySQL 8.0 | 国内SaaS首选 |
| | 缓存 | Redis 7.0 | 缓存/会话/锁 |
| | 文档库 | MongoDB 6.0 | 会员档案/日志 |
| | 搜索引擎 | Elasticsearch 8.x | 日志分析/全文搜索 |
| **消息队列** | RabbitMQ / RocketMQ | 异步解耦/事件驱动 | |
| **文件存储** | OSS / MinIO | 图片/文件存储 | |
| **容器** | Docker + K8s | 弹性伸缩/微服务治理 | |
| **CI/CD** | Jenkins / GitLab CI | 自动化构建部署 | |
| **监控** | Prometheus + Grafana | 监控告警 | |
| **日志** | ELK Stack | 分布式日志 | |

### 2.3 部署架构

```
                    ┌─────────────┐
                    │  负载均衡    │
                    │  (SLB/Nginx)│
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼─────┐┌─────▼─────┐┌─────▼─────┐
        │  API网关   ││  API网关   ││  API网关   │
        │ (Kong)   ││ (Kong)   ││ (Kong)   │
        └─────┬─────┘└─────┬─────┘└─────┬─────┘
              │            │            │
        ┌─────▼────────────▼────────────▼─────┐
        │              服务集群                  │
        │  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ │
        │  │auth│ │user│ │book│ │pos │ │more│ │
        │  └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ │
        └─────┼──────┼──────┼──────┼──────┼───┘
              │      │      │      │      │
        ┌─────▼──┐┌─▼──────▼──────▼──────▼──┐
        │MySQL主库││      Redis 集群         │
        │ + 从库  ││  (主从/哨兵/Cluster)    │
        └─────────┘└────────────────────────┘
```

---

## 3. 模块划分

### 3.1 服务模块清单

| 模块 | 服务名 | 职责 | API数量 |
|------|--------|------|--------|
| M01 | auth-service | 用户认证/授权/JWT/SSO | 8 |
| M02 | member-service | 会员档案/标签/等级 | 15 |
| M03 | booking-service | 预约/排班/资源调度 | 12 |
| M04 | pos-service | 收银/订单/退款 | 10 |
| M05 | package-service | 套餐/次卡/疗程卡 | 10 |
| M06 | wallet-service | 储值/积分/优惠券 | 8 |
| M07 | inventory-service | 库存/批次/耗材 | 10 |
| M08 | employee-service | 员工/排班/提成 | 12 |
| M09 | marketing-service | 营销/短信/自动化 | 8 |
| M10 | report-service | 报表/看板/导出 | 6 |
| M11 | file-service | 文件上传/OSS | 4 |
| M12 | message-service | 消息/通知/订阅 | 6 |

### 3.2 模块依赖关系

```
                    ┌──────────────┐
                    │  auth-service│ ← 基础依赖
                    └──────┬───────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
    ┌────▼────┐      ┌────▼────┐      ┌────▼────┐
    │ member  │      │employee │      │ booking │
    │ service │      │ service │      │ service │
    └────┬────┘      └────┬────┘      └────┬────┘
         │                │                 │
         └────────────────┼─────────────────┘
                          │
              ┌───────────┼───────────┐
              │           │           │
         ┌────▼───┐ ┌────▼───┐ ┌────▼───┐
         │ wallet │ │package │ │   pos  │
         │service │ │service │ │service │
         └────┬───┘ └────┬───┘ └────┬───┘
              │           │           │
              └───────────┼───────────┘
                          │
              ┌───────────┼───────────┐
              │           │           │
         ┌────▼───┐ ┌────▼───┐ ┌────▼───┐
         │inventory│ │marketing│ │ report │
         │service │ │service │ │service │
         └─────────┘ └─────────┘ └─────────┘
```

### 3.3 核心模块设计

#### 3.3.1 预约服务 (booking-service)

```
职责：
- 预约创建/取消/改期
- 技师/房间排班管理
- 资源冲突检测
- 候补队列管理
- 爽约/迟到规则

对外API：
POST   /api/booking          # 创建预约
GET    /api/booking/{id}    # 预约详情
PUT    /api/booking/{id}    # 修改预约
DELETE /api/booking/{id}    # 取消预约
POST   /api/booking/{id}/checkin  # 签到
GET    /api/booking/schedule # 排班表
POST   /api/booking/waitlist  # 加入候补
```

#### 3.3.2 收银服务 (pos-service)

```
职责：
- 订单创建/支付
- 混合支付
- 退款/撤销
- 分账/提成计算
- 小票打印

对外API：
POST   /api/pos/order        # 创建订单
POST   /api/pos/order/pay   # 支付
POST   /api/pos/order/refund # 退款
GET    /api/pos/order/{id}  # 订单详情
POST   /api/pos/order/void  # 整单撤销
```

#### 3.3.3 会员服务 (member-service)

```
职责：
- 会员档案CRUD
- 肤质/发质/甲面评估
- 标签管理
- 会员等级
- 隐私权限

对外API：
POST   /api/member           # 创建会员
GET    /api/member/{id}     # 会员详情
PUT    /api/member/{id}     # 更新会员
GET    /api/member/list     # 会员列表
POST   /api/member/{id}/profile  # 添加档案
GET    /api/member/{id}/history  # 消费历史
```

---

## 4. 数据库设计

### 4.1 数据库规划

```
┌────────────────────────────────────────┐
│           美业SaaS 数据库规划             │
├────────────────────────────────────────┤
│                                        │
│  ┌─────────────┐  ┌─────────────┐    │
│  │  saas_admin │  │  saas_order  │    │
│  │  (系统配置)  │  │  (交易订单)   │    │
│  └─────────────┘  └─────────────┘    │
│                                        │
│  ┌─────────────┐  ┌─────────────┐    │
│  │  saas_member │  │  saas_asset │    │
│  │  (会员数据)  │  │  (资源库存)   │    │
│  └─────────────┘  └─────────────┘    │
│                                        │
│  ┌─────────────┐  ┌─────────────┐    │
│  │  saas_market │  │  saas_report│    │
│  │  (营销数据)  │  │  (报表数据)   │    │
│  └─────────────┘  └─────────────┘    │
│                                        │
│  ┌─────────────┐  ┌─────────────┐    │
│  │  saas_log   │  │  saas_file  │    │
│  │  (日志库)   │  │  (文件索引)   │    │
│  └─────────────┘  └─────────────┘    │
│                                        │
└────────────────────────────────────────┘
```

### 4.2 核心表结构

#### 4.2.1 租户表 (tenants)

```sql
CREATE TABLE `tenants` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT COMMENT '租户ID',
  `code` varchar(32) UNIQUE NOT NULL COMMENT '租户编码',
  `name` varchar(128) NOT NULL COMMENT '商户名称',
  `type` tinyint NOT NULL COMMENT '类型:1美容院 2美发 3美甲 4SPA 5医美',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态:0禁用 1启用',
  `plan` varchar(32) NOT NULL COMMENT '套餐:basic/pro/elite/med',
  `expire_date` datetime COMMENT '到期时间',
  `settings` json COMMENT '租户配置',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='租户表';

CREATE INDEX idx_tenants_code ON tenants(code);
CREATE INDEX idx_tenants_status ON tenants(status);
```

#### 4.2.2 门店表 (stores)

```sql
CREATE TABLE `stores` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `code` varchar(32) NOT NULL COMMENT '门店编码',
  `name` varchar(128) NOT NULL COMMENT '门店名称',
  `province` varchar(32) COMMENT '省',
  `city` varchar(32) COMMENT '市',
  `district` varchar(32) COMMENT '区',
  `address` varchar(255) COMMENT '详细地址',
  `lat` decimal(10,6) COMMENT '纬度',
  `lng` decimal(10,6) COMMENT '经度',
  `phone` varchar(20) COMMENT '电话',
  `business_hours` json COMMENT '营业时间',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='门店表';

CREATE INDEX idx_stores_tenant ON stores(tenant_id);
CREATE INDEX idx_stores_code ON stores(tenant_id, code);
```

#### 4.2.3 会员表 (members)

```sql
CREATE TABLE `members` (
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
  `source` varchar(32) COMMENT '来源:门店/小程序/美团等',
  `tags` json COMMENT '标签',
  `status` tinyint DEFAULT 1 COMMENT '状态:0禁用 1正常',
  `last_visit_at` datetime COMMENT '最近到店',
  `total_consume` decimal(12,2) DEFAULT 0 COMMENT '累计消费',
  `total_points` bigint DEFAULT 0 COMMENT '累计积分',
  `balance` decimal(12,2) DEFAULT 0 COMMENT '储值余额',
  `privacy_consent` tinyint DEFAULT 0 COMMENT '隐私授权',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='会员表';

CREATE INDEX idx_members_tenant ON members(tenant_id);
CREATE INDEX idx_members_phone ON members(tenant_id, phone);
CREATE INDEX idx_members_no ON members(member_no);
CREATE INDEX idx_members_store ON members(store_id);
```

#### 4.2.4 会员档案表 (member_profiles)

```sql
CREATE TABLE `member_profiles` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `store_id` bigint,
  `type` varchar(32) NOT NULL COMMENT '类型:skin/hair/nail/body',
  `skin_type` varchar(32) COMMENT '肤质',
  `skin_condition` json COMMENT '肤质状况',
  `allergy` json COMMENT '过敏信息',
  `contraindication` json COMMENT '禁忌事项',
  `assessment_record` json COMMENT '评估记录',
  `photos` json COMMENT '照片记录',
  `notes` text COMMENT '备注',
  `assessed_by` bigint COMMENT '评估人',
  `assessed_at` datetime,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='会员档案表(肤质/发质/甲面)';

CREATE INDEX idx_profiles_member ON member_profiles(member_id);
CREATE INDEX idx_profiles_type ON member_profiles(member_id, type);
```

#### 4.2.5 服务项目表 (services)

```sql
CREATE TABLE `services` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint COMMENT '门店ID,NULL表示通用',
  `code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL COMMENT '项目名称',
  `category_id` bigint COMMENT '分类',
  `type` tinyint NOT NULL COMMENT '类型:1单项 2套餐 3疗程',
  `duration` int NOT NULL COMMENT '时长(分钟)',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `cost` decimal(10,2) COMMENT '成本',
  `status` tinyint DEFAULT 1,
  `sop` json COMMENT '服务SOP',
  `materials` json COMMENT '所需耗材',
  `formulas` json COMMENT '配方',
  `tags` json COMMENT '标签',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='服务项目表';

CREATE INDEX idx_services_tenant ON services(tenant_id);
CREATE INDEX idx_services_store ON services(tenant_id, store_id);
CREATE INDEX idx_services_category ON services(category_id);
```

#### 4.2.6 套餐/次卡表 (packages)

```sql
CREATE TABLE `packages` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL COMMENT '套餐名称',
  `type` tinyint NOT NULL COMMENT '类型:1计次卡 2套餐 3疗程卡',
  `service_ids` json NOT NULL COMMENT '包含服务ID列表',
  `total_times` int COMMENT '总次数',
  `price` decimal(10,2) NOT NULL COMMENT '售价',
  `cost` decimal(10,2) COMMENT '成本',
  `validity_days` int COMMENT '有效期(天)',
  `shareable` tinyint DEFAULT 0 COMMENT '是否可转赠',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='套餐/次卡表';

CREATE INDEX idx_packages_tenant ON packages(tenant_id);
CREATE INDEX idx_packages_type ON packages(type);
```

#### 4.2.7 套餐购买记录 (package_orders)

```sql
CREATE TABLE `package_orders` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `member_id` bigint NOT NULL COMMENT '购买会员',
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
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='套餐购买记录';

CREATE INDEX idx_pkg_orders_member ON package_orders(member_id);
CREATE INDEX idx_pkg_orders_status ON package_orders(member_id, status);
```

#### 4.2.8 套餐核销记录 (package_redemptions)

```sql
CREATE TABLE `package_redemptions` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `package_order_id` bigint NOT NULL COMMENT '套餐订单ID',
  `member_id` bigint NOT NULL,
  `service_id` bigint NOT NULL COMMENT '核销服务',
  `booking_id` bigint COMMENT '关联预约',
  `order_id` bigint COMMENT '关联收银订单',
  `times` int DEFAULT 1 COMMENT '核销次数',
  `operator_id` bigint COMMENT '操作人',
  `store_id` bigint COMMENT '核销门店',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='套餐核销记录';

CREATE INDEX idx_redemptions_order ON package_redemptions(package_order_id);
CREATE INDEX idx_redemptions_member ON package_redemptions(member_id);
```

#### 4.2.9 预约表 (bookings)

```sql
CREATE TABLE `bookings` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `booking_no` varchar(64) UNIQUE NOT NULL COMMENT '预约号',
  `member_id` bigint COMMENT '会员ID',
  `member_name` varchar(64) COMMENT '预约人姓名',
  `member_phone` varchar(20) COMMENT '预约人电话',
  `service_id` bigint NOT NULL COMMENT '服务项目',
  `employee_id` bigint COMMENT '技师',
  `room_id` bigint COMMENT '房间/床位',
  `booking_date` date NOT NULL COMMENT '预约日期',
  `booking_time` time NOT NULL COMMENT '预约时间',
  `duration` int COMMENT '服务时长(分钟)',
  `end_time` time COMMENT '预计结束时间',
  `status` tinyint DEFAULT 1 COMMENT '状态:1待确认 2已确认 3已完成 4已取消 5爽约',
  `source` varchar(32) COMMENT '来源:小程序/门店/美团',
  `remark` varchar(255) COMMENT '备注',
  `checkin_at` datetime COMMENT '签到时间',
  `start_at` datetime COMMENT '开始服务时间',
  `finish_at` datetime COMMENT '完成时间',
  `cancel_reason` varchar(255) COMMENT '取消原因',
  `cancel_at` datetime,
  `no_show_fee` decimal(10,2) DEFAULT 0 COMMENT '爽约扣费',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='预约表';

CREATE INDEX idx_bookings_tenant ON bookings(tenant_id);
CREATE INDEX idx_bookings_store_date ON bookings(store_id, booking_date);
CREATE INDEX idx_bookings_employee ON bookings(employee_id, booking_date);
CREATE INDEX idx_bookings_member ON bookings(member_id);
CREATE INDEX idx_bookings_status ON bookings(booking_date, status);
```

#### 4.2.10 技师表 (employees)

```sql
CREATE TABLE `employees` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(64) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `avatar` varchar(255),
  `role` tinyint NOT NULL COMMENT '角色:1技师 2店长 3管理员',
  `level_id` bigint COMMENT '技师等级',
  `skills` json COMMENT '擅长技能',
  `status` tinyint DEFAULT 1 COMMENT '状态:0离职 1在职 2休假',
  `hire_date` date COMMENT '入职日期',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='员工表';

CREATE INDEX idx_employees_tenant ON employees(tenant_id);
CREATE INDEX idx_employees_store ON employees(store_id);
CREATE INDEX idx_employees_role ON employees(store_id, role);
```

#### 4.2.11 技师排班表 (employee_schedules)

```sql
CREATE TABLE `employee_schedules` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `work_date` date NOT NULL COMMENT '工作日期',
  `shift_type` tinyint NOT NULL COMMENT '班次:1早班 2中班 3晚班 4全天',
  `start_time` time COMMENT '上班时间',
  `end_time` time COMMENT '下班时间',
  `break_start` time COMMENT '休息开始',
  `break_end` time COMMENT '休息结束',
  `is_off` tinyint DEFAULT 0 COMMENT '是否休息',
  `off_reason` varchar(64) COMMENT '休息原因',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_employee_date` (`employee_id`, `work_date`)
) COMMENT='技师排班表';

CREATE INDEX idx_schedule_store_date ON employee_schedules(store_id, work_date);
```

#### 4.2.12 房间/资源表 (rooms)

```sql
CREATE TABLE `rooms` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(64) NOT NULL COMMENT '房间名/床位号',
  `type` varchar(32) NOT NULL COMMENT '类型:room/bed/chair',
  `capacity` int DEFAULT 1 COMMENT '容量',
  `status` tinyint DEFAULT 1 COMMENT '状态:0维修 1可用',
  `facilities` json COMMENT '设施配置',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='房间/资源表';

CREATE INDEX idx_rooms_store ON rooms(store_id);
```

#### 4.2.13 收银订单表 (orders)

```sql
CREATE TABLE `orders` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `order_no` varchar(64) UNIQUE NOT NULL COMMENT '订单号',
  `member_id` bigint COMMENT '会员ID',
  `member_name` varchar(64) COMMENT '客户姓名',
  `member_phone` varchar(20) COMMENT '客户电话',
  `booking_id` bigint COMMENT '关联预约',
  `type` tinyint NOT NULL COMMENT '类型:1消费 2充值 3混合',
  `total_amount` decimal(12,2) NOT NULL COMMENT '原价总额',
  `discount_amount` decimal(12,2) DEFAULT 0 COMMENT '折扣金额',
  `final_amount` decimal(12,2) NOT NULL COMMENT '实付金额',
  `cashier_id` bigint NOT NULL COMMENT '收银员',
  `employee_id` bigint COMMENT '服务技师',
  `remark` varchar(255) COMMENT '备注',
  `status` tinyint DEFAULT 1 COMMENT '状态:1进行中 2已完成 3已退款',
  `pay_time` datetime COMMENT '支付时间',
  `refund_time` datetime COMMENT '退款时间',
  `refund_amount` decimal(12,2) COMMENT '退款金额',
  `refund_reason` varchar(255) COMMENT '退款原因',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='收银订单表';

CREATE INDEX idx_orders_tenant ON orders(tenant_id);
CREATE INDEX idx_orders_store ON orders(store_id);
CREATE INDEX idx_orders_member ON orders(member_id);
CREATE INDEX idx_orders_no ON orders(order_no);
CREATE INDEX idx_orders_date ON orders(store_id, created_at);
```

#### 4.2.14 订单明细表 (order_items)

```sql
CREATE TABLE `order_items` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `service_id` bigint NOT NULL COMMENT '服务项目',
  `service_name` varchar(128) NOT NULL COMMENT '项目名称(冗余)',
  `quantity` int DEFAULT 1 COMMENT '数量',
  `unit_price` decimal(10,2) NOT NULL COMMENT '单价',
  `total_price` decimal(12,2) NOT NULL COMMENT '小计',
  `discount_amount` decimal(10,2) DEFAULT 0 COMMENT '折扣',
  `final_price` decimal(12,2) NOT NULL COMMENT '实收',
  `package_order_id` bigint COMMENT '套餐核销ID',
  `package_times` int COMMENT '核销次数',
  `commission_amount` decimal(10,2) COMMENT '提成金额',
  `employee_id` bigint COMMENT '服务技师',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='订单明细表';

CREATE INDEX idx_items_order ON order_items(order_id);
CREATE INDEX idx_items_service ON order_items(service_id);
```

#### 4.2.15 支付记录表 (payments)

```sql
CREATE TABLE `payments` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `payment_no` varchar(64) UNIQUE NOT NULL COMMENT '支付流水号',
  `method` varchar(32) NOT NULL COMMENT '支付方式',
  `sub_method` varchar(32) COMMENT '子方式',
  `amount` decimal(12,2) NOT NULL COMMENT '支付金额',
  `status` tinyint DEFAULT 0 COMMENT '状态:0待支付 1成功 2失败 3已退款',
  `transaction_id` varchar(128) COMMENT '第三方流水号',
  `payer_info` json COMMENT '付款方信息',
  `pay_time` datetime COMMENT '支付时间',
  `channel_order_no` varchar(128) COMMENT '通道订单号',
  `error_msg` varchar(255) COMMENT '错误信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='支付记录表';

-- 支付方式字典
-- wechat_jsapi: 微信JSAPI
-- wechat_native: 微信扫码
-- wechat_app: 微信APP
-- alipay_wap: 支付宝手机网站
-- alipay_app: 支付宝APP
-- unionpay_qr: 银联二维码
-- unionpay_card: 银联卡
-- cash: 现金
-- wallet: 储值余额
-- points: 积分
-- coupon: 优惠券
-- e_cny_hard: 数字人民币硬钱包
-- e_cny_soft: 数字人民币软钱包

CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_method ON payments(method);
CREATE INDEX idx_payments_status ON payments(status);
```

#### 4.2.16 储值记录表 (wallet_transactions)

```sql
CREATE TABLE `wallet_transactions` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `order_id` bigint COMMENT '关联订单',
  `type` tinyint NOT NULL COMMENT '类型:1充值 2消费 3赠送 4退款 5冻结 6解冻',
  `amount` decimal(12,2) NOT NULL COMMENT '变动金额',
  `balance_before` decimal(12,2) NOT NULL COMMENT '变动前余额',
  `balance_after` decimal(12,2) NOT NULL COMMENT '变动后余额',
  `gift_amount` decimal(12,2) DEFAULT 0 COMMENT '赠送金额',
  `pay_method` varchar(32) COMMENT '支付方式',
  `operator_id` bigint COMMENT '操作人',
  `remark` varchar(255) COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='储值变动记录';

CREATE INDEX idx_wallet_member ON wallet_transactions(member_id);
CREATE INDEX idx_wallet_date ON wallet_transactions(member_id, created_at);
```

#### 4.2.17 优惠券表 (coupons)

```sql
CREATE TABLE `coupons` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(32) UNIQUE NOT NULL,
  `name` varchar(128) NOT NULL COMMENT '券名称',
  `type` tinyint NOT NULL COMMENT '类型:1满减 2折扣 3兑换 4体验',
  `value` decimal(10,2) COMMENT '优惠值',
  `min_amount` decimal(10,2) DEFAULT 0 COMMENT '满减门槛',
  `total_count` int COMMENT '发行总量',
  `remain_count` int COMMENT '剩余数量',
  `valid_days` int COMMENT '领取后有效期(天)',
  `valid_start` datetime COMMENT '有效期开始',
  `valid_end` datetime COMMENT '有效期结束',
  `scope_type` tinyint DEFAULT 1 COMMENT '使用范围:1全场 2指定门店 3指定项目',
  `scope_ids` json COMMENT '范围ID列表',
  `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='优惠券表';

CREATE INDEX idx_coupons_tenant ON coupons(tenant_id);
```

#### 4.2.18 会员优惠券表 (member_coupons)

```sql
CREATE TABLE `member_coupons` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `coupon_code` varchar(64) NOT NULL COMMENT '券码',
  `status` tinyint DEFAULT 0 COMMENT '状态:0未使用 1已使用 2已过期 3已作废',
  `receive_time` datetime COMMENT '领取时间',
  `use_time` datetime COMMENT '使用时间',
  `use_order_id` bigint COMMENT '使用订单',
  `expire_time` datetime COMMENT '过期时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='会员优惠券';

CREATE INDEX idx_member_coupons_member ON member_coupons(member_id);
CREATE INDEX idx_member_coupons_code ON member_coupons(coupon_code);
```

#### 4.2.19 库存表 (inventory)

```sql
CREATE TABLE `inventory` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_name` varchar(128) NOT NULL,
  `sku` varchar(64) COMMENT 'SKU编码',
  `category_id` bigint COMMENT '分类',
  `unit` varchar(16) COMMENT '单位',
  `quantity` decimal(12,2) DEFAULT 0 COMMENT '当前库存',
  `warning_quantity` decimal(12,2) COMMENT '预警数量',
  `cost_price` decimal(10,2) COMMENT '成本价',
  `retail_price` decimal(10,2) COMMENT '零售价',
  `batch_enabled` tinyint DEFAULT 0 COMMENT '是否启用批次管理',
  `expiry_enabled` tinyint DEFAULT 0 COMMENT '是否启用效期管理',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='库存表';

CREATE INDEX idx_inventory_store ON inventory(store_id);
CREATE INDEX idx_inventory_product ON inventory(product_id);
```

#### 4.2.20 库存流水表 (inventory_transactions)

```sql
CREATE TABLE `inventory_transactions` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `inventory_id` bigint NOT NULL,
  `type` tinyint NOT NULL COMMENT '类型:1采购入库 2消费扣减 3盘点调整 4退货 5调拨',
  `batch_no` varchar(64) COMMENT '批次号',
  `quantity` decimal(12,2) NOT NULL COMMENT '变动数量',
  `quantity_before` decimal(12,2) NOT NULL COMMENT '变动前',
  `quantity_after` decimal(12,2) NOT NULL COMMENT '变动后',
  `cost_price` decimal(10,2) COMMENT '成本价',
  `related_order_id` bigint COMMENT '关联订单',
  `remark` varchar(255),
  `operator_id` bigint,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='库存流水表';

CREATE INDEX idx_inv_txn_inventory ON inventory_transactions(inventory_id);
CREATE INDEX idx_inv_txn_date ON inventory_transactions(store_id, created_at);
```

#### 4.2.21 提成规则表 (commission_rules)

```sql
CREATE TABLE `commission_rules` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `type` tinyint NOT NULL COMMENT '类型:1项目提成 2产品提成 3储值提成 4套餐提成',
  `service_category_id` bigint COMMENT '服务分类',
  `service_id` bigint COMMENT '指定服务/产品',
  `employee_level_id` bigint COMMENT '技师等级',
  `cal_type` tinyint NOT NULL COMMENT '计算方式:1比例 2固定金额',
  `cal_value` decimal(10,2) NOT NULL COMMENT '计算值',
  `min_amount` decimal(10,2) COMMENT '最低消费额',
  `max_amount` decimal(10,2) COMMENT '最高提成',
  `status` tinyint DEFAULT 1,
  `effective_start` date COMMENT '生效日期',
  `effective_end` date COMMENT '失效日期',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='提成规则表';
```

#### 4.2.22 提成记录表 (commissions)

```sql
CREATE TABLE `commissions` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `order_id` bigint COMMENT '订单ID',
  `order_item_id` bigint COMMENT '订单明细ID',
  `rule_id` bigint COMMENT '规则ID',
  `type` tinyint NOT NULL COMMENT '类型:1项目 2产品 3储值 4套餐',
  `order_amount` decimal(12,2) NOT NULL COMMENT '订单金额',
  `commission_amount` decimal(10,2) NOT NULL COMMENT '提成金额',
  `calculate_type` tinyint COMMENT '计算方式',
  `settlement_status` tinyint DEFAULT 0 COMMENT '结算状态:0未结算 1已结算',
  `settlement_time` datetime COMMENT '结算时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='提成记录表';

CREATE INDEX idx_commissions_employee ON commissions(employee_id);
CREATE INDEX idx_commissions_settlement ON commissions(settlement_status);
```

---

## 5. API接口设计

### 5.1 API规范

```
Base URL: https://api.meiyetec.com/v1
          https://api.meiyetec.com/v1 (生产环境)

认证方式: Bearer Token (JWT)
请求格式: Content-Type: application/json
响应格式: JSON
```

### 5.2 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "timestamp": 1690781234567,
  "requestId": "uuid-xxx-xxx"
}

// 错误响应
{
  "code": 40001,
  "message": "余额不足",
  "data": null,
  "timestamp": 1690781234567,
  "requestId": "uuid-xxx-xxx"
}
```

### 5.3 核心API清单

#### 5.3.1 认证模块

| 接口 | 方法 | 说明 |
|------|------|------|
| POST /auth/login | 账号密码登录 |
| POST /auth/sms/send | 发送短信验证码 |
| POST /auth/sms/login | 短信验证码登录 |
| POST /auth/refresh | 刷新Token |
| POST /auth/logout | 登出 |
| GET /auth/me | 获取当前用户信息 |
| POST /auth/password/reset | 重置密码 |

#### 5.3.2 会员模块

| 接口 | 方法 | 说明 |
|------|------|------|
| POST /members | 创建会员 |
| GET /members/{id} | 获取会员详情 |
| PUT /members/{id} | 更新会员 |
| DELETE /members/{id} | 删除会员 |
| GET /members | 会员列表 |
| POST /members/{id}/profile | 创建会员档案 |
| GET /members/{id}/profile | 获取会员档案 |
| PUT /members/{id}/profile | 更新会员档案 |
| GET /members/{id}/history | 消费历史 |
| GET /members/{id}/packages | 会员套餐 |
| GET /members/{id}/wallet | 储值余额 |
| POST /members/{id}/recharge | 储值充值 |
| GET /members/{id}/points | 积分明细 |
| POST /members/{id}/tags | 添加标签 |
| GET /members/{id}/portrait | 会员画像 |

#### 5.3.3 预约模块

| 接口 | 方法 | 说明 |
|------|------|------|
| POST /bookings | 创建预约 |
| GET /bookings/{id} | 预约详情 |
| PUT /bookings/{id} | 修改预约 |
| DELETE /bookings/{id} | 取消预约 |
| POST /bookings/{id}/confirm | 确认预约 |
| POST /bookings/{id}/checkin | 签到 |
| POST /bookings/{id}/start | 开始服务 |
| POST /bookings/{id}/complete | 完成服务 |
| POST /bookings/{id}/noshow | 标记爽约 |
| GET /bookings/schedule | 排班表 |
| GET /bookings/available | 可预约时间 |
| POST /bookings/waitlist | 加入候补 |
| GET /bookings/waitlist | 候补列表 |

#### 5.3.4 收银模块

| 接口 | 方法 | 说明 |
|------|------|------|
| POST /pos/orders | 创建订单 |
| GET /pos/orders/{id} | 订单详情 |
| POST /pos/orders/{id}/pay | 支付 |
| POST /pos/orders/{id}/refund | 退款 |
| POST /pos/orders/{id}/void | 整单撤销 |
| GET /pos/orders | 订单列表 |
| POST /pos/orders/{id}/items | 添加明细 |
| DELETE /pos/orders/{id}/items/{itemId} | 删除明细 |
| POST /pos/orders/{id}/discount | 整单折扣 |
| GET /pos/print/{id} | 打印小票 |

#### 5.3.5 套餐模块

| 接口 | 方法 | 说明 |
|------|------|------|
| POST /packages | 创建套餐 |
| GET /packages/{id} | 套餐详情 |
| PUT /packages/{id} | 更新套餐 |
| GET /packages | 套餐列表 |
| POST /packages/purchase | 购买套餐 |
| GET /packages/orders | 套餐订单列表 |
| POST /packages/{id}/redeem | 核销 |
| GET /packages/member/{memberId} | 会员套餐列表 |
| POST /packages/{id}/freeze | 冻结 |
| POST /packages/{id}/unfreeze | 解冻 |
| POST /packages/{id}/transfer | 转让 |

#### 5.3.6 支付模块

| 接口 | 方法 | 说明 |
|------|------|------|
| POST /payments/pay | 发起支付 |
| POST /payments/query | 支付查询 |
| POST /payments/refund | 申请退款 |
| POST /payments/callback/{channel} | 支付回调 |
| POST /payments/h5/{channel} | H5支付 |
| GET /payments/{id} | 支付记录详情 |
| POST /payments/wallet/pay | 储值支付 |
| POST /payments/wallet/recharge | 储值充值 |

### 5.4 数字人民币 API

```json
// POST /payments/e-cny/create
// 创建数字人民币支付订单
{
  "orderId": "order_xxx",
  "amount": 199.00,
  "walletType": "soft",  // soft: 软钱包, hard: 硬钱包
  "appId": "wx_xxx",     // 关联App
  "notifyUrl": "https://xxx.com/callback",
  "sceneInfo": {
    "storeName": "美容店",
    "storeId": "store_001"
  }
}

// POST /payments/e-cny/query
// 查询支付状态
{
  "paymentNo": "pay_xxx"
}

// POST /payments/e-cny/refund
// 退款
{
  "paymentNo": "pay_xxx",
  "refundAmount": 99.00,
  "reason": "部分退款"
}

// POST /payments/e-cny/hardwallet/pay
// 硬钱包支付（NFC）
{
  "orderId": "order_xxx",
  "amount": 199.00,
  "cardId": "card_xxx",      // 硬钱包卡ID
  "deviceSn": "device_xxx"   // 收款设备SN
}
```

---

## 6. 安全设计

### 6.1 认证授权

```
┌─────────┐     ┌─────────┐     ┌─────────┐
│  用户   │────▶│  认证   │────▶│  授权   │
│         │     │ JWT/SSO │     │ RBAC   │
└─────────┘     └─────────┘     └─────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
               ┌────▼────┐  ┌────▼────┐  ┌────▼────┐
               │ 门店权限  │  │ 模块权限  │  │ 数据权限  │
               │ (租户隔离)│  │ (菜单/按钮)│  │ (行级)  │
               └─────────┘  └─────────┘  └─────────┘
```

### 6.2 数据安全

| 措施 | 说明 |
|------|------|
| 传输加密 | HTTPS + TLS 1.3 |
| 存储加密 | 敏感字段AES加密（身份证、银行卡） |
| 密码加密 | bcrypt |
| 支付安全 | 支付密钥隔离，PCI-DSS合规 |
| 审计日志 | 关键操作全记录 |
| PIPL合规 | 数据脱敏、隐私授权、删除权 |

### 6.3 数字人民币安全

```
智能合约：
├── 预付卡资金监管（防止跑路）
├── 限时储值自动过期
├── 条件触发自动发券
└── 异常交易拦截

风控：
├── 大额充值预警
├── 异常退款拦截
├── 黑名单管理
└── 可疑交易监控
```

---

## 7. 性能与可用性

### 7.1 性能指标

| 指标 | 目标 |
|------|------|
| API响应时间 | P95 < 200ms |
| 首页加载时间 | < 3s |
| 预约查询 | < 100ms |
| 支付回调处理 | < 500ms |
| 系统可用性 | 99.9% |

### 7.2 容量规划

| 场景 | 并发 | TPS | 说明 |
|------|------|-----|------|
| 单店 | 100 | 50 | 日常经营 |
| 连锁（10店） | 500 | 200 | 促销高峰 |
| 大型连锁（100店） | 2000 | 500 | 节假日 |

### 7.3 高可用方案

```
┌─────────────────────────────────────────┐
│              多AZ部署                     │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │  AZ1    │  │  AZ2    │  │  AZ3    │  │
│  │ 主机房   │  │ 备机房   │  │ 冷备    │  │
│  └────┬────┘  └────┬────┘  └────┬────┘  │
│       │            │            │        │
│       └────────────┼────────────┘        │
│                    │                     │
│              ┌────▼────┐                │
│              │  Redis  │                │
│              │ Sentinel│                │
│              └─────────┘                │
└─────────────────────────────────────────┘
```

---

## 8. 开发计划

### Phase 1: MVP（4周）

| 周次 | 模块 | 功能 |
|------|------|------|
| Week 1 | 基础框架 | 项目初始化、用户认证、租户隔离 |
| Week 2 | 会员+储值 | 会员CRUD、储值充值消费、积分 |
| Week 3 | 预约+排班 | 预约CRUD、技师排班、签到 |
| Week 4 | 收银+支付 | 订单、混合支付、退款、数字人民币 |

### Phase 2: 增强（4周）

| 周次 | 模块 | 功能 |
|------|------|------|
| Week 5 | 套餐次卡 | 套餐管理、购买、核销 |
| Week 6 | 库存+提成 | 库存管理、提成规则、自动计算 |
| Week 7 | 营销 | 优惠券、短信触达 |
| Week 8 | 报表 | 经营看板、导出 |

### Phase 3: 高级（4周）

| 周次 | 模块 | 功能 |
|------|------|------|
| Week 9 | 连锁管理 | 总部管控、跨店核销 |
| Week 10 | 会员档案 | 肤质档案、AI对比照 |
| Week 11 | 自动化 | 营销旅程、回访任务 |
| Week 12 | 智能功能 | 智能排班、LTV预测 |

---

## 9. 下一步行动

- [ ] 确认技术栈（Java/Spring Boot vs Node.js/NestJS）
- [ ] 确认部署方案（阿里云/腾讯云/私有化）
- [ ] 原型设计（核心页面线框图）
- [ ] 团队分工（前后端/测试/运维）
- [ ] 开发环境搭建

---

> 📌 **文档状态**：技术方案初稿
> **下一步**：技术选型确认 → 原型设计 → 开发
