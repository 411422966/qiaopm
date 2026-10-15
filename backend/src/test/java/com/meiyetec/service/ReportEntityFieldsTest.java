package com.meiyetec.service;

import com.meiyetec.entity.ReportConfig;
import com.meiyetec.entity.ReportExportLog;
import com.meiyetec.entity.ReportExportTask;
import com.meiyetec.entity.ReportMetricCaliber;
import com.meiyetec.entity.ReportMetricSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REPORT 实体字段完整性测试：
 * 1. 实体必须含 tenantId 字段（多租户隔离）
 * 2. 表名与 inventory 对齐（即使 inventory 有错）
 * 3. POJO setter/getter 可用
 */
class ReportEntityFieldsTest {

    @Test
    void report_config_has_tenantId_and_table_name() {
        assertNotNull(findField(ReportConfig.class, "tenantId"));
        assertNotNull(findField(ReportConfig.class, "storeId"));
        assertEquals("report_config",
                ReportConfig.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void report_export_log_has_tenantId() {
        assertNotNull(findField(ReportExportLog.class, "tenantId"));
        assertEquals("report_export_log",
                ReportExportLog.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void report_export_task_has_tenantId() {
        assertNotNull(findField(ReportExportTask.class, "tenantId"));
        assertEquals("report_export_task",
                ReportExportTask.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void report_metric_caliber_table_name() {
        assertEquals("report_metric_caliber",
                ReportMetricCaliber.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void report_metric_snapshot_table_name_and_has_metric_key() {
        assertEquals("report_metric_snapshot",
                ReportMetricSnapshot.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
        assertNotNull(findField(ReportMetricSnapshot.class, "metricKey"));
        assertNotNull(findField(ReportMetricSnapshot.class, "metricValue"));
    }

    @Test
    void pojo_setter_getter_works() {
        ReportConfig cfg = new ReportConfig();
        cfg.setTenantId(7L);
        cfg.setStoreId(123L);
        cfg.setName("默认");
        cfg.setType(1);
        assertEquals(7L, cfg.getTenantId());
        assertEquals(123L, cfg.getStoreId());
        assertEquals("默认", cfg.getName());
        assertEquals(1, cfg.getType());

        ReportExportLog log = new ReportExportLog();
        log.setTenantId(7L);
        log.setStoreId(123L);
        log.setName("导出");
        log.setType(2);
        log.setStatus(3);
        assertEquals(7L, log.getTenantId());
        assertEquals(3, log.getStatus());

        ReportExportTask t = new ReportExportTask();
        t.setTenantId(7L);
        t.setReportCode("X");
        t.setStatus(1);
        assertEquals(7L, t.getTenantId());
        assertEquals("X", t.getReportCode());
    }

    private Field findField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(name)) return f;
        }
        return null;
    }
}