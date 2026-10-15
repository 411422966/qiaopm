-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: meiyetec
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ab_assignment`
--

DROP TABLE IF EXISTS `ab_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ab_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `exp_key` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `variant_key` varchar(64) DEFAULT NULL,
  `assigned_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_exp_user` (`tenant_id`,`exp_key`,`user_id`),
  KEY `idx_tenant_exp` (`tenant_id`,`exp_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ab_experiment`
--

DROP TABLE IF EXISTS `ab_experiment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ab_experiment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `exp_key` varchar(64) NOT NULL,
  `exp_name` varchar(128) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `variants` text,
  `target_rule` varchar(32) DEFAULT NULL,
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `primary_metric` varchar(32) DEFAULT NULL,
  `owner` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_key` (`tenant_id`,`exp_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_ltv_model`
--

DROP TABLE IF EXISTS `ai_ltv_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ltv_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `model_code` varchar(64) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `model_type` varchar(32) NOT NULL COMMENT 'BG/NBD/HISTORICAL/ML',
  `train_start` date DEFAULT NULL,
  `train_end` date DEFAULT NULL,
  `auc` decimal(5,4) DEFAULT NULL,
  `recall` decimal(5,4) DEFAULT NULL,
  `precision_score` decimal(5,4) DEFAULT NULL,
  `params_json` text,
  `status` tinyint DEFAULT '1' COMMENT '1训练中 2已部署 3停用',
  `is_active` tinyint DEFAULT '0' COMMENT '是否当前激活',
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`model_code`),
  KEY `idx_status` (`status`,`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LTV 模型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_recommend_cache`
--

DROP TABLE IF EXISTS `ai_recommend_cache`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_recommend_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `recommend_type` varchar(32) DEFAULT NULL,
  `recommend_items` text,
  `algorithm` varchar(32) DEFAULT NULL,
  `score` decimal(5,4) DEFAULT NULL,
  `cache_key` varchar(128) DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `hit_count` int DEFAULT '0',
  `last_hit_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_member_type` (`tenant_id`,`member_id`,`recommend_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_review_summary`
--

DROP TABLE IF EXISTS `ai_review_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_review_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `summary_key` varchar(128) NOT NULL,
  `summary_type` varchar(32) DEFAULT NULL,
  `total_reviews` int DEFAULT NULL,
  `positive_count` int DEFAULT NULL,
  `neutral_count` int DEFAULT NULL,
  `negative_count` int DEFAULT NULL,
  `avg_rating` decimal(3,2) DEFAULT NULL,
  `nps` decimal(5,2) DEFAULT NULL,
  `positive_keywords` varchar(500) DEFAULT NULL,
  `negative_keywords` varchar(500) DEFAULT NULL,
  `summary_text` text,
  `sentiment_score` decimal(5,4) DEFAULT NULL,
  `algorithm` varchar(32) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_key` (`tenant_id`,`summary_key`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_sales_forecast`
--

DROP TABLE IF EXISTS `ai_sales_forecast`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sales_forecast` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL,
  `forecast_date` date NOT NULL,
  `forecast_type` varchar(32) DEFAULT NULL,
  `predicted_value` decimal(12,2) DEFAULT NULL,
  `upper_bound` decimal(12,2) DEFAULT NULL,
  `lower_bound` decimal(12,2) DEFAULT NULL,
  `confidence` decimal(5,4) DEFAULT NULL,
  `algorithm` varchar(32) DEFAULT NULL,
  `base_window_days` int DEFAULT NULL,
  `input_features` text,
  `comment` varchar(500) DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_forecast_date` (`forecast_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_schedule_plan`
--

DROP TABLE IF EXISTS `ai_schedule_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_schedule_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `week_start` date NOT NULL,
  `staff_count` int DEFAULT NULL,
  `expected_bookings` int DEFAULT NULL,
  `plan_json` text,
  `status` varchar(32) DEFAULT 'DRAFT',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_store_week` (`store_id`,`week_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_tag_model`
--

DROP TABLE IF EXISTS `ai_tag_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_tag_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `model_code` varchar(64) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `model_type` varchar(32) NOT NULL COMMENT 'RFM/RULE/ML/AI_VISION',
  `rule_json` text COMMENT '规则/模型参数',
  `schedule_cron` varchar(32) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `description` varchar(500) DEFAULT NULL,
  `auc` decimal(5,4) DEFAULT NULL,
  `recall` decimal(5,4) DEFAULT NULL,
  `precision_score` decimal(5,4) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`model_code`),
  KEY `idx_type` (`model_type`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签模型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `attendance_config`
--

DROP TABLE IF EXISTS `attendance_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attendance_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID锛宯ull琛ㄧず閫氱敤閰嶇疆',
  `config_name` varchar(128) NOT NULL COMMENT '閰嶇疆鍚嶇О',
  `work_start_time` time NOT NULL COMMENT '涓婄彮鏃堕棿',
  `work_end_time` time NOT NULL COMMENT '涓嬬彮鏃堕棿',
  `allow_late_minutes` int DEFAULT '5' COMMENT '鍏佽?杩熷埌鍒嗛挓鏁',
  `flexible_enabled` tinyint DEFAULT '0' COMMENT '鏄?惁鍚?敤寮规?鎵撳崱锛?-鍚︼紝1-鏄',
  `flexible_start_time` time DEFAULT NULL COMMENT '寮规?涓婄彮寮??鏃堕棿',
  `flexible_end_time` time DEFAULT NULL COMMENT '寮规?涓婄彮缁撴潫鏃堕棿',
  `early_leave_minutes` int DEFAULT '30' COMMENT '鏃╅?璀﹀憡鍒嗛挓鏁',
  `location_enabled` tinyint DEFAULT '0' COMMENT '鏄?惁鍚?敤浣嶇疆鎵撳崱锛?-鍚︼紝1-鏄',
  `location_config` text COMMENT '鎵撳崱浣嶇疆閰嶇疆(JSON)',
  `face_enabled` tinyint DEFAULT '0' COMMENT '鏄?惁鍚?敤浜鸿劯璇嗗埆锛?-鍚︼紝1-鏄',
  `status` tinyint DEFAULT '1' COMMENT '鐘舵?锛?-鍋滅敤锛?-鍚?敤',
  `deleted` tinyint DEFAULT '0' COMMENT '鏄?惁鍒犻櫎锛?-鍚︼紝1-鏄',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鑰冨嫟閰嶇疆琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `attendance_leave_request`
--

DROP TABLE IF EXISTS `attendance_leave_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attendance_leave_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `employee_id` bigint NOT NULL COMMENT '鍛樺伐ID',
  `leave_type` varchar(32) NOT NULL COMMENT '璇峰亣绫诲瀷锛歛nnual/sick/personal/marriage/maternity/funeral/other',
  `start_date` date NOT NULL COMMENT '寮??鏃ユ湡',
  `end_date` date NOT NULL COMMENT '缁撴潫鏃ユ湡',
  `leave_days` int DEFAULT '1' COMMENT '璇峰亣鏃堕暱(澶╂暟)',
  `reason` varchar(1000) DEFAULT NULL COMMENT '璇峰亣鍘熷洜',
  `status` varchar(32) DEFAULT 'pending' COMMENT '鐢宠?鐘舵?锛歱ending/approved/rejected/cancelled',
  `approver_id` bigint DEFAULT NULL COMMENT '瀹℃壒浜篒D',
  `approve_time` datetime DEFAULT NULL COMMENT '瀹℃壒鏃堕棿',
  `approve_remark` varchar(500) DEFAULT NULL COMMENT '瀹℃壒鎰忚?',
  `store_id` bigint DEFAULT NULL COMMENT '鐢宠?闂ㄥ簵ID',
  `deleted` tinyint DEFAULT '0' COMMENT '鏄?惁鍒犻櫎锛?-鍚︼紝1-鏄',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_employee_id` (`employee_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_date` (`start_date`),
  KEY `idx_end_date` (`end_date`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='璇峰亣鐢宠?琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `attendance_record`
--

DROP TABLE IF EXISTS `attendance_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attendance_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `employee_id` bigint NOT NULL COMMENT '鍛樺伐ID',
  `check_date` date NOT NULL COMMENT '鎵撳崱鏃ユ湡',
  `check_in_time` datetime DEFAULT NULL COMMENT '涓婄彮鎵撳崱鏃堕棿',
  `check_out_time` datetime DEFAULT NULL COMMENT '涓嬬彮鎵撳崱鏃堕棿',
  `check_in_method` varchar(32) DEFAULT NULL COMMENT '涓婄彮鎵撳崱鏂瑰紡锛歛pp/pad/face',
  `check_out_method` varchar(32) DEFAULT NULL COMMENT '涓嬬彮鎵撳崱鏂瑰紡',
  `check_in_location` varchar(255) DEFAULT NULL COMMENT '涓婄彮鎵撳崱鍦扮偣',
  `check_out_location` varchar(255) DEFAULT NULL COMMENT '涓嬬彮鎵撳崱鍦扮偣',
  `check_in_status` varchar(32) DEFAULT NULL COMMENT '涓婄彮鎵撳崱鐘舵?锛歯ormal/late/early/absence',
  `check_out_status` varchar(32) DEFAULT NULL COMMENT '涓嬬彮鎵撳崱鐘舵?锛歯ormal/early/absence',
  `is_late` tinyint DEFAULT '0' COMMENT '鏄?惁杩熷埌锛?-鍚︼紝1-鏄',
  `late_minutes` int DEFAULT '0' COMMENT '杩熷埌鍒嗛挓鏁',
  `is_early_leave` tinyint DEFAULT '0' COMMENT '鏄?惁鏃╅?锛?-鍚︼紝1-鏄',
  `early_leave_minutes` int DEFAULT '0' COMMENT '鏃╅?鍒嗛挓鏁',
  `is_absence` tinyint DEFAULT '0' COMMENT '鏄?惁鏃峰伐锛?-鍚︼紝1-鏄',
  `work_minutes` int DEFAULT '0' COMMENT '鍑哄嫟鏃堕暱(鍒嗛挓)',
  `remark` varchar(500) DEFAULT NULL COMMENT '澶囨敞',
  `config_id` bigint DEFAULT NULL COMMENT '鑰冨嫟閰嶇疆ID',
  `store_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `deleted` tinyint DEFAULT '0' COMMENT '鏄?惁鍒犻櫎锛?-鍚︼紝1-鏄',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_date` (`employee_id`,`check_date`,`deleted`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_check_date` (`check_date`),
  KEY `idx_employee_id` (`employee_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鑰冨嫟鎵撳崱璁板綍琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_bnpl_order`
--

DROP TABLE IF EXISTS `biz_bnpl_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_bnpl_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户ID',
  `store_id` bigint DEFAULT NULL COMMENT '所属门店ID',
  `biz_order_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务订单号 (e.g. pos_order.order_no)',
  `member_id` bigint DEFAULT NULL COMMENT '会员ID -> member.id',
  `member_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员姓名 (冗余)',
  `member_email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员邮箱 (BNPL 平台通知用)',
  `member_phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会员手机号 (BNPL 平台通知用)',
  `provider` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BNPL 平台 atome/klarna/afterpay/affirm',
  `provider_order_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BNPL 平台返回的订单号',
  `amount` decimal(18,2) DEFAULT NULL COMMENT '订单总额',
  `currency` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT 'CNY' COMMENT '币种',
  `installments` int DEFAULT NULL COMMENT '分期期数',
  `installment_amount` decimal(18,2) DEFAULT NULL COMMENT '每期应还金额',
  `down_payment` decimal(18,2) DEFAULT NULL COMMENT '首付金额',
  `platform_fee` decimal(18,2) DEFAULT NULL COMMENT '平台手续费',
  `status` int DEFAULT '1' COMMENT '1待确认 2已确认 3已发货 4已收货 5已完成 6已取消 7退款中 8已退款',
  `repayment_schedule` text COLLATE utf8mb4_unicode_ci COMMENT '分期还款计划 (JSON)',
  `first_repay_date` date DEFAULT NULL COMMENT '首次还款日',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_bnpl_tenant` (`tenant_id`),
  KEY `idx_bnpl_store` (`store_id`),
  KEY `idx_bnpl_member` (`member_id`),
  KEY `idx_bnpl_biz_order` (`biz_order_no`),
  KEY `idx_bnpl_provider` (`provider`),
  KEY `idx_bnpl_status` (`status`),
  KEY `idx_bnpl_provider_order` (`provider_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BNPL 先买后付订单 (BnplOrder)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_booking`
--

DROP TABLE IF EXISTS `biz_booking`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_booking` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预约ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `booking_no` varchar(32) NOT NULL COMMENT '预约编号',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `member_name` varchar(50) DEFAULT NULL COMMENT '会员姓名',
  `member_phone` varchar(20) DEFAULT NULL COMMENT '会员手机',
  `employee_id` bigint NOT NULL COMMENT '技师ID',
  `employee_name` varchar(50) DEFAULT NULL COMMENT '技师姓名',
  `service_item` varchar(100) DEFAULT NULL COMMENT '服务项目',
  `room_id` bigint DEFAULT NULL COMMENT '房间ID',
  `scene_mode_id` bigint DEFAULT NULL COMMENT 'SPA场景模式ID',
  `scene_mode_code` varchar(32) DEFAULT NULL COMMENT '场景编码',
  `guest_count` int DEFAULT '1' COMMENT '同行人数',
  `room_name` varchar(50) DEFAULT NULL COMMENT '房间名',
  `booking_date` date DEFAULT NULL COMMENT '预约日期',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `status` tinyint DEFAULT '1' COMMENT '状态：1待确认 2已确认 3已签到 4已完成 5已取消 6爽约',
  `source` tinyint DEFAULT '1' COMMENT '来源：1会员自助 2前台代约 3电话',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `checkin_time` datetime DEFAULT NULL COMMENT '签到时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '取消原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `service_item_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `waitlist_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_employee` (`employee_id`),
  KEY `idx_room` (`room_id`),
  KEY `idx_date` (`booking_date`),
  KEY `idx_status` (`status`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `fk_booking_operator` (`operator_id`),
  CONSTRAINT `fk_booking_employee` FOREIGN KEY (`employee_id`) REFERENCES `pos_employee` (`id`),
  CONSTRAINT `fk_booking_member` FOREIGN KEY (`member_id`) REFERENCES `crm_member` (`id`),
  CONSTRAINT `fk_booking_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_booking_room` FOREIGN KEY (`room_id`) REFERENCES `biz_room` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_booking_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=427 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_care_suggestion_log`
--

DROP TABLE IF EXISTS `biz_care_suggestion_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_care_suggestion_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(50) DEFAULT NULL,
  `rule_id` bigint DEFAULT NULL,
  `rule_name` varchar(100) DEFAULT NULL,
  `template_id` bigint DEFAULT NULL,
  `title` varchar(200) DEFAULT NULL,
  `content` text,
  `recommended_services` text,
  `recommended_products` text,
  `trigger_reason` varchar(500) DEFAULT NULL,
  `matched_tags` varchar(500) DEFAULT NULL,
  `skin_type` varchar(50) DEFAULT NULL,
  `suggestion_type` varchar(50) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'pending',
  `send_channel` varchar(20) DEFAULT NULL,
  `send_time` datetime DEFAULT NULL,
  `view_time` datetime DEFAULT NULL,
  `accept_time` datetime DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(50) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_care_suggestion_rule`
--

DROP TABLE IF EXISTS `biz_care_suggestion_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_care_suggestion_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `rule_name` varchar(100) DEFAULT NULL,
  `rule_code` varchar(50) DEFAULT NULL,
  `condition_type` varchar(50) DEFAULT NULL,
  `condition_value` varchar(500) DEFAULT NULL,
  `template_id` bigint DEFAULT NULL,
  `suggestion_action` varchar(50) DEFAULT NULL,
  `priority` int DEFAULT '0',
  `cooldown_days` int DEFAULT '0',
  `status` int DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_care_suggestion_template`
--

DROP TABLE IF EXISTS `biz_care_suggestion_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_care_suggestion_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `template_name` varchar(100) DEFAULT NULL,
  `template_code` varchar(50) DEFAULT NULL,
  `template_type` varchar(50) DEFAULT NULL,
  `target_tags` varchar(500) DEFAULT NULL,
  `title` varchar(200) DEFAULT NULL,
  `content` text,
  `recommended_services` text,
  `recommended_products` text,
  `priority` int DEFAULT '0',
  `status` int DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_dcep_anti_double_spend_proof`
--

DROP TABLE IF EXISTS `biz_dcep_anti_double_spend_proof`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_dcep_anti_double_spend_proof` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_id` bigint DEFAULT NULL,
  `wallet_code` varchar(50) DEFAULT '',
  `nonce` bigint NOT NULL,
  `merkle_root` varchar(100) DEFAULT '',
  `dual_signature` varchar(1000) DEFAULT '',
  `wallet_proof` varchar(500) DEFAULT '',
  `merchant_proof` varchar(500) DEFAULT '',
  `timestamp` datetime NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'pending',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_wallet` (`wallet_id`),
  KEY `idx_nonce` (`nonce`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_dcep_contract_execution_log`
--

DROP TABLE IF EXISTS `biz_dcep_contract_execution_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_dcep_contract_execution_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_id` bigint DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `executor_type` varchar(20) NOT NULL,
  `executor_id` bigint DEFAULT NULL,
  `executor_name` varchar(100) DEFAULT '',
  `before_amount` decimal(18,2) DEFAULT NULL,
  `change_amount` decimal(18,2) DEFAULT NULL,
  `after_amount` decimal(18,2) DEFAULT NULL,
  `trigger_condition` varchar(200) DEFAULT '',
  `result` varchar(20) NOT NULL,
  `error_message` varchar(500) DEFAULT '',
  `execute_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_contract` (`contract_id`),
  KEY `idx_execute` (`execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_dcep_hardware_wallet`
--

DROP TABLE IF EXISTS `biz_dcep_hardware_wallet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_dcep_hardware_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(100) DEFAULT '',
  `wallet_name` varchar(100) DEFAULT '',
  `wallet_type` varchar(30) NOT NULL,
  `wallet_code` varchar(50) NOT NULL,
  `se_chip_id` varchar(100) DEFAULT '',
  `nfc_sim_id` varchar(100) DEFAULT '',
  `device_model` varchar(100) DEFAULT '',
  `brand` varchar(50) DEFAULT '',
  `public_key` varchar(500) DEFAULT '',
  `wallet_address` varchar(100) DEFAULT '',
  `operator_org` varchar(50) DEFAULT '',
  `balance` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_recharge` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_consumption` decimal(18,2) NOT NULL DEFAULT '0.00',
  `max_amount_per_transaction` decimal(18,2) DEFAULT NULL,
  `max_amount_per_day` decimal(18,2) DEFAULT NULL,
  `daily_remaining` decimal(18,2) DEFAULT NULL,
  `offline_support` tinyint NOT NULL DEFAULT '0',
  `offline_balance_limit` decimal(18,2) DEFAULT NULL,
  `offline_balance` decimal(18,2) NOT NULL DEFAULT '0.00',
  `biometric_enabled` tinyint NOT NULL DEFAULT '0',
  `pin_set` tinyint NOT NULL DEFAULT '0',
  `last_sync_time` datetime DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `activated_time` datetime DEFAULT NULL,
  `expired_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `pin_hash` varchar(255) DEFAULT NULL COMMENT 'PIN BCrypt 鍝堝笇',
  `private_key_enc` varchar(2048) DEFAULT NULL COMMENT '绉侀挜(AES鍔犲瘑)',
  `daily_used` decimal(15,2) DEFAULT '0.00' COMMENT '褰撴棩绱??娑堣垂',
  `daily_reset_at` datetime DEFAULT NULL COMMENT '褰撴棩闄愰?閲嶇疆鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_code` (`wallet_code`),
  KEY `idx_wallet_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_dcep_offline_transaction`
--

DROP TABLE IF EXISTS `biz_dcep_offline_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_dcep_offline_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `wallet_id` bigint DEFAULT NULL,
  `wallet_code` varchar(50) DEFAULT '',
  `offline_tx_no` varchar(50) NOT NULL,
  `offline_cert` varchar(500) DEFAULT '',
  `offline_signature` varchar(1000) DEFAULT '',
  `amount` decimal(18,2) NOT NULL,
  `offline_balance` decimal(18,2) NOT NULL,
  `tx_type` varchar(30) NOT NULL,
  `merchant_id` varchar(50) DEFAULT '',
  `merchant_name` varchar(200) DEFAULT '',
  `order_no` varchar(50) DEFAULT '',
  `dual_offline` tinyint NOT NULL DEFAULT '0',
  `sync_status` varchar(20) NOT NULL DEFAULT 'pending',
  `sync_time` datetime DEFAULT NULL,
  `sync_attempts` int NOT NULL DEFAULT '0',
  `conflict_detected` tinyint NOT NULL DEFAULT '0',
  `conflict_type` varchar(50) DEFAULT '',
  `conflict_resolution` varchar(100) DEFAULT '',
  `anti_double_spend_proof` varchar(500) DEFAULT '',
  `offline_tx_time` datetime DEFAULT NULL,
  `geo_location` varchar(200) DEFAULT '',
  `remark` varchar(500) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_wallet` (`wallet_id`),
  KEY `idx_tx_no` (`offline_tx_no`),
  KEY `idx_sync` (`sync_status`),
  KEY `idx_tx_time` (`offline_tx_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_dcep_smart_contract`
--

DROP TABLE IF EXISTS `biz_dcep_smart_contract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_dcep_smart_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `contract_name` varchar(100) NOT NULL,
  `contract_type` varchar(50) NOT NULL,
  `contract_address` varchar(100) DEFAULT '',
  `contract_code_hash` varchar(100) DEFAULT '',
  `contract_code` text,
  `wallet_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `escrow_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `released_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `used_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `refunded_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `currency` varchar(10) NOT NULL DEFAULT 'CNY',
  `trigger_conditions` text,
  `status` varchar(20) NOT NULL DEFAULT 'pending',
  `activated_time` datetime DEFAULT NULL,
  `expired_time` datetime DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `auto_execute_count` int NOT NULL DEFAULT '0',
  `manual_execute_count` int NOT NULL DEFAULT '0',
  `event_log` text,
  `remark` varchar(500) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expired` (`expired_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_dcep_sub_wallet`
--

DROP TABLE IF EXISTS `biz_dcep_sub_wallet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_dcep_sub_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `store_id` bigint DEFAULT NULL COMMENT '鎵?睘闂ㄥ簵锛坣ull=鎬婚儴缁熶竴璐︽埛锛',
  `parent_wallet_id` bigint NOT NULL COMMENT '鎵?睘涓婚挶鍖匢D',
  `parent_wallet_type` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '涓婚挶鍖呯被鍨? hardware/soft',
  `sub_wallet_code` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT '瀛愰挶鍖呯紪鍙凤紙鍏ㄥ眬鍞?竴锛',
  `sub_wallet_name` varchar(100) COLLATE utf8mb4_bin NOT NULL COMMENT '瀛愰挶鍖呭悕绉',
  `wallet_type` varchar(30) COLLATE utf8mb4_bin NOT NULL COMMENT '瀛愰挶鍖呯被鍨? store_fund/product_fund/project_fund/brand_share/settlement',
  `currency` varchar(10) COLLATE utf8mb4_bin NOT NULL DEFAULT 'CNY',
  `balance` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '鐙?珛璐︽埛浣欓?',
  `total_in` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '绱??杞?叆',
  `total_out` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '绱??杞?嚭',
  `frozen_balance` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '鍐荤粨閲戦?',
  `available_balance` decimal(15,2) GENERATED ALWAYS AS ((`balance` - `frozen_balance`)) STORED COMMENT '鍙?敤浣欓?锛堣?绠楀垪锛',
  `max_balance` decimal(15,2) DEFAULT NULL COMMENT '鍗曢挶鍖呬綑棰濅笂闄愶紙null=鏃犱笂闄愶級',
  `min_balance` decimal(15,2) DEFAULT '0.00' COMMENT '淇濆簳浣欓?',
  `transfer_in_ratio` decimal(5,4) DEFAULT NULL COMMENT '杞?叆鎵嬬画璐圭巼锛坣ull=鍏嶆墜缁?垂锛',
  `transfer_out_ratio` decimal(5,4) DEFAULT NULL COMMENT '杞?嚭鎵嬬画璐圭巼',
  `auto_sweep_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '鑷?姩褰掗泦: 0-鍚?1-鏄',
  `sweep_threshold` decimal(15,2) DEFAULT NULL COMMENT '鑷?姩褰掗泦瑙﹀彂闃堝?',
  `sweep_target_id` bigint DEFAULT NULL COMMENT '鑷?姩褰掗泦鐩?爣瀛愰挶鍖匢D',
  `settlement_mode` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '缁撶畻妯″紡: daily/weekly/monthly',
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'active' COMMENT '鐘舵?: active/frozen/closed',
  `priority` int DEFAULT '0' COMMENT '浼樺厛绾э紙鐢ㄤ簬鑷?姩褰掗泦椤哄簭锛',
  `tags` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '鏍囩?JSON: ["缇庣敳","鍌ㄥ?鍗?]',
  `remark` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `activated_time` datetime DEFAULT NULL,
  `closed_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_wallet_code` (`sub_wallet_code`),
  KEY `idx_parent_wallet` (`parent_wallet_id`),
  KEY `idx_tenant_store` (`tenant_id`,`store_id`),
  KEY `idx_wallet_type` (`wallet_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='鏁板瓧浜烘皯甯佸瓙閽卞寘锛堢嫭绔嬭处鏈?級';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_dcep_sub_wallet_ledger`
--

DROP TABLE IF EXISTS `biz_dcep_sub_wallet_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_dcep_sub_wallet_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `sub_wallet_id` bigint NOT NULL COMMENT '瀛愰挶鍖匢D',
  `ledger_no` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT '璐︽湰缂栧彿锛堟祦姘村彿锛',
  `tx_type` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT '浜ゆ槗绫诲瀷: transfer_in/transfer_out/consume/refund/freeze/unfreeze/settle/adjust',
  `amount` decimal(15,2) NOT NULL COMMENT '浜ゆ槗閲戦?锛堟?鏁帮級',
  `balance_before` decimal(15,2) NOT NULL COMMENT '鍙樺姩鍓嶄綑棰',
  `balance_after` decimal(15,2) NOT NULL COMMENT '鍙樺姩鍚庝綑棰',
  `fee` decimal(15,2) DEFAULT '0.00' COMMENT '鎵嬬画璐',
  `opposite_wallet_id` bigint DEFAULT NULL COMMENT '瀵规墜瀛愰挶鍖匢D锛堣浆璐︽椂锛',
  `opposite_wallet_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '瀵规墜閽卞寘鍚嶇О',
  `order_no` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '鍏宠仈璁㈠崟鍙',
  `merchant_id` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '鍟嗘埛鍙',
  `merchant_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '鍟嗘埛鍚嶇О',
  `operator_id` bigint DEFAULT NULL COMMENT '鎿嶄綔浜',
  `operator_name` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '鎿嶄綔浜哄?鍚',
  `remark` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sub_wallet` (`sub_wallet_id`),
  KEY `idx_ledger_no` (`ledger_no`),
  KEY `idx_tx_type` (`tx_type`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='瀛愰挶鍖呯嫭绔嬭处鏈?祦姘达紙澶嶅紡璁拌处锛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_detection_report`
--

DROP TABLE IF EXISTS `biz_detection_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_detection_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(100) DEFAULT NULL,
  `report_no` varchar(64) DEFAULT NULL,
  `report_type` varchar(32) DEFAULT NULL,
  `skin_detection_id` bigint DEFAULT NULL,
  `hair_detection_id` bigint DEFAULT NULL,
  `nail_detection_id` bigint DEFAULT NULL,
  `overall_score` decimal(6,2) DEFAULT NULL,
  `overall_assessment` text,
  `priority_concerns` text,
  `recommended_services` text,
  `recommended_products` text,
  `home_care_advice` text,
  `treatment_cycle` int DEFAULT NULL,
  `expected_outcome` varchar(500) DEFAULT NULL,
  `report_pdf_url` varchar(500) DEFAULT NULL,
  `report_images` text,
  `customer_viewed` int DEFAULT '0',
  `customer_feedback` text,
  `check_employee_id` bigint DEFAULT NULL,
  `check_employee_name` varchar(100) DEFAULT NULL,
  `generate_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(32) DEFAULT 'draft',
  `remark` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_type` (`report_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_detection_solution`
--

DROP TABLE IF EXISTS `biz_detection_solution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_detection_solution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `solution_name` varchar(200) NOT NULL,
  `solution_code` varchar(64) DEFAULT NULL,
  `apply_type` varchar(32) DEFAULT NULL,
  `trigger_conditions` varchar(500) DEFAULT NULL,
  `score_threshold` int DEFAULT NULL,
  `priority` int DEFAULT '5',
  `recommended_services` text,
  `recommended_products` text,
  `home_care_advice` text,
  `treatment_cycle` int DEFAULT NULL,
  `expected_outcome` varchar(500) DEFAULT NULL,
  `status` int DEFAULT '1',
  `remark` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_apply` (`apply_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_employee_schedule`
--

DROP TABLE IF EXISTS `biz_employee_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_employee_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '排班ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `employee_id` bigint NOT NULL COMMENT '技师ID',
  `employee_name` varchar(50) DEFAULT NULL COMMENT '技师姓名',
  `work_date` date NOT NULL COMMENT '工作日期',
  `shift_type` tinyint DEFAULT '4' COMMENT '班次：1早班 2中班 3晚班 4全天',
  `start_time` time DEFAULT NULL COMMENT '开始时间',
  `end_time` time DEFAULT NULL COMMENT '结束时间',
  `max_bookings` int DEFAULT '8' COMMENT '最大预约数',
  `status` tinyint DEFAULT '1' COMMENT '状态：1正常 2请假 3休息',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_employee` (`employee_id`),
  KEY `idx_date` (`work_date`),
  KEY `idx_tenant` (`tenant_id`),
  CONSTRAINT `fk_schedule_employee` FOREIGN KEY (`employee_id`) REFERENCES `pos_employee` (`id`),
  CONSTRAINT `fk_schedule_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技师排班表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_expiry_alert_log`
--

DROP TABLE IF EXISTS `biz_expiry_alert_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_expiry_alert_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `inventory_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_name` varchar(200) DEFAULT NULL,
  `batch_no` varchar(50) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `production_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `days_to_expiry` int DEFAULT NULL,
  `alert_level` varchar(20) DEFAULT NULL,
  `alert_type` varchar(20) DEFAULT NULL,
  `alert_message` varchar(500) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'pending',
  `handle_method` varchar(100) DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL,
  `handler_name` varchar(50) DEFAULT NULL,
  `handle_time` datetime DEFAULT NULL,
  `handle_remark` varchar(500) DEFAULT NULL,
  `notified` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_inventory_id` (`inventory_id`),
  KEY `idx_expiry_date` (`expiry_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_expiry_alert_rule`
--

DROP TABLE IF EXISTS `biz_expiry_alert_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_expiry_alert_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `rule_name` varchar(100) NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `critical_days` int DEFAULT '7',
  `warning_days` int DEFAULT '30',
  `recipients` varchar(500) DEFAULT NULL,
  `notify_channels` varchar(100) DEFAULT NULL,
  `auto_handle` tinyint DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_hair_detection`
--

DROP TABLE IF EXISTS `biz_hair_detection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_hair_detection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(100) DEFAULT NULL,
  `detection_no` varchar(64) DEFAULT NULL,
  `hair_type` varchar(32) DEFAULT NULL,
  `overall_score` decimal(6,2) DEFAULT NULL,
  `moisture_score` decimal(6,2) DEFAULT NULL,
  `oil_score` decimal(6,2) DEFAULT NULL,
  `elasticity_score` decimal(6,2) DEFAULT NULL,
  `diameter` varchar(32) DEFAULT NULL,
  `density` varchar(32) DEFAULT NULL,
  `damage_score` decimal(6,2) DEFAULT NULL,
  `breakage_risk` decimal(6,2) DEFAULT NULL,
  `dandruff_level` int DEFAULT NULL,
  `scalp_sensitivity` decimal(6,2) DEFAULT NULL,
  `hair_loss_risk` decimal(6,2) DEFAULT NULL,
  `split_end_score` decimal(6,2) DEFAULT NULL,
  `dye_count` int DEFAULT NULL,
  `perm_count` int DEFAULT NULL,
  `main_concerns` varchar(500) DEFAULT NULL,
  `diagnosis_advice` text,
  `detection_device` varchar(64) DEFAULT NULL,
  `detection_images` text,
  `check_store_id` bigint DEFAULT NULL,
  `check_employee_id` bigint DEFAULT NULL,
  `check_employee_name` varchar(100) DEFAULT NULL,
  `detection_date` datetime DEFAULT NULL,
  `next_check_date` datetime DEFAULT NULL,
  `status` varchar(32) DEFAULT 'pending',
  `remark` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_type` (`hair_type`),
  KEY `idx_date` (`detection_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_iot_device`
--

DROP TABLE IF EXISTS `biz_iot_device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_iot_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `store_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `device_code` varchar(64) NOT NULL COMMENT '璁惧?缂栧彿',
  `device_name` varchar(128) DEFAULT NULL COMMENT '璁惧?鍚嶇О',
  `device_type` varchar(32) DEFAULT 'skin_analyzer' COMMENT '璁惧?绫诲瀷',
  `device_model` varchar(64) DEFAULT NULL COMMENT '璁惧?鍨嬪彿',
  `brand` varchar(64) DEFAULT NULL COMMENT '鍝佺墝',
  `firmware_version` varchar(32) DEFAULT NULL COMMENT '鍥轰欢鐗堟湰',
  `software_version` varchar(32) DEFAULT NULL COMMENT '杞?欢鐗堟湰',
  `mac_address` varchar(64) DEFAULT NULL COMMENT 'MAC鍦板潃',
  `serial_number` varchar(128) DEFAULT NULL COMMENT '搴忓垪鍙',
  `protocol` varchar(16) DEFAULT 'mqtt' COMMENT '閫氫俊鍗忚?',
  `ip_address` varchar(64) DEFAULT NULL COMMENT 'IP鍦板潃',
  `mqtt_topic` varchar(256) DEFAULT NULL COMMENT 'MQTT Topic',
  `room_id` bigint DEFAULT NULL COMMENT '鍏宠仈鎴块棿ID',
  `room_name` varchar(128) DEFAULT NULL COMMENT '鎴块棿鍚嶇О',
  `location` varchar(256) DEFAULT NULL COMMENT '瀹夎?浣嶇疆',
  `capabilities` text COMMENT '璁惧?鑳藉姏JSON',
  `status` varchar(16) DEFAULT 'offline' COMMENT '鐘舵?',
  `last_online_time` datetime DEFAULT NULL COMMENT '鏈?悗鍦ㄧ嚎鏃堕棿',
  `last_data_time` datetime DEFAULT NULL COMMENT '鏈?悗涓婃姤鏃堕棿',
  `activated` tinyint DEFAULT '0' COMMENT '鏄?惁婵?椿',
  `activated_time` datetime DEFAULT NULL COMMENT '婵?椿鏃堕棿',
  `purchase_date` datetime DEFAULT NULL COMMENT '閲囪喘鏃ユ湡',
  `warranty_expires` datetime DEFAULT NULL COMMENT '淇濅慨鎴??',
  `total_reports` bigint DEFAULT '0' COMMENT '鎬讳笂鎶ユ?鏁',
  `remark` varchar(500) DEFAULT NULL COMMENT '澶囨敞',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_code` (`device_code`),
  KEY `idx_device_type` (`device_type`),
  KEY `idx_status` (`status`),
  KEY `idx_room_id` (`room_id`),
  KEY `idx_protocol` (`protocol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IoT璁惧?妗ｆ?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_iot_device_alert`
--

DROP TABLE IF EXISTS `biz_iot_device_alert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_iot_device_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `store_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `device_id` bigint DEFAULT NULL COMMENT '璁惧?ID',
  `device_name` varchar(128) DEFAULT NULL COMMENT '璁惧?鍚嶇О',
  `alert_level` varchar(16) DEFAULT 'warning' COMMENT '鍛婅?绾у埆',
  `alert_type` varchar(32) DEFAULT 'error' COMMENT '鍛婅?绫诲瀷',
  `title` varchar(256) DEFAULT NULL COMMENT '鏍囬?',
  `message` varchar(1000) DEFAULT NULL COMMENT '璇︽儏',
  `status` varchar(16) DEFAULT 'pending' COMMENT '鐘舵?',
  `handler_id` bigint DEFAULT NULL COMMENT '澶勭悊浜篒D',
  `handler_name` varchar(64) DEFAULT NULL COMMENT '澶勭悊浜哄?鍚',
  `handle_time` datetime DEFAULT NULL COMMENT '澶勭悊鏃堕棿',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '澶勭悊澶囨敞',
  `alert_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍛婅?鏃堕棿',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_alert_level` (`alert_level`),
  KEY `idx_status` (`status`),
  KEY `idx_alert_time` (`alert_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IoT璁惧?鍛婅?';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_iot_device_data`
--

DROP TABLE IF EXISTS `biz_iot_device_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_iot_device_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `store_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `device_id` bigint NOT NULL COMMENT '璁惧?ID',
  `device_code` varchar(64) DEFAULT NULL COMMENT '璁惧?缂栧彿',
  `device_type` varchar(32) DEFAULT NULL COMMENT '璁惧?绫诲瀷',
  `member_id` bigint DEFAULT NULL COMMENT '瀹㈡埛ID',
  `member_name` varchar(128) DEFAULT NULL COMMENT '瀹㈡埛濮撳悕',
  `order_id` bigint DEFAULT NULL COMMENT '鍏宠仈璁㈠崟ID',
  `data_type` varchar(32) DEFAULT 'detection' COMMENT '鏁版嵁绫诲瀷',
  `metrics` text COMMENT '鎸囨爣JSON',
  `primary_metric` decimal(15,4) DEFAULT NULL COMMENT '涓绘寚鏍',
  `secondary_metric` decimal(15,4) DEFAULT NULL COMMENT '杈呭姪鎸囨爣',
  `image_url` varchar(500) DEFAULT NULL COMMENT '妫?祴鍥剧墖',
  `report_url` varchar(500) DEFAULT NULL COMMENT '妫?祴鎶ュ憡',
  `source` varchar(32) DEFAULT 'device' COMMENT '鏁版嵁鏉ユ簮',
  `collect_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '閲囬泦鏃堕棿',
  `device_report_time` datetime DEFAULT NULL COMMENT '璁惧?涓婃姤鏃堕棿',
  `valid` tinyint DEFAULT '1' COMMENT '鏄?惁鏈夋晥',
  `remark` varchar(500) DEFAULT NULL COMMENT '澶囨敞',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_data_type` (`data_type`),
  KEY `idx_collect_time` (`collect_time`),
  KEY `idx_device_collect` (`device_id`,`collect_time`),
  KEY `idx_member_type_time` (`member_id`,`data_type`,`collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IoT璁惧?鏁版嵁閲囬泦璁板綍';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_iot_trigger_log`
--

DROP TABLE IF EXISTS `biz_iot_trigger_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_iot_trigger_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `rule_id` bigint DEFAULT NULL COMMENT '瑙勫垯ID',
  `rule_name` varchar(128) DEFAULT NULL COMMENT '瑙勫垯鍚嶇О',
  `device_id` bigint DEFAULT NULL COMMENT '璁惧?ID',
  `data_id` bigint DEFAULT NULL COMMENT '鏁版嵁ID',
  `member_id` bigint DEFAULT NULL COMMENT '瀹㈡埛ID',
  `trigger_condition` text COMMENT '瑙﹀彂鏉′欢',
  `actual_metric` text COMMENT '瀹為檯鎸囨爣',
  `status` varchar(16) DEFAULT 'pending' COMMENT '鎵ц?鐘舵?',
  `result` varchar(500) DEFAULT NULL COMMENT '鎵ц?缁撴灉',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '閿欒?淇℃伅',
  `execution_ms` bigint DEFAULT '0' COMMENT '鎵ц?鑰楁椂',
  `trigger_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '瑙﹀彂鏃堕棿',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_status` (`status`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_trigger_time` (`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IoT鑱斿姩瑙﹀彂鏃ュ織';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_iot_trigger_rule`
--

DROP TABLE IF EXISTS `biz_iot_trigger_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_iot_trigger_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '绉熸埛ID',
  `store_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `rule_name` varchar(128) NOT NULL COMMENT '瑙勫垯鍚嶇О',
  `device_type` varchar(32) DEFAULT NULL COMMENT '璁惧?绫诲瀷',
  `data_type` varchar(32) DEFAULT NULL COMMENT '鏁版嵁绫诲瀷',
  `trigger_condition` text COMMENT '瑙﹀彂鏉′欢JSON',
  `trigger_action` text COMMENT '瑙﹀彂鍔ㄤ綔JSON',
  `action_type` varchar(32) DEFAULT 'send_coupon' COMMENT '鍔ㄤ綔绫诲瀷',
  `priority` int DEFAULT '5' COMMENT '浼樺厛绾',
  `enabled` tinyint DEFAULT '1' COMMENT '鏄?惁鍚?敤',
  `trigger_count` int DEFAULT '0' COMMENT '瑙﹀彂娆℃暟',
  `last_trigger_time` datetime DEFAULT NULL COMMENT '鏈?悗瑙﹀彂鏃堕棿',
  `remark` varchar(500) DEFAULT NULL COMMENT '澶囨敞',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_type` (`device_type`),
  KEY `idx_data_type` (`data_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IoT鑱斿姩瑙﹀彂瑙勫垯';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_level_benefit`
--

DROP TABLE IF EXISTS `biz_level_benefit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_level_benefit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `level_id` bigint NOT NULL COMMENT '绛夌骇ID',
  `benefit_type` varchar(32) NOT NULL COMMENT '鏉冪泭绫诲瀷锛歞iscount/commission/priority/exclusive/training',
  `benefit_name` varchar(128) NOT NULL COMMENT '鏉冪泭鍚嶇О',
  `benefit_value` varchar(255) DEFAULT NULL COMMENT '鏉冪泭鍊',
  `description` varchar(500) DEFAULT NULL COMMENT '鏉冪泭璇存槑',
  `sort_order` int DEFAULT '0' COMMENT '鎺掑簭',
  `status` tinyint DEFAULT '1' COMMENT '鐘舵?锛?-鍋滅敤锛?-鍚?敤',
  `deleted` tinyint DEFAULT '0' COMMENT '鏄?惁鍒犻櫎锛?-鍚︼紝1-鏄',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_level_id` (`level_id`),
  KEY `idx_benefit_type` (`benefit_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎶?笀绛夌骇鏉冪泭琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_level_evaluation`
--

DROP TABLE IF EXISTS `biz_level_evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_level_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL COMMENT '鍛樺伐ID',
  `from_level_id` bigint DEFAULT NULL COMMENT '鍘熺瓑绾?D',
  `to_level_id` bigint NOT NULL COMMENT '璇勫畾绛夌骇ID',
  `evaluation_type` varchar(32) DEFAULT 'auto' COMMENT '璇勫畾绫诲瀷锛歛uto/manual',
  `result` varchar(32) NOT NULL COMMENT '璇勫畾缁撴灉锛歶pgrade/downgrade/keep',
  `evaluation_date` date NOT NULL COMMENT '璇勫畾鏃ユ湡',
  `total_sales` decimal(15,2) DEFAULT '0.00' COMMENT '褰撳墠绱??涓氱哗',
  `rating_score` decimal(5,2) DEFAULT '0.00' COMMENT '褰撳墠濂借瘎鐜',
  `service_months` int DEFAULT '0' COMMENT '褰撳墠鏈嶅姟鏈堟暟',
  `complaint_rate` decimal(5,2) DEFAULT '0.00' COMMENT '鎶曡瘔鐜',
  `evaluation_note` varchar(1000) DEFAULT NULL COMMENT '璇勫畾璇存槑',
  `operator_id` bigint DEFAULT NULL COMMENT '鎿嶄綔浜篒D',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '鎿嶄綔浜哄?鍚',
  `status` varchar(32) DEFAULT 'active' COMMENT '鐘舵?锛歱ending/active/reverted',
  `effective_time` datetime DEFAULT NULL COMMENT '鐢熸晥鏃堕棿',
  `store_id` bigint DEFAULT NULL COMMENT '闂ㄥ簵ID',
  `deleted` tinyint DEFAULT '0' COMMENT '鏄?惁鍒犻櫎锛?-鍚︼紝1-鏄',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_employee_id` (`employee_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`),
  KEY `idx_evaluation_date` (`evaluation_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎶?笀绛夌骇璇勫畾璁板綍琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_material_package`
--

DROP TABLE IF EXISTS `biz_material_package`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_material_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `package_code` varchar(50) DEFAULT NULL,
  `package_name` varchar(100) DEFAULT NULL,
  `package_type` varchar(50) DEFAULT NULL,
  `purpose` varchar(200) DEFAULT NULL,
  `main_image` varchar(500) DEFAULT NULL,
  `total_cost` decimal(10,2) DEFAULT NULL,
  `sale_price` decimal(10,2) DEFAULT NULL,
  `discount_rate` decimal(5,4) DEFAULT NULL,
  `final_price` decimal(10,2) DEFAULT NULL,
  `style_count` int DEFAULT NULL,
  `serve_count` int DEFAULT NULL,
  `apply_scope` varchar(100) DEFAULT NULL,
  `tags` varchar(500) DEFAULT NULL,
  `activity_id` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `sales_count` int DEFAULT '0',
  `stock_quantity` int DEFAULT '0',
  `creator_id` bigint DEFAULT NULL,
  `creator_name` varchar(50) DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_package_type` (`package_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_member_skin_profile`
--

DROP TABLE IF EXISTS `biz_member_skin_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_member_skin_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(50) DEFAULT NULL,
  `skin_type` varchar(50) DEFAULT NULL,
  `skin_concerns` text,
  `sensitivity_score` int DEFAULT NULL,
  `moisture_score` int DEFAULT NULL,
  `oil_score` int DEFAULT NULL,
  `pigmentation_score` int DEFAULT NULL,
  `wrinkle_score` int DEFAULT NULL,
  `pore_score` int DEFAULT NULL,
  `last_check_time` datetime DEFAULT NULL,
  `check_store_id` bigint DEFAULT NULL,
  `check_device` varchar(100) DEFAULT NULL,
  `check_images` text,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_member_style_preference`
--

DROP TABLE IF EXISTS `biz_member_style_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_member_style_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `preferred_type` varchar(50) DEFAULT NULL,
  `preferred_styles` text,
  `preferred_colors` text,
  `avoid_colors` text,
  `preferred_length` varchar(20) DEFAULT NULL,
  `preferred_shape` varchar(20) DEFAULT NULL,
  `allergy_ingredients` text,
  `preferred_technician_id` bigint DEFAULT NULL,
  `preferred_technician_name` varchar(50) DEFAULT NULL,
  `special_note` text,
  `recent_styles` text,
  `favorite_styles` text,
  `history_styles` text,
  `recommend_count` int DEFAULT '0',
  `remark` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_nail_detection`
--

DROP TABLE IF EXISTS `biz_nail_detection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_nail_detection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(100) DEFAULT NULL,
  `detection_no` varchar(64) DEFAULT NULL,
  `nail_type` varchar(32) DEFAULT NULL,
  `overall_score` decimal(6,2) DEFAULT NULL,
  `hardness_score` decimal(6,2) DEFAULT NULL,
  `gloss_score` decimal(6,2) DEFAULT NULL,
  `moisture_score` decimal(6,2) DEFAULT NULL,
  `periungual_score` decimal(6,2) DEFAULT NULL,
  `surface_smoothness` decimal(6,2) DEFAULT NULL,
  `toughness_score` decimal(6,2) DEFAULT NULL,
  `paronychia_risk` decimal(6,2) DEFAULT NULL,
  `fungal_risk` decimal(6,2) DEFAULT NULL,
  `vertical_cracks` int DEFAULT NULL,
  `horizontal_ridges` int DEFAULT NULL,
  `hangnail_count` int DEFAULT NULL,
  `break_history` int DEFAULT NULL,
  `frequent_manicure` int DEFAULT NULL,
  `gel_manicure_count` int DEFAULT NULL,
  `main_concerns` varchar(500) DEFAULT NULL,
  `diagnosis_advice` text,
  `detection_device` varchar(64) DEFAULT NULL,
  `detection_images` text,
  `check_store_id` bigint DEFAULT NULL,
  `check_employee_id` bigint DEFAULT NULL,
  `check_employee_name` varchar(100) DEFAULT NULL,
  `detection_date` datetime DEFAULT NULL,
  `next_check_date` datetime DEFAULT NULL,
  `status` varchar(32) DEFAULT 'pending',
  `remark` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_type` (`nail_type`),
  KEY `idx_date` (`detection_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_overseas_payment`
--

DROP TABLE IF EXISTS `biz_overseas_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_overseas_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `store_id` bigint DEFAULT NULL COMMENT '门店ID',
  `order_id` bigint DEFAULT NULL COMMENT '业务订单ID',
  `order_type` varchar(32) DEFAULT 'service_order' COMMENT '订单类型',
  `order_no` varchar(64) NOT NULL COMMENT '业务订单号',
  `member_id` bigint DEFAULT NULL COMMENT '客户ID',
  `member_name` varchar(128) DEFAULT NULL COMMENT '客户姓名',
  `member_email` varchar(128) DEFAULT NULL COMMENT '客户邮箱',
  `gateway_code` varchar(32) NOT NULL COMMENT '网关编码',
  `gateway_intent_id` varchar(128) DEFAULT NULL COMMENT '网关支付意图ID',
  `gateway_payment_id` varchar(128) DEFAULT NULL COMMENT '网关支付ID',
  `gateway_client_secret` varchar(256) DEFAULT NULL COMMENT '网关客户端密钥',
  `amount` decimal(15,2) NOT NULL COMMENT '交易金额',
  `currency` varchar(8) DEFAULT 'USD' COMMENT '货币',
  `fee` decimal(15,2) DEFAULT '0.00' COMMENT '手续费',
  `net_amount` decimal(15,2) DEFAULT '0.00' COMMENT '实际到账',
  `payment_method` varchar(32) DEFAULT NULL COMMENT '支付方式',
  `payment_method_detail` varchar(256) DEFAULT NULL COMMENT '支付方式详情',
  `status` varchar(16) DEFAULT 'pending' COMMENT '状态',
  `failure_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `refund_amount` decimal(15,2) DEFAULT '0.00' COMMENT '退款金额',
  `refund_reason` varchar(500) DEFAULT NULL COMMENT '退款原因',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `paid_at` datetime DEFAULT NULL COMMENT '支付确认时间',
  `source` varchar(32) DEFAULT 'web' COMMENT '支付来源',
  `ip_address` varchar(64) DEFAULT NULL COMMENT 'IP地址',
  `device_fingerprint` varchar(128) DEFAULT NULL COMMENT '设备指纹',
  `three_ds_result` varchar(64) DEFAULT NULL COMMENT '3DS验证',
  `fraud_score` decimal(3,2) DEFAULT NULL COMMENT '防欺诈评分',
  `country_code` varchar(8) DEFAULT NULL COMMENT '国家',
  `metadata` text COMMENT '扩展数据JSON',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_gateway_code` (`gateway_code`),
  KEY `idx_status` (`status`),
  KEY `idx_intent_id` (`gateway_intent_id`),
  KEY `idx_payment_id` (`gateway_payment_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='海外支付交易';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_overseas_payment_gateway`
--

DROP TABLE IF EXISTS `biz_overseas_payment_gateway`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_overseas_payment_gateway` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `store_id` bigint DEFAULT NULL COMMENT '门店ID',
  `gateway_code` varchar(32) NOT NULL COMMENT '网关编码',
  `gateway_name` varchar(128) DEFAULT NULL COMMENT '网关名称',
  `api_base_url` varchar(256) DEFAULT NULL COMMENT 'API Base URL',
  `publishable_key` varchar(512) DEFAULT NULL COMMENT 'Publishable Key',
  `secret_key` varchar(512) DEFAULT NULL COMMENT 'Secret Key',
  `webhook_secret` varchar(512) DEFAULT NULL COMMENT 'Webhook Secret',
  `merchant_id` varchar(128) DEFAULT NULL COMMENT '商户ID',
  `account_id` varchar(128) DEFAULT NULL COMMENT '账户ID',
  `supported_currencies` varchar(500) DEFAULT NULL COMMENT '支持货币JSON',
  `payment_methods` varchar(500) DEFAULT NULL COMMENT '支付方式JSON',
  `fee_rate` decimal(5,4) DEFAULT '0.0000' COMMENT '手续费率',
  `fixed_fee` decimal(15,2) DEFAULT '0.00' COMMENT '固定费用',
  `max_amount_per_transaction` decimal(15,2) DEFAULT '0.00' COMMENT '单笔限额',
  `max_amount_per_day` decimal(15,2) DEFAULT '0.00' COMMENT '单日限额',
  `environment` varchar(16) DEFAULT 'sandbox' COMMENT '环境',
  `api_version` varchar(32) DEFAULT NULL COMMENT 'API版本',
  `country_code` varchar(8) DEFAULT NULL COMMENT '国家代码',
  `status` tinyint DEFAULT '1' COMMENT '状态',
  `health_status` varchar(16) DEFAULT 'unknown' COMMENT '健康状态',
  `last_health_check` datetime DEFAULT NULL COMMENT '最新检查时间',
  `metadata` text COMMENT '元数据JSON',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_gateway_code` (`gateway_code`),
  KEY `idx_status` (`status`),
  KEY `idx_environment` (`environment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='海外支付网关';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_payment_route_rule`
--

DROP TABLE IF EXISTS `biz_payment_route_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_payment_route_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `rule_name` varchar(128) NOT NULL COMMENT '规则名称',
  `priority` int DEFAULT '5' COMMENT '优先级',
  `currency` varchar(8) DEFAULT NULL COMMENT '货币',
  `country_code` varchar(8) DEFAULT NULL COMMENT '国家',
  `min_amount` decimal(15,2) DEFAULT '0.00' COMMENT '最小金额',
  `max_amount` decimal(15,2) DEFAULT '0.00' COMMENT '最大金额',
  `payment_method` varchar(32) DEFAULT NULL COMMENT '支付方式',
  `gateway_code` varchar(32) NOT NULL COMMENT '目标网关',
  `fee_rate` decimal(5,4) DEFAULT '0.0000' COMMENT '费率',
  `priority_strategy` varchar(16) DEFAULT 'cost' COMMENT '策略',
  `status` tinyint DEFAULT '1' COMMENT '状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_currency` (`currency`),
  KEY `idx_country` (`country_code`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_payment_webhook_log`
--

DROP TABLE IF EXISTS `biz_payment_webhook_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_payment_webhook_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `gateway_code` varchar(32) NOT NULL COMMENT '网关编码',
  `event_type` varchar(64) DEFAULT NULL COMMENT '事件类型',
  `gateway_event_id` varchar(128) DEFAULT NULL COMMENT '网关事件ID',
  `gateway_payment_id` varchar(128) DEFAULT NULL COMMENT '网关支付ID',
  `payload` text COMMENT '事件载荷',
  `signature` varchar(512) DEFAULT NULL COMMENT '签名',
  `verify_status` tinyint DEFAULT '0' COMMENT '验证状态',
  `process_status` varchar(16) DEFAULT 'pending' COMMENT '处理状态',
  `process_time` datetime DEFAULT NULL COMMENT '处理时间',
  `retry_count` int DEFAULT '0' COMMENT '重试次数',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `receive_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  `source_ip` varchar(64) DEFAULT NULL COMMENT '源IP',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`gateway_event_id`),
  KEY `idx_gateway_code` (`gateway_code`),
  KEY `idx_process_status` (`process_status`),
  KEY `idx_receive_time` (`receive_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付Webhook日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_resource_match_rule`
--

DROP TABLE IF EXISTS `biz_resource_match_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_resource_match_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `service_id` bigint DEFAULT NULL,
  `service_name` varchar(100) DEFAULT NULL,
  `required_room_types` varchar(200) DEFAULT NULL,
  `min_room_level` int DEFAULT NULL,
  `required_skills` varchar(200) DEFAULT NULL,
  `min_technician_level` int DEFAULT NULL,
  `duration_minutes` int DEFAULT NULL,
  `required_technician_count` int DEFAULT '1',
  `required_room_capacity` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `need_dual_technician` tinyint DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_service_id` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_room`
--

DROP TABLE IF EXISTS `biz_room`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_room` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '房间ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `store_id` bigint DEFAULT NULL COMMENT '门店ID',
  `room_name` varchar(50) NOT NULL COMMENT '房间名',
  `room_type` varchar(20) DEFAULT NULL COMMENT '房间类型',
  `capacity` int DEFAULT '1' COMMENT '容纳人数',
  `equipment` varchar(255) DEFAULT NULL COMMENT '设备配置',
  `status` tinyint DEFAULT '1' COMMENT '状态：1可用 2维修 3停用',
  `sort_no` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_store` (`store_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_room_store` FOREIGN KEY (`store_id`) REFERENCES `pos_store` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_room_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房间表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_service`
--

DROP TABLE IF EXISTS `biz_service`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_service` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `service_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `service_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_id` bigint DEFAULT NULL COMMENT '分类',
  `unit_price` decimal(10,2) NOT NULL,
  `member_price` decimal(10,2) DEFAULT NULL COMMENT '会员价',
  `unit` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT '次',
  `duration` int DEFAULT '0' COMMENT '时长(分钟)',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_package` tinyint DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `sort_order` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_biz_service_store` (`store_id`),
  KEY `idx_biz_service_cat` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务/产品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_service_category`
--

DROP TABLE IF EXISTS `biz_service_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_service_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `category_code` varchar(64) NOT NULL COMMENT '分类编码',
  `category_name` varchar(128) NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父级分类（0=根）',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_category` (`tenant_id`,`category_code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务项目分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_service_item`
--

DROP TABLE IF EXISTS `biz_service_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_service_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `service_code` varchar(64) NOT NULL COMMENT '项目编码',
  `service_name` varchar(128) NOT NULL COMMENT '项目名称',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `unit_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '标准价',
  `member_price` decimal(10,2) DEFAULT NULL COMMENT '会员价',
  `cost_price` decimal(10,2) DEFAULT '0.00' COMMENT '成本价（耗材加权）',
  `duration` int DEFAULT '0' COMMENT '服务时长(分钟)',
  `unit` varchar(16) DEFAULT '次' COMMENT '单位',
  `description` varchar(500) DEFAULT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  `sop` text COMMENT 'SOP 操作流程',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `sort_order` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`service_code`),
  KEY `idx_category` (`category_id`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_service_material`
--

DROP TABLE IF EXISTS `biz_service_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_service_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT '1' COMMENT '租户ID',
  `service_id` bigint NOT NULL COMMENT '服务ID (biz_service.id)',
  `product_id` bigint NOT NULL COMMENT '耗材ID (stock_product.id)',
  `quantity` decimal(10,2) NOT NULL DEFAULT '1.00' COMMENT '单次消耗数量',
  `unit` varchar(16) DEFAULT NULL COMMENT '单位（冗余展示）',
  `is_required` tinyint DEFAULT '1' COMMENT '是否必需:1必需0可选',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '软删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_service_product` (`tenant_id`,`service_id`,`product_id`),
  KEY `idx_service` (`service_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务-耗材消耗关系表（BOM）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_skin_detection`
--

DROP TABLE IF EXISTS `biz_skin_detection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_skin_detection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(100) DEFAULT NULL,
  `detection_no` varchar(64) DEFAULT NULL,
  `skin_type` varchar(32) DEFAULT NULL,
  `overall_score` decimal(6,2) DEFAULT NULL,
  `moisture_score` decimal(6,2) DEFAULT NULL,
  `oil_score` decimal(6,2) DEFAULT NULL,
  `elasticity_score` decimal(6,2) DEFAULT NULL,
  `pore_score` decimal(6,2) DEFAULT NULL,
  `pigmentation_score` decimal(6,2) DEFAULT NULL,
  `wrinkle_score` decimal(6,2) DEFAULT NULL,
  `sensitivity_score` decimal(6,2) DEFAULT NULL,
  `acne_level` int DEFAULT NULL,
  `dark_circle_level` int DEFAULT NULL,
  `spot_count` int DEFAULT NULL,
  `fine_line_count` int DEFAULT NULL,
  `main_concerns` varchar(500) DEFAULT NULL,
  `diagnosis_advice` text,
  `detection_device` varchar(64) DEFAULT NULL,
  `device_serial` varchar(100) DEFAULT NULL,
  `detection_images` text,
  `check_store_id` bigint DEFAULT NULL,
  `check_employee_id` bigint DEFAULT NULL,
  `check_employee_name` varchar(100) DEFAULT NULL,
  `detection_date` datetime DEFAULT NULL,
  `next_check_date` datetime DEFAULT NULL,
  `status` varchar(32) DEFAULT 'pending',
  `remark` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_type` (`skin_type`),
  KEY `idx_date` (`detection_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_spa_scene_mode`
--

DROP TABLE IF EXISTS `biz_spa_scene_mode`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_spa_scene_mode` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户ID',
  `store_id` bigint NOT NULL DEFAULT '0' COMMENT '门店ID',
  `scene_code` varchar(32) NOT NULL COMMENT '场景编码 SINGLE/COUPLE/GIRLFRIEND/FAMILY',
  `scene_name` varchar(64) NOT NULL COMMENT '场景名称',
  `scene_icon` varchar(128) DEFAULT NULL COMMENT '场景图标',
  `cover_img` varchar(256) DEFAULT NULL COMMENT '封面图',
  `description` varchar(512) DEFAULT NULL COMMENT '场景描述',
  `guest_count` int NOT NULL DEFAULT '1' COMMENT '人数',
  `duration_min` int NOT NULL DEFAULT '60' COMMENT '建议时长(分钟)',
  `base_price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '基础价格',
  `tags` varchar(256) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_store_code` (`tenant_id`,`store_id`,`scene_code`,`deleted`),
  KEY `idx_status` (`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPA场景模式主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_spa_scene_room_slot`
--

DROP TABLE IF EXISTS `biz_spa_scene_room_slot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_spa_scene_room_slot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `store_id` bigint NOT NULL DEFAULT '0',
  `scene_id` bigint NOT NULL COMMENT '场景模式ID',
  `room_id` bigint NOT NULL COMMENT '房间ID',
  `room_name` varchar(64) DEFAULT NULL COMMENT '房间名称快照',
  `sort_no` int NOT NULL DEFAULT '1' COMMENT '房间序号(1=主房,2=次房…)',
  `role_label` varchar(32) DEFAULT NULL COMMENT '角色标签(主理疗房/次理疗房)',
  `required_count` int NOT NULL DEFAULT '1' COMMENT '所需房间数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_scene` (`scene_id`,`deleted`),
  KEY `idx_room` (`room_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPA场景-房间组合';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_style_browse_log`
--

DROP TABLE IF EXISTS `biz_style_browse_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_style_browse_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `style_id` bigint DEFAULT NULL,
  `action_type` varchar(20) DEFAULT NULL COMMENT 'view/favorite/share/order',
  `source` varchar(20) DEFAULT NULL COMMENT 'app/wechat/miniprogram/store',
  `dwell_time` int DEFAULT NULL,
  `session_id` varchar(100) DEFAULT NULL,
  `action_time` datetime DEFAULT NULL,
  `ip_address` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_style_id` (`style_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_style_library`
--

DROP TABLE IF EXISTS `biz_style_library`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_style_library` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `style_code` varchar(50) DEFAULT NULL,
  `style_name` varchar(100) DEFAULT NULL,
  `style_type` varchar(50) DEFAULT NULL,
  `style_tag` varchar(200) DEFAULT NULL,
  `color_scheme` varchar(500) DEFAULT NULL,
  `difficulty_level` int DEFAULT NULL,
  `duration_minutes` int DEFAULT NULL,
  `base_price` decimal(10,2) DEFAULT NULL,
  `vip_price` decimal(10,2) DEFAULT NULL,
  `main_image` varchar(500) DEFAULT NULL,
  `detail_images` text,
  `video_url` varchar(500) DEFAULT NULL,
  `description` text,
  `operation_steps` text,
  `required_tools` varchar(500) DEFAULT NULL,
  `technician_level` varchar(50) DEFAULT NULL,
  `view_count` int DEFAULT '0',
  `favorite_count` int DEFAULT '0',
  `use_count` int DEFAULT '0',
  `rating_score` decimal(3,2) DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `is_new` tinyint DEFAULT '0',
  `is_hot` tinyint DEFAULT '0',
  `publish_employee_id` bigint DEFAULT NULL,
  `publish_employee_name` varchar(50) DEFAULT NULL,
  `publish_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_style_type` (`style_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_technician_level`
--

DROP TABLE IF EXISTS `biz_technician_level`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_technician_level` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `level_code` varchar(32) NOT NULL COMMENT '绛夌骇缂栫爜锛屽?L1/L2/L3',
  `level_name` varchar(64) NOT NULL COMMENT '绛夌骇鍚嶇О',
  `level_icon` varchar(255) DEFAULT NULL COMMENT '绛夌骇鍥炬爣URL',
  `level_color` varchar(16) DEFAULT '#409EFF' COMMENT '绛夌骇棰滆壊 #RRGGBB',
  `sort_order` int DEFAULT '1' COMMENT '绛夌骇鎺掑簭锛屾暟瀛楄秺灏忕瓑绾ц秺楂',
  `discount_rate` decimal(5,2) DEFAULT '0.00' COMMENT '鏈嶅姟鎶樻墸姣斾緥%',
  `commission_bonus` decimal(5,2) DEFAULT '0.00' COMMENT '鎻愭垚鍔犳垚姣斾緥%',
  `promotion_sales` decimal(15,2) DEFAULT NULL COMMENT '鏅嬪崌瑕佹眰涓氱哗(鍏?',
  `promotion_rating` decimal(5,2) DEFAULT NULL COMMENT '鏅嬪崌瑕佹眰濂借瘎鐜',
  `promotion_months` int DEFAULT NULL COMMENT '鏅嬪崌瑕佹眰鏈?綆鏈嶅姟鏈堟暟',
  `privileges` varchar(1000) DEFAULT NULL COMMENT '绛夌骇鐗规潈鎻忚堪',
  `service_categories` varchar(500) DEFAULT NULL COMMENT '鍏宠仈椤圭洰鍒嗙被(JSON鏁扮粍)',
  `status` tinyint DEFAULT '1' COMMENT '鐘舵?锛?-鍋滅敤锛?-鍚?敤',
  `deleted` tinyint DEFAULT '0' COMMENT '鏄?惁鍒犻櫎锛?-鍚︼紝1-鏄',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_level_code` (`level_code`,`deleted`),
  KEY `idx_status` (`status`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎶?笀绛夌骇琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_treatment_phase`
--

DROP TABLE IF EXISTS `biz_treatment_phase`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_treatment_phase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `plan_id` bigint NOT NULL COMMENT '疗程方案ID',
  `phase_no` int NOT NULL DEFAULT '1' COMMENT '阶段序号（从1开始）',
  `phase_name` varchar(128) NOT NULL COMMENT '阶段名称（如：导入期、强化期、维护期）',
  `session_count` int NOT NULL DEFAULT '1' COMMENT '本阶段次数',
  `sop` text COMMENT '阶段 SOP',
  `expected_duration` int DEFAULT '0' COMMENT '预计时长(分钟)',
  `sort_order` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_phase` (`plan_id`,`phase_no`),
  KEY `idx_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='疗程阶段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `biz_treatment_plan`
--

DROP TABLE IF EXISTS `biz_treatment_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_treatment_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `plan_code` varchar(64) NOT NULL COMMENT '方案编码',
  `plan_name` varchar(128) NOT NULL COMMENT '方案名称（如：美白亮肤 8 次）',
  `service_id` bigint NOT NULL COMMENT '对应服务项目 biz_service_item.id',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID（冗余）',
  `total_sessions` int NOT NULL DEFAULT '1' COMMENT '总次数',
  `cycle_days` int DEFAULT '7' COMMENT '建议周期（天）',
  `plan_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '方案总价',
  `member_price` decimal(10,2) DEFAULT NULL COMMENT '会员价',
  `description` varchar(500) DEFAULT NULL,
  `sop` text COMMENT '整体 SOP',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_plan_code` (`tenant_id`,`plan_code`),
  KEY `idx_service` (`service_id`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='疗程方案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blockchain_chain_block`
--

DROP TABLE IF EXISTS `blockchain_chain_block`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blockchain_chain_block` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chain_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '链标识（meiyetec-main / meiyetec-drug...）',
  `block_index` bigint NOT NULL,
  `block_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prev_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `merkle_root` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tx_count` int DEFAULT '0',
  `size_bytes` int DEFAULT '0',
  `difficulty` int DEFAULT '2',
  `nonce` bigint DEFAULT '0',
  `miner` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT 'system',
  `sealed_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chain_index` (`chain_id`,`block_index`),
  UNIQUE KEY `uk_chain_hash` (`chain_id`,`block_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区块链 - 区块';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blockchain_record`
--

DROP TABLE IF EXISTS `blockchain_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blockchain_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `record_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PRODUCT / DRUG / DEVICE / ORDER / CONSENT',
  `biz_id` bigint NOT NULL COMMENT '业务主键',
  `biz_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `block_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '本块哈希',
  `prev_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上一块哈希',
  `block_index` bigint NOT NULL COMMENT '块序号',
  `merkle_root` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload` text COLLATE utf8mb4_unicode_ci COMMENT 'JSON 存证内容',
  `payload_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'payload 哈希',
  `nonce` bigint DEFAULT '0',
  `difficulty` int DEFAULT '2',
  `miner` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT 'system',
  `consensus` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'POW' COMMENT 'POW / POS / DPOS',
  `signature` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verified` tinyint DEFAULT '1',
  `verify_count` int DEFAULT '0',
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_block_hash` (`block_hash`),
  KEY `idx_type_biz` (`record_type`,`biz_id`),
  KEY `idx_block_index` (`block_index`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区块链存证';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_booking_resource`
--

DROP TABLE IF EXISTS `booking_booking_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_booking_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `booking_id` bigint NOT NULL COMMENT 'biz_booking.id',
  `resource_id` bigint NOT NULL COMMENT 'booking_resource.id',
  `resource_code` varchar(64) DEFAULT NULL,
  `resource_name` varchar(128) DEFAULT NULL,
  `resource_type` varchar(16) DEFAULT NULL,
  `quantity` int DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_booking_resource` (`booking_id`,`resource_id`),
  KEY `idx_resource` (`resource_id`),
  KEY `idx_booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约占用资源';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_forecast_feature`
--

DROP TABLE IF EXISTS `booking_forecast_feature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_forecast_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL,
  `feature_date` date NOT NULL,
  `feature_hour` int DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `booking_count` int DEFAULT '0',
  `checkin_count` int DEFAULT '0',
  `complete_count` int DEFAULT '0',
  `noshow_count` int DEFAULT '0',
  `cancel_count` int DEFAULT '0',
  `noshow_rate` decimal(5,2) DEFAULT NULL,
  `checkin_rate` decimal(5,2) DEFAULT NULL,
  `avg_service_duration_min` int DEFAULT NULL,
  `peak_hour` int DEFAULT NULL,
  `day_of_week` int DEFAULT NULL,
  `is_weekend` tinyint DEFAULT NULL,
  `is_holiday` tinyint DEFAULT NULL,
  `is_payday` tinyint DEFAULT NULL,
  `is_member_day` tinyint DEFAULT NULL,
  `days_before_holiday` int DEFAULT NULL,
  `days_after_holiday` int DEFAULT NULL,
  `same_dow_avg7` decimal(10,2) DEFAULT NULL,
  `same_dow_avg30` decimal(10,2) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_feature_date` (`feature_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_forecast_result`
--

DROP TABLE IF EXISTS `booking_forecast_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_forecast_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL,
  `forecast_date` date NOT NULL,
  `forecast_hour` int DEFAULT NULL,
  `forecast_type` varchar(32) NOT NULL,
  `predicted_value` decimal(10,2) DEFAULT NULL,
  `lower_bound` decimal(10,2) DEFAULT NULL,
  `upper_bound` decimal(10,2) DEFAULT NULL,
  `confidence` decimal(5,4) DEFAULT NULL,
  `algorithm` varchar(32) DEFAULT NULL,
  `model_version` varchar(16) DEFAULT NULL,
  `base_window_days` int DEFAULT NULL,
  `actual_value` decimal(10,2) DEFAULT NULL,
  `error_pct` decimal(5,2) DEFAULT NULL,
  `mae` decimal(10,4) DEFAULT NULL,
  `generated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_forecast_date` (`forecast_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_no_show_rule`
--

DROP TABLE IF EXISTS `booking_no_show_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_no_show_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `name` varchar(64) NOT NULL,
  `no_show_count_threshold` int DEFAULT '3' COMMENT '累计爽约次数阈值',
  `lock_days` int DEFAULT '30' COMMENT '锁定天数',
  `credit_penalty` int DEFAULT '10' COMMENT '信用分扣除',
  `fee_penalty` decimal(10,2) DEFAULT '0.00' COMMENT '扣费金额',
  `description` varchar(255) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `sort_order` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_store` (`store_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爽约规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_resource`
--

DROP TABLE IF EXISTS `booking_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `resource_code` varchar(64) NOT NULL COMMENT '资源编码',
  `resource_name` varchar(128) NOT NULL,
  `resource_type` varchar(16) NOT NULL COMMENT 'ROOM/BED/DEVICE',
  `capacity` int DEFAULT '1' COMMENT '同时容纳人数',
  `location` varchar(128) DEFAULT NULL COMMENT '位置描述',
  `equipment` varchar(500) DEFAULT NULL COMMENT '配套设备/说明',
  `default_duration` int DEFAULT '60' COMMENT '默认服务时长(分钟)',
  `available_from` time DEFAULT '09:00:00' COMMENT '可用时段起',
  `available_to` time DEFAULT '21:00:00' COMMENT '可用时段止',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用 2维护中',
  `sort_order` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`resource_code`),
  KEY `idx_type` (`resource_type`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约资源';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_resource_link`
--

DROP TABLE IF EXISTS `booking_resource_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_resource_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `resource_id` bigint NOT NULL COMMENT 'booking_resource.id',
  `service_item_id` bigint NOT NULL COMMENT 'biz_service_item.id',
  `is_required` tinyint DEFAULT '1' COMMENT '是否必选资源',
  `priority` int DEFAULT '0' COMMENT '推荐优先级（大者优先）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_res_svc` (`resource_id`,`service_item_id`),
  KEY `idx_service` (`service_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源-项目绑定';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_waitlist`
--

DROP TABLE IF EXISTS `booking_waitlist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_waitlist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `waitlist_no` varchar(64) NOT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `member_phone` varchar(32) DEFAULT NULL,
  `service_item_id` bigint DEFAULT NULL COMMENT 'biz_service_item.id',
  `service_item_name` varchar(128) DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL COMMENT '指定技师',
  `employee_name` varchar(64) DEFAULT NULL,
  `preferred_date` date DEFAULT NULL COMMENT '期望日期',
  `preferred_time_from` time DEFAULT NULL,
  `preferred_time_to` time DEFAULT NULL,
  `priority` int DEFAULT '0' COMMENT '优先级（数字大者优先）',
  `status` tinyint DEFAULT '1' COMMENT '1等待 2已通知 3已转预约 4已取消 5已过期',
  `notify_at` datetime DEFAULT NULL COMMENT '最后通知时间',
  `notify_count` int DEFAULT '0' COMMENT '通知次数',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_waitlist` (`tenant_id`,`waitlist_no`),
  KEY `idx_member` (`member_id`),
  KEY `idx_store_status` (`store_id`,`status`,`deleted`),
  KEY `idx_preferred` (`preferred_date`,`preferred_time_from`),
  KEY `idx_priority` (`priority`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候补队列';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_waitlist_notify`
--

DROP TABLE IF EXISTS `booking_waitlist_notify`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_waitlist_notify` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `waitlist_id` bigint NOT NULL,
  `notify_type` varchar(16) NOT NULL COMMENT 'SMS/APP/PUSH',
  `target` varchar(128) DEFAULT NULL COMMENT '短信/推送号码',
  `content` varchar(500) DEFAULT NULL,
  `response` varchar(128) DEFAULT NULL COMMENT '会员反馈',
  `response_at` datetime DEFAULT NULL,
  `succeeded` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_waitlist` (`waitlist_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候补通知日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `brand`
--

DROP TABLE IF EXISTS `brand`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `brand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `brand_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `logo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `website` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `business_license` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `legal_person` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `registered_at` date DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SUSPENDED / CLOSED',
  `sort_order` int DEFAULT '100',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_brand` (`tenant_id`,`brand_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍝佺墝';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `brand_store_rel`
--

DROP TABLE IF EXISTS `brand_store_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `brand_store_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `brand_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `store_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `join_at` date DEFAULT NULL,
  `leave_at` date DEFAULT NULL,
  `is_flagship` tinyint DEFAULT '0',
  `is_active` tinyint DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_brand_store` (`brand_id`,`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍝佺墝闂ㄥ簵鍏崇郴';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `care_recipe`
--

DROP TABLE IF EXISTS `care_recipe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_recipe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `recipe_code` varchar(64) DEFAULT NULL,
  `recipe_name` varchar(200) NOT NULL,
  `skin_type` varchar(32) DEFAULT NULL,
  `concerns` varchar(255) DEFAULT NULL,
  `description` text,
  `steps_json` text,
  `session_count` int DEFAULT '4',
  `interval_days` int DEFAULT '14',
  `estimated_cost` decimal(10,2) DEFAULT NULL,
  `expected_improvement` varchar(500) DEFAULT NULL,
  `cover_image` varchar(500) DEFAULT NULL,
  `tag` varchar(64) DEFAULT NULL,
  `applicable_season` varchar(32) DEFAULT NULL,
  `gender` varchar(16) DEFAULT NULL,
  `age_min` int DEFAULT NULL,
  `age_max` int DEFAULT NULL,
  `score` int DEFAULT '80',
  `use_count` int DEFAULT '0',
  `status` varchar(32) DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_skin` (`skin_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `care_recipe_item`
--

DROP TABLE IF EXISTS `care_recipe_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_recipe_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recipe_id` bigint NOT NULL,
  `service_id` bigint DEFAULT NULL,
  `item_type` varchar(32) DEFAULT NULL,
  `item_name` varchar(200) NOT NULL,
  `description` text,
  `usage_qty` varchar(64) DEFAULT NULL,
  `sequence` int DEFAULT '0',
  `estimated_minutes` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_recipe` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `care_skin_assessment`
--

DROP TABLE IF EXISTS `care_skin_assessment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_skin_assessment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `member_id` bigint NOT NULL,
  `assessment_no` varchar(64) DEFAULT NULL,
  `skin_type` varchar(32) DEFAULT NULL,
  `oil_level` int DEFAULT NULL,
  `moisture_level` int DEFAULT NULL,
  `elasticity_level` int DEFAULT NULL,
  `pigment_level` int DEFAULT NULL,
  `pore_level` int DEFAULT NULL,
  `wrinkle_level` int DEFAULT NULL,
  `concerns_json` text,
  `skin_score` int DEFAULT NULL,
  `recommended_recipe_id` bigint DEFAULT NULL,
  `recommended_recipe_ids` varchar(255) DEFAULT NULL,
  `analysis_json` text,
  `source` varchar(32) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `assessment_date` datetime DEFAULT CURRENT_TIMESTAMP,
  `next_assessment_date` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `churn_model_registry`
--

DROP TABLE IF EXISTS `churn_model_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `churn_model_registry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `model_code` varchar(64) DEFAULT NULL,
  `model_name` varchar(128) DEFAULT NULL,
  `model_type` varchar(64) DEFAULT NULL,
  `algorithm` varchar(32) DEFAULT NULL,
  `feature_window_days` int DEFAULT NULL,
  `churn_horizon_days` int DEFAULT NULL,
  `churn_threshold` int DEFAULT NULL,
  `training_samples` int DEFAULT NULL,
  `positive_samples` int DEFAULT NULL,
  `negative_samples` int DEFAULT NULL,
  `train_auc` decimal(10,4) DEFAULT NULL,
  `train_logloss` decimal(10,4) DEFAULT NULL,
  `train_accuracy` decimal(10,4) DEFAULT NULL,
  `train_precision` decimal(10,4) DEFAULT NULL,
  `train_recall` decimal(10,4) DEFAULT NULL,
  `train_f1` decimal(10,4) DEFAULT NULL,
  `val_auc` decimal(10,4) DEFAULT NULL,
  `val_logloss` decimal(10,4) DEFAULT NULL,
  `val_accuracy` decimal(10,4) DEFAULT NULL,
  `val_precision` decimal(10,4) DEFAULT NULL,
  `val_recall` decimal(10,4) DEFAULT NULL,
  `val_f1` decimal(10,4) DEFAULT NULL,
  `feature_importance_json` text,
  `lr_coefficients_json` text,
  `is_active` tinyint DEFAULT '0',
  `status` int DEFAULT '0',
  `trained_at` datetime DEFAULT NULL,
  `deployed_at` datetime DEFAULT NULL,
  `version` varchar(16) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `churn_training_run_log`
--

DROP TABLE IF EXISTS `churn_training_run_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `churn_training_run_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `model_id` bigint DEFAULT NULL,
  `run_type` varchar(32) DEFAULT NULL,
  `step` varchar(64) DEFAULT NULL,
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `samples` int DEFAULT NULL,
  `metric` varchar(64) DEFAULT NULL,
  `metric_value` decimal(10,4) DEFAULT NULL,
  `message` varchar(500) DEFAULT NULL,
  `error` varchar(1000) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cohort_definition`
--

DROP TABLE IF EXISTS `cohort_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cohort_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `cohort_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `cohort_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `cohort_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `metric` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `target_dim` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `description` text COLLATE utf8mb4_bin,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`cohort_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cohort_result`
--

DROP TABLE IF EXISTS `cohort_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cohort_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `cohort_id` bigint NOT NULL,
  `cohort_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `cohort_period` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `size` int DEFAULT NULL,
  `period_index` int DEFAULT NULL,
  `value` decimal(18,2) DEFAULT NULL,
  `value_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_cohort` (`cohort_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_record`
--

DROP TABLE IF EXISTS `commission_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `rule_id` bigint NOT NULL,
  `rule_version` int DEFAULT '1' COMMENT 'commission_rule_version.version_no',
  `rule_name` varchar(100) NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `source_type` varchar(20) NOT NULL COMMENT 'ORDER订单/PACKAGE_REDEMPTION核销',
  `source_id` bigint NOT NULL COMMENT '来源主记录ID',
  `source_item_id` bigint NOT NULL COMMENT '来源明细ID，用于幂等',
  `order_no` varchar(64) DEFAULT NULL,
  `service_id` bigint DEFAULT NULL,
  `service_name` varchar(128) DEFAULT NULL,
  `base_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '提成基数',
  `calculation_method` varchar(16) NOT NULL,
  `rate` decimal(7,4) DEFAULT NULL,
  `fixed_amount` decimal(12,2) DEFAULT NULL,
  `commission_amount` decimal(12,2) NOT NULL COMMENT '提成金额',
  `business_time` datetime NOT NULL,
  `salary_month` char(7) NOT NULL COMMENT '工资月份YYYY-MM',
  `settlement_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1待结算 2已结算 3已作废',
  `reversal_status` tinyint DEFAULT '0' COMMENT '0未冲正 1已冲正',
  `reversal_id` bigint DEFAULT NULL COMMENT 'commission_reversal.id',
  `invalid_reason` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comm_source_item` (`source_type`,`source_item_id`),
  KEY `idx_comm_record_employee_month` (`employee_id`,`salary_month`,`status`),
  KEY `idx_comm_record_order` (`source_id`,`order_no`),
  KEY `idx_comm_record_business_time` (`business_time`)
) ENGINE=InnoDB AUTO_INCREMENT=747 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工提成记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_reversal`
--

DROP TABLE IF EXISTS `commission_reversal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_reversal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `reversal_no` varchar(64) NOT NULL,
  `reversal_type` varchar(16) NOT NULL COMMENT 'REFUND/UNDO_REDEEM/MANUAL',
  `source_type` varchar(20) NOT NULL COMMENT 'ORDER/PACKAGE_REDEMPTION',
  `source_id` bigint NOT NULL,
  `source_item_id` bigint DEFAULT NULL COMMENT '来源明细ID',
  `original_record_id` bigint DEFAULT NULL COMMENT 'commission_record.id（被冲正的记录）',
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL COMMENT '冲正金额（正数）',
  `reason` varchar(500) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待审核 2已生效 3已拒绝',
  `approve_time` datetime DEFAULT NULL,
  `approve_by` bigint DEFAULT NULL,
  `approve_by_name` varchar(64) DEFAULT NULL,
  `batch_id` bigint DEFAULT NULL COMMENT '关联的结算批次ID',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_reversal` (`tenant_id`,`reversal_no`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_employee` (`employee_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提成冲正记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_rule`
--

DROP TABLE IF EXISTS `commission_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `business_type` varchar(20) NOT NULL COMMENT 'SERVICE服务/PRODUCT产品/CARD开卡/RECHARGE充值',
  `target_id` bigint DEFAULT NULL COMMENT '指定服务/商品ID，NULL表示业务类型通用规则',
  `calculation_method` varchar(16) NOT NULL COMMENT 'RATE按比例/FIXED固定金额',
  `rate` decimal(7,4) DEFAULT NULL COMMENT '比例，如0.1000表示10%',
  `fixed_amount` decimal(12,2) DEFAULT NULL COMMENT '固定提成金额',
  `priority` int NOT NULL DEFAULT '0' COMMENT '优先级，越大越优先',
  `version_no` int DEFAULT '1' COMMENT '当前版本号',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期，NULL长期有效',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:1启用 0停用',
  `is_active` tinyint DEFAULT '1' COMMENT '1现行 0历史',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_comm_rule_match` (`tenant_id`,`store_id`,`business_type`,`target_id`,`status`),
  KEY `idx_comm_rule_effective` (`effective_date`,`expiry_date`)
) ENGINE=InnoDB AUTO_INCREMENT=272 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工提成规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_rule_version`
--

DROP TABLE IF EXISTS `commission_rule_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_rule_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `rule_id` bigint NOT NULL COMMENT 'commission_rule.id',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号（递增）',
  `rule_name` varchar(100) NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `calculation_method` varchar(16) NOT NULL,
  `rate` decimal(7,4) DEFAULT NULL,
  `fixed_amount` decimal(12,2) DEFAULT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `effective_date` date NOT NULL,
  `expiry_date` date DEFAULT NULL,
  `snapshot_reason` varchar(255) DEFAULT NULL COMMENT 'CREATE/UPDATE/ROLLBACK',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_version` (`rule_id`,`version_no`),
  KEY `idx_business` (`business_type`,`target_id`,`effective_date`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提成规则版本快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_salary`
--

DROP TABLE IF EXISTS `commission_salary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_salary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `salary_month` char(7) NOT NULL,
  `base_salary` decimal(12,2) NOT NULL DEFAULT '0.00',
  `performance_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '业绩金额',
  `commission_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `adjustment_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '奖励/扣款调整',
  `payable_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '应发金额',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1草稿 2已确认 3已发放',
  `confirm_time` datetime DEFAULT NULL,
  `pay_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comm_salary_employee_month` (`tenant_id`,`store_id`,`employee_id`,`salary_month`),
  KEY `idx_comm_salary_month_status` (`salary_month`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工工资表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_settlement`
--

DROP TABLE IF EXISTS `commission_settlement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_settlement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint NOT NULL,
  `settlement_no` varchar(64) NOT NULL COMMENT '结算单号',
  `salary_month` char(7) NOT NULL COMMENT '结算月份YYYY-MM',
  `employee_count` int NOT NULL DEFAULT '0' COMMENT '涉及员工数',
  `total_commission` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '提成总额',
  `total_adjustment` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '调整总额',
  `total_payable` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '应付总额',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1草稿 2已确认 3已发放 4已取消',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `confirm_by` bigint DEFAULT NULL COMMENT '确认人',
  `pay_time` datetime DEFAULT NULL COMMENT '发放时间',
  `pay_by` bigint DEFAULT NULL COMMENT '发放人',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  KEY `idx_settlement_month` (`salary_month`,`store_id`),
  KEY `idx_settlement_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=169 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提成结算单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_settlement_batch`
--

DROP TABLE IF EXISTS `commission_settlement_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_settlement_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `batch_no` varchar(64) NOT NULL,
  `batch_type` varchar(16) NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY/WEEKLY/CUSTOM',
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `salary_month` char(7) NOT NULL,
  `employee_count` int DEFAULT '0',
  `record_count` int DEFAULT '0',
  `total_commission` decimal(12,2) DEFAULT '0.00',
  `total_adjustment` decimal(12,2) DEFAULT '0.00',
  `total_payable` decimal(12,2) DEFAULT '0.00',
  `status` tinyint DEFAULT '1' COMMENT '1草稿 2已确认 3已发放 4已取消 5审批中',
  `confirm_time` datetime DEFAULT NULL,
  `confirm_by` bigint DEFAULT NULL,
  `confirm_by_name` varchar(64) DEFAULT NULL,
  `pay_time` datetime DEFAULT NULL,
  `pay_by` bigint DEFAULT NULL,
  `pay_by_name` varchar(64) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_batch` (`tenant_id`,`batch_no`),
  KEY `idx_month` (`salary_month`,`status`),
  KEY `idx_store` (`store_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提成结算批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_settlement_batch_item`
--

DROP TABLE IF EXISTS `commission_settlement_batch_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_settlement_batch_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `batch_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) DEFAULT NULL,
  `base_salary` decimal(12,2) DEFAULT '0.00',
  `commission_amount` decimal(12,2) DEFAULT '0.00',
  `adjustment_amount` decimal(12,2) DEFAULT '0.00',
  `reversal_amount` decimal(12,2) DEFAULT '0.00' COMMENT '冲正总额（负数）',
  `payable_amount` decimal(12,2) DEFAULT '0.00',
  `record_count` int DEFAULT '0',
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_emp` (`batch_id`,`employee_id`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算批次员工明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `commission_settlement_record`
--

DROP TABLE IF EXISTS `commission_settlement_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commission_settlement_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL COMMENT '结算单ID',
  `commission_record_id` bigint NOT NULL COMMENT '提成记录ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  `employee_name` varchar(64) NOT NULL COMMENT '员工姓名快照',
  `base_salary` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '基本工资',
  `commission_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '提成金额',
  `adjustment_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '调整金额',
  `payable_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '应付金额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_commission` (`settlement_id`,`commission_record_id`),
  KEY `idx_settlement_employee` (`settlement_id`,`employee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16171 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结算单与提成记录关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `complaint_category`
--

DROP TABLE IF EXISTS `complaint_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `complaint_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `category_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `default_priority` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'P3' COMMENT 'P1 / P2 / P3 / P4',
  `default_sla_minutes` int DEFAULT '1440' COMMENT '榛樿? SLA锛堝垎閽燂級',
  `enabled` tinyint DEFAULT '1',
  `sort_order` int DEFAULT '100',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_category` (`tenant_id`,`category_code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎶曡瘔鍒嗙被';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `complaint_escalation`
--

DROP TABLE IF EXISTS `complaint_escalation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `complaint_escalation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_id` bigint NOT NULL,
  `from_assignee` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `to_assignee` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `escalate_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `escalated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `accepted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ticket` (`ticket_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎶曡瘔鍗囩骇';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `complaint_satisfaction`
--

DROP TABLE IF EXISTS `complaint_satisfaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `complaint_satisfaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_id` bigint NOT NULL,
  `rating` int DEFAULT NULL COMMENT '1-5',
  `feedback` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `nps_score` int DEFAULT NULL COMMENT '-100 ~ 100',
  `rated_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎶曡瘔婊℃剰搴';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `complaint_sla_policy`
--

DROP TABLE IF EXISTS `complaint_sla_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `complaint_sla_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `priority` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'P1 / P2 / P3 / P4',
  `response_minutes` int DEFAULT '30',
  `resolve_minutes` int DEFAULT '1440',
  `escalate_to` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notify_channels` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `compensation_limit` decimal(10,2) DEFAULT '0.00',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_priority` (`tenant_id`,`priority`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎶曡瘔 SLA 绛栫暐';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `complication_event`
--

DROP TABLE IF EXISTS `complication_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `complication_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `event_no` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `sop_id` bigint DEFAULT NULL,
  `sop_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `service_id` bigint DEFAULT NULL,
  `severity` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `symptoms` text COLLATE utf8mb4_bin,
  `trigger_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `response_time` datetime DEFAULT NULL,
  `resolved_time` datetime DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL,
  `handler_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT 'OPEN',
  `execution_log` text COLLATE utf8mb4_bin,
  `outcome` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `complication_sop`
--

DROP TABLE IF EXISTS `complication_sop`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `complication_sop` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `sop_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `sop_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `category` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `severity` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `trigger_symptom` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `trigger_timing` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `steps_json` text COLLATE utf8mb4_bin,
  `medications` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `equipment` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `escalation_rule` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `response_deadline_min` int DEFAULT NULL,
  `responsible_role` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `version` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`sop_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_complaint_log`
--

DROP TABLE IF EXISTS `crm_complaint_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_complaint_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `ticket_id` bigint NOT NULL,
  `action_type` varchar(32) NOT NULL COMMENT 'CREATE创建/ASSIGN分配/REPLY回复/RESOLVE解决/CLOSE关闭/REOPEN重启/ESCALATE升级/REFUND退款',
  `from_status` tinyint DEFAULT NULL,
  `to_status` tinyint DEFAULT NULL,
  `content` varchar(2000) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket` (`ticket_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投诉工单日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_complaint_ticket`
--

DROP TABLE IF EXISTS `crm_complaint_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_complaint_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `ticket_no` varchar(64) NOT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `member_phone` varchar(20) DEFAULT NULL,
  `complaint_type` varchar(32) NOT NULL COMMENT 'SERVICE服务/STAFF员工/PRODUCT产品/PRICE价格/ENVIRONMENT环境/OTHER其他',
  `level` tinyint DEFAULT '1' COMMENT '1一般 2较严重 3严重 4紧急',
  `subject` varchar(255) NOT NULL,
  `content` text,
  `evidence` varchar(2000) DEFAULT NULL COMMENT '证据 URL CSV',
  `source_type` varchar(32) DEFAULT NULL COMMENT 'ORDER/CARD/MEMBER/FOLLOW_UP/REVIEW',
  `source_id` bigint DEFAULT NULL,
  `source_no` varchar(64) DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL COMMENT '处理人',
  `handler_name` varchar(64) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `sla_expire_at` datetime DEFAULT NULL,
  `sla_minutes` int DEFAULT '1440',
  `status` tinyint DEFAULT '1' COMMENT '1待分配 2已分配 3处理中 4等待客户 5已解决 6已关闭 7已升级',
  `solution` varchar(2000) DEFAULT NULL,
  `solution_type` varchar(32) DEFAULT NULL COMMENT 'REFUND退款/EXCHANGE更换/APOLOGY道歉/COMPENSATION赔偿/NONE无需处理',
  `refund_amount` decimal(12,2) DEFAULT '0.00',
  `resolved_at` datetime DEFAULT NULL,
  `satisfaction_rating` int DEFAULT NULL COMMENT '客户满意度 1-5',
  `satisfaction_note` varchar(500) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL COMMENT '上级工单',
  `escalate_count` int DEFAULT '0',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_ticket` (`tenant_id`,`ticket_no`),
  KEY `idx_member` (`member_id`),
  KEY `idx_handler` (`handler_id`,`status`),
  KEY `idx_status` (`status`,`sla_expire_at`),
  KEY `idx_store` (`store_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投诉工单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_cross_store_redemption`
--

DROP TABLE IF EXISTS `crm_cross_store_redemption`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_cross_store_redemption` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `member_phone` varchar(20) DEFAULT NULL,
  `source_store_id` bigint NOT NULL COMMENT '会员归属门店',
  `target_store_id` bigint NOT NULL COMMENT '实际核销门店',
  `redemption_type` varchar(16) NOT NULL COMMENT 'PACKAGE套餐/PROJECT项目/BALANCE储值/POINTS积分/COUPON券/DISCOUNT折扣',
  `source_id` bigint DEFAULT NULL COMMENT '对应的套餐/项目ID',
  `source_name` varchar(128) DEFAULT NULL,
  `original_amount` decimal(12,2) DEFAULT '0.00' COMMENT '原消费金额',
  `cross_fee` decimal(12,2) DEFAULT '0.00' COMMENT '跨店手续费',
  `net_amount` decimal(12,2) DEFAULT '0.00' COMMENT '实际入账金额',
  `status` tinyint DEFAULT '1' COMMENT '1已核销 2已撤销',
  `rule_id` bigint DEFAULT NULL COMMENT '命中的跨店规则ID',
  `rule_snapshot` varchar(500) DEFAULT NULL COMMENT '规则快照',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_target_store` (`target_store_id`,`status`,`deleted`),
  KEY `idx_source_store` (`source_store_id`,`status`,`deleted`),
  KEY `idx_type` (`redemption_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨店核销记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_cross_store_rule`
--

DROP TABLE IF EXISTS `crm_cross_store_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_cross_store_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL COMMENT '品牌（NULL=租户级通用规则）',
  `rule_name` varchar(64) NOT NULL,
  `remark` varchar(500) DEFAULT NULL COMMENT '??????/???',
  `start_time` datetime DEFAULT NULL COMMENT 'Start time',
  `end_time` datetime DEFAULT NULL COMMENT 'End time',
  `member_cross_enabled` tinyint DEFAULT '1' COMMENT '跨店识别（手机号匹配）',
  `package_cross_enabled` tinyint DEFAULT '1' COMMENT '跨店核销套餐',
  `package_cross_mode` varchar(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌内 / CHAIN链内 / ANY任意门店 / DISABLED禁止',
  `points_cross_enabled` tinyint DEFAULT '1' COMMENT '跨店积分共享',
  `balance_cross_enabled` tinyint DEFAULT '1' COMMENT '跨店储值共享',
  `balance_cross_mode` varchar(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌内 / STORE仅本门店 / DISABLED禁止',
  `discount_cross_enabled` tinyint DEFAULT '1' COMMENT '跨店折扣共享',
  `coupon_cross_enabled` tinyint DEFAULT '1' COMMENT '跨店优惠券',
  `cross_fee_rate` decimal(5,2) DEFAULT '0.00' COMMENT '跨店手续费比例% 0=不收',
  `cross_fee_type` varchar(16) DEFAULT 'NONE' COMMENT 'NONE不收/PERCENT按比例/FIXED固定',
  `cross_max_amount` decimal(12,2) DEFAULT '0.00' COMMENT '跨店单笔限额 0=不限',
  `enabled` tinyint DEFAULT '1',
  `priority` int DEFAULT '0',
  `hit_count` bigint DEFAULT '0' COMMENT 'Hit count',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_brand` (`tenant_id`,`brand_id`,`enabled`,`deleted`),
  KEY `idx_priority` (`priority` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨店共享规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_follow_up_note`
--

DROP TABLE IF EXISTS `crm_follow_up_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_follow_up_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `action_type` varchar(20) COLLATE utf8mb4_bin NOT NULL COMMENT 'CREATE/ASSIGN/START/FINISH/CLOSE/REOPEN/EDIT',
  `from_status` int DEFAULT NULL,
  `to_status` int DEFAULT NULL,
  `content` varchar(2000) COLLATE utf8mb4_bin DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(60) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_action` (`action_type`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='回访任务操作日志/沟通履历';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_follow_up_plan`
--

DROP TABLE IF EXISTS `crm_follow_up_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_follow_up_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL COMMENT '单店计划所属门店',
  `plan_code` varchar(64) NOT NULL,
  `plan_name` varchar(128) NOT NULL,
  `plan_type` varchar(32) NOT NULL COMMENT 'POST_TREATMENT/BIRTHDAY/EXPIRE/CUSTOM',
  `trigger_offset_days` int DEFAULT '0' COMMENT '相对触发日偏移天数（+3=3天后）',
  `trigger_event` varchar(64) DEFAULT NULL COMMENT 'ORDER_COMPLETED/CARD_DELIVERED/BIRTHDAY/EXPIRE_30D',
  `subject_template` varchar(255) DEFAULT NULL,
  `content_template` text,
  `script_template` text,
  `priority` tinyint DEFAULT '1',
  `sla_minutes` int DEFAULT '1440',
  `default_owner_id` bigint DEFAULT NULL,
  `default_owner_name` varchar(64) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint DEFAULT '0',
  `trigger_time` time DEFAULT NULL COMMENT '每日触发时间 HH:mm',
  `frequency` varchar(20) DEFAULT 'DAILY' COMMENT 'DAILY/WEEKLY/MONTHLY/ONCE',
  `store_ids` varchar(500) DEFAULT NULL COMMENT 'JSON 数组：限定门店，空=全部',
  `min_amount` decimal(12,2) DEFAULT NULL COMMENT '订单最小金额（疗程后回访用）',
  `max_per_day` int DEFAULT '100' COMMENT '单日上限',
  `last_run_at` datetime DEFAULT NULL,
  `last_run_count` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`plan_code`),
  KEY `idx_type` (`plan_type`,`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回访计划模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_follow_up_task`
--

DROP TABLE IF EXISTS `crm_follow_up_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_follow_up_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `task_no` varchar(64) NOT NULL,
  `task_type` varchar(32) NOT NULL COMMENT 'POST_TREATMENT疗程后 / BIRTHDAY生日 / EXPIRE到期 / COMPLAINT投诉回访 / CUSTOM自定义',
  `plan_id` bigint DEFAULT NULL COMMENT '关联回访计划',
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `member_phone` varchar(20) DEFAULT NULL,
  `source_type` varchar(32) DEFAULT NULL COMMENT 'ORDER订单/CARD套餐/MEMBER会员/COMPLAINT工单',
  `source_id` bigint DEFAULT NULL,
  `source_no` varchar(64) DEFAULT NULL,
  `planned_at` datetime NOT NULL COMMENT '计划回访时间',
  `sla_expire_at` datetime DEFAULT NULL COMMENT 'SLA 截止时间',
  `sla_minutes` int DEFAULT '1440' COMMENT 'SLA 阈值（分钟）',
  `priority` tinyint DEFAULT '1' COMMENT '1低 2中 3高',
  `subject` varchar(255) NOT NULL COMMENT '回访主题',
  `content` text COMMENT '回访要点',
  `suggested_script` text COMMENT '建议话术',
  `owner_id` bigint DEFAULT NULL COMMENT '处理人',
  `owner_name` varchar(64) DEFAULT NULL,
  `dept_id` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待处理 2处理中 3已完成 4已关闭 5已逾期',
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `result_type` varchar(32) DEFAULT NULL COMMENT 'SATISFIED满意 / NORMAL一般 / UNSATISFIED不满意 / COMPLAINT投诉',
  `result_note` varchar(500) DEFAULT NULL,
  `rating` int DEFAULT NULL COMMENT '评分 1-5',
  `touch_count` int DEFAULT '0',
  `last_touch_at` datetime DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_task` (`tenant_id`,`task_no`),
  KEY `idx_member` (`member_id`,`status`),
  KEY `idx_owner` (`owner_id`,`status`),
  KEY `idx_status` (`status`,`planned_at`),
  KEY `idx_store` (`store_id`,`status`,`planned_at`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回访任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_follow_up_trigger_log`
--

DROP TABLE IF EXISTS `crm_follow_up_trigger_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_follow_up_trigger_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `plan_id` bigint NOT NULL,
  `plan_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `generated_count` int DEFAULT '0',
  `trigger_source` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MANUAL/SCHEDULED',
  `trigger_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_plan_time` (`plan_id`,`trigger_time`),
  KEY `idx_tenant_time` (`tenant_id`,`trigger_time`),
  KEY `idx_tenant_deleted` (`tenant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍥炶?璁″垝瑙﹀彂鏃ュ織';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_integration`
--

DROP TABLE IF EXISTS `crm_integration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_integration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `platform` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `app_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `client_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `client_secret` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `access_token` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `refresh_token` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `token_expire_at` datetime DEFAULT NULL,
  `api_base_url` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `portal_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_direction` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_members` tinyint DEFAULT '0',
  `sync_orders` tinyint DEFAULT '0',
  `sync_contacts` tinyint DEFAULT '0',
  `sync_deals` tinyint DEFAULT '0',
  `auto_sync` tinyint DEFAULT '0',
  `last_sync_at` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_ltv_snapshot`
--

DROP TABLE IF EXISTS `crm_ltv_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_ltv_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `historical_ltv` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '历史已实现 LTV（累计消费）',
  `predicted_ltv_30d` decimal(12,2) DEFAULT NULL COMMENT '30 天预测 LTV',
  `predicted_ltv_90d` decimal(12,2) DEFAULT NULL COMMENT '90 天预测 LTV',
  `predicted_ltv_365d` decimal(12,2) DEFAULT NULL COMMENT '365 天预测 LTV',
  `predicted_ltv_lifetime` decimal(12,2) DEFAULT NULL COMMENT '终身 LTV',
  `repurchase_prob_30d` decimal(5,4) DEFAULT NULL COMMENT '30 天复购概率 0-1',
  `repurchase_prob_90d` decimal(5,4) DEFAULT NULL,
  `repurchase_prob_180d` decimal(5,4) DEFAULT NULL,
  `model_type` varchar(32) NOT NULL COMMENT 'BG/NBD/HISTORICAL/ML/RULE',
  `model_version` varchar(32) DEFAULT NULL,
  `avg_order_value` decimal(12,2) DEFAULT NULL COMMENT '客单价',
  `purchase_frequency` decimal(8,2) DEFAULT NULL COMMENT '月均消费频次',
  `last_purchase_days` int DEFAULT NULL COMMENT '距上次消费天数',
  `total_orders` int DEFAULT NULL,
  `auc` decimal(5,4) DEFAULT NULL,
  `recall` decimal(5,4) DEFAULT NULL,
  `precision_score` decimal(5,4) DEFAULT NULL,
  `snapshot_date` date NOT NULL,
  `calc_duration_ms` int DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT 'system',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_brand_date` (`brand_id`,`snapshot_date`)
) ENGINE=InnoDB AUTO_INCREMENT=4355 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LTV 预测快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_member`
--

DROP TABLE IF EXISTS `crm_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gender` tinyint DEFAULT '0',
  `birthday` date DEFAULT NULL,
  `level_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `balance` decimal(12,2) DEFAULT '0.00',
  `points` int DEFAULT '0',
  `total_consume` decimal(12,2) DEFAULT '0.00',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `join_date` date DEFAULT (curdate()),
  `last_visit` datetime DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户ID',
  `deleted` tinyint DEFAULT '0',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`,`deleted`),
  UNIQUE KEY `uk_member_no` (`member_no`,`deleted`),
  KEY `idx_name` (`name`),
  KEY `idx_level` (`level_id`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1557 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_member_profile`
--

DROP TABLE IF EXISTS `crm_member_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_member_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `skin_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `allergy` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hobby` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `job` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wechat` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `emergency_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `emergency_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `consume_pref` text COLLATE utf8mb4_unicode_ci,
  `visit_record` text COLLATE utf8mb4_unicode_ci,
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member` (`member_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_member_store`
--

DROP TABLE IF EXISTS `crm_member_store`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_member_store` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `member_id` bigint NOT NULL,
  `store_id` bigint NOT NULL,
  `brand_id` bigint DEFAULT NULL,
  `relation_type` varchar(16) DEFAULT 'CROSS' COMMENT 'ORIGIN归属门店/CROSS跨店关联',
  `bind_type` varchar(16) DEFAULT 'PHONE' COMMENT 'PHONE手机号/CARD会员卡/MANUAL人工',
  `first_active_at` datetime DEFAULT NULL,
  `last_active_at` datetime DEFAULT NULL,
  `visit_count` int DEFAULT '0',
  `status` tinyint DEFAULT '1' COMMENT '1活跃 0停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_store` (`member_id`,`store_id`),
  KEY `idx_store` (`store_id`,`status`),
  KEY `idx_member` (`member_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员-门店关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_member_tag`
--

DROP TABLE IF EXISTS `crm_member_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_member_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tag_color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '#409EFF',
  `sort_order` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_member_tag_rel`
--

DROP TABLE IF EXISTS `crm_member_tag_rel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_member_tag_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tag` (`member_id`,`tag_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_tag` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_member_tag_value`
--

DROP TABLE IF EXISTS `crm_member_tag_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_member_tag_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `tag_code` varchar(64) NOT NULL COMMENT '标签编码：RFM-CHAMPION, RFM-LOST, ...',
  `tag_name` varchar(128) NOT NULL,
  `tag_category` varchar(32) NOT NULL COMMENT 'RFM/LIFECYCLE/VALUE/PREFERENCE/AI',
  `source_type` varchar(32) NOT NULL COMMENT 'AUTO自动/MANUAL人工/AI智能',
  `model_id` bigint DEFAULT NULL COMMENT '关联模型',
  `tag_value` varchar(255) DEFAULT NULL,
  `score` decimal(8,2) DEFAULT NULL COMMENT '评分 0-100',
  `related_type` varchar(32) DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `effective_at` datetime DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tag` (`tenant_id`,`member_id`,`tag_code`),
  KEY `idx_category` (`tag_category`,`tag_code`),
  KEY `idx_tag` (`tag_code`,`score`),
  KEY `idx_score` (`score` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=1737 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员标签值';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_package`
--

DROP TABLE IF EXISTS `crm_package`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '通用',
  `cover` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_times` int NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `cost_price` decimal(12,2) DEFAULT '0.00',
  `valid_days` int DEFAULT '365',
  `sale_count` int DEFAULT '0',
  `redeem_count` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`,`deleted`),
  KEY `idx_name` (`name`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_package_order`
--

DROP TABLE IF EXISTS `crm_package_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_package_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL COMMENT 'è´­ä¹°é—¨åº—IDï¼ˆç”¨äºŽè·¨åº—æ ¸é”€åˆ¤æ–­ï¼‰',
  `package_id` bigint NOT NULL,
  `package_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_times` int NOT NULL,
  `remaining_times` int NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `pay_method` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT '微信',
  `operator_id` bigint DEFAULT NULL,
  `buy_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `expire_time` datetime DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted` tinyint DEFAULT '0',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`,`deleted`),
  KEY `idx_member` (`member_id`),
  KEY `idx_package` (`package_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expire` (`expire_time`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐购买记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_package_redemption`
--

DROP TABLE IF EXISTS `crm_package_redemption`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_package_redemption` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `order_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL COMMENT 'æ ¸é”€é—¨åº—ID',
  `package_id` bigint NOT NULL,
  `times` int DEFAULT '1',
  `cross_fee` decimal(12,2) DEFAULT '0.00' COMMENT 'è·¨åº—æ‰‹ç»­è´¹',
  `cross_rule_id` bigint DEFAULT NULL COMMENT 'å‘½ä¸­çš„è·¨åº—è§„åˆ™ID',
  `service_item` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `beautician_id` bigint DEFAULT NULL,
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `redeem_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1',
  `deleted` tinyint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_package` (`package_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐核销记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_recharge_audit`
--

DROP TABLE IF EXISTS `crm_recharge_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_recharge_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `audit_no` varchar(50) DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(50) DEFAULT NULL,
  `member_phone` varchar(20) DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `gift_amount` decimal(12,2) DEFAULT NULL,
  `total_amount` decimal(12,2) DEFAULT NULL,
  `pay_method` varchar(50) DEFAULT NULL,
  `trade_no` varchar(100) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `apply_user_id` bigint DEFAULT NULL,
  `apply_user_name` varchar(50) DEFAULT NULL,
  `audit_user_id` bigint DEFAULT NULL,
  `audit_user_name` varchar(50) DEFAULT NULL,
  `audit_time` datetime DEFAULT NULL,
  `audit_remark` varchar(500) DEFAULT NULL,
  `source` tinyint DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `version` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_status` (`status`),
  KEY `idx_audit_no` (`audit_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_recharge_audit_log`
--

DROP TABLE IF EXISTS `crm_recharge_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_recharge_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `audit_id` bigint DEFAULT NULL,
  `audit_no` varchar(50) DEFAULT '',
  `action` int DEFAULT NULL COMMENT '1=apply 2=approve 3=reject 4=cancel 5=grant',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(100) DEFAULT '',
  `remark` varchar(500) DEFAULT '',
  `before_status` varchar(20) DEFAULT '',
  `after_status` varchar(20) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_audit` (`audit_id`),
  KEY `idx_audit_no` (`audit_no`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_review`
--

DROP TABLE IF EXISTS `crm_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL COMMENT 'ORDER订单/STAFF员工/SERVICE服务/STORE门店',
  `source_id` bigint NOT NULL,
  `source_no` varchar(64) DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `member_phone` varchar(20) DEFAULT NULL,
  `overall_rating` int NOT NULL COMMENT '综合评分 1-5',
  `service_rating` int DEFAULT NULL COMMENT '服务 1-5',
  `skill_rating` int DEFAULT NULL COMMENT '技术 1-5',
  `environment_rating` int DEFAULT NULL COMMENT '环境 1-5',
  `price_rating` int DEFAULT NULL COMMENT '性价比 1-5',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签 CSV',
  `content` varchar(2000) DEFAULT NULL,
  `images` varchar(2000) DEFAULT NULL COMMENT '图片 URL CSV',
  `anonymous` tinyint DEFAULT '0' COMMENT '是否匿名',
  `channel` varchar(32) DEFAULT 'INTERNAL' COMMENT 'INTERNAL内部/MINI_PROGRAM小程序/SMS短信/OTHER其他',
  `reply_content` varchar(2000) DEFAULT NULL,
  `reply_at` datetime DEFAULT NULL,
  `reply_by` varchar(64) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待审核 2已发布 3已隐藏 4已申诉',
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_store` (`store_id`,`status`,`create_time`),
  KEY `idx_rating` (`overall_rating`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_review_template`
--

DROP TABLE IF EXISTS `crm_review_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_review_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `template_code` varchar(64) NOT NULL,
  `template_name` varchar(128) NOT NULL,
  `trigger_event` varchar(64) DEFAULT NULL COMMENT 'ORDER_COMPLETED/CARD_DELIVERED',
  `dimensions` varchar(500) DEFAULT NULL COMMENT '评价维度（CSV: 服务,技术,环境,价格）',
  `tags_pool` varchar(1000) DEFAULT NULL COMMENT '标签池（CSV）',
  `intro_text` varchar(500) DEFAULT NULL COMMENT '引导语',
  `share_image` varchar(500) DEFAULT NULL COMMENT '分享图',
  `touch_channel` varchar(32) DEFAULT 'MINI_PROGRAM',
  `touch_delay_minutes` int DEFAULT '60',
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`template_code`),
  KEY `idx_event` (`trigger_event`,`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_rfm_snapshot`
--

DROP TABLE IF EXISTS `crm_rfm_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_rfm_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `recency_days` int NOT NULL COMMENT '最近一次消费距今天数',
  `frequency` int NOT NULL COMMENT '消费频次（次）',
  `monetary` decimal(12,2) NOT NULL COMMENT '消费金额（元）',
  `r_score` tinyint DEFAULT NULL,
  `f_score` tinyint DEFAULT NULL,
  `m_score` tinyint DEFAULT NULL,
  `rfm_score` varchar(8) DEFAULT NULL COMMENT '如 "555" / "135"',
  `segment` varchar(32) DEFAULT NULL COMMENT 'CHAMPION/LOYAL/POTENTIAL/NEW/AT_RISK/LOST/...',
  `segment_name` varchar(64) DEFAULT NULL,
  `explanation` varchar(500) DEFAULT NULL,
  `snapshot_date` date NOT NULL,
  `calc_mode` varchar(16) DEFAULT 'ONLINE' COMMENT 'ONLINE实时/BATCH批量',
  `calc_duration_ms` int DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT 'system',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_segment` (`segment`,`snapshot_date`),
  KEY `idx_brand` (`brand_id`,`snapshot_date`)
) ENGINE=InnoDB AUTO_INCREMENT=2379 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RFM 评分快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_sync_log`
--

DROP TABLE IF EXISTS `crm_sync_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `platform` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `direction` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL,
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `skipped_count` int DEFAULT '0',
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_tag_dictionary`
--

DROP TABLE IF EXISTS `crm_tag_dictionary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_tag_dictionary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '0',
  `tag_code` varchar(64) NOT NULL,
  `tag_name` varchar(128) NOT NULL,
  `category` varchar(32) NOT NULL,
  `description` varchar(500) DEFAULT NULL COMMENT '标签解释',
  `rule` varchar(500) DEFAULT NULL COMMENT '计算规则可解释',
  `color` varchar(16) DEFAULT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `enabled` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag` (`tag_code`),
  KEY `idx_category` (`category`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签字典';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_tenant`
--

DROP TABLE IF EXISTS `crm_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) NOT NULL,
  `tenant_name` varchar(128) NOT NULL,
  `contact_person` varchar(64) DEFAULT NULL,
  `contact_phone` varchar(32) DEFAULT NULL,
  `province` varchar(32) DEFAULT NULL,
  `city` varchar(32) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `brand_count` int DEFAULT '0',
  `store_count` int DEFAULT '0',
  `member_count` int DEFAULT '0',
  `plan` varchar(32) DEFAULT 'STANDARD',
  `plan_expire_at` datetime DEFAULT NULL,
  `status` varchar(16) DEFAULT 'ACTIVE',
  `logo_url` varchar(255) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM 绉熸埛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_wallet`
--

DROP TABLE IF EXISTS `crm_wallet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '钱包ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `balance` decimal(12,2) DEFAULT '0.00' COMMENT '可用余额',
  `total_recharge` decimal(12,2) DEFAULT '0.00' COMMENT '累计充值',
  `total_consume` decimal(12,2) DEFAULT '0.00' COMMENT '累计消费',
  `frozen_amount` decimal(12,2) DEFAULT '0.00' COMMENT '冻结金额',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tenant` (`member_id`,`tenant_id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_wallet_member` FOREIGN KEY (`member_id`) REFERENCES `crm_member` (`id`),
  CONSTRAINT `fk_wallet_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1019 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员钱包表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_wallet_drift_log`
--

DROP TABLE IF EXISTS `crm_wallet_drift_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_wallet_drift_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `member_balance` decimal(12,2) NOT NULL,
  `wallet_balance` decimal(12,2) NOT NULL,
  `diff` decimal(12,2) NOT NULL,
  `fix_type` varchar(32) NOT NULL COMMENT 'AUTO_FIX / MANUAL_REVIEW',
  `fixed_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `remark` varchar(255) DEFAULT 'P0-MONEY-002 batch 4 auto-fix',
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_fixed_at` (`fixed_at`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包漂移修复日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_wallet_orphan_log`
--

DROP TABLE IF EXISTS `crm_wallet_orphan_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_wallet_orphan_log` (
  `id` bigint NOT NULL DEFAULT '0' COMMENT '钱包ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `balance` decimal(12,2) DEFAULT '0.00' COMMENT '可用余额',
  `total_recharge` decimal(12,2) DEFAULT '0.00' COMMENT '累计充值',
  `total_consume` decimal(12,2) DEFAULT '0.00' COMMENT '累计消费',
  `frozen_amount` decimal(12,2) DEFAULT '0.00' COMMENT '冻结金额',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `reason` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_wallet_transaction`
--

DROP TABLE IF EXISTS `crm_wallet_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_wallet_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `type` tinyint NOT NULL COMMENT '类型：1充值 2消费 3退款 4赠送 5冻结 6解冻',
  `amount` decimal(12,2) NOT NULL COMMENT '变动金额（正数）',
  `balance_before` decimal(12,2) DEFAULT NULL COMMENT '变动前余额',
  `balance_after` decimal(12,2) DEFAULT NULL COMMENT '变动后余额',
  `gift_amount` decimal(12,2) DEFAULT '0.00' COMMENT '赠送金额',
  `pay_method` varchar(20) DEFAULT NULL COMMENT '支付方式',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_type` (`type`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_order` (`order_id`),
  KEY `idx_deleted` (`deleted`),
  KEY `fk_wallet_txn_operator` (`operator_id`),
  CONSTRAINT `fk_wallet_txn_member` FOREIGN KEY (`member_id`) REFERENCES `crm_member` (`id`),
  CONSTRAINT `fk_wallet_txn_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_wallet_txn_order` FOREIGN KEY (`order_id`) REFERENCES `pos_order` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_wallet_txn_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=3995 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `crm_wallet_txn_orphan_log`
--

DROP TABLE IF EXISTS `crm_wallet_txn_orphan_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `crm_wallet_txn_orphan_log` (
  `id` bigint NOT NULL DEFAULT '0' COMMENT '流水ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `type` tinyint NOT NULL COMMENT '类型：1充值 2消费 3退款 4赠送 5冻结 6解冻',
  `amount` decimal(12,2) NOT NULL COMMENT '变动金额（正数）',
  `balance_before` decimal(12,2) DEFAULT NULL COMMENT '变动前余额',
  `balance_after` decimal(12,2) DEFAULT NULL COMMENT '变动后余额',
  `gift_amount` decimal(12,2) DEFAULT '0.00' COMMENT '赠送金额',
  `pay_method` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '支付方式',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `reason` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cv_model`
--

DROP TABLE IF EXISTS `cv_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cv_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID（0=全局）',
  `model_code` varchar(64) NOT NULL COMMENT '模型编码（如 TENCENT_FACE）',
  `model_name` varchar(128) NOT NULL COMMENT '模型名称',
  `provider` varchar(32) NOT NULL COMMENT '厂商：TENCENT/ALIYUN/BAIDU/AZURE/GOOGLE/LOCAL',
  `model_type` varchar(32) NOT NULL COMMENT '类型：FACE/SKIN/MEDICAL/SCENE/NAIL/HAIR',
  `endpoint` varchar(256) DEFAULT NULL COMMENT 'API 端点 URL',
  `api_key` varchar(512) DEFAULT NULL COMMENT 'API Key（加密存储）',
  `api_secret` varchar(512) DEFAULT NULL COMMENT 'API Secret（加密存储）',
  `app_id` varchar(128) DEFAULT NULL COMMENT '腾讯云 AppId / 阿里云 AppId',
  `version` varchar(32) DEFAULT '1.0' COMMENT '模型版本',
  `accuracy` decimal(5,2) DEFAULT NULL COMMENT '准确率（%）',
  `latency_ms` int DEFAULT NULL COMMENT '平均延迟（ms）',
  `is_sandbox` tinyint DEFAULT '1' COMMENT '沙箱模式：0=生产 1=沙箱',
  `enabled` tinyint DEFAULT '1' COMMENT '启用：0=禁用 1=启用',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `config_json` text COMMENT '额外配置 JSON',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`model_code`),
  KEY `idx_provider` (`provider`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CV 模型注册表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cv_recognition_log`
--

DROP TABLE IF EXISTS `cv_recognition_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cv_recognition_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `model_code` varchar(64) DEFAULT NULL COMMENT '调用的模型编码',
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型：SKIN_TEST/FACE_RECOGNIZE/NAIL_TRYON/HAIR_ANALYSIS',
  `image_url` varchar(512) DEFAULT NULL COMMENT '图像 URL',
  `member_id` bigint DEFAULT NULL COMMENT '会员ID',
  `member_face_id` varchar(128) DEFAULT NULL COMMENT '人脸 ID（用于 1:N 搜索）',
  `result_json` text COMMENT '识别结果 JSON',
  `confidence` decimal(5,4) DEFAULT NULL COMMENT '置信度（0~1）',
  `latency_ms` int DEFAULT NULL COMMENT '实际延迟（ms）',
  `provider` varchar(32) DEFAULT NULL COMMENT '实际调用的提供商',
  `inference_mode` varchar(16) DEFAULT 'http' COMMENT '推理模式：http/sdk/local/mock',
  `status` varchar(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/SKIPPED',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `request_payload` text COMMENT '请求载荷（调试）',
  `response_payload` text COMMENT '响应载荷（调试）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_model` (`model_code`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_created` (`created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CV 识别日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dashboard`
--

DROP TABLE IF EXISTS `dashboard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dashboard` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `dashboard_code` varchar(64) NOT NULL,
  `dashboard_name` varchar(128) NOT NULL,
  `dashboard_type` varchar(32) DEFAULT 'OPS',
  `layout_json` text,
  `metric_codes` varchar(1000) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `is_default` tinyint DEFAULT '0',
  `status` varchar(32) DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dashboard_subscription`
--

DROP TABLE IF EXISTS `dashboard_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dashboard_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `dashboard_id` bigint NOT NULL,
  `subscriber_id` bigint DEFAULT NULL,
  `frequency` varchar(32) DEFAULT 'DAILY',
  `channel` varchar(32) DEFAULT 'IN_APP',
  `status` varchar(32) DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dashboard` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dcep_merchant_qualification`
--

DROP TABLE IF EXISTS `dcep_merchant_qualification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dcep_merchant_qualification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `config_id` bigint DEFAULT NULL COMMENT 'FK→dcep_config.id (NULL 表示尚未绑定)',
  `legal_person` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '法人姓名',
  `legal_id_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '法人身份证号 (存文本，加密存储由上层负责)',
  `legal_id_front` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '法人身份证正面图路径',
  `legal_id_back` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '法人身份证反面图路径',
  `business_license_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '营业执照注册号',
  `business_license_img` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '营业执照图片路径',
  `org_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构代码证 (可选)',
  `tax_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '税务登记号',
  `bank_account_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行账户名',
  `bank_account_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '银行账号',
  `bank_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户行名称',
  `bank_branch` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '开户支行',
  `contact_person` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `contact_email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系邮箱',
  `address` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '经营地址',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态 0=待提交 1=审核中 2=已通过 3=已驳回',
  `audit_remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核意见',
  `audit_at` datetime DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config` (`config_id`),
  KEY `idx_audit` (`audit_status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字人民币商户资质材料';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dcep_qualification_attachment`
--

DROP TABLE IF EXISTS `dcep_qualification_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dcep_qualification_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'PK',
  `qualification_id` bigint NOT NULL COMMENT 'FK→dcep_merchant_qualification.id',
  `material_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '材料类型 legal_id_front/legal_id_back/business_license/other',
  `file_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_path` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '相对 storage/dcep/qualification/ 的路径',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '字节',
  `content_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '同类型内的排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_qual` (`qualification_id`,`material_type`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数字人民币资质材料附件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dim_table`
--

DROP TABLE IF EXISTS `dim_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dim_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `dim_code` varchar(64) NOT NULL,
  `dim_name` varchar(128) NOT NULL,
  `dim_type` varchar(32) DEFAULT 'BUSINESS',
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`dim_code`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dim_value`
--

DROP TABLE IF EXISTS `dim_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dim_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dim_id` bigint NOT NULL,
  `value_code` varchar(64) NOT NULL,
  `value_name` varchar(128) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `extra_json` text,
  `status` varchar(32) DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  KEY `idx_dim` (`dim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ecommerce_order`
--

DROP TABLE IF EXISTS `ecommerce_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ecommerce_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '0',
  `platform` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `external_order_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `local_order_id` bigint DEFAULT NULL,
  `order_no` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_name` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_phone` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `amount` decimal(18,2) DEFAULT NULL,
  `shipping_fee` decimal(18,2) DEFAULT NULL,
  `total_amount` decimal(18,2) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `address` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `tracking_no` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `shipping_status` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `items_json` text COLLATE utf8mb4_bin,
  `raw_payload` text COLLATE utf8mb4_bin,
  `order_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ecommerce_platform`
--

DROP TABLE IF EXISTS `ecommerce_platform`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ecommerce_platform` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '0',
  `platform` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `shop_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `shop_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `app_key` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `app_secret` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `access_token` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `token_expire_at` datetime DEFAULT NULL,
  `webhook_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `webhook_secret` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `api_base_url` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_products` tinyint DEFAULT '0',
  `sync_orders` tinyint DEFAULT '0',
  `sync_inventory` tinyint DEFAULT '0',
  `last_sync_at` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ecommerce_product`
--

DROP TABLE IF EXISTS `ecommerce_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ecommerce_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '0',
  `platform` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `external_product_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `local_product_id` bigint DEFAULT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `sku` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `price` decimal(18,2) DEFAULT NULL,
  `stock` int DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `last_sync_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ecommerce_sync_log`
--

DROP TABLE IF EXISTS `ecommerce_sync_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ecommerce_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `platform` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `duration_ms` int DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `feature_flag`
--

DROP TABLE IF EXISTS `feature_flag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feature_flag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `flag_key` varchar(64) NOT NULL,
  `flag_name` varchar(128) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `enabled` int DEFAULT '0',
  `rollout_percent` int DEFAULT '0',
  `whitelist` varchar(1000) DEFAULT NULL,
  `blacklist` varchar(1000) DEFAULT NULL,
  `user_rule` varchar(32) DEFAULT NULL,
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `owner` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_key` (`tenant_id`,`flag_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fiscal_invoice`
--

DROP TABLE IF EXISTS `fiscal_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fiscal_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `invoice_no` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `invoice_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_tax_no` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_address` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_phone` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_email` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_bank` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `buyer_account` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `amount` decimal(18,2) DEFAULT NULL,
  `tax_amount` decimal(18,2) DEFAULT NULL,
  `total_amount` decimal(18,2) DEFAULT NULL,
  `tax_rate` decimal(5,4) DEFAULT NULL,
  `invoice_status` varchar(32) COLLATE utf8mb4_bin DEFAULT 'ISSUED',
  `invoice_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `pdf_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `fiscal_no` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `channel` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `issued_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `forecast_accuracy_log`
--

DROP TABLE IF EXISTS `forecast_accuracy_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forecast_accuracy_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL,
  `algorithm` varchar(32) DEFAULT NULL,
  `model_version` varchar(16) DEFAULT NULL,
  `eval_date` date DEFAULT NULL,
  `window_days` int DEFAULT NULL,
  `mae` decimal(10,4) DEFAULT NULL,
  `mape` decimal(5,4) DEFAULT NULL,
  `rmse` decimal(10,4) DEFAULT NULL,
  `smape` decimal(5,4) DEFAULT NULL,
  `weekday_mae` decimal(10,4) DEFAULT NULL,
  `weekend_mae` decimal(10,4) DEFAULT NULL,
  `conclusion` varchar(16) DEFAULT NULL,
  `recommendation` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gdpr_audit_log`
--

DROP TABLE IF EXISTS `gdpr_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gdpr_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `actor_id` bigint DEFAULT NULL,
  `actor_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `action` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `target_type` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `target_id` bigint DEFAULT NULL,
  `pii_data` tinyint DEFAULT NULL,
  `details` text COLLATE utf8mb4_bin,
  `ip_address` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gdpr_consent`
--

DROP TABLE IF EXISTS `gdpr_consent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gdpr_consent` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `member_id` bigint DEFAULT NULL,
  `consent_type` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `granted` tinyint DEFAULT '1',
  `version` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `user_agent` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `channel` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gdpr_data_request`
--

DROP TABLE IF EXISTS `gdpr_data_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gdpr_data_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `request_no` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `member_id` bigint DEFAULT NULL,
  `request_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT 'SUBMITTED',
  `export_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `export_expire_at` datetime DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `processor_id` bigint DEFAULT NULL,
  `processed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gift_card`
--

DROP TABLE IF EXISTS `gift_card`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gift_card` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `card_no` varchar(64) NOT NULL,
  `card_type` varchar(32) DEFAULT 'AMOUNT' COMMENT 'AMOUNT/SERVICE/TIMES',
  `face_value` decimal(18,2) DEFAULT '0.00',
  `balance` decimal(18,2) DEFAULT '0.00',
  `service_id` bigint DEFAULT NULL,
  `service_count` int DEFAULT '0',
  `buyer_id` bigint DEFAULT NULL,
  `buyer_name` varchar(64) DEFAULT NULL,
  `recipient_name` varchar(64) DEFAULT NULL,
  `recipient_phone` varchar(32) DEFAULT NULL,
  `gift_message` varchar(500) DEFAULT NULL,
  `theme` varchar(64) DEFAULT NULL,
  `cover_image` varchar(255) DEFAULT NULL,
  `purchase_order_id` bigint DEFAULT NULL,
  `purchase_time` datetime DEFAULT NULL,
  `expire_time` datetime DEFAULT NULL,
  `status` varchar(16) DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED',
  `use_time` datetime DEFAULT NULL,
  `use_order_id` bigint DEFAULT NULL,
  `use_store_id` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_cardno` (`tenant_id`,`card_no`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绀煎搧鍗';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gift_card_transaction`
--

DROP TABLE IF EXISTS `gift_card_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gift_card_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `card_id` bigint DEFAULT NULL,
  `tx_type` varchar(20) DEFAULT '',
  `amount` decimal(12,2) DEFAULT '0.00',
  `balance` decimal(12,2) DEFAULT '0.00',
  `order_id` bigint DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `notes` varchar(255) DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_card` (`card_id`),
  KEY `idx_tx_type` (`tx_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hipaa_encryption_key`
--

DROP TABLE IF EXISTS `hipaa_encryption_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hipaa_encryption_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `key_alias` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `key_version` int DEFAULT NULL,
  `algorithm` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `key_status` varchar(32) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `public_key` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `kms_arn` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `rotated_at` datetime DEFAULT NULL,
  `next_rotate_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hipaa_phi_access`
--

DROP TABLE IF EXISTS `hipaa_phi_access`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hipaa_phi_access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `member_id` bigint DEFAULT NULL,
  `field_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `access_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `actor_id` bigint DEFAULT NULL,
  `actor_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `actor_role` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `purpose` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `is_break_glass` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hq_cross_store_metric`
--

DROP TABLE IF EXISTS `hq_cross_store_metric`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hq_cross_store_metric` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `period_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'DAY',
  `period_key` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_stores` int DEFAULT '0',
  `active_stores` int DEFAULT '0',
  `total_revenue` decimal(18,2) DEFAULT '0.00',
  `total_orders` bigint DEFAULT '0',
  `total_members` bigint DEFAULT '0',
  `new_members` bigint DEFAULT '0',
  `avg_order_amt` decimal(10,2) DEFAULT '0.00',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_period` (`tenant_id`,`period_type`,`period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='璺ㄩ棬搴楁寚鏍';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hq_dashboard_snapshot`
--

DROP TABLE IF EXISTS `hq_dashboard_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hq_dashboard_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `period_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'DAY' COMMENT 'DAY / WEEK / MONTH',
  `period_key` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '2026-08-14',
  `brand_id` bigint DEFAULT NULL,
  `widget_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `metric_value` decimal(18,4) DEFAULT '0.0000',
  `extra` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `snapshot_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_period` (`tenant_id`,`period_type`,`period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎬婚儴鐪嬫澘蹇?収';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hq_dashboard_widget`
--

DROP TABLE IF EXISTS `hq_dashboard_widget`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hq_dashboard_widget` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `widget_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `widget_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `widget_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'KPI' COMMENT 'KPI / CHART / TABLE / LIST',
  `metric_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `query_sql` text COLLATE utf8mb4_unicode_ci,
  `layout_x` int DEFAULT '0',
  `layout_y` int DEFAULT '0',
  `width` int DEFAULT '4',
  `height` int DEFAULT '3',
  `refresh_sec` int DEFAULT '60',
  `enabled` tinyint DEFAULT '1',
  `sort_order` int DEFAULT '100',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_widget` (`tenant_id`,`widget_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎬婚儴鐪嬫澘缁勪欢';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `industry_service_relation`
--

DROP TABLE IF EXISTS `industry_service_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `industry_service_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `industry_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SERVICE / PRODUCT / FORM / ROLE',
  `target_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `required` tinyint DEFAULT '1' COMMENT '是否该行业必备',
  `sort_order` int DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_industry_target` (`industry_code`,`target_type`,`target_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行业-服务/商品关联';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `industry_template`
--

DROP TABLE IF EXISTS `industry_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `industry_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `industry_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BEAUTY / MEDSPA / DENTAL / HAIR / MATERNITY',
  `industry_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '美业 / 医美 / 口腔 / 美发 / 月子',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `default_services` text COLLATE utf8mb4_unicode_ci COMMENT 'JSON 默认服务列表',
  `default_products` text COLLATE utf8mb4_unicode_ci COMMENT 'JSON 默认商品列表',
  `default_forms` text COLLATE utf8mb4_unicode_ci COMMENT 'JSON 默认表单（如医美同意书）',
  `enabled` tinyint DEFAULT '1',
  `sort_order` int DEFAULT '0',
  `icon` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_industry_code` (`industry_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行业模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `iot_command_dest`
--

DROP TABLE IF EXISTS `iot_command_dest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iot_command_dest` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `device_id` bigint NOT NULL COMMENT '目标设备',
  `device_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `command_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'START / STOP / RESET / CALIBRATE / UPGRADE / CUSTOM',
  `command_payload` text COLLATE utf8mb4_unicode_ci COMMENT 'JSON 命令负载',
  `priority` int DEFAULT '5' COMMENT '1-10, 1 最高',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING' COMMENT 'PENDING / SENT / ACK / SUCCESS / FAIL / TIMEOUT',
  `sent_at` datetime DEFAULT NULL,
  `ack_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `response_payload` text COLLATE utf8mb4_unicode_ci COMMENT '设备响应',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retry_count` int DEFAULT '0',
  `max_retry` int DEFAULT '3',
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_status` (`device_id`,`status`),
  KEY `idx_status` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT 命令下发';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `iot_device`
--

DROP TABLE IF EXISTS `iot_device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iot_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `device_code` varchar(50) DEFAULT NULL,
  `device_sn` varchar(100) DEFAULT NULL,
  `device_name` varchar(100) DEFAULT NULL,
  `device_type` varchar(50) DEFAULT NULL,
  `protocol` varchar(20) DEFAULT NULL,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `firmware_version` varchar(50) DEFAULT NULL,
  `firmware` varchar(50) DEFAULT NULL,
  `mac_address` varchar(50) DEFAULT NULL,
  `ip_address` varchar(50) DEFAULT NULL,
  `vendor` varchar(100) DEFAULT NULL,
  `mqtt_client_id` varchar(100) DEFAULT NULL,
  `mqtt_topic` varchar(200) DEFAULT NULL,
  `auth_token` varchar(200) DEFAULT NULL,
  `last_telemetry` text,
  `last_online_at` datetime DEFAULT NULL,
  `last_heartbeat` datetime DEFAULT NULL,
  `online_status` varchar(20) DEFAULT NULL,
  `bind_member_id` bigint DEFAULT NULL,
  `status` varchar(20) DEFAULT 'OFFLINE',
  `enabled` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_device_code` (`device_code`),
  KEY `idx_device_type` (`device_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `iot_device_link`
--

DROP TABLE IF EXISTS `iot_device_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iot_device_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `master_device_id` bigint NOT NULL,
  `slave_device_id` bigint NOT NULL,
  `trigger_type` varchar(32) NOT NULL,
  `trigger_condition` varchar(1000) DEFAULT NULL,
  `action_json` varchar(1000) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `description` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_master` (`master_device_id`),
  KEY `idx_slave` (`slave_device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `iot_event_dest`
--

DROP TABLE IF EXISTS `iot_event_dest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iot_event_dest` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `device_id` bigint NOT NULL,
  `device_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'TELEMETRY / ALERT / STATUS / ERROR',
  `event_payload` text COLLATE utf8mb4_unicode_ci COMMENT 'JSON 事件数据',
  `severity` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'INFO' COMMENT 'INFO / WARN / ERROR / CRITICAL',
  `occurred_at` datetime DEFAULT NULL,
  `received_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `processed` tinyint DEFAULT '0',
  `processed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_device_event` (`device_id`,`event_type`,`occurred_at`),
  KEY `idx_processed` (`processed`,`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IoT 事件上报';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `kiosk_checkin`
--

DROP TABLE IF EXISTS `kiosk_checkin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kiosk_checkin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `device_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `booking_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `checkin_method` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `checkin_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `arrived_at` datetime DEFAULT NULL,
  `service_started_at` datetime DEFAULT NULL,
  `service_finished_at` datetime DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT 'CHECKED_IN',
  `staff_id` bigint DEFAULT NULL,
  `notes` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_device` (`device_id`),
  KEY `idx_member` (`member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `kiosk_device`
--

DROP TABLE IF EXISTS `kiosk_device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kiosk_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `device_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `device_name` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `location` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `last_heartbeat` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ltv_churn_result`
--

DROP TABLE IF EXISTS `ltv_churn_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ltv_churn_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `metric_type` varchar(32) NOT NULL,
  `member_id` bigint NOT NULL,
  `period` varchar(16) DEFAULT NULL,
  `value` decimal(15,4) DEFAULT '0.0000',
  `value_type` varchar(16) DEFAULT NULL,
  `details` varchar(2000) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_metric` (`tenant_id`,`metric_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ltv_training_feature`
--

DROP TABLE IF EXISTS `ltv_training_feature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ltv_training_feature` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `feature_date` date NOT NULL,
  `recency_days` int DEFAULT NULL,
  `frequency_total` int DEFAULT NULL,
  `frequency_90d` int DEFAULT NULL,
  `monetary_total` decimal(12,2) DEFAULT NULL,
  `monetary_90d` decimal(12,2) DEFAULT NULL,
  `aov` decimal(10,2) DEFAULT NULL,
  `avg_interval_days` int DEFAULT NULL,
  `tenure_days` int DEFAULT NULL,
  `member_age_group` varchar(32) DEFAULT NULL,
  `lifecycle_stage` varchar(32) DEFAULT NULL,
  `service_category_count` int DEFAULT NULL,
  `preferred_category_id` bigint DEFAULT NULL,
  `preferred_employee_id` bigint DEFAULT NULL,
  `preferred_store_id` bigint DEFAULT NULL,
  `preferred_hour` int DEFAULT NULL,
  `weekend_ratio` decimal(5,4) DEFAULT NULL,
  `holiday_ratio` decimal(5,4) DEFAULT NULL,
  `wallet_balance` decimal(12,2) DEFAULT NULL,
  `active_packages` int DEFAULT NULL,
  `total_package_balance` decimal(12,2) DEFAULT NULL,
  `package_consumption_rate` decimal(5,4) DEFAULT NULL,
  `booking_count` int DEFAULT NULL,
  `noshow_count` int DEFAULT NULL,
  `noshow_rate` decimal(5,4) DEFAULT NULL,
  `review_count` int DEFAULT NULL,
  `avg_rating` decimal(3,2) DEFAULT NULL,
  `complaint_count` int DEFAULT NULL,
  `coupon_used_count` int DEFAULT NULL,
  `point_balance` bigint DEFAULT NULL,
  `member_level` varchar(32) DEFAULT NULL,
  `rfm_segment` varchar(32) DEFAULT NULL,
  `churn_risk_score` decimal(5,4) DEFAULT NULL,
  `ltv_score` decimal(12,2) DEFAULT NULL,
  `lifecycle_label` varchar(32) DEFAULT NULL,
  `next_90d_revenue` decimal(12,2) DEFAULT NULL,
  `next_90d_active` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_feature_date` (`feature_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `market_activity`
--

DROP TABLE IF EXISTS `market_activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint DEFAULT NULL,
  `tenant_id` bigint DEFAULT NULL COMMENT 'æ‰€å±žç§Ÿæˆ·ID',
  `name` varchar(100) DEFAULT NULL,
  `type` int DEFAULT NULL COMMENT '1满减 2折扣 3赠礼 4双倍积分',
  `rule_config` text,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `status` int DEFAULT '1' COMMENT '0禁用 1启用 2已结束',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`),
  KEY `idx_ma_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `market_coupon`
--

DROP TABLE IF EXISTS `market_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `type` int DEFAULT NULL COMMENT '1满减券 2折扣券 3礼品券',
  `rule_type` int DEFAULT NULL COMMENT '1满X元减Y 2折扣X% 3无门槛',
  `rule_value` decimal(10,2) DEFAULT NULL,
  `min_amount` decimal(10,2) DEFAULT NULL,
  `max_discount` decimal(10,2) DEFAULT NULL,
  `total_count` int DEFAULT NULL,
  `per_limit` int DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `status` int DEFAULT '1' COMMENT '0禁用 1启用',
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `market_coupon_grant`
--

DROP TABLE IF EXISTS `market_coupon_grant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_coupon_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `coupon_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `code` varchar(50) DEFAULT NULL,
  `status` int DEFAULT '1' COMMENT '1未使用 2已使用 3已过期',
  `source` int DEFAULT '1' COMMENT '1主动领取 2系统发放 3活动奖励',
  `order_id` bigint DEFAULT NULL,
  `used_at` datetime DEFAULT NULL,
  `expire_time` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` int NOT NULL DEFAULT '0' COMMENT '?????????',
  PRIMARY KEY (`id`),
  KEY `idx_coupon_id` (`coupon_id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `market_point_record`
--

DROP TABLE IF EXISTS `market_point_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_point_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL DEFAULT '1',
  `tenant_id` bigint DEFAULT NULL COMMENT 'æ‰€å±žç§Ÿæˆ·ID',
  `member_id` bigint NOT NULL,
  `type` tinyint NOT NULL COMMENT '1=获得 2=消耗',
  `point_value` int NOT NULL,
  `balance_before` int NOT NULL,
  `balance_after` int NOT NULL,
  `source` varchar(32) NOT NULL,
  `ref_id` bigint DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member_created` (`member_id`,`created_at`),
  KEY `idx_source` (`source`),
  KEY `idx_ref` (`ref_id`),
  KEY `idx_mpr_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=489 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketing_touch_conversion`
--

DROP TABLE IF EXISTS `marketing_touch_conversion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketing_touch_conversion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `touch_log_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `conversion_type` varchar(32) NOT NULL COMMENT 'ORDER订单/REDEMPTION核销/COUPON优惠券/MEMBER_REGISTER注册',
  `related_id` bigint NOT NULL,
  `related_no` varchar(64) DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT '0.00',
  `converted_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log` (`touch_log_id`),
  KEY `idx_task` (`task_id`,`conversion_type`),
  KEY `idx_type` (`conversion_type`,`amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='触达转化明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketing_touch_log`
--

DROP TABLE IF EXISTS `marketing_touch_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketing_touch_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_id` bigint NOT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `member_phone` varchar(20) DEFAULT NULL,
  `channel_type` varchar(32) DEFAULT NULL,
  `content` varchar(2000) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待发送 2已发送 3失败 4已读 5已转化',
  `error_msg` varchar(500) DEFAULT NULL,
  `scheduled_at` datetime DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `read_at` datetime DEFAULT NULL,
  `convert_at` datetime DEFAULT NULL,
  `related_order_id` bigint DEFAULT NULL,
  `retry_count` int DEFAULT '0',
  `external_msg_id` varchar(128) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`,`status`),
  KEY `idx_member` (`member_id`,`status`),
  KEY `idx_status_time` (`status`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='触达执行明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `marketing_touch_task`
--

DROP TABLE IF EXISTS `marketing_touch_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marketing_touch_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `task_no` varchar(64) NOT NULL,
  `source_type` varchar(32) DEFAULT NULL COMMENT 'JOURNEY旅程/MANUAL手动/SCHEDULE定时/TRIGGER触发器',
  `journey_id` bigint DEFAULT NULL COMMENT '关联旅程',
  `journey_node_id` bigint DEFAULT NULL COMMENT '旅程节点',
  `campaign_name` varchar(128) NOT NULL,
  `channel_type` varchar(32) NOT NULL,
  `template_id` bigint DEFAULT NULL,
  `template_snapshot` varchar(2000) DEFAULT NULL COMMENT '模板快照',
  `trigger_event` varchar(64) DEFAULT NULL COMMENT 'ORDER_PAYED/ORDER_COMPLETED/CARD_DELIVERED/BIRTHDAY/EXPIRE_30D/MEMBER_REGISTER/INACTIVE_30D',
  `trigger_offset_minutes` int DEFAULT '0' COMMENT '相对触发偏移（负数=前）',
  `audience_filter` text COMMENT '人群过滤条件（JSON）',
  `scheduled_at` datetime NOT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待执行 2执行中 3已完成 4已暂停 5已失败 6已取消',
  `target_count` int DEFAULT '0',
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `read_count` int DEFAULT '0',
  `convert_count` int DEFAULT '0',
  `max_reach_per_day` int DEFAULT NULL COMMENT '每日最大触达',
  `remark` varchar(500) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_task` (`tenant_id`,`task_no`),
  KEY `idx_status` (`status`,`scheduled_at`),
  KEY `idx_journey` (`journey_id`),
  KEY `idx_event` (`trigger_event`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销触达任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mem_level`
--

DROP TABLE IF EXISTS `mem_level`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mem_level` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '等级ID',
  `level_code` varchar(50) NOT NULL COMMENT '等级编码',
  `level_name` varchar(50) NOT NULL COMMENT '等级名称',
  `level_icon` varchar(255) DEFAULT NULL COMMENT '等级图标',
  `min_points` int DEFAULT '0' COMMENT '最低积分要求',
  `max_points` int DEFAULT NULL COMMENT '最高积分要求',
  `discount_rate` decimal(4,2) DEFAULT '1.00' COMMENT '折扣率（0-1）',
  `birthday_gift` varchar(100) DEFAULT NULL COMMENT '生日礼物',
  `birthday_discount` decimal(4,2) DEFAULT '1.00' COMMENT '生日折扣',
  `points_multiplier` decimal(3,2) DEFAULT '1.00' COMMENT '积分倍数',
  `level_color` varchar(20) DEFAULT '#409EFF' COMMENT '等级颜色',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `description` text COMMENT '等级说明',
  `status` tinyint DEFAULT '1' COMMENT '状态：0禁用 1启用',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `level_code` (`level_code`),
  KEY `idx_level_code` (`level_code`),
  KEY `idx_sort_order` (`sort_order`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员等级表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_credit`
--

DROP TABLE IF EXISTS `member_credit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_credit` (
  `member_id` bigint NOT NULL,
  `tenant_id` bigint DEFAULT '1',
  `credit` int DEFAULT '100' COMMENT '当前信用分',
  `no_show_count` int DEFAULT '0' COMMENT '累计爽约次数',
  `total_bookings` int DEFAULT '0' COMMENT '累计预约次数',
  `locked_until` datetime DEFAULT NULL COMMENT '被锁定截止时间',
  `last_no_show_at` datetime DEFAULT NULL COMMENT '最近一次爽约',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员信用汇总';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_credit_log`
--

DROP TABLE IF EXISTS `member_credit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_credit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `member_id` bigint NOT NULL,
  `change_type` varchar(16) NOT NULL COMMENT 'NO_SHOW/ON_TIME/CANCEL_LATE/MANUAL',
  `change_value` int NOT NULL COMMENT '正为加分，负为扣分',
  `credit_after` int DEFAULT '0' COMMENT '调整后分数',
  `booking_id` bigint DEFAULT NULL COMMENT '关联预约',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单',
  `fee_penalty` decimal(10,2) DEFAULT '0.00' COMMENT '扣费',
  `lock_until` datetime DEFAULT NULL COMMENT '锁定截止',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_booking` (`booking_id`),
  KEY `idx_type` (`change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员信用分日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_day_config`
--

DROP TABLE IF EXISTS `member_day_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_day_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `config_name` varchar(128) NOT NULL,
  `day_type` varchar(32) DEFAULT 'BIRTHDAY' COMMENT 'BIRTHDAY/FIXED/ANNIVERSARY',
  `trigger_rule` varchar(255) DEFAULT NULL,
  `discount_rate` decimal(5,2) DEFAULT '1.00',
  `gift_amount` decimal(18,2) DEFAULT '0.00',
  `gift_points` int DEFAULT '0',
  `gift_coupon_id` bigint DEFAULT NULL,
  `free_service_ids` varchar(500) DEFAULT NULL,
  `coupon_amount` decimal(18,2) DEFAULT '0.00',
  `target_level` varchar(64) DEFAULT NULL,
  `target_store_ids` varchar(500) DEFAULT NULL,
  `birthday_offset_days` int DEFAULT '0',
  `fixed_month` int DEFAULT NULL,
  `fixed_day` int DEFAULT NULL,
  `repeat_annually` tinyint DEFAULT '1',
  `banner_image` varchar(255) DEFAULT NULL,
  `sms_template` varchar(500) DEFAULT NULL,
  `push_title` varchar(128) DEFAULT NULL,
  `push_content` varchar(500) DEFAULT NULL,
  `priority` int DEFAULT '100',
  `status` varchar(16) DEFAULT 'ACTIVE',
  `effective_date` date DEFAULT NULL,
  `expire_date` date DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浼氬憳鏃ラ厤缃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_day_execution`
--

DROP TABLE IF EXISTS `member_day_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_day_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `config_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(100) DEFAULT '',
  `member_phone` varchar(50) DEFAULT '',
  `trigger_reason` varchar(50) DEFAULT '',
  `trigger_date` date DEFAULT NULL,
  `channel` varchar(20) DEFAULT '',
  `discount_amount` decimal(12,2) DEFAULT '0.00',
  `gift_amount` decimal(12,2) DEFAULT '0.00',
  `gift_points` int DEFAULT '0',
  `coupon_code` varchar(50) DEFAULT '',
  `send_status` varchar(20) DEFAULT '',
  `sent_at` datetime DEFAULT NULL,
  `used_at` datetime DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `error_message` varchar(500) DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_config` (`config_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_trigger_date` (`trigger_date`),
  KEY `idx_status` (`send_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_invite_code`
--

DROP TABLE IF EXISTS `member_invite_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_invite_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `tenant_id` bigint NOT NULL COMMENT '?? ID',
  `inviter_id` bigint NOT NULL COMMENT '????? ID',
  `invite_code` varchar(20) NOT NULL COMMENT '???',
  `invite_channel` varchar(20) DEFAULT NULL COMMENT '???? WX/QR/LINK/SMS',
  `invite_count` int NOT NULL DEFAULT '0' COMMENT '?????',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '?????',
  `reward_coupon_template` bigint DEFAULT NULL COMMENT '?????',
  `reward_coupon_amount` decimal(10,2) DEFAULT NULL COMMENT '?????',
  `expire_at` datetime DEFAULT NULL COMMENT '????',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/DISABLED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_tenant_inviter` (`tenant_id`,`inviter_id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='???????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_invite_reward`
--

DROP TABLE IF EXISTS `member_invite_reward`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_invite_reward` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `tenant_id` bigint NOT NULL COMMENT '?? ID',
  `invite_code_id` bigint NOT NULL COMMENT '??? ID',
  `inviter_id` bigint NOT NULL COMMENT '???',
  `invitee_id` bigint NOT NULL COMMENT '????',
  `invitee_name` varchar(50) DEFAULT NULL COMMENT '??????',
  `invitee_phone` varchar(20) DEFAULT NULL COMMENT '??????',
  `register_at` datetime NOT NULL COMMENT '????????',
  `first_order_id` bigint DEFAULT NULL COMMENT '?? ID',
  `first_order_at` datetime DEFAULT NULL COMMENT '????',
  `inviter_reward_type` varchar(20) NOT NULL COMMENT '??????? COUPON/POINTS/CASH',
  `inviter_reward_value` varchar(100) NOT NULL COMMENT '??????',
  `inviter_reward_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/USED',
  `invitee_reward_type` varchar(20) NOT NULL COMMENT '????????',
  `invitee_reward_value` varchar(100) NOT NULL COMMENT '???????',
  `invitee_reward_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `operator_name` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_inviter` (`tenant_id`,`inviter_id`),
  KEY `idx_tenant_invitee` (`tenant_id`,`invitee_id`),
  KEY `idx_invite_code` (`invite_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_lifecycle_log`
--

DROP TABLE IF EXISTS `member_lifecycle_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_lifecycle_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `tenant_id` bigint NOT NULL COMMENT '?? ID',
  `rule_id` bigint NOT NULL COMMENT '?? ID',
  `rule_code` varchar(50) NOT NULL COMMENT '????',
  `rule_type` varchar(20) NOT NULL COMMENT '????',
  `member_id` bigint NOT NULL COMMENT '???? ID',
  `member_name` varchar(50) DEFAULT NULL COMMENT '????',
  `member_phone` varchar(20) DEFAULT NULL COMMENT '????',
  `trigger_date` date NOT NULL COMMENT '????',
  `trigger_reason` varchar(200) DEFAULT NULL COMMENT '????',
  `channel` varchar(20) NOT NULL COMMENT '????',
  `template_id` bigint DEFAULT NULL COMMENT '????',
  `template_content` text COMMENT '??????',
  `coupon_code` varchar(50) DEFAULT NULL COMMENT '??????',
  `points_awarded` int DEFAULT NULL COMMENT '?????',
  `gift_value` varchar(100) DEFAULT NULL COMMENT '????',
  `journey_instance_id` bigint DEFAULT NULL COMMENT '?????? ID',
  `status` varchar(20) NOT NULL COMMENT 'PENDING/SENT/FAILED/USED/EXPIRED',
  `error_message` varchar(500) DEFAULT NULL COMMENT '????',
  `external_msg_id` varchar(100) DEFAULT NULL COMMENT '???? ID',
  `converted_at` datetime DEFAULT NULL COMMENT '????',
  `related_order_id` bigint DEFAULT NULL COMMENT '????',
  `sent_at` datetime DEFAULT NULL COMMENT '????',
  `used_at` datetime DEFAULT NULL COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_member_date` (`rule_id`,`member_id`,`trigger_date`,`deleted`),
  KEY `idx_tenant_rule_date` (`tenant_id`,`rule_id`,`trigger_date`),
  KEY `idx_tenant_member` (`tenant_id`,`member_id`,`rule_type`),
  KEY `idx_status_date` (`status`,`trigger_date`)
) ENGINE=InnoDB AUTO_INCREMENT=111 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='??????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_lifecycle_rule`
--

DROP TABLE IF EXISTS `member_lifecycle_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_lifecycle_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `tenant_id` bigint NOT NULL COMMENT '?? ID',
  `brand_id` bigint DEFAULT NULL COMMENT '?? ID',
  `rule_code` varchar(50) NOT NULL COMMENT '????',
  `rule_type` varchar(20) NOT NULL COMMENT 'BIRTHDAY/MEMBER_DAY/RETENTION/INVITE',
  `rule_name` varchar(100) NOT NULL COMMENT '????',
  `description` varchar(500) DEFAULT NULL COMMENT '??',
  `trigger_config_json` json DEFAULT NULL COMMENT '???? JSON',
  `template_id` bigint DEFAULT NULL COMMENT '???? ID',
  `rfm_segment` varchar(20) DEFAULT NULL COMMENT '?? RFM ??',
  `invoke_journey_id` bigint DEFAULT NULL COMMENT '???? ID',
  `invoke_coupon_amount` decimal(10,2) DEFAULT NULL COMMENT '????????',
  `invoke_points` int DEFAULT NULL COMMENT '??????',
  `schedule_cron` varchar(50) DEFAULT NULL COMMENT 'cron ???',
  `max_per_day` int DEFAULT '1000' COMMENT '???????',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '1=?? 0=??',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/PAUSED/ARCHIVED',
  `priority` int NOT NULL DEFAULT '100' COMMENT '???',
  `operator_name` varchar(50) DEFAULT NULL COMMENT '???',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_rule_code` (`tenant_id`,`rule_code`,`deleted`),
  KEY `idx_tenant_type` (`tenant_id`,`rule_type`,`enabled`),
  KEY `idx_tenant_priority` (`tenant_id`,`priority`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='??????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_treatment_progress`
--

DROP TABLE IF EXISTS `member_treatment_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_treatment_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `member_id` bigint NOT NULL COMMENT '会员ID',
  `plan_id` bigint NOT NULL COMMENT '疗程方案ID',
  `service_id` bigint NOT NULL COMMENT '服务项目ID',
  `package_order_id` bigint DEFAULT NULL COMMENT '关联的套餐订单ID',
  `total_sessions` int NOT NULL DEFAULT '1' COMMENT '总次数',
  `used_sessions` int NOT NULL DEFAULT '0' COMMENT '已用次数',
  `remaining_sessions` int NOT NULL DEFAULT '1' COMMENT '剩余次数',
  `current_phase_no` int DEFAULT '1' COMMENT '当前阶段序号',
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `last_session_at` datetime DEFAULT NULL COMMENT '上次服务时间',
  `expected_finish_at` datetime DEFAULT NULL COMMENT '预计完成时间',
  `finished_at` datetime DEFAULT NULL COMMENT '实际完成时间',
  `status` tinyint DEFAULT '1' COMMENT '1进行中 2已完成 3已停用',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_plan` (`plan_id`),
  KEY `idx_status` (`status`),
  KEY `idx_member_status` (`member_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员疗程进度';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `member_treatment_session`
--

DROP TABLE IF EXISTS `member_treatment_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_treatment_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `progress_id` bigint NOT NULL COMMENT 'member_treatment_progress.id',
  `member_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `phase_no` int NOT NULL,
  `session_no` int NOT NULL COMMENT '本阶段内的第几次',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单',
  `employee_id` bigint DEFAULT NULL COMMENT '技师ID',
  `employee_name` varchar(64) DEFAULT NULL,
  `session_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '服务时间',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_progress` (`progress_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会员疗程服务记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `meta_column`
--

DROP TABLE IF EXISTS `meta_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meta_column` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `meta_table_id` bigint NOT NULL,
  `column_name` varchar(128) NOT NULL,
  `column_type` varchar(64) DEFAULT NULL,
  `data_type` varchar(64) DEFAULT NULL,
  `is_primary_key` tinyint DEFAULT '0',
  `is_nullable` tinyint DEFAULT '1',
  `description` varchar(500) DEFAULT NULL,
  `is_sensitive` tinyint DEFAULT '0',
  `enum_values` varchar(1000) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_meta_table` (`meta_table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `meta_lineage`
--

DROP TABLE IF EXISTS `meta_lineage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meta_lineage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `source_table_code` varchar(128) NOT NULL,
  `target_table_code` varchar(128) NOT NULL,
  `transformation` varchar(1000) DEFAULT NULL,
  `lineage_type` varchar(32) DEFAULT 'ETL',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `meta_table`
--

DROP TABLE IF EXISTS `meta_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meta_table` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `table_code` varchar(128) NOT NULL,
  `table_name` varchar(255) NOT NULL,
  `db_name` varchar(128) DEFAULT NULL,
  `schema_name` varchar(128) DEFAULT NULL,
  `category` varchar(64) DEFAULT 'BUSINESS',
  `description` varchar(1000) DEFAULT NULL,
  `row_count` bigint DEFAULT '0',
  `size_mb` decimal(15,2) DEFAULT '0.00',
  `owner` varchar(128) DEFAULT NULL,
  `tag` varchar(255) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'ACTIVE',
  `last_sync_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_code` (`table_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `metric_definition`
--

DROP TABLE IF EXISTS `metric_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `metric_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `metric_code` varchar(64) NOT NULL,
  `metric_name` varchar(128) NOT NULL,
  `metric_category` varchar(64) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `unit` varchar(32) DEFAULT NULL,
  `sql_template` varchar(2000) DEFAULT NULL,
  `source_table` varchar(128) DEFAULT NULL,
  `default_dimensions` varchar(255) DEFAULT NULL,
  `aggregation` varchar(32) DEFAULT 'SUM',
  `target_value` decimal(20,2) DEFAULT NULL,
  `warning_value` decimal(20,2) DEFAULT NULL,
  `higher_is_better` tinyint DEFAULT '1',
  `owner` varchar(128) DEFAULT NULL,
  `status` varchar(32) DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`metric_code`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `metric_snapshot`
--

DROP TABLE IF EXISTS `metric_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `metric_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `metric_id` bigint DEFAULT NULL,
  `metric_code` varchar(64) NOT NULL,
  `snapshot_date` date DEFAULT NULL,
  `value` decimal(20,4) DEFAULT NULL,
  `dim_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `period` varchar(32) DEFAULT 'DAILY',
  `calc_duration_ms` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_code` (`tenant_id`,`metric_code`),
  KEY `idx_tenant_date` (`tenant_id`,`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_consent_sign_link`
--

DROP TABLE IF EXISTS `mh_consent_sign_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_consent_sign_link` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_name` varchar(80) COLLATE utf8mb4_bin DEFAULT NULL,
  `member_phone` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `template_code` varchar(40) COLLATE utf8mb4_bin DEFAULT NULL,
  `template_version` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `project_name` varchar(80) COLLATE utf8mb4_bin DEFAULT NULL,
  `token` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `sign_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `expire_at` datetime DEFAULT NULL,
  `signed_at` datetime DEFAULT NULL,
  `signature_id` bigint DEFAULT NULL,
  `signature_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(60) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_member` (`member_id`),
  KEY `idx_template` (`template_id`),
  KEY `idx_token` (`token`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='知情同意扫码签链接表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_consent_signature`
--

DROP TABLE IF EXISTS `mh_consent_signature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_consent_signature` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `template_code` varchar(64) DEFAULT NULL,
  `template_version` varchar(16) DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `project_name` varchar(128) DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `order_no` varchar(64) DEFAULT NULL,
  `signature_id` varchar(64) NOT NULL,
  `sign_doc_url` varchar(500) NOT NULL COMMENT '签署 PDF',
  `sign_doc_hash` varchar(128) NOT NULL COMMENT '文档 SHA-256（防篡改）',
  `signer_name` varchar(64) NOT NULL,
  `signer_id_no` varchar(32) DEFAULT NULL COMMENT '身份证（脱敏）',
  `signer_relation` varchar(32) DEFAULT 'SELF' COMMENT 'SELF本人/PARENT监护人/AGENT代理人',
  `witness_id` bigint DEFAULT NULL,
  `witness_name` varchar(64) DEFAULT NULL,
  `witness_license_no` varchar(64) DEFAULT NULL COMMENT '医师执业证号',
  `checked_items` varchar(2000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `effective_at` datetime NOT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1已签署 2已撤回 3已过期',
  `revoked_at` datetime DEFAULT NULL,
  `revoked_reason` varchar(500) DEFAULT NULL,
  `sign_ip` varchar(64) DEFAULT NULL,
  `sign_device` varchar(128) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `expire_at` datetime DEFAULT NULL COMMENT '签署有效期截止',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_signature_id` (`signature_id`),
  KEY `idx_member` (`member_id`,`status`),
  KEY `idx_template` (`template_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知情同意签署';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_consent_template`
--

DROP TABLE IF EXISTS `mh_consent_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_consent_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `template_code` varchar(64) NOT NULL,
  `template_name` varchar(128) NOT NULL,
  `template_type` varchar(32) NOT NULL COMMENT 'GENERAL通用/SURGERY手术/INJECTION注射/LASER激光/SKINCARE护肤/PHOTO影像',
  `project_id` bigint DEFAULT NULL,
  `project_name` varchar(128) DEFAULT NULL,
  `content_md` text NOT NULL,
  `required_items` varchar(2000) DEFAULT NULL COMMENT '必勾选条款（JSON 数组）',
  `risks_md` text COMMENT '风险告知',
  `legal_basis` varchar(500) DEFAULT NULL COMMENT '法规依据',
  `version` varchar(16) DEFAULT '1.0',
  `audit_status` tinyint DEFAULT '1' COMMENT '1待审核 2已审核 3驳回',
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`template_code`,`version`),
  KEY `idx_type` (`template_type`,`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知情同意书模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_drug_device`
--

DROP TABLE IF EXISTS `mh_drug_device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_drug_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `item_code` varchar(64) NOT NULL COMMENT '物料编码',
  `item_name` varchar(128) NOT NULL,
  `item_type` varchar(32) NOT NULL COMMENT 'DRUG药品/DEVICE器械/CONSUMABLE耗材',
  `registration_no` varchar(64) NOT NULL COMMENT '国药准字/注册证号',
  `specification` varchar(128) DEFAULT NULL,
  `manufacturer` varchar(128) DEFAULT NULL,
  `category` varchar(64) DEFAULT NULL COMMENT 'BOTOX肉毒/FILLER玻尿酸/SOLDIER线雕/LASER激光/...',
  `storage_condition` varchar(128) DEFAULT NULL,
  `shelf_life_days` int DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`item_code`),
  KEY `idx_reg` (`registration_no`),
  KEY `idx_type` (`item_type`,`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药械主档';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_drug_device_batch`
--

DROP TABLE IF EXISTS `mh_drug_device_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_drug_device_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `drug_device_id` bigint NOT NULL,
  `batch_no` varchar(64) NOT NULL COMMENT '生产批号',
  `serial_no` varchar(128) DEFAULT NULL,
  `production_date` date DEFAULT NULL,
  `expire_date` date DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `supplier_name` varchar(128) DEFAULT NULL,
  `purchase_order_no` varchar(64) DEFAULT NULL,
  `inbound_qty` int NOT NULL DEFAULT '0',
  `used_qty` int DEFAULT '0',
  `remaining_qty` int DEFAULT '0',
  `unit_price` decimal(12,2) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1正常 2冻结 3过期 4召回',
  `freeze_reason` varchar(500) DEFAULT NULL,
  `recall_at` datetime DEFAULT NULL,
  `recall_reason` varchar(500) DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `location` varchar(64) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drug_batch` (`drug_device_id`,`batch_no`),
  KEY `idx_expire` (`expire_date`,`status`),
  KEY `idx_store` (`store_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药械批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_drug_device_usage`
--

DROP TABLE IF EXISTS `mh_drug_device_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_drug_device_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `batch_id` bigint NOT NULL,
  `drug_device_id` bigint NOT NULL,
  `item_name` varchar(128) DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(64) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `doctor_id` bigint DEFAULT NULL,
  `doctor_name` varchar(64) DEFAULT NULL,
  `doctor_license_no` varchar(64) DEFAULT NULL,
  `qty` int NOT NULL DEFAULT '1',
  `body_part` varchar(64) DEFAULT NULL,
  `serial_no` varchar(128) DEFAULT NULL,
  `used_at` datetime DEFAULT NULL,
  `adverse_reaction` varchar(1000) DEFAULT NULL,
  `has_adverse` tinyint DEFAULT '0',
  `status` tinyint DEFAULT '1' COMMENT '1已使用 2已撤回',
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_doctor` (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药械使用追溯';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_image_consent`
--

DROP TABLE IF EXISTS `mh_image_consent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_image_consent` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `member_id` bigint NOT NULL,
  `consent_type` varchar(32) NOT NULL COMMENT 'STORAGE存储/AI_ANALYSIS分析/ANONYMIZED_PUB匿名公开/STAFF_INTERNAL员工内部',
  `scope` varchar(500) DEFAULT NULL COMMENT 'JSON：可访问门店、用途、期限',
  `sign_method` varchar(32) NOT NULL COMMENT 'ELECTRONIC电子签名/HANDWRITTEN手写/WET_INK印泥/OFFLINE_OFFLINE线下',
  `signature_hash` varchar(128) DEFAULT NULL COMMENT '电子签名 SHA-256',
  `signer_name` varchar(64) DEFAULT NULL,
  `signer_id_no` varchar(32) DEFAULT NULL COMMENT '身份证后四位（脱敏）',
  `signer_relation` varchar(32) DEFAULT NULL COMMENT 'SELF本人/PARENT监护人/AGENT代理人',
  `effective_at` datetime NOT NULL,
  `expire_at` datetime DEFAULT NULL,
  `consent_doc_url` varchar(500) DEFAULT NULL COMMENT '电子授权书 PDF',
  `revoked` tinyint DEFAULT '0',
  `revoked_at` datetime DEFAULT NULL,
  `revoked_reason` varchar(500) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`,`consent_type`),
  KEY `idx_expire` (`expire_at`,`revoked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图像授权';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_image_withdraw`
--

DROP TABLE IF EXISTS `mh_image_withdraw`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_image_withdraw` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `member_id` bigint NOT NULL,
  `withdraw_scope` varchar(32) NOT NULL COMMENT 'ALL全部/AI_TRAINING仅AI训练/STAFF_VIEW仅员工可见',
  `target_type` varchar(32) DEFAULT NULL COMMENT 'ARCHIVE档案/COMPARE对比/CONSENT授权',
  `target_ids` varchar(2000) DEFAULT NULL COMMENT '关联 ID CSV',
  `reason` varchar(500) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待处理 2已处理 3已驳回',
  `handler_id` bigint DEFAULT NULL,
  `handler_name` varchar(64) DEFAULT NULL,
  `handle_remark` varchar(500) DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`,`status`),
  KEY `idx_status_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图像撤回申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_visual_archive`
--

DROP TABLE IF EXISTS `mh_visual_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_visual_archive` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `archive_code` varchar(64) NOT NULL,
  `archive_type` varchar(32) NOT NULL COMMENT 'FACE面部/SKIN皮肤/BODY体态/HAIR发质/SCAR瘢痕/OTHER',
  `body_part` varchar(64) DEFAULT NULL COMMENT '如：面部左颊、颈部',
  `project_id` bigint DEFAULT NULL COMMENT '关联服务项目',
  `project_name` varchar(128) DEFAULT NULL,
  `image_url` varchar(500) NOT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `image_hash` varchar(128) NOT NULL COMMENT 'SHA-256，用于防篡改',
  `face_masked` tinyint DEFAULT '1' COMMENT '是否脱敏（0=原始 1=脱敏）',
  `face_mask_type` varchar(32) DEFAULT NULL COMMENT 'BLUR/EYE/MOSAIC/REPLACE',
  `ai_analyzed` tinyint DEFAULT '0',
  `ai_features_json` text COMMENT 'AI 特征（JSON）',
  `ai_tags` varchar(500) DEFAULT NULL COMMENT 'AI 标签 CSV：色斑、毛孔、皱纹',
  `ai_score` decimal(5,2) DEFAULT NULL COMMENT 'AI 综合分 0-100',
  `related_order_id` bigint DEFAULT NULL,
  `related_order_no` varchar(64) DEFAULT NULL,
  `photographer_id` bigint DEFAULT NULL,
  `photographer_name` varchar(64) DEFAULT NULL,
  `device` varchar(128) DEFAULT NULL COMMENT '拍摄设备型号',
  `env_info` varchar(255) DEFAULT NULL COMMENT '环境（灯光/角度）',
  `remark` varchar(500) DEFAULT NULL,
  `revoked` tinyint DEFAULT '0',
  `revoked_at` datetime DEFAULT NULL,
  `revoked_reason` varchar(500) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`archive_code`),
  KEY `idx_member` (`member_id`,`archive_type`),
  KEY `idx_store` (`store_id`,`create_time`),
  KEY `idx_hash` (`image_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视觉档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mh_visual_compare`
--

DROP TABLE IF EXISTS `mh_visual_compare`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mh_visual_compare` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `before_id` bigint NOT NULL COMMENT '前图 archive_id',
  `after_id` bigint NOT NULL COMMENT '后图 archive_id',
  `compare_type` varchar(32) NOT NULL COMMENT 'SAME_DAY同日/CROSS_DAY跨日/CROSS_TREATMENT跨疗程',
  `similarity` decimal(5,4) DEFAULT NULL COMMENT '相似度 0-1',
  `diff_score` decimal(5,2) DEFAULT NULL COMMENT '差异分 0-100',
  `skin_diff` varchar(1000) DEFAULT NULL COMMENT '皮肤维度差异 JSON',
  `ai_summary` varchar(2000) DEFAULT NULL,
  `improvement_areas` varchar(500) DEFAULT NULL COMMENT '改善项',
  `regression_areas` varchar(500) DEFAULT NULL COMMENT '退步项',
  `manual_reviewed` tinyint DEFAULT '0',
  `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(64) DEFAULT NULL,
  `review_note` varchar(1000) DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `model_version` varchar(32) DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT 'system',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`,`create_time`),
  KEY `idx_before_after` (`before_id`,`after_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 对比记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_brand`
--

DROP TABLE IF EXISTS `mkt_brand`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_brand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `brand_code` varchar(64) NOT NULL COMMENT '品牌编码',
  `brand_name` varchar(128) NOT NULL COMMENT '品牌名称',
  `brand_type` varchar(16) DEFAULT 'CHAIN' COMMENT 'CHAIN直营/FRANCHISE加盟/FLAGSHIP旗舰店',
  `logo_url` varchar(255) DEFAULT NULL COMMENT '品牌Logo',
  `contact_person` varchar(64) DEFAULT NULL COMMENT '品牌联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `province` varchar(50) DEFAULT NULL,
  `city` varchar(50) DEFAULT NULL,
  `district` varchar(50) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `member_share_scope` varchar(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌共享/STORE门店独立',
  `points_share_scope` varchar(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌积分/STORE门店积分',
  `balance_share_scope` varchar(16) DEFAULT 'BRAND' COMMENT 'BRAND储值共享/STORE门店储值',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `priority` int DEFAULT '0' COMMENT '排序',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`brand_code`),
  KEY `idx_tenant` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_channel_config`
--

DROP TABLE IF EXISTS `mkt_channel_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_channel_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `channel_type` varchar(32) NOT NULL COMMENT 'SMS短信/MINI_PROGRAM订阅消息/MP公众号/WECHAT_WORK企业微信/IN_APP站内信/EMAIL邮件/PUSH推送',
  `channel_name` varchar(64) NOT NULL,
  `provider` varchar(64) DEFAULT NULL COMMENT '阿里云/腾讯云/华为云/微信',
  `config_json` text COMMENT '凭证/密钥等',
  `rate_limit_per_min` int DEFAULT '60' COMMENT '每分钟限流',
  `rate_limit_per_day` int DEFAULT '5000' COMMENT '每日限额',
  `signature` varchar(64) DEFAULT NULL COMMENT '短信签名',
  `template_prefix` varchar(64) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `last_health_at` datetime DEFAULT NULL,
  `last_health_msg` varchar(255) DEFAULT NULL,
  `retry_max` int DEFAULT '3',
  `retry_interval_sec` int DEFAULT '60',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_type_brand` (`tenant_id`,`channel_type`,`brand_id`,`deleted`),
  KEY `idx_tenant` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_channel_template`
--

DROP TABLE IF EXISTS `mkt_channel_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_channel_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `channel_type` varchar(32) NOT NULL,
  `channel_config_id` bigint DEFAULT NULL,
  `template_code` varchar(64) NOT NULL,
  `template_name` varchar(128) NOT NULL,
  `external_template_id` varchar(128) DEFAULT NULL,
  `scene` varchar(64) NOT NULL COMMENT 'ORDER_PAYED/CARD_EXPIRE/BIRTHDAY/EVALUATION/MARKETING/COUPON...',
  `subject` varchar(255) DEFAULT NULL COMMENT '标题/主题',
  `content` varchar(2000) NOT NULL,
  `variables` varchar(500) DEFAULT NULL COMMENT '可用变量 CSV',
  `link_url` varchar(500) DEFAULT NULL,
  `link_type` varchar(32) DEFAULT 'MINI' COMMENT 'MINI小程序/MINI_PAGE/H5/IMAGE_NOPAGE',
  `audit_status` tinyint DEFAULT '1' COMMENT '1待审核 2已审核 3驳回',
  `audit_remark` varchar(255) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`template_code`),
  KEY `idx_scene` (`channel_type`,`scene`,`enabled`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_coupon`
--

DROP TABLE IF EXISTS `mkt_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `batch_id` bigint NOT NULL,
  `coupon_code` varchar(64) NOT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `coupon_type` varchar(16) NOT NULL,
  `discount_amount` decimal(12,2) DEFAULT '0.00',
  `discount_rate` decimal(3,2) DEFAULT '1.00',
  `min_consume` decimal(12,2) DEFAULT '0.00',
  `discount_scope` varchar(16) DEFAULT 'ALL',
  `status` tinyint DEFAULT '1' COMMENT '1未使用 2已使用 3已过期 4已作废',
  `used_order_id` bigint DEFAULT NULL,
  `used_time` datetime DEFAULT NULL,
  `used_amount` decimal(12,2) DEFAULT NULL,
  `effective_date` date NOT NULL,
  `expire_date` date NOT NULL,
  `granted_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `granted_by` varchar(64) DEFAULT NULL,
  `idempotency_key` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`coupon_code`),
  KEY `idx_member` (`member_id`,`status`,`deleted`),
  KEY `idx_expire` (`status`,`expire_date`),
  KEY `idx_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_coupon_batch`
--

DROP TABLE IF EXISTS `mkt_coupon_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_coupon_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `batch_no` varchar(64) NOT NULL,
  `promotion_id` bigint DEFAULT NULL,
  `coupon_name` varchar(128) NOT NULL,
  `coupon_type` varchar(16) NOT NULL COMMENT 'CASH/PERCENT/POINTS',
  `discount_amount` decimal(12,2) DEFAULT '0.00',
  `discount_rate` decimal(3,2) DEFAULT '1.00',
  `min_consume` decimal(12,2) DEFAULT '0.00',
  `discount_scope` varchar(16) DEFAULT 'ALL',
  `total_count` int DEFAULT '0',
  `issued_count` int DEFAULT '0',
  `used_count` int DEFAULT '0',
  `effective_days` int DEFAULT '30',
  `fixed_start_date` date DEFAULT NULL,
  `fixed_end_date` date DEFAULT NULL,
  `grant_type` varchar(16) DEFAULT 'GENERAL',
  `status` tinyint DEFAULT '1' COMMENT '1草稿 2发放中 3已结束 4取消',
  `expire_warning_days` int DEFAULT '3',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_batch` (`tenant_id`,`batch_no`),
  KEY `idx_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_dynamic_pricing_rule`
--

DROP TABLE IF EXISTS `mkt_dynamic_pricing_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_dynamic_pricing_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `rule_code` varchar(64) NOT NULL,
  `rule_name` varchar(128) NOT NULL,
  `rule_type` varchar(32) NOT NULL COMMENT 'TECHNICIAN按技师/TIME_SLOT按时段/PROJECT按项目/MEMBER_LEVEL按会员等级/STORE按门店',
  `target_type` varchar(32) NOT NULL COMMENT 'TECHNICIAN/TIME_SLOT/PROJECT/MEMBER_LEVEL/STORE',
  `target_id` bigint DEFAULT NULL,
  `target_code` varchar(64) DEFAULT NULL COMMENT '技师编号/时段编码/项目编号',
  `target_name` varchar(128) DEFAULT NULL,
  `pricing_mode` varchar(16) NOT NULL COMMENT 'PERCENT百分比/FIXED固定/MULTIPLIER倍数',
  `pricing_value` decimal(8,2) NOT NULL COMMENT '正值=加价，负值=折扣',
  `min_amount` decimal(12,2) DEFAULT NULL COMMENT '最低消费金额',
  `max_amount` decimal(12,2) DEFAULT NULL COMMENT '最高消费金额',
  `time_start` time DEFAULT NULL,
  `time_end` time DEFAULT NULL,
  `day_of_week` varchar(20) DEFAULT NULL COMMENT '1,2,3,4,5,6,7 (周一到周日)',
  `priority` int DEFAULT '0',
  `enabled` tinyint DEFAULT '1',
  `valid_from` date DEFAULT NULL,
  `valid_to` date DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`rule_code`),
  KEY `idx_target` (`target_type`,`target_id`,`enabled`,`deleted`),
  KEY `idx_priority` (`priority` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态定价规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_journey`
--

DROP TABLE IF EXISTS `mkt_journey`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_journey` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `journey_code` varchar(64) NOT NULL,
  `journey_name` varchar(128) NOT NULL,
  `journey_type` varchar(32) DEFAULT 'MARKETING' COMMENT 'MARKETING营销/CARE关怀/RETENTION挽回/ONBOARD新客',
  `description` varchar(500) DEFAULT NULL,
  `nodes_json` text COMMENT '旅程节点（JSON 数组）',
  `edges_json` text COMMENT '节点连接（JSON 数组）',
  `trigger_event` varchar(64) DEFAULT NULL COMMENT 'ORDER_PAYED/CARD_DELIVERED/...',
  `trigger_filter` text COMMENT '过滤条件',
  `status` tinyint DEFAULT '1' COMMENT '1草稿 2已发布 3已暂停 4已结束',
  `published_at` datetime DEFAULT NULL,
  `enter_count` int DEFAULT '0',
  `complete_count` int DEFAULT '0',
  `exit_count` int DEFAULT '0',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`journey_code`),
  KEY `idx_status` (`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销旅程';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_journey_instance`
--

DROP TABLE IF EXISTS `mkt_journey_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_journey_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `journey_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) DEFAULT NULL,
  `current_node_id` varchar(64) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1进行中 2已完成 3已退出',
  `entered_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `context_json` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_journey` (`journey_id`,`status`),
  KEY `idx_member` (`member_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='旅程执行实例';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_journey_trigger`
--

DROP TABLE IF EXISTS `mkt_journey_trigger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_journey_trigger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `trigger_code` varchar(64) NOT NULL COMMENT '触发器编码',
  `trigger_name` varchar(128) NOT NULL COMMENT '触发器名称',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型：MEMBER_REGISTER/ORDER_PLACED/RECHARGE/BOOKING/CONSUME/BIRTHDAY',
  `filter_conditions` json DEFAULT NULL COMMENT '过滤条件 JSON',
  `journey_id` bigint NOT NULL COMMENT '关联旅程 ID',
  `delay_minutes` int DEFAULT '0' COMMENT '延时触发（分钟），0=立即触发',
  `delay_window_hours` int DEFAULT '0' COMMENT '延时执行窗口（小时），用于批量归集',
  `daily_limit_per_member` int DEFAULT '1' COMMENT '每会员每日最多触发 1 次（防骚扰）',
  `batch_limit` int DEFAULT '1000' COMMENT '每个事件最多触发会员数',
  `status` int DEFAULT '1' COMMENT '1草稿 2启用 3暂停',
  `priority` int DEFAULT '100' COMMENT '优先级，数字越小越优先',
  `match_count` int DEFAULT '0' COMMENT '匹配次数',
  `enter_count` int DEFAULT '0' COMMENT '入旅程次数',
  `last_trigger_at` datetime DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `operator_name` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`trigger_code`,`deleted`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_status` (`status`),
  KEY `idx_journey` (`journey_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销旅程事件触发器规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_journey_trigger_log`
--

DROP TABLE IF EXISTS `mkt_journey_trigger_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_journey_trigger_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `trigger_id` bigint NOT NULL COMMENT '触发的规则 ID',
  `trigger_code` varchar(64) DEFAULT NULL COMMENT '触发器编码',
  `event_type` varchar(64) DEFAULT NULL COMMENT '事件类型',
  `event_id` varchar(128) DEFAULT NULL COMMENT '业务事件唯一 ID（幂等）',
  `member_id` bigint DEFAULT NULL COMMENT '会员 ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '会员姓名',
  `journey_id` bigint DEFAULT NULL COMMENT '关联旅程',
  `journey_instance_id` bigint DEFAULT NULL COMMENT '生成的旅程实例 ID',
  `status` varchar(16) NOT NULL DEFAULT 'MATCHED' COMMENT 'MATCHED/ENTERED/SKIPPED/FAILED',
  `match_detail` varchar(500) DEFAULT NULL COMMENT '匹配详情',
  `event_payload` text COMMENT '事件载荷',
  `latency_ms` bigint DEFAULT '0' COMMENT '从事件到执行延迟 ms',
  `error_message` varchar(500) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_trigger` (`trigger_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_status` (`status`),
  KEY `idx_create` (`create_time`),
  KEY `idx_tenant_event` (`tenant_id`,`event_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='触发器执行日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_order_discount`
--

DROP TABLE IF EXISTS `mkt_order_discount`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_order_discount` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `order_id` bigint NOT NULL,
  `order_no` varchar(64) DEFAULT NULL,
  `source_type` varchar(16) NOT NULL COMMENT 'PROMOTION/COUPON/POINTS/MANUAL',
  `source_id` bigint DEFAULT NULL,
  `source_name` varchar(128) DEFAULT NULL,
  `discount_amount` decimal(12,2) NOT NULL DEFAULT '0.00',
  `coupon_id` bigint DEFAULT NULL,
  `points_used` int DEFAULT '0',
  `status` tinyint DEFAULT '1' COMMENT '1已应用 2已撤销',
  `refund_amount` decimal(12,2) DEFAULT '0.00',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_source` (`source_type`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单优惠明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_points_log`
--

DROP TABLE IF EXISTS `mkt_points_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_points_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `change_type` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `change_value` int NOT NULL,
  `points_after` int NOT NULL,
  `related_id` bigint DEFAULT NULL,
  `related_no` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `related_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_mpl_tenant_id` (`tenant_id`),
  KEY `idx_mpl_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_points_rule`
--

DROP TABLE IF EXISTS `mkt_points_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_points_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `rule_name` varchar(64) NOT NULL,
  `consume_rate` decimal(5,2) DEFAULT '1.00',
  `recharge_multiplier` decimal(3,2) DEFAULT '1.00',
  `promotion_multiplier` decimal(3,2) DEFAULT '1.00',
  `points_to_cash` decimal(5,2) DEFAULT '0.01',
  `max_use_percent` decimal(5,2) DEFAULT '30.00',
  `enabled` tinyint DEFAULT '1',
  `priority` int DEFAULT '0',
  `effective_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_enabled` (`tenant_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_pricing_log`
--

DROP TABLE IF EXISTS `mkt_pricing_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_pricing_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `project_id` bigint DEFAULT NULL,
  `project_name` varchar(128) DEFAULT NULL,
  `technician_id` bigint DEFAULT NULL,
  `technician_name` varchar(64) DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `member_level_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `pricing_time` datetime DEFAULT NULL,
  `base_amount` decimal(12,2) DEFAULT NULL,
  `hit_rule_id` bigint DEFAULT NULL,
  `hit_rule_code` varchar(64) DEFAULT NULL,
  `final_amount` decimal(12,2) DEFAULT NULL,
  `discount_amount` decimal(12,2) DEFAULT NULL,
  `rules_json` text COMMENT '命中的所有规则 JSON',
  `checkout_visible` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`,`create_time`),
  KEY `idx_member` (`member_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定价计算日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_promotion`
--

DROP TABLE IF EXISTS `mkt_promotion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_promotion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `promotion_code` varchar(64) NOT NULL,
  `promotion_name` varchar(128) NOT NULL,
  `promotion_type` varchar(16) NOT NULL COMMENT 'FULL_REDUCE/DISCOUNT/GIFT/CASH_COUPON/POINTS_EXCHANGE',
  `description` varchar(500) DEFAULT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `threshold_amount` decimal(12,2) DEFAULT '0.00',
  `reduce_amount` decimal(12,2) DEFAULT '0.00',
  `discount_rate` decimal(3,2) DEFAULT '1.00',
  `scope_type` varchar(16) DEFAULT 'ALL' COMMENT 'ALL/SERVICE/PRODUCT/CATEGORY',
  `scope_ids` varchar(500) DEFAULT NULL COMMENT 'JSON 列表',
  `member_level_ids` varchar(500) DEFAULT NULL COMMENT 'JSON 列表',
  `stackable` tinyint DEFAULT '0',
  `exclusive_codes` varchar(500) DEFAULT NULL COMMENT 'JSON 列表',
  `quota_total` int DEFAULT '0',
  `quota_used` int DEFAULT '0',
  `per_member_limit` int DEFAULT '0',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用 2结束',
  `priority` int DEFAULT '0',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`promotion_code`),
  KEY `idx_status` (`status`,`deleted`),
  KEY `idx_period` (`start_time`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='营销活动';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mkt_segment`
--

DROP TABLE IF EXISTS `mkt_segment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mkt_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `segment_code` varchar(64) NOT NULL,
  `segment_name` varchar(128) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `filter_json` text,
  `member_count` int DEFAULT '0',
  `source_type` varchar(32) DEFAULT NULL COMMENT 'RFM/RULE/AI/MANUAL',
  `source_model_id` bigint DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`segment_code`),
  KEY `idx_source` (`source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人群';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pay_dcep_channel`
--

DROP TABLE IF EXISTS `pay_dcep_channel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_dcep_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `channel_code` varchar(64) NOT NULL COMMENT 'DCEP-DCBC建行/DCEP-ICBC工行/DCEP-ABC农行',
  `channel_name` varchar(128) NOT NULL,
  `merchant_id` varchar(128) NOT NULL,
  `app_id` varchar(128) DEFAULT NULL,
  `app_secret` varchar(256) DEFAULT NULL,
  `private_key_path` varchar(500) DEFAULT NULL,
  `public_key_path` varchar(500) DEFAULT NULL,
  `api_base_url` varchar(500) NOT NULL,
  `notify_url` varchar(500) DEFAULT NULL,
  `env_mode` varchar(16) DEFAULT 'SANDBOX' COMMENT 'SANDBOX/MOCK/PROD',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `last_health_at` datetime DEFAULT NULL,
  `last_health_msg` varchar(255) DEFAULT NULL,
  `rate_limit_per_min` int DEFAULT '60',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`channel_code`),
  KEY `idx_status` (`status`,`deleted`),
  CONSTRAINT `ck_pay_dcep_channel_env_mode` CHECK ((`env_mode` in (_latin1'MOCK',_latin1'SANDBOX',_latin1'PROD')))
) ENGINE=InnoDB AUTO_INCREMENT=362 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数字人民币通道';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pay_dcep_notify`
--

DROP TABLE IF EXISTS `pay_dcep_notify`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_dcep_notify` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `dcep_order_id` bigint NOT NULL,
  `notify_id` varchar(64) DEFAULT NULL,
  `event_type` varchar(32) DEFAULT NULL COMMENT 'PAY_SUCCESS/REFUND_SUCCESS/...',
  `payload` text,
  `sign_verified` tinyint DEFAULT '0',
  `sign_algo` varchar(32) DEFAULT NULL,
  `processed` tinyint DEFAULT '0',
  `process_msg` varchar(500) DEFAULT NULL,
  `notify_at` datetime DEFAULT NULL,
  `process_at` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`dcep_order_id`,`processed`)
) ENGINE=InnoDB AUTO_INCREMENT=394 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='DCEP 回调日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pay_dcep_order`
--

DROP TABLE IF EXISTS `pay_dcep_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_dcep_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `brand_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `biz_order_id` bigint NOT NULL,
  `biz_order_no` varchar(64) NOT NULL,
  `dcep_order_no` varchar(64) NOT NULL COMMENT 'DCEP 订单号',
  `prepay_id` varchar(128) DEFAULT NULL COMMENT 'DCEP 预支付 ID',
  `transaction_id` varchar(128) DEFAULT NULL COMMENT 'DCEP 交易流水',
  `amount_cents` bigint NOT NULL,
  `channel_id` bigint DEFAULT NULL,
  `channel_code` varchar(64) DEFAULT NULL,
  `wallet_type` varchar(32) DEFAULT 'PERSONAL' COMMENT 'PERSONAL个人/MERCHANT对公',
  `payer_phone` varchar(20) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待支付 2已支付 3已关闭 4已退款 5失败',
  `prepay_at` datetime DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `close_at` datetime DEFAULT NULL,
  `refund_at` datetime DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `error_msg` varchar(500) DEFAULT NULL,
  `notify_raw` text,
  `notify_count` int DEFAULT '0',
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_dcep_no` (`tenant_id`,`dcep_order_no`),
  UNIQUE KEY `uk_biz_order` (`biz_order_id`),
  KEY `idx_status` (`status`,`create_time`),
  KEY `idx_channel` (`channel_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=344 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数字人民币订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_channel`
--

DROP TABLE IF EXISTS `payment_channel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `channel_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'THIRD_PARTY' COMMENT 'THIRD_PARTY / BANK / INTERNAL',
  `icon_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `sort_order` int DEFAULT '100',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_code` (`channel_code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鏀?粯娓犻亾';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_config`
--

DROP TABLE IF EXISTS `payment_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `config_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'WECHAT / ALIPAY / UNIONPAY / DCEP / BANK / CASH / WALLET / CARD',
  `merchant_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `app_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_secret` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cert_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `private_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `public_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notify_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fee_rate` decimal(6,4) DEFAULT '0.0060' COMMENT '鎵嬬画璐圭巼 0.6%',
  `fee_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'PERCENTAGE' COMMENT 'PERCENTAGE / FIXED',
  `min_fee` decimal(10,2) DEFAULT '0.00',
  `max_fee` decimal(10,2) DEFAULT NULL,
  `daily_limit` decimal(18,2) DEFAULT NULL,
  `monthly_limit` decimal(18,2) DEFAULT NULL,
  `env` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'PROD' COMMENT 'SANDBOX / PROD / MOCK',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED / PENDING',
  `extra_config` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`config_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鏀?粯閰嶇疆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_transaction_log`
--

DROP TABLE IF EXISTS `payment_transaction_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_transaction_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `config_id` bigint DEFAULT NULL,
  `channel_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `out_trade_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trade_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '娓犻亾娴佹按鍙',
  `amount` decimal(18,2) DEFAULT '0.00',
  `fee` decimal(10,2) DEFAULT '0.00',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'INIT' COMMENT 'INIT / SUCCESS / FAILED / REFUNDED / CLOSED',
  `request_payload` text COLLATE utf8mb4_unicode_ci,
  `response_payload` text COLLATE utf8mb4_unicode_ci,
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `client_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cost_ms` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_out_trade` (`out_trade_no`),
  KEY `idx_trade` (`trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鏀?粯娴佹按鏃ュ織';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `perf_load_test_result`
--

DROP TABLE IF EXISTS `perf_load_test_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_load_test_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `test_name` varchar(255) DEFAULT NULL,
  `scene_count` int DEFAULT NULL,
  `total_requests` bigint DEFAULT NULL,
  `success_count` bigint DEFAULT NULL,
  `failure_count` bigint DEFAULT NULL,
  `error_rate` decimal(8,4) DEFAULT NULL,
  `actual_qps` decimal(15,2) DEFAULT NULL,
  `expected_qps` decimal(15,2) DEFAULT NULL,
  `avg_latency_ms` decimal(15,2) DEFAULT NULL,
  `p50_latency_ms` decimal(15,2) DEFAULT NULL,
  `p95_latency_ms` decimal(15,2) DEFAULT NULL,
  `p99_latency_ms` decimal(15,2) DEFAULT NULL,
  `max_latency_ms` decimal(15,2) DEFAULT NULL,
  `tps` decimal(15,2) DEFAULT NULL,
  `concurrency` int DEFAULT NULL,
  `duration_sec` int DEFAULT NULL,
  `conclusion` varchar(1000) DEFAULT NULL,
  `report_url` varchar(500) DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `perf_traffic_model`
--

DROP TABLE IF EXISTS `perf_traffic_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perf_traffic_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `scene_name` varchar(128) NOT NULL,
  `endpoint` varchar(255) DEFAULT NULL,
  `method` varchar(16) DEFAULT NULL,
  `weight` int DEFAULT '0',
  `expected_qps` decimal(15,2) DEFAULT NULL,
  `avg_latency_ms` decimal(15,2) DEFAULT NULL,
  `p99_latency_ms` decimal(15,2) DEFAULT NULL,
  `error_rate` decimal(8,4) DEFAULT NULL,
  `enabled` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `plugin_install`
--

DROP TABLE IF EXISTS `plugin_install`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plugin_install` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `plugin_id` bigint DEFAULT NULL,
  `plugin_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `installed_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `expire_at` datetime DEFAULT NULL,
  `config_json` text COLLATE utf8mb4_bin,
  `enabled` tinyint DEFAULT '1',
  `install_status` varchar(32) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `last_used_at` datetime DEFAULT NULL,
  `use_count` bigint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `plugin_market`
--

DROP TABLE IF EXISTS `plugin_market`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plugin_market` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '0',
  `plugin_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `plugin_name` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `category` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `version` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `vendor` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `short_desc` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `description` text COLLATE utf8mb4_bin,
  `icon_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `tags` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `price` decimal(18,2) DEFAULT NULL,
  `billing_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `install_count` int DEFAULT '0',
  `rating` decimal(3,2) DEFAULT '0.00',
  `review_count` int DEFAULT '0',
  `is_official` tinyint DEFAULT '0',
  `is_featured` tinyint DEFAULT '0',
  `homepage` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `tarball_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `config_json` text COLLATE utf8mb4_bin,
  `permissions` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `events` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'PUBLISHED',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_code` (`plugin_code`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `plugin_review`
--

DROP TABLE IF EXISTS `plugin_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plugin_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `plugin_id` bigint NOT NULL,
  `member_id` bigint DEFAULT NULL,
  `rating` int DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `content` text COLLATE utf8mb4_bin,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plugin` (`plugin_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `point_goods`
--

DROP TABLE IF EXISTS `point_goods`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `point_goods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `name` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `category` varchar(32) COLLATE utf8mb4_bin DEFAULT 'GENERAL',
  `image_url` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `points_price` int NOT NULL,
  `cash_price` decimal(12,2) DEFAULT '0.00',
  `stock` int DEFAULT '0',
  `sold_count` int DEFAULT '0',
  `description` text COLLATE utf8mb4_bin,
  `status` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_pg_tenant_id` (`tenant_id`),
  KEY `idx_pg_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `point_order`
--

DROP TABLE IF EXISTS `point_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `point_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `order_no` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `goods_id` bigint NOT NULL,
  `goods_name` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `points_cost` int NOT NULL,
  `cash_paid` decimal(12,2) DEFAULT '0.00',
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'PENDING',
  `delivery_info` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_po_tenant_id` (`tenant_id`),
  KEY `idx_po_member_id` (`member_id`),
  KEY `idx_po_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_device`
--

DROP TABLE IF EXISTS `pos_device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `vendor` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `device_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `device_name` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `serial_number` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `api_key` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `location_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `webhook_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `last_heartbeat` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_device_code` (`device_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_employee`
--

DROP TABLE IF EXISTS `pos_employee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '员工ID',
  `employee_code` varchar(50) DEFAULT NULL COMMENT '员工工号',
  `employee_name` varchar(50) NOT NULL COMMENT '员工姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `password` varchar(100) DEFAULT NULL COMMENT '登录密码',
  `gender` tinyint DEFAULT NULL COMMENT '性别：0女 1男',
  `id_card` varchar(20) DEFAULT NULL COMMENT '身份证号',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像',
  `position` varchar(50) DEFAULT NULL COMMENT '职位',
  `department` varchar(50) DEFAULT NULL COMMENT '部门/组别',
  `store_id` bigint NOT NULL COMMENT '所属门店ID',
  `entry_date` date DEFAULT NULL COMMENT '入职日期',
  `status` tinyint DEFAULT '1' COMMENT '状态：0离职 1在职 2休假',
  `salary` decimal(10,2) DEFAULT NULL COMMENT '基本工资',
  `commission_rate` decimal(5,4) DEFAULT NULL COMMENT '提成比例',
  `role_ids` varchar(255) DEFAULT NULL COMMENT '角色ID列表（逗号分隔）',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `employee_code` (`employee_code`),
  UNIQUE KEY `phone` (`phone`),
  KEY `idx_employee_code` (`employee_code`),
  KEY `idx_phone` (`phone`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`),
  KEY `idx_employee_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=628 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_order`
--

DROP TABLE IF EXISTS `pos_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint NOT NULL,
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint DEFAULT NULL COMMENT '会员ID',
  `member_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `booking_id` bigint DEFAULT NULL COMMENT '关联预约',
  `type` tinyint NOT NULL COMMENT '类型:1消费 2充值 3混合',
  `total_amount` decimal(12,2) NOT NULL COMMENT '原价总额',
  `discount_amount` decimal(12,2) DEFAULT '0.00' COMMENT '折扣金额',
  `final_amount` decimal(12,2) NOT NULL COMMENT '实付金额',
  `paid_amount` decimal(12,2) DEFAULT '0.00' COMMENT '已付金额',
  `refunded_amount` decimal(12,2) DEFAULT '0.00' COMMENT '已退款总额',
  `refund_count` int DEFAULT '0' COMMENT '退款次数',
  `cashier_id` bigint NOT NULL COMMENT '收银员',
  `cashier_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '状态:1进行中 2已支付 3已退款 4已撤销',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `refund_amount` decimal(12,2) DEFAULT '0.00',
  `refund_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_no` (`order_no`),
  KEY `idx_pos_order_store` (`store_id`),
  KEY `idx_pos_order_member` (`member_id`),
  KEY `idx_pos_order_date` (`store_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3937 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收银订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_order_consumption`
--

DROP TABLE IF EXISTS `pos_order_consumption`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_order_consumption` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT '1' COMMENT '租户ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `stock_record_id` bigint NOT NULL COMMENT 'stock_record.id',
  `product_id` bigint NOT NULL COMMENT '耗材ID',
  `service_id` bigint DEFAULT NULL COMMENT '关联服务ID',
  `quantity` int NOT NULL COMMENT '消耗数量',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键 (pos_order_consume:{orderId}:{serviceId}:{productId})',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency` (`tenant_id`,`idempotency_key`),
  KEY `idx_order` (`order_id`),
  KEY `idx_record` (`stock_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单-消耗幂等记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_order_item`
--

DROP TABLE IF EXISTS `pos_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `order_id` bigint NOT NULL,
  `service_id` bigint DEFAULT NULL,
  `service_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `quantity` int DEFAULT '1',
  `unit_price` decimal(10,2) NOT NULL,
  `total_price` decimal(12,2) NOT NULL,
  `discount_amount` decimal(10,2) DEFAULT '0.00',
  `final_price` decimal(12,2) NOT NULL,
  `package_order_id` bigint DEFAULT NULL COMMENT '套餐核销ID',
  `package_times` int DEFAULT '0' COMMENT '核销次数',
  `employee_id` bigint DEFAULT NULL COMMENT '服务技师',
  `employee_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_pos_item_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=317 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_payment`
--

DROP TABLE IF EXISTS `pos_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `order_id` bigint NOT NULL,
  `payment_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `method` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付方式',
  `amount` decimal(12,2) NOT NULL,
  `status` tinyint DEFAULT '0' COMMENT '状态:0待支付 1成功 2失败 3已退款',
  `transaction_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方流水号',
  `pay_time` datetime DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  `error_msg` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `payment_no` (`payment_no`),
  KEY `idx_pos_payment_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=531 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_reconciliation`
--

DROP TABLE IF EXISTS `pos_reconciliation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_reconciliation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户',
  `store_id` bigint DEFAULT NULL COMMENT '门店ID',
  `reconcile_date` date NOT NULL COMMENT '对账日期',
  `channel` varchar(32) NOT NULL COMMENT '支付通道：wechat/alipay/unionpay/cash',
  `channel_txn_id` varchar(128) DEFAULT NULL COMMENT '通道流水号',
  `order_id` bigint DEFAULT NULL COMMENT '本地订单ID',
  `order_no` varchar(40) DEFAULT NULL COMMENT '订单号',
  `payment_id` bigint DEFAULT NULL COMMENT '本地支付ID',
  `channel_amount` decimal(12,2) DEFAULT NULL COMMENT '通道侧金额',
  `local_amount` decimal(12,2) DEFAULT NULL COMMENT '本地侧金额',
  `diff_amount` decimal(12,2) DEFAULT '0.00' COMMENT '差异金额',
  `status` varchar(16) NOT NULL DEFAULT 'MATCHED' COMMENT 'MATCHED / DIFF_LOCAL_MORE / DIFF_CHANNEL_MORE / DIFF_AMOUNT / MISSING_LOCAL / MISSING_CHANNEL',
  `handle_status` varchar(16) DEFAULT 'PENDING' COMMENT 'PENDING / HANDLED / IGNORED',
  `handle_by` bigint DEFAULT NULL COMMENT '处理人ID',
  `handle_by_name` varchar(64) DEFAULT NULL COMMENT '处理人',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_note` varchar(512) DEFAULT NULL COMMENT '处理备注',
  `raw_payload` mediumtext COMMENT '通道原始报文',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_channel_txn` (`tenant_id`,`channel`,`channel_txn_id`),
  KEY `idx_tenant_date` (`tenant_id`,`reconcile_date`),
  KEY `idx_status` (`status`),
  KEY `idx_channel` (`channel`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付对账表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_refund`
--

DROP TABLE IF EXISTS `pos_refund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户',
  `store_id` bigint NOT NULL COMMENT '门店ID',
  `refund_no` varchar(40) NOT NULL COMMENT '退款单号（全局唯一）',
  `order_id` bigint NOT NULL COMMENT '原订单ID',
  `order_no` varchar(40) NOT NULL COMMENT '原订单号（冗余）',
  `member_id` bigint DEFAULT NULL COMMENT '会员ID',
  `member_name` varchar(64) DEFAULT NULL COMMENT '会员姓名（冗余）',
  `refund_type` varchar(16) NOT NULL DEFAULT 'FULL' COMMENT '退款类型：FULL 全单 / PART 部分 / CROSS 跨单',
  `refund_amount` decimal(12,2) NOT NULL COMMENT '本次退款金额',
  `paid_amount` decimal(12,2) NOT NULL COMMENT '原订单实付',
  `refunded_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '已退款总额（累计）',
  `remaining_amount` decimal(12,2) NOT NULL COMMENT '尚可退金额',
  `refund_strategy` varchar(16) NOT NULL DEFAULT 'ORIGINAL' COMMENT '退款策略：ORIGINAL 原路 / WALLET 储值返还 / MIXED 混合',
  `cash_refund` decimal(12,2) DEFAULT '0.00' COMMENT '现金/原路退款金额',
  `wallet_refund` decimal(12,2) DEFAULT '0.00' COMMENT '储值返还金额',
  `thirdparty_refund` decimal(12,2) DEFAULT '0.00' COMMENT '第三方通道退款金额（cash/wechat/alipay/unionpay）',
  `reverse_package` tinyint DEFAULT '0' COMMENT '是否已回退套餐次数 0/1',
  `reverse_points` tinyint DEFAULT '0' COMMENT '是否已返还积分 0/1',
  `reverse_stock` tinyint DEFAULT '0' COMMENT '是否已回退库存 0/1',
  `reverse_commission` tinyint DEFAULT '0' COMMENT '是否已冲正提成 0/1',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/AUDIT/APPROVED/REJECTED/PROCESSING/SUCCESS/FAILED/CANCELLED',
  `current_audit_level` int DEFAULT '0' COMMENT '褰撳墠瀹℃牳绾у埆锛?鏃犻渶瀹℃牳 / 1搴楅暱 / 2鍖哄煙缁忕悊 / 3璐㈠姟鎬荤洃',
  `audit_required` tinyint DEFAULT '0' COMMENT '鏄?惁闇??瀹℃牳锛?鍚?1鏄',
  `reason` varchar(512) DEFAULT NULL COMMENT '退款原因',
  `reject_reason` varchar(512) DEFAULT NULL COMMENT '拒绝原因',
  `operator_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '申请人姓名',
  `auditor_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `auditor_name` varchar(64) DEFAULT NULL COMMENT '审核人姓名',
  `channel_txn_id` varchar(128) DEFAULT NULL COMMENT '第三方通道流水号',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '幂等键',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路跟踪ID',
  `apply_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `refund_time` datetime DEFAULT NULL COMMENT '完成时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `cross_group_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_refund_no` (`tenant_id`,`refund_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_audit_level` (`current_audit_level`)
) ENGINE=InnoDB AUTO_INCREMENT=306 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_refund_audit_log`
--

DROP TABLE IF EXISTS `pos_refund_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_refund_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `refund_id` bigint NOT NULL,
  `refund_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `audit_level` int DEFAULT '0' COMMENT '瀹℃牳绾у埆锛?鏃犻渶 / 1搴楅暱 / 2鍖哄煙缁忕悊 / 3璐㈠姟鎬荤洃',
  `audit_level_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SUBMIT/AUTO_APPROVE/L1_APPROVE/L2_APPROVE/L3_APPROVE/REJECT/RESUBMIT/CANCEL/PROCESS/SUCCESS/FAILED',
  `actor_id` bigint DEFAULT NULL,
  `actor_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_role` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `before_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `after_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_refund` (`tenant_id`,`refund_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_action` (`action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='閫??瀹℃牳娴佹按';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_refund_audit_rule`
--

DROP TABLE IF EXISTS `pos_refund_audit_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_refund_audit_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `audit_level` int NOT NULL COMMENT '瀹℃牳绾у埆锛?搴楅暱 2鍖哄煙缁忕悊 3璐㈠姟鎬荤洃',
  `audit_level_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `min_amount` decimal(12,2) DEFAULT '0.00' COMMENT '瑙﹀彂瀹℃牳鐨勬渶灏忛噾棰濓紙涓嶅惈锛',
  `max_amount` decimal(12,2) DEFAULT '999999.00' COMMENT '瑙﹀彂瀹℃牳鐨勬渶澶ч噾棰濓紙鍚?級',
  `required` tinyint DEFAULT '1' COMMENT '鏈?骇鍒?槸鍚﹀繀瀹',
  `auto_approve` tinyint DEFAULT '0' COMMENT '鏈?骇鍒?槸鍚﹁嚜鍔ㄩ?杩',
  `enabled` tinyint DEFAULT '1',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_level` (`tenant_id`,`audit_level`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='閫??瀹℃牳瑙勫垯閰嶇疆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_refund_item`
--

DROP TABLE IF EXISTS `pos_refund_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_refund_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户',
  `refund_id` bigint NOT NULL COMMENT '退款单ID',
  `payment_id` bigint NOT NULL COMMENT '原 PosPayment.ID',
  `payment_method` varchar(32) NOT NULL COMMENT '支付方式：cash/wechat/alipay/wallet/package',
  `payment_amount` decimal(12,2) NOT NULL COMMENT '原支付金额',
  `refund_amount` decimal(12,2) NOT NULL COMMENT '本次退款金额',
  `payment_no` varchar(40) DEFAULT NULL COMMENT '原支付流水号',
  `status` varchar(16) DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
  `error_msg` varchar(512) DEFAULT NULL COMMENT '错误信息',
  `channel_txn_id` varchar(128) DEFAULT NULL COMMENT '第三方通道流水号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_refund_id` (`refund_id`),
  KEY `idx_payment_id` (`payment_id`)
) ENGINE=InnoDB AUTO_INCREMENT=367 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_refund_reverse_log`
--

DROP TABLE IF EXISTS `pos_refund_reverse_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_refund_reverse_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户',
  `refund_id` bigint NOT NULL COMMENT '退款单ID',
  `reverse_type` varchar(32) NOT NULL COMMENT 'PACKAGE / POINTS / STOCK / COMMISSION',
  `ref_id` bigint DEFAULT NULL COMMENT '反向业务主键（如 redemption_id / commission_id）',
  `payload` mediumtext COMMENT '反向流水 JSON',
  `operator_id` bigint DEFAULT NULL COMMENT '执行人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_refund_id` (`refund_id`),
  KEY `idx_reverse_type` (`reverse_type`)
) ENGINE=InnoDB AUTO_INCREMENT=735 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款逆向事件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_store`
--

DROP TABLE IF EXISTS `pos_store`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_store` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '门店ID',
  `store_name` varchar(100) NOT NULL COMMENT '门店名称',
  `store_code` varchar(50) DEFAULT NULL COMMENT '门店编码',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `address` varchar(255) DEFAULT NULL COMMENT '详细地址',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区县',
  `business_hours` varchar(100) DEFAULT NULL COMMENT '营业时间',
  `status` tinyint DEFAULT '1' COMMENT '状态：0停业 1营业',
  `logo_url` varchar(255) DEFAULT NULL COMMENT '门店Logo',
  `description` text COMMENT '门店简介',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint DEFAULT NULL COMMENT 'ç§Ÿæˆ·ID (P0-T01 è¡¥åˆ—, ä¸Ž Store.tenantId å¯¹é½)',
  `brand_id` bigint DEFAULT NULL COMMENT 'æ‰€å±žå“ç‰Œ',
  `store_type` varchar(16) DEFAULT 'DIRECT' COMMENT 'DIRECTç›´è¥/FRANCHISEåŠ ç›Ÿ/FLAGSHIPæ——èˆ°åº—',
  `region_code` varchar(32) DEFAULT NULL COMMENT 'åŒºåŸŸç¼–ç ï¼ˆå¦‚åŽä¸œ-001ï¼‰',
  `is_hq` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦æ€»éƒ¨ï¼ˆ1=æ€»éƒ¨ï¼‰',
  `manager_employee_id` bigint DEFAULT NULL COMMENT 'é—¨åº—è´Ÿè´£äºº',
  `manager_name` varchar(64) DEFAULT NULL COMMENT 'è´Ÿè´£äººå§“å',
  `open_date` date DEFAULT NULL COMMENT 'å¼€ä¸šæ—¥æœŸ',
  `brand_priority` int DEFAULT '0' COMMENT 'å“ç‰Œå†…æŽ’åº',
  PRIMARY KEY (`id`),
  UNIQUE KEY `store_code` (`store_code`),
  KEY `idx_store_code` (`store_code`),
  KEY `idx_status` (`status`),
  KEY `idx_pos_store_tenant_id` (`tenant_id`),
  KEY `idx_brand` (`brand_id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pos_transaction`
--

DROP TABLE IF EXISTS `pos_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pos_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `pos_id` bigint DEFAULT NULL,
  `store_id` bigint DEFAULT NULL,
  `tx_no` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `vendor` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `external_tx_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `amount` decimal(18,2) DEFAULT NULL,
  `tip` decimal(18,2) DEFAULT NULL,
  `tax` decimal(18,2) DEFAULT NULL,
  `currency` varchar(8) COLLATE utf8mb4_bin DEFAULT 'USD',
  `card_brand` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `card_last4` varchar(8) COLLATE utf8mb4_bin DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `raw_payload` text COLLATE utf8mb4_bin,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_pos` (`pos_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pricing_experiment`
--

DROP TABLE IF EXISTS `pricing_experiment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pricing_experiment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `exp_key` varchar(64) NOT NULL COMMENT '实验 key（URL-safe，英文）',
  `exp_name` varchar(128) NOT NULL COMMENT '实验名称',
  `status` varchar(16) DEFAULT 'DRAFT' COMMENT 'DRAFT/RUNNING/PAUSED/COMPLETED',
  `group_a_name` varchar(64) DEFAULT '控制组A（原价）',
  `group_a_mode` varchar(16) DEFAULT 'ORIGINAL' COMMENT 'ORIGINAL/PERCENT/FIXED',
  `group_a_value` decimal(10,4) DEFAULT '0.0000' COMMENT 'ORIGINAL=0, PERCENT=折扣%, FIXED=减多少',
  `group_b_name` varchar(64) DEFAULT '控制组B（9折）',
  `group_b_mode` varchar(16) DEFAULT 'PERCENT',
  `group_b_value` decimal(10,4) DEFAULT '10.0000' COMMENT '10=减10%（打9折）',
  `group_c_name` varchar(64) DEFAULT '变异组C（8折）',
  `group_c_mode` varchar(16) DEFAULT 'PERCENT',
  `group_c_value` decimal(10,4) DEFAULT '20.0000' COMMENT '20=减20%（打8折）',
  `group_d_name` varchar(64) DEFAULT '变异组D（7折）',
  `group_d_mode` varchar(16) DEFAULT 'PERCENT',
  `group_d_value` decimal(10,4) DEFAULT '30.0000' COMMENT '30=减30%（打7折）',
  `group_count` int DEFAULT '2' COMMENT '实验组数量（2=控制组A/B，4=ABCD全部）',
  `control_traffic_ratio` decimal(5,4) DEFAULT '0.5000' COMMENT '控制组总流量占比（0.0~1.0）',
  `control_ab_ratio` decimal(5,4) DEFAULT '0.5000' COMMENT '控制组内 A:B 分配（默认 1:1）',
  `variant_cd_ratio` decimal(5,4) DEFAULT '0.5000' COMMENT '变异组内 C:D 分配（默认 1:1）',
  `applicable_projects` varchar(500) DEFAULT 'ALL' COMMENT '适用项目（逗号分隔 ID，ALL=全部）',
  `applicable_stores` varchar(500) DEFAULT 'ALL' COMMENT '适用门店',
  `applicable_member_levels` varchar(500) DEFAULT 'ALL' COMMENT '适用会员等级',
  `min_order_amount` decimal(12,2) DEFAULT '0.00' COMMENT '最低订单金额',
  `metric_type` varchar(32) DEFAULT 'conversion_rate' COMMENT 'conversion_rate / revenue_per_session / avg_order_value',
  `significance_threshold` decimal(5,4) DEFAULT '0.9500' COMMENT '统计显著性阈值',
  `min_sample_size` int DEFAULT '100' COMMENT '每组最少样本量',
  `control_sample_size` int DEFAULT '0' COMMENT '控制组样本数',
  `variant_sample_size` int DEFAULT '0' COMMENT '变异组样本数',
  `control_conversions` int DEFAULT '0' COMMENT '控制组转化数',
  `variant_conversions` int DEFAULT '0' COMMENT '变异组转化数',
  `control_metric_value` decimal(12,4) DEFAULT NULL COMMENT '控制组均值',
  `variant_metric_value` decimal(12,4) DEFAULT NULL COMMENT '变异组均值',
  `confidence_level` decimal(5,4) DEFAULT NULL COMMENT '置信度（0.0~1.0）',
  `lift_percent` decimal(8,4) DEFAULT NULL COMMENT '提升幅度（%）',
  `conclusion` varchar(32) DEFAULT 'PENDING' COMMENT 'WIN_CONTROL / WIN_VARIANT / NO_SIGNIFICANCE / PENDING',
  `start_date` date DEFAULT NULL COMMENT '实验开始日期',
  `end_date` date DEFAULT NULL COMMENT '实验结束日期（null=无限期）',
  `description` varchar(500) DEFAULT NULL,
  `operator_name` varchar(50) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_exp_key` (`tenant_id`,`exp_key`,`deleted`),
  KEY `idx_status` (`status`),
  KEY `idx_conclusion` (`conclusion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定价A/B实验配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pricing_experiment_result`
--

DROP TABLE IF EXISTS `pricing_experiment_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pricing_experiment_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `exp_id` bigint DEFAULT NULL COMMENT '实验 ID',
  `exp_key` varchar(64) DEFAULT NULL COMMENT '实验 key',
  `member_id` bigint DEFAULT NULL COMMENT '会员 ID',
  `store_id` bigint DEFAULT NULL COMMENT '门店 ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目 ID',
  `quote_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'quote 时间',
  `variant` varchar(8) NOT NULL COMMENT 'A / B / C / D',
  `is_control` tinyint NOT NULL DEFAULT '0' COMMENT '1=控制组 0=变异组',
  `original_price` decimal(12,2) NOT NULL COMMENT '原始价格',
  `final_price` decimal(12,2) NOT NULL COMMENT '最终价格',
  `discount_amount` decimal(12,2) DEFAULT '0.00' COMMENT '折扣金额',
  `rule_id` bigint DEFAULT NULL COMMENT '命中的规则 ID',
  `rule_code` varchar(64) DEFAULT NULL COMMENT '命中的规则编码',
  `conversion_status` varchar(16) DEFAULT 'RECORDED' COMMENT 'RECORDED / CONVERTED / EXPIRED',
  `converted_at` datetime DEFAULT NULL COMMENT '转化时间',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单 ID',
  `conversion_amount` decimal(12,2) DEFAULT NULL COMMENT '转化金额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_exp_id` (`exp_id`),
  KEY `idx_exp_key` (`exp_key`),
  KEY `idx_member` (`member_id`),
  KEY `idx_variant` (`variant`,`is_control`),
  KEY `idx_conversion_status` (`conversion_status`),
  KEY `idx_quote_at` (`quote_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定价实验结果记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `report_config`
--

DROP TABLE IF EXISTS `report_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `type` int DEFAULT NULL,
  `dimension` varchar(50) DEFAULT NULL,
  `date_range` varchar(50) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `is_pinned` tinyint DEFAULT '0',
  `sort_order` int DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_type` (`type`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `report_export_log`
--

DROP TABLE IF EXISTS `report_export_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_export_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `store_id` bigint DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `type` int DEFAULT '1',
  `date_range` varchar(64) DEFAULT NULL,
  `file_url` varchar(500) DEFAULT NULL,
  `file_size` bigint DEFAULT '0',
  `row_count` int DEFAULT '0',
  `status` int DEFAULT '1' COMMENT '1澶勭悊涓?2鎴愬姛 3澶辫触',
  `error_msg` varchar(1000) DEFAULT NULL,
  `export_by` bigint DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `file_path` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_store` (`store_id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎶ヨ〃瀵煎嚭鏃ュ織';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `report_export_task`
--

DROP TABLE IF EXISTS `report_export_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `task_no` varchar(64) NOT NULL,
  `report_code` varchar(64) NOT NULL COMMENT '报表编码: SALES/PAY/MEMBER/COMMISSION/PRODUCT',
  `report_name` varchar(128) NOT NULL,
  `date_range_start` date DEFAULT NULL,
  `date_range_end` date DEFAULT NULL,
  `filter_json` varchar(1000) DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1待处理 2处理中 3成功 4失败 5已取消',
  `progress` int DEFAULT '0' COMMENT '进度 0-100',
  `file_path` varchar(500) DEFAULT NULL,
  `file_url` varchar(500) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `row_count` int DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `error_msg` varchar(2000) DEFAULT NULL,
  `idempotency_key` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_task` (`tenant_id`,`task_no`),
  KEY `idx_status` (`status`,`deleted`),
  KEY `idx_report` (`report_code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表导出任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `report_metric_caliber`
--

DROP TABLE IF EXISTS `report_metric_caliber`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_metric_caliber` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `metric_key` varchar(64) NOT NULL,
  `metric_name` varchar(128) NOT NULL,
  `category` varchar(32) NOT NULL COMMENT 'SALES/PAY/MEMBER/COMMISSION/INVENTORY',
  `formula` varchar(500) DEFAULT NULL COMMENT '计算公式（人类可读）',
  `sql_template` varchar(2000) DEFAULT NULL COMMENT '实际 SQL 模板',
  `unit` varchar(16) DEFAULT '元',
  `description` varchar(500) DEFAULT NULL,
  `version` varchar(16) DEFAULT 'v1',
  `enabled` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_version` (`metric_key`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表指标口径';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `report_metric_snapshot`
--

DROP TABLE IF EXISTS `report_metric_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_metric_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint DEFAULT '1',
  `snapshot_date` date NOT NULL,
  `metric_key` varchar(64) NOT NULL,
  `metric_name` varchar(128) DEFAULT NULL,
  `metric_value` decimal(18,4) DEFAULT '0.0000',
  `extra` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_store_date_metric` (`tenant_id`,`store_id`,`snapshot_date`,`metric_key`),
  KEY `idx_date` (`snapshot_date`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表指标快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rfm_batch_log`
--

DROP TABLE IF EXISTS `rfm_batch_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rfm_batch_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `action` varchar(16) NOT NULL COMMENT 'BATCH / SEED',
  `brand_id` bigint DEFAULT NULL COMMENT '品牌ID，可空',
  `total` int NOT NULL DEFAULT '0' COMMENT '总数',
  `success` int NOT NULL DEFAULT '0' COMMENT '成功数',
  `fail` int NOT NULL DEFAULT '0' COMMENT '失败数',
  `duration_ms` int NOT NULL DEFAULT '0' COMMENT '耗时(ms)',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注/失败样例',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_create` (`tenant_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RFM 批量执行日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rfm_batch_task`
--

DROP TABLE IF EXISTS `rfm_batch_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rfm_batch_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户',
  `action` varchar(16) NOT NULL COMMENT 'BATCH / SEED',
  `brand_id` bigint DEFAULT NULL COMMENT '品牌ID，可空',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人',
  `status` varchar(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / DONE / FAILED / CANCELLED',
  `total` int NOT NULL DEFAULT '0' COMMENT '计划处理总数',
  `processed` int NOT NULL DEFAULT '0' COMMENT '已处理数',
  `success` int NOT NULL DEFAULT '0' COMMENT '成功数',
  `fail` int NOT NULL DEFAULT '0' COMMENT '失败数',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '失败样例 / 备注',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status_start` (`tenant_id`,`status`,`start_time`),
  KEY `idx_tenant_action_start` (`tenant_id`,`action`,`start_time`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RFM 批量任务实时进度';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schedule_recommendation`
--

DROP TABLE IF EXISTS `schedule_recommendation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_recommendation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL,
  `schedule_date` date NOT NULL,
  `recommendation_json` text,
  `coverage_rate` decimal(5,2) DEFAULT NULL,
  `utilization_rate` decimal(5,2) DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `algorithm` varchar(32) DEFAULT NULL,
  `model_version` varchar(16) DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `applied_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_schedule_date` (`schedule_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stock_check`
--

DROP TABLE IF EXISTS `stock_check`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_check` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint NOT NULL,
  `check_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `check_date` date NOT NULL,
  `total_items` int DEFAULT '0',
  `diff_items` int DEFAULT '0',
  `profit_qty` int DEFAULT '0',
  `loss_qty` int DEFAULT '0',
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  `confirmed_by` bigint DEFAULT NULL COMMENT '确认人ID',
  `confirmed_by_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '确认人姓名',
  `confirmed_time` datetime DEFAULT NULL COMMENT '确认时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `check_no` (`check_no`),
  KEY `idx_stock_check_store` (`store_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存盘点单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stock_inventory`
--

DROP TABLE IF EXISTS `stock_inventory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `batch_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '0',
  `production_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `supplier` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_in_time` datetime DEFAULT NULL,
  `last_out_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_stock_inv_store_product` (`store_id`,`product_id`),
  KEY `idx_stock_inv_batch` (`batch_no`),
  KEY `idx_stock_inv_expiry` (`expiry_date`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表（支持批次）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stock_product`
--

DROP TABLE IF EXISTS `stock_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint NOT NULL,
  `product_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '耗材',
  `spec` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unit` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT '瓶',
  `cost_price` decimal(12,2) DEFAULT '0.00',
  `sale_price` decimal(12,2) DEFAULT '0.00',
  `min_stock` int DEFAULT '10',
  `max_stock` int DEFAULT '1000',
  `enable_batch` tinyint DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `product_code` (`product_code`),
  KEY `idx_stock_product_store` (`store_id`),
  KEY `idx_stock_product_cat` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品/耗材表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stock_record`
--

DROP TABLE IF EXISTS `stock_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT '1',
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `batch_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` tinyint NOT NULL,
  `quantity` int NOT NULL,
  `before_qty` int DEFAULT '0',
  `after_qty` int DEFAULT '0',
  `unit_price` decimal(12,2) DEFAULT '0.00',
  `total_amount` decimal(12,2) DEFAULT '0.00',
  `supplier` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_stock_record_product` (`product_id`),
  KEY `idx_stock_record_type` (`type`),
  KEY `idx_stock_record_time` (`record_time`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出入库记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `style_gallery`
--

DROP TABLE IF EXISTS `style_gallery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `style_gallery` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `style_code` varchar(50) DEFAULT NULL,
  `style_name` varchar(100) DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL,
  `difficulty` int DEFAULT NULL,
  `estimated_minutes` int DEFAULT NULL,
  `base_price` decimal(10,2) DEFAULT NULL,
  `service_id` bigint DEFAULT NULL,
  `description` text,
  `cover_image` varchar(500) DEFAULT NULL,
  `images_json` text,
  `tags` varchar(500) DEFAULT NULL,
  `view_count` int DEFAULT '0',
  `order_count` int DEFAULT '0',
  `like_count` int DEFAULT '0',
  `is_hot` tinyint DEFAULT '0',
  `is_new` tinyint DEFAULT '0',
  `is_recommend` tinyint DEFAULT '0',
  `season` varchar(20) DEFAULT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'active',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `style_view_log`
--

DROP TABLE IF EXISTS `style_view_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `style_view_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `style_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `open_id` varchar(100) DEFAULT NULL,
  `source` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_style_id` (`style_id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subscription_billing`
--

DROP TABLE IF EXISTS `subscription_billing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription_billing` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `sub_member_id` bigint DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `plan_id` bigint DEFAULT NULL,
  `billing_date` date DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT '0.00',
  `payment_method` varchar(20) DEFAULT '',
  `payment_status` varchar(20) DEFAULT '',
  `bill_no` varchar(50) DEFAULT '',
  `transaction_id` varchar(100) DEFAULT '',
  `error_message` varchar(500) DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `paid_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sub_member` (`sub_member_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_plan` (`plan_id`),
  KEY `idx_billing_date` (`billing_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subscription_member`
--

DROP TABLE IF EXISTS `subscription_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `sub_no` varchar(64) NOT NULL,
  `plan_id` bigint DEFAULT NULL,
  `plan_code` varchar(64) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `next_bill_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `used_count` int DEFAULT '0',
  `remaining_count` int DEFAULT '0',
  `auto_renew` tinyint DEFAULT '0',
  `status` varchar(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/CANCELLED',
  `payment_method` varchar(32) DEFAULT NULL,
  `total_paid` decimal(18,2) DEFAULT '0.00',
  `renew_count` int DEFAULT '0',
  `cancelled_at` datetime DEFAULT NULL,
  `cancel_reason` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_no` (`sub_no`),
  KEY `idx_member` (`member_id`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='璁㈤槄浼氬憳';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subscription_plan`
--

DROP TABLE IF EXISTS `subscription_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `plan_code` varchar(50) DEFAULT '',
  `plan_name` varchar(100) DEFAULT '',
  `plan_type` varchar(20) DEFAULT '',
  `price` decimal(12,2) DEFAULT '0.00',
  `billing_cycle_days` int DEFAULT '30',
  `trial_days` int DEFAULT '0',
  `service_count` int DEFAULT '0',
  `included_services` varchar(500) DEFAULT '',
  `discount_rate` decimal(4,2) DEFAULT '0.00',
  `gift_points` int DEFAULT '0',
  `extra_benefits` varchar(500) DEFAULT '',
  `description` varchar(500) DEFAULT '',
  `cover_image` varchar(255) DEFAULT '',
  `total_subscribers` int DEFAULT '0',
  `active_subscribers` int DEFAULT '0',
  `status` varchar(20) DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`),
  KEY `idx_code` (`plan_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_category`
--

DROP TABLE IF EXISTS `sys_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_code` varchar(50) DEFAULT NULL COMMENT '分类编码',
  `category_name` varchar(50) NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父级ID',
  `level` int DEFAULT '1' COMMENT '层级',
  `path` varchar(255) DEFAULT NULL COMMENT '路径（逗号分隔）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `icon` varchar(50) DEFAULT NULL COMMENT '图标',
  `category_type` varchar(20) DEFAULT 'goods' COMMENT '类型：goods服务项目 product商品',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `status` tinyint DEFAULT '1' COMMENT '状态：0禁用 1启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_category_type` (`category_type`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `config_key` varchar(64) NOT NULL COMMENT 'é…ç½® keyï¼Œå”¯ä¸€',
  `config_value` varchar(512) DEFAULT NULL COMMENT 'é…ç½® valueï¼ˆå­—ç¬¦ä¸²å­˜å‚¨ï¼Œæ•°å­—ç”± Service ç«¯è§£æžï¼‰',
  `config_group` varchar(64) DEFAULT 'system' COMMENT 'åˆ†ç»„',
  `description` varchar(255) DEFAULT NULL COMMENT 'è¯´æ˜Ž',
  `sort_order` int DEFAULT '0' COMMENT 'æŽ’åº',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç³»ç»Ÿé…ç½®è¡¨ (P0-T02 æ–°å»º)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_idempotency_record`
--

DROP TABLE IF EXISTS `sys_idempotency_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_idempotency_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键（前端或第三方传入）',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型：wallet.recharge / wallet.consume / wallet.refund / package.purchase / stock.outbound / pos.pay / pos.refund',
  `biz_id` bigint DEFAULT NULL COMMENT '业务主键（首次成功落库后回填）',
  `request_hash` varchar(64) DEFAULT NULL COMMENT '请求体摘要（SHA-256），用于检测同一 key 不同 body 的冲突',
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING / SUCCESS / FAILED',
  `response_code` int DEFAULT NULL COMMENT '落库时的 BizCode.code',
  `response_message` varchar(512) DEFAULT NULL COMMENT '落库时的消息',
  `response_data` mediumtext COMMENT '落库时的 response.data 序列化',
  `retry_count` int DEFAULT '0' COMMENT '已重试次数',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间，过期后可被复用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_key_biz` (`tenant_id`,`idempotency_key`,`biz_type`),
  KEY `idx_biz_id` (`biz_id`),
  KEY `idx_expired_at` (`expired_at`),
  KEY `idx_biz_type` (`biz_type`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='幂等记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_log`
--

DROP TABLE IF EXISTS `sys_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作用户名',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户ID',
  `module` varchar(50) DEFAULT NULL COMMENT '模块',
  `action` varchar(50) DEFAULT NULL COMMENT '动作',
  `description` varchar(500) DEFAULT NULL COMMENT '操作描述',
  `request_url` varchar(255) DEFAULT NULL COMMENT '请求URL',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方法',
  `request_params` text COMMENT '请求参数',
  `ip` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'UA',
  `cost_ms` int DEFAULT NULL COMMENT '耗时(ms)',
  `status` tinyint DEFAULT '1' COMMENT '状态：0失败 1成功',
  `error_msg` text COMMENT '错误信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_module` (`module`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=8666 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户ID',
  `login_type` varchar(20) DEFAULT 'password' COMMENT '登录类型',
  `status` tinyint DEFAULT '1' COMMENT '状态：0失败 1成功',
  `message` varchar(255) DEFAULT NULL COMMENT '备注/错误信息',
  `ip` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'UA',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_deleted` (`deleted`),
  CONSTRAINT `fk_login_log_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_login_log_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1654 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_menu_permission`
--

DROP TABLE IF EXISTS `sys_menu_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `permission_id` bigint NOT NULL COMMENT 'sys_permission.id',
  `route_path` varchar(255) NOT NULL COMMENT '前端路由路径',
  `component_path` varchar(255) DEFAULT NULL COMMENT '前端组件路径(动态 import)',
  `redirect_path` varchar(255) DEFAULT NULL COMMENT '重定向路径',
  `icon` varchar(64) DEFAULT NULL COMMENT '图标名称',
  `active_paths` varchar(512) DEFAULT NULL COMMENT '激活路径集合，逗号分隔，用于高亮',
  `hidden` tinyint DEFAULT '0' COMMENT '是否隐藏：0 否 1 是',
  `keep_alive` tinyint DEFAULT '1' COMMENT '是否缓存页面：0 否 1 是',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_id` (`permission_id`),
  KEY `idx_route_path` (`route_path`)
) ENGINE=InnoDB AUTO_INCREMENT=374 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单 UI 扩展表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_code` varchar(100) NOT NULL COMMENT '权限代码',
  `permission_name` varchar(50) NOT NULL COMMENT '权限名称',
  `permission_type` varchar(20) DEFAULT 'button' COMMENT '权限类型：menu按钮',
  `parent_id` bigint DEFAULT '0' COMMENT '父级ID',
  `path` varchar(255) DEFAULT NULL COMMENT '路由路径',
  `icon` varchar(50) DEFAULT NULL COMMENT '图标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：0禁用 1启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_code` (`permission_code`),
  KEY `idx_permission_code` (`permission_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1373 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` varchar(50) NOT NULL COMMENT '角色代码',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `status` tinyint DEFAULT '1' COMMENT '状态：0禁用 1启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`),
  KEY `idx_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_data_scope`
--

DROP TABLE IF EXISTS `sys_role_data_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `data_scope` varchar(16) NOT NULL COMMENT '数据范围：ALL/BRAND/STORE/SELF',
  `data_scope_value` varchar(512) DEFAULT NULL COMMENT '范围值（逗号分隔的门店/品牌ID；ALL 不填）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_scope` (`role_id`,`data_scope`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_data_scope` (`data_scope`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色数据范围表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB AUTO_INCREMENT=841 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_sequence`
--

DROP TABLE IF EXISTS `sys_sequence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `seq_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `current_val` bigint DEFAULT '0',
  `prefix` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `seq_name` (`seq_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务序列号表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_tenant`
--

DROP TABLE IF EXISTS `sys_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tenant_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `logo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `expire_at` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`)
) ENGINE=InnoDB AUTO_INCREMENT=900020 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_tenant_config`
--

DROP TABLE IF EXISTS `sys_tenant_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `cfg_key` varchar(128) NOT NULL COMMENT '配置键',
  `cfg_value` text COMMENT '配置值',
  `description` varchar(255) DEFAULT NULL COMMENT '说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_key` (`tenant_id`,`cfg_key`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（BCrypt加密）',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `status` tinyint DEFAULT '1' COMMENT '状态：0禁用 1启用',
  `store_id` bigint DEFAULT NULL COMMENT '所属门店ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '???????D',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标记：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_username` (`username`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=900012 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `trace_span`
--

DROP TABLE IF EXISTS `trace_span`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trace_span` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'é“¾è·¯ traceId',
  `span_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'å½“å‰ spanId',
  `parent_span_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è°ƒç”¨æ–¹ spanIdï¼ˆæ ¹è°ƒç”¨ä¸ºç©ºï¼‰',
  `service_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'æœåŠ¡å',
  `operation_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'æ–¹æ³•å / æ“ä½œå',
  `http_method` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HTTP æ–¹æ³•',
  `uri` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è¯·æ±‚ URI',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / ERROR / TIMEOUT',
  `http_status` int DEFAULT NULL COMMENT 'HTTP çŠ¶æ€ç ',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'é”™è¯¯ç ',
  `start_at` datetime(3) DEFAULT NULL COMMENT 'å¼€å§‹æ—¶é—´',
  `duration_ms` bigint DEFAULT NULL COMMENT 'è€—æ—¶ ms',
  `tenant_id` bigint DEFAULT NULL COMMENT 'ç§Ÿæˆ· ID',
  `user_id` bigint DEFAULT NULL COMMENT 'ç”¨æˆ· ID',
  `tags` text COLLATE utf8mb4_unicode_ci COMMENT 'é¢å¤–æ ‡ç­¾ JSON',
  `client_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'è°ƒç”¨æ¥æº IP',
  `user_agent` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  PRIMARY KEY (`id`),
  KEY `idx_trace_span_trace_id` (`trace_id`),
  KEY `idx_trace_span_span_id` (`span_id`),
  KEY `idx_trace_span_parent` (`parent_span_id`),
  KEY `idx_trace_span_tenant` (`tenant_id`),
  KEY `idx_trace_span_user` (`user_id`),
  KEY `idx_trace_span_start_at` (`start_at`),
  KEY `idx_trace_span_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9834 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='P3.1 å…¨é“¾è·¯è¿½è¸ª Span æ—¥å¿—';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary view structure for view `v_wallet_drift`
--

DROP TABLE IF EXISTS `v_wallet_drift`;
/*!50001 DROP VIEW IF EXISTS `v_wallet_drift`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_wallet_drift` AS SELECT 
 1 AS `member_id`,
 1 AS `tenant_id`,
 1 AS `name`,
 1 AS `phone`,
 1 AS `member_balance`,
 1 AS `wallet_balance`,
 1 AS `diff`,
 1 AS `drift_type`*/;
SET character_set_client = @saved_cs_client;

--
-- Dumping routines for database 'meiyetec'
--
/*!50003 DROP PROCEDURE IF EXISTS `seed_member` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE DEFINER=`root`@`localhost` PROCEDURE `seed_member`()
BEGIN
  DECLARE i INT DEFAULT 0;
  DECLARE v_id BIGINT;
  DECLARE v_phone VARCHAR(20);
  DECLARE v_name VARCHAR(20);
  DECLARE v_gender TINYINT;
  DECLARE v_level BIGINT;
  DECLARE v_tag_count INT;
  DECLARE v_tag INT;
  DECLARE v_balance DECIMAL(12,2);
  DECLARE v_points INT;
  DECLARE v_total DECIMAL(12,2);
  DECLARE v_visit DATETIME;
  DECLARE v_join DATE;
  DECLARE v_status TINYINT;

  WHILE i < 50 DO
    SET v_phone = CONCAT('138', LPAD(FLOOR(RAND() * 100000000), 8, '0'));
    IF RAND() < 0.3 THEN
      SET v_name = CONCAT('Xiao', LPAD(i+1, 3, '0'));
      SET v_gender = 1;
    ELSE
      SET v_name = CONCAT('M', LPAD(i+1, 3, '0'));
      SET v_gender = 2;
    END IF;
    SET v_level = (SELECT id FROM mem_level WHERE deleted=0 ORDER BY RAND() LIMIT 1);
    SET v_balance = FLOOR(RAND() * 5000);
    SET v_points = FLOOR(RAND() * 8000);
    SET v_total = FLOOR(RAND() * 30000 + 100);
    SET v_visit = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 60) DAY);
    SET v_join = DATE_SUB(CURDATE(), INTERVAL FLOOR(30 + RAND() * 700) DAY);
    SET v_status = IF(RAND() < 0.95, 1, 2);

    INSERT INTO crm_member (member_no, name, phone, gender, birthday, level_id, store_id, tenant_id, balance, points, total_consume, status, join_date, last_visit)
      VALUES (CONCAT('M', DATE_FORMAT(NOW(),'%y%m%d'), LPAD(i+1, 5, '0')),
              v_name, v_phone, v_gender,
              DATE_SUB(CURDATE(), INTERVAL FLOOR(18 + RAND() * 30) YEAR),
              v_level, 1, 1, v_balance, v_points, v_total, v_status, v_join, v_visit);
    SET v_id = LAST_INSERT_ID();

    INSERT INTO crm_wallet (tenant_id, member_id, balance, total_recharge, total_consume, frozen_amount)
      VALUES (1, v_id, v_balance, v_balance + 200, v_total, 0);

    SET v_tag_count = 1 + FLOOR(RAND() * 3);
    SET v_tag = 0;
    WHILE v_tag < v_tag_count DO
      INSERT IGNORE INTO crm_member_tag_rel (member_id, tag_id)
        SELECT v_id, id FROM crm_member_tag WHERE deleted=0 ORDER BY RAND() LIMIT 1;
      SET v_tag = v_tag + 1;
    END WHILE;

    SET i = i + 1;
  END WHILE;
END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 DROP PROCEDURE IF EXISTS `seed_pos` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE DEFINER=`root`@`localhost` PROCEDURE `seed_pos`()
BEGIN
  DECLARE i INT DEFAULT 0;
  DECLARE v_order_id BIGINT;
  DECLARE v_member_id BIGINT;
  DECLARE v_total DECIMAL(12,2);
  DECLARE v_discount DECIMAL(12,2);
  DECLARE v_final DECIMAL(12,2);
  DECLARE v_status TINYINT;
  DECLARE v_pay_time DATETIME;
  DECLARE v_no VARCHAR(64);
  DECLARE v_pay_method VARCHAR(32);

  WHILE i < 80 DO
    SET v_pay_time = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 60) DAY);
    SET v_status = IF(RAND() < 0.85, 2, IF(RAND() < 0.5, 1, 3));
    SET v_total = FLOOR(80 + RAND() * 4000);
    SET v_discount = IF(RAND() < 0.5, FLOOR(RAND() * v_total * 0.2), 0);
    SET v_final = v_total - v_discount;

    IF FLOOR(RAND() * 2) = 0 THEN
      SET v_member_id = (SELECT id FROM crm_member WHERE deleted=0 ORDER BY RAND() LIMIT 1);
    ELSE
      SET v_member_id = NULL;
    END IF;

    SET v_no = CONCAT('POS', DATE_FORMAT(v_pay_time, '%Y%m%d%H%i%s'), LPAD(i+1, 4, '0'));

    INSERT INTO pos_order (tenant_id, store_id, order_no, member_id, member_name, member_phone,
                           type, total_amount, discount_amount, final_amount, paid_amount,
                           cashier_id, cashier_name, status, pay_time, refund_time)
      SELECT 1, 1, v_no, v_member_id, name, phone,
             1, v_total, v_discount, v_final, IF(v_status=2, v_final, 0),
             1, 'admin', v_status, v_pay_time, IF(v_status=3, v_pay_time, NULL)
      FROM (SELECT 1) t LEFT JOIN crm_member ON crm_member.id = v_member_id;
    SET v_order_id = LAST_INSERT_ID();

    INSERT INTO pos_order_item (tenant_id, order_id, service_id, service_name, quantity, unit_price,
                                total_price, discount_amount, final_price, employee_id, employee_name, create_time)
      VALUES (1, v_order_id,
              FLOOR(1 + RAND() * 22),
              CONCAT('Service-', LPAD(i+1, 3, '0')),
              1, v_total, v_total, v_discount, v_final, 1, 'admin', v_pay_time);

    IF v_status = 2 THEN
      SET v_pay_method = ELT(FLOOR(1 + RAND() * 4), 'WeChat', 'Alipay', 'Card', 'Cash');
      INSERT INTO pos_payment (tenant_id, order_id, payment_no, method, amount, status, pay_time)
        VALUES (1, v_order_id, CONCAT('PAY', v_no), v_pay_method, v_final, 1, v_pay_time);
    END IF;

    SET i = i + 1;
  END WHILE;
END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 DROP PROCEDURE IF EXISTS `seed_stock` */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = latin1 */ ;
/*!50003 SET character_set_results = latin1 */ ;
/*!50003 SET collation_connection  = latin1_swedish_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
CREATE DEFINER=`root`@`localhost` PROCEDURE `seed_stock`()
BEGIN
  DECLARE v_id BIGINT;
  DECLARE v_qty INT;
  DECLARE v_done INT DEFAULT FALSE;
  DECLARE cur CURSOR FOR SELECT id FROM stock_product WHERE deleted=0 AND tenant_id=1;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;
  OPEN cur;
  READ_LOOP: LOOP
    FETCH cur INTO v_id;
    IF v_done THEN LEAVE READ_LOOP; END IF;
    SET v_qty = FLOOR(20 + RAND() * 200);
    INSERT INTO stock_inventory (tenant_id, store_id, product_id, batch_no, quantity, version, supplier, last_in_time)
      VALUES (1, 1, v_id, CONCAT('B', DATE_FORMAT(NOW(),'%Y%m%d'), '-', v_id), v_qty, 0, '?????????', NOW());
    INSERT INTO stock_record (tenant_id, store_id, product_id, batch_no, type, quantity, before_qty, after_qty, unit_price, total_amount, supplier, operator_id, operator_name, remark, record_time)
      VALUES (1, 1, v_id, CONCAT('B', DATE_FORMAT(NOW(),'%Y%m%d'), '-', v_id), 1, v_qty, 0, v_qty, 50 + RAND() * 100, v_qty * (50 + RAND() * 100), '?????????', 1, 'admin', '??????', NOW());
  END LOOP;
  CLOSE cur;
END ;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Final view structure for view `v_wallet_drift`
--

/*!50001 DROP VIEW IF EXISTS `v_wallet_drift`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`%` SQL SECURITY DEFINER */
/*!50001 VIEW `v_wallet_drift` AS select `m`.`id` AS `member_id`,`m`.`tenant_id` AS `tenant_id`,`m`.`name` AS `name`,`m`.`phone` AS `phone`,coalesce(`m`.`balance`,0) AS `member_balance`,coalesce(`w`.`balance`,0) AS `wallet_balance`,(coalesce(`m`.`balance`,0) - coalesce(`w`.`balance`,0)) AS `diff`,(case when (`w`.`id` is null) then 'WALLET_MISSING' when (`m`.`balance` is null) then 'MEMBER_BALANCE_NULL' else 'DRIFT' end) AS `drift_type` from (`crm_member` `m` left join `crm_wallet` `w` on(((`w`.`member_id` = `m`.`id`) and (`w`.`deleted` = 0)))) where ((`m`.`deleted` = 0) and (`m`.`id` in (select `crm_wallet`.`member_id` from `crm_wallet` where (`crm_wallet`.`deleted` = 0)) is false or (coalesce(`m`.`balance`,0) <> coalesce(`w`.`balance`,0)))) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-12 20:51:46
