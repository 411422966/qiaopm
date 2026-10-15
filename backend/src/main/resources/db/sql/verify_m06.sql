-- ============================================================
-- M06 后端 Postman 测试结果验证
-- ============================================================
USE meiyetec;

SELECT 'M06 后端测试结果' AS section, NULL AS cnt, NULL AS note
UNION ALL SELECT '房间数',           COUNT(*), 'biz_room'                FROM biz_room WHERE deleted=0
UNION ALL SELECT '排班数',           COUNT(*), 'biz_employee_schedule'   FROM biz_employee_schedule WHERE deleted=0
UNION ALL SELECT '预约总数',         COUNT(*), 'biz_booking'             FROM biz_booking WHERE deleted=0
UNION ALL SELECT '→ 待确认(1)',     COUNT(*), NULL                     FROM biz_booking WHERE deleted=0 AND status=1
UNION ALL SELECT '→ 已确认(2)',     COUNT(*), NULL                     FROM biz_booking WHERE deleted=0 AND status=2
UNION ALL SELECT '→ 已签到(3)',     COUNT(*), NULL                     FROM biz_booking WHERE deleted=0 AND status=3
UNION ALL SELECT '→ 已完成(4)',     COUNT(*), NULL                     FROM biz_booking WHERE deleted=0 AND status=4
UNION ALL SELECT '→ 已取消(5)',     COUNT(*), NULL                     FROM biz_booking WHERE deleted=0 AND status=5
UNION ALL SELECT '刚才 PS 创建的',  COUNT(*), 'BK20260801201028'      FROM biz_booking WHERE booking_no='BK20260801201028' AND deleted=0;