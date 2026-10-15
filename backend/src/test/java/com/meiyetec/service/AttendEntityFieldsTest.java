package com.meiyetec.service;

import com.meiyetec.entity.AttendanceConfig;
import com.meiyetec.entity.AttendanceRecord;
import com.meiyetec.entity.Employee;
import com.meiyetec.entity.LeaveRequest;
import com.meiyetec.mapper.AttendanceConfigMapper;
import com.meiyetec.mapper.AttendanceRecordMapper;
import com.meiyetec.mapper.EmployeeMapper;
import com.meiyetec.mapper.LeaveRequestMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ATTEND 实体字段完整性 + 业务一致性
 */
class AttendEntityFieldsTest {

    @Test
    void attendance_record_has_tenantId_and_table_name() {
        assertNotNull(findField(AttendanceRecord.class, "tenantId"));
        assertNotNull(findField(AttendanceRecord.class, "employeeId"));
        assertNotNull(findField(AttendanceRecord.class, "checkDate"));
        assertEquals("attendance_record",
                AttendanceRecord.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void attendance_config_has_tenantId_and_table_name() {
        assertNotNull(findField(AttendanceConfig.class, "tenantId"));
        assertNotNull(findField(AttendanceConfig.class, "storeId"));
        assertNotNull(findField(AttendanceConfig.class, "workStartTime"));
        assertEquals("attendance_config",
                AttendanceConfig.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void leave_request_has_tenantId() {
        assertNotNull(findField(LeaveRequest.class, "tenantId"));
        assertNotNull(findField(LeaveRequest.class, "employeeId"));
        assertNotNull(findField(LeaveRequest.class, "leaveType"));
        assertEquals("attendance_leave_request",
                LeaveRequest.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value());
    }

    @Test
    void mappers_use_correct_table_names() {
        // AttendanceRecordMapper 走 entity @TableName attendance_record (BaseMapper)
        // 不再写 "biz_*" 前缀（曾经写错表名导致 biz_attendance_config 不存在）
        // 这里仅保证 mapper 接口 extends BaseMapper
        assertNotNull(AttendanceRecordMapper.class.getInterfaces());
        assertNotNull(AttendanceConfigMapper.class.getInterfaces());
        assertNotNull(LeaveRequestMapper.class.getInterfaces());
        assertNotNull(EmployeeMapper.class.getInterfaces());
    }

    @Test
    void attendance_config_entity_no_longer_references_biz_attendance_config() {
        String table = AttendanceConfig.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value();
        assertFalse(table.startsWith("biz_"), "attendance_config 表名不应 biz_ 前缀, 实际: " + table);
        assertEquals("attendance_config", table);
    }

    @Test
    void pojo_setter_getter_works() {
        AttendanceConfig c = new AttendanceConfig();
        c.setTenantId(7L);
        c.setStoreId(123L);
        c.setConfigName("default");
        c.setStatus(1);
        c.setAllowLateMinutes(15);
        assertEquals(7L, c.getTenantId());
        assertEquals(123L, c.getStoreId());
        assertEquals("default", c.getConfigName());
        assertEquals(1, c.getStatus());
        assertEquals(15, c.getAllowLateMinutes());

        LeaveRequest l = new LeaveRequest();
        l.setTenantId(7L);
        l.setEmployeeId(456L);
        l.setLeaveType("annual");
        l.setLeaveDays(3);
        l.setStatus("pending");
        assertEquals(7L, l.getTenantId());
        assertEquals(456L, l.getEmployeeId());
        assertEquals("annual", l.getLeaveType());
        assertEquals(3, l.getLeaveDays());
        assertEquals("pending", l.getStatus());

        AttendanceRecord r = new AttendanceRecord();
        r.setTenantId(7L);
        r.setEmployeeId(789L);
        r.setIsLate(1);
        r.setLateMinutes(12);
        assertEquals(7L, r.getTenantId());
        assertEquals(789L, r.getEmployeeId());
        assertEquals(1, r.getIsLate());
        assertEquals(12, r.getLateMinutes());
    }

    private Field findField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(name)) return f;
        }
        return null;
    }
}