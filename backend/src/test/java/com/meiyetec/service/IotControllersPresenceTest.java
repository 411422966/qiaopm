package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 4.24 IOT 模块的关键 controller / service 存在并暴露预期端点
 */
class IotControllersPresenceTest {

    @Test
    void iot_device_controller_exposes_link_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.IotDeviceController");
        assertHasMethod(c, "linkList");
        assertHasMethod(c, "createLink");
        assertHasMethod(c, "updateLink");
        assertHasMethod(c, "deleteLink");
        assertHasMethod(c, "toggleLink");
    }

    @Test
    void iot_device_data_controller_exposes_crud() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.IotDeviceDataController");
        assertHasMethod(c, "list");
        assertHasMethod(c, "recentByMember");
        assertHasMethod(c, "trendByMember");
        assertHasMethod(c, "create");
    }

    @Test
    void iot_trigger_rule_controller_exposes_active() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.IotTriggerRuleController");
        assertHasMethod(c, "list");
        assertHasMethod(c, "active");
    }

    @Test
    void iot_device_alert_controller_exposes_handle() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.IotDeviceAlertController");
        assertHasMethod(c, "list");
        assertHasMethod(c, "handle");
    }

    @Test
    void iot_device_link_service_exists() {
        assertNotNull(findClassIfExists("com.meiyetec.service.IotDeviceLinkService"));
        assertNotNull(findClassIfExists("com.meiyetec.service.impl.IotDeviceLinkServiceImpl"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.IotDeviceLink"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.IotDeviceLinkMapper"));
    }

    @Test
    void iot_device_data_service_signature_has_tenant_id() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.IotDeviceDataService");
        Method m = c.getMethod("queryList", Long.class, Long.class, Long.class, String.class,
                java.time.LocalDateTime.class, java.time.LocalDateTime.class);
        assertEquals("queryList", m.getName());
    }

    @Test
    void iot_trigger_rule_service_signature_has_tenant_id() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.IotTriggerRuleService");
        Method m = c.getMethod("findActive", Long.class, String.class, String.class);
        assertEquals("findActive", m.getName());
    }

    @Test
    void iot_device_alert_service_signature_has_tenant_id() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.IotDeviceAlertService");
        Method m = c.getMethod("queryList", Long.class, Long.class, String.class, String.class, Long.class);
        assertEquals("queryList", m.getName());
    }

    @Test
    void iot_device_link_entity_has_tenant_id_and_table() {
        Class<?> c = findClassIfExists("com.meiyetec.entity.IotDeviceLink");
        assertNotNull(c);
        assertTrue(hasField(c, "tenantId"));
        assertTrue(hasField(c, "masterDeviceId"));
        assertTrue(hasField(c, "slaveDeviceId"));
        com.baomidou.mybatisplus.annotation.TableName tn =
                (com.baomidou.mybatisplus.annotation.TableName) c.getAnnotation(
                        com.baomidou.mybatisplus.annotation.TableName.class);
        assertNotNull(tn);
        assertEquals("iot_device_link", tn.value());
    }

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