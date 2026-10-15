package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 REPORT 模块的关键 controller 存在并暴露预期端点。
 */
class ReportControllersPresenceTest {

    @Test
    void report_caliber_controller_exists_and_exposes_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.ReportCaliberController");
        assertNotNull(c);
        assertHasMethod(c, "list");
        assertHasMethod(c, "query");
        assertHasMethod(c, "snapshotList");
        assertHasMethod(c, "runSnapshot");
    }

    @Test
    void report_task_controller_exists_and_exposes_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.ReportTaskController");
        assertNotNull(c);
        assertHasMethod(c, "page");
        assertHasMethod(c, "create");
        assertHasMethod(c, "cancel");
        assertHasMethod(c, "download");
    }

    @Test
    void report_export_task_service_exists() {
        assertNotNull(findClassIfExists("com.meiyetec.service.ReportExportTaskService"));
        assertNotNull(findClassIfExists("com.meiyetec.service.impl.ReportExportTaskServiceImpl"));
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