docker : mysqldump: [Warning] Using a password on the command line interface can be insecure.
所在位置 C:\Users\qq411\AppData\Local\Temp\ps-script-e1dbbad8-9e31-4c28-bd8e-363e2ef2f2a2.ps1:84 字符: 125
+ ... | Out-Null; docker exec meiyetec-mysql mysqldump -uroot -proot123456  ...
+                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : NotSpecified: (mysqldump: [War...an be insecure.:String) [], RemoteException
    + FullyQualifiedErrorId : NativeCommandError
 
-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
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
  `tenant_id` bigint DEFAULT '1' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `market_coupon`
--

LOCK TABLES `market_coupon` WRITE;
/*!40000 ALTER TABLE `market_coupon` DISABLE KEYS */;
INSERT INTO `market_coupon` VALUES (1,1,'Test Coupon',1,1,10.00,100.00,NULL,100,1,'2026-08-21 16:51:39','2026-09-20 16:51:39',1,NULL,'2026-08-21 16:51:39','2026-08-21 16:51:39',1),(2,1,'audit-p0-level-grant',1,1,10.00,0.00,NULL,50,1,'2026-01-01 00:00:00','2027-12-31 00:00:00',1,NULL,'2026-11-09 21:14:19',NULL,1),(3,1,'audit-p0-total-truncate',1,1,10.00,0.00,NULL,2,0,'2026-01-01 00:00:00','2027-12-31 00:00:00',1,NULL,'2026-11-09 21:15:02',NULL,1),(4,1,'audit-p0-single-grant-fix',1,1,10.00,0.00,NULL,50,0,'2026-11-01 00:00:00','2026-12-31 23:59:59',1,NULL,'2026-11-09 21:41:58','2026-11-09 21:52:58',1);
/*!40000 ALTER TABLE `market_coupon` ENABLE KEYS */;
UNLOCK TABLES;

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
  `tenant_id` bigint DEFAULT '1' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_coupon_id` (`coupon_id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `market_coupon_grant`
--

LOCK TABLES `market_coupon_grant` WRITE;
/*!40000 ALTER TABLE `market_coupon_grant` DISABLE KEYS */;
INSERT INTO `market_coupon_grant` VALUES (1,2,1,'E5RSAD643CDA',1,5,NULL,NULL,'2027-12-31 00:00:00','2026-11-09 21:14:30',NULL,0,1),(2,2,4,'3B6YKLYPEMXM',1,5,NULL,NULL,'2027-12-31 00:00:00','2026-11-09 21:14:30',NULL,0,1),(3,2,6,'AQU4XB8VVWAH',1,5,NULL,NULL,'2027-12-31 00:00:00','2026-11-09 21:14:30',NULL,0,1),(4,3,1,'T6E9E9LK3DN2',1,5,NULL,NULL,'2027-12-31 00:00:00','2026-11-09 21:15:12',NULL,0,1),(5,3,4,'JH24VW8WGK4G',1,5,NULL,NULL,'2027-12-31 00:00:00','2026-11-09 21:15:12',NULL,0,1),(6,4,3,'XML9H56RS7PB',1,1,NULL,NULL,'2026-12-31 23:59:59','2026-11-09 21:45:31',NULL,0,1),(7,4,3,'PQ2A6ZSQC2TF',1,1,NULL,NULL,'2026-12-31 23:59:59','2026-11-09 21:45:32',NULL,0,1),(8,4,3,'5C7XX3UBLU2F',1,1,NULL,NULL,'2026-12-31 23:59:59','2026-11-09 21:52:58',NULL,0,1);
/*!40000 ALTER TABLE `market_coupon_grant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `m09_invite_campaign`
--

DROP TABLE IF EXISTS `m09_invite_campaign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `m09_invite_campaign` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `campaign_code` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '活动编码（租户内唯一）',
  `campaign_name` varchar(120) COLLATE utf8mb4_bin NOT NULL COMMENT '活动名称',
  `campaign_type` varchar(20) COLLATE utf8mb4_bin DEFAULT 'INVITE_REWARD' COMMENT '活动类型（INVITE_REWARD / GROUP_BUY）',
  `inviter_reward_type` tinyint NOT NULL COMMENT '邀请人奖励类型 1优惠券 2积分 3储值 4自定义',
  `inviter_reward_value` decimal(12,2) DEFAULT '0.00' COMMENT '邀请人奖励值',
  `inviter_reward_meta` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '奖励元数据 JSON（couponId/templateId 等）',
  `invitee_reward_type` tinyint NOT NULL COMMENT '新人奖励类型 1优惠券 2积分 3储值 4自定义',
  `invitee_reward_value` decimal(12,2) DEFAULT '0.00' COMMENT '新人奖励值',
  `invitee_reward_meta` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '奖励元数据 JSON',
  `invitee_min_consume` decimal(12,2) DEFAULT '0.00' COMMENT '新人首单最低消费（元），达此门槛才发放双方奖励',
  `invite_need_register` tinyint(1) DEFAULT '1' COMMENT '是否需要注册（0=仅点击即可 1=必须注册）',
  `inviter_quota_per_day` int DEFAULT '5' COMMENT '单人每日邀请上限',
  `inviter_quota_total` int DEFAULT '50' COMMENT '单人累计邀请上限',
  `campaign_quota_total` int DEFAULT '0' COMMENT '活动总配额（0=不限）',
  `share_title` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '分享标题',
  `share_desc` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '分享描述',
  `share_image_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '分享图片',
  `share_landing_url` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '落地页URL（可空 = 系统默认）',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态 1启用 0停用',
  `operator_id` bigint DEFAULT NULL COMMENT '创建人ID',
  `operator_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人姓名',
  `remark` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`campaign_code`),
  KEY `idx_tenant_status` (`tenant_id`,`status`),
  KEY `idx_active_time` (`tenant_id`,`status`,`start_time`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请活动配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `m09_invite_campaign`
--

LOCK TABLES `m09_invite_campaign` WRITE;
/*!40000 ALTER TABLE `m09_invite_campaign` DISABLE KEYS */;
/*!40000 ALTER TABLE `m09_invite_campaign` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `m09_invite_code`
--

DROP TABLE IF EXISTS `m09_invite_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `m09_invite_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `campaign_id` bigint NOT NULL COMMENT '活动ID',
  `member_id` bigint NOT NULL COMMENT '邀请人会员ID',
  `member_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '邀请人姓名',
  `member_phone` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '邀请人手机号（脱敏）',
  `invite_code` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '邀请码（活动内唯一）',
  `short_link` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '短链',
  `share_count` int DEFAULT '0' COMMENT '分享次数（小程序/短信/H5 按钮点击）',
  `scan_count` int DEFAULT '0' COMMENT '扫码/访问次数',
  `register_count` int DEFAULT '0' COMMENT '通过该码注册的新人数',
  `success_count` int DEFAULT '0' COMMENT '成功邀请数（首单完成）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_campaign_code` (`campaign_id`,`invite_code`),
  UNIQUE KEY `uk_campaign_member` (`campaign_id`,`member_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_tenant_campaign` (`tenant_id`,`campaign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请码';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `m09_invite_code`
--

LOCK TABLES `m09_invite_code` WRITE;
/*!40000 ALTER TABLE `m09_invite_code` DISABLE KEYS */;
/*!40000 ALTER TABLE `m09_invite_code` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `m09_invite_record`
--

DROP TABLE IF EXISTS `m09_invite_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `m09_invite_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `campaign_id` bigint NOT NULL COMMENT '活动ID',
  `campaign_name` varchar(120) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '冗余活动名（活动删除/改名后仍可读）',
  `invite_code` varchar(32) COLLATE utf8mb4_bin NOT NULL COMMENT '邀请码',
  `inviter_member_id` bigint NOT NULL COMMENT '邀请人',
  `inviter_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '邀请人姓名',
  `invitee_member_id` bigint DEFAULT NULL COMMENT '受邀人会员ID（注册后回填）',
  `invitee_phone` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '受邀人手机号（脱敏存储 138****1234）',
  `invitee_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '受邀人姓名',
  `channel` varchar(20) COLLATE utf8mb4_bin DEFAULT 'LINK' COMMENT '渠道：WECHAT/SMS/LINK/QR/POSTER',
  `status` tinyint DEFAULT '1' COMMENT '1待注册 2已注册 3已首单 4已奖励 5已失效 6已拒绝',
  `clicked_at` datetime DEFAULT NULL COMMENT '点击时间',
  `register_at` datetime DEFAULT NULL COMMENT '注册时间',
  `first_consume_at` datetime DEFAULT NULL COMMENT '首次消费时间',
  `first_order_id` bigint DEFAULT NULL COMMENT '首单订单ID',
  `inviter_rewarded_at` datetime DEFAULT NULL COMMENT '邀请人奖励发放时间',
  `invitee_rewarded_at` datetime DEFAULT NULL COMMENT '新人奖励发放时间',
  `inviter_reward_log_id` bigint DEFAULT NULL,
  `invitee_reward_log_id` bigint DEFAULT NULL,
  `ip` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `ua` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `device_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `fail_reason` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '失败原因（如触发风控）',
  `remark` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_campaign_inviter_phone` (`campaign_id`,`inviter_member_id`,`invitee_phone`),
  KEY `idx_inviter` (`inviter_member_id`,`status`),
  KEY `idx_invitee_member` (`invitee_member_id`),
  KEY `idx_campaign_status` (`campaign_id`,`status`),
  KEY `idx_status` (`status`,`created_at`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `m09_invite_record`
--

LOCK TABLES `m09_invite_record` WRITE;
/*!40000 ALTER TABLE `m09_invite_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `m09_invite_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `m09_invite_reward_log`
--

DROP TABLE IF EXISTS `m09_invite_reward_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `m09_invite_reward_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL,
  `record_id` bigint NOT NULL COMMENT '关联邀请记录ID',
  `campaign_id` bigint NOT NULL,
  `campaign_name` varchar(120) COLLATE utf8mb4_bin DEFAULT NULL,
  `reward_to` tinyint NOT NULL COMMENT '1邀请人 2受邀人',
  `member_id` bigint NOT NULL COMMENT '收奖人',
  `member_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL,
  `member_phone` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL,
  `reward_type` tinyint NOT NULL COMMENT '1优惠券 2积分 3储值',
  `reward_type_text` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL,
  `reward_value` decimal(12,2) DEFAULT '0.00' COMMENT '奖励值',
  `reward_name` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '奖励名（如「满100减30券」「500积分」）',
  `coupon_grant_id` bigint DEFAULT NULL,
  `points_record_id` bigint DEFAULT NULL,
  `wallet_txn_id` bigint DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1成功 2失败 3已回滚',
  `fail_reason` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `error_code` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL,
  `is_auto` tinyint(1) DEFAULT '1' COMMENT '是否系统自动发放',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',
  PRIMARY KEY (`id`),
  KEY `idx_record` (`record_id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_campaign_status` (`campaign_id`,`status`),
  KEY `idx_tenant_created` (`tenant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请奖励发放日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `m09_invite_reward_log`
--

LOCK TABLES `m09_invite_reward_log` WRITE;
/*!40000 ALTER TABLE `m09_invite_reward_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `m09_invite_reward_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-10 22:56:20
