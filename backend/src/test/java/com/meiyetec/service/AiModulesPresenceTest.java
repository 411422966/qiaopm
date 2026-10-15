package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 4.25 AI 模块的关键 controller / service / entity 存在并暴露预期端点
 */
class AiModulesPresenceTest {

    @Test
    void ai_scheduler_exposes_real_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.AiSchedulerService");
        assertHasMethod(c, "forecast");
        assertHasMethod(c, "listForecasts");
        assertHasMethod(c, "generate");
        assertHasMethod(c, "listSchedules");
        assertHasMethod(c, "staffLoad");
    }

    @Test
    void ai_alias_controller_exposes_inventory_paths() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.AiApiAliasController");
        assertHasMethod(c, "churnTrain");
        assertHasMethod(c, "churnModelList");
        assertHasMethod(c, "churnModelDeploy");
        assertHasMethod(c, "abCreate");
        assertHasMethod(c, "abStart");
        assertHasMethod(c, "abAssign");
        assertHasMethod(c, "abStats");
        assertHasMethod(c, "enhance");
        assertHasMethod(c, "enhanceRecommend");
        assertHasMethod(c, "enhanceForecastGenerate");
        assertHasMethod(c, "enhanceForecastList");
        assertHasMethod(c, "enhanceReviewGenerate");
        assertHasMethod(c, "enhanceReviewGet");
    }

    @Test
    void ai_schedule_plan_entity_table() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.entity.AiSchedulePlan");
        com.baomidou.mybatisplus.annotation.TableName tn =
                (com.baomidou.mybatisplus.annotation.TableName) c.getAnnotation(
                        com.baomidou.mybatisplus.annotation.TableName.class);
        assertNotNull(tn);
        assertEquals("ai_schedule_plan", tn.value());
        assertTrue(hasField(c, "tenantId"));
        assertTrue(hasField(c, "storeId"));
        assertTrue(hasField(c, "weekStart"));
    }

    @Test
    void ai_schedule_plan_mapper_exists() {
        assertNotNull(findClassIfExists("com.meiyetec.mapper.AiSchedulePlanMapper"));
    }

    @Test
    void ai_required_entities_and_mappers_present() {
        // BookingForecast
        assertNotNull(findClassIfExists("com.meiyetec.entity.BookingForecastFeature"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.BookingForecastResult"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.ForecastAccuracyLog"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.ScheduleRecommendation"));
        // CV
        assertNotNull(findClassIfExists("com.meiyetec.entity.CvModel"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.CvRecognitionLog"));
        // Churn
        assertNotNull(findClassIfExists("com.meiyetec.entity.ChurnModelRegistry"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.ChurnTrainingRunLog"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.LtvTrainingFeature"));
        // Gray / AB
        assertNotNull(findClassIfExists("com.meiyetec.entity.FeatureFlag"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.AbExperiment"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.AbAssignment"));
        // Enhance
        assertNotNull(findClassIfExists("com.meiyetec.entity.AiRecommendCache"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.AiSalesForecast"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.AiReviewSummary"));
    }

    @Test
    void ai_required_tables_exist_in_db() {
        String[] requiredTables = {
            "booking_forecast_feature", "booking_forecast_result", "forecast_accuracy_log",
            "schedule_recommendation",
            "cv_model", "cv_recognition_log",
            "churn_model_registry", "churn_training_run_log", "ltv_training_feature",
            "feature_flag", "ab_experiment", "ab_assignment",
            "ai_recommend_cache", "ai_sales_forecast", "ai_review_summary",
            "ai_schedule_plan"
        };
        for (String t : requiredTables) {
            assertEquals(true, tableExists(t), "AI 表不存在: " + t);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'meiyetec' AND table_name = ?",
                Integer.class, tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&characterEncoding=utf-8",
                    "root", "root123456"));

    private boolean hasField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) if (f.getName().equals(name)) return true;
        return false;
    }

    private void assertHasMethod(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return;
        }
        fail("缺少方法: " + name + " on " + c.getSimpleName());
    }

    private Class<?> findClassIfExists(String fqn) {
        try { return Class.forName(fqn); } catch (ClassNotFoundException e) { return null; }
    }
}