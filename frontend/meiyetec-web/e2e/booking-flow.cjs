// Booking flow verification -- full lifecycle:
// Create -> Confirm (status 1->2) -> Checkin (2->3) -> Complete (3->4)
// Plus: read DB state at each step, verify side effects, clean up at end.

const http = require('http');
const mysql = require('mysql2/promise');

function call(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const headers = { 'Content-Type': 'application/json; charset=utf-8' };
    if (token) headers.Authorization = 'Bearer ' + token;
    const data = body ? Buffer.from(JSON.stringify(body), 'utf8') : null;
    if (data) headers['Content-Length'] = data.length;
    const req = http.request({
      hostname: 'localhost', port: 8080, path, method, headers
    }, (res) => {
      let chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const buf = Buffer.concat(chunks).toString('utf8');
        try { resolve({ status: res.statusCode, json: JSON.parse(buf) }); }
        catch { resolve({ status: res.statusCode, body: buf }); }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

const log = (...args) => console.log(...args);
const step = (n, title) => console.log(`\n========== STEP ${n}: ${title} ==========`);

(async () => {
  // Connect to MySQL to read/write side effects directly
  const db = await mysql.createConnection({
    host: 'localhost', port: 3307, user: 'root', password: 'root123456',
    database: 'meiyetec', charset: 'utf8mb4'
  });

  // 1. Login
  step('0', 'Login as admin');
  const login = await call('POST', '/auth/login', { username: 'admin', password: '123456' });
  if (login.json.code !== 200) { console.log('LOGIN FAILED', login); process.exit(1); }
  const token = login.json.data.token;
  log('Login OK, token length=' + token.length);

  // 2. Create a test member (or reuse one)
  step('1', 'Create / find test member');
  const rand = Math.floor(Math.random() * 9000) + 1000;
  const newPhone = '139' + rand + '0001';
  const memberName = 'BookingFlowTest' + rand;
  const create = await call('POST', '/member', { name: memberName, phone: newPhone, gender: 1, levelId: 1, storeId: 1 }, token);
  log('CREATE member: code=' + create.json.code + ' msg="' + create.json.message + '"');
  const search = await call('POST', '/member/page', { pageNum: 1, pageSize: 5, keyword: newPhone }, token);
  let memberId = null;
  for (const r of search.json.data.records) if (r.phone === newPhone) memberId = r.id;
  if (!memberId) { log('FATAL: cannot find newly created member'); process.exit(1); }
  log('memberId=' + memberId + ' phone=' + newPhone);

  // 3. Find an employee with schedule today
  step('2', 'Find employee with schedule today');
  const today = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
  // First list employees - try to find one with a schedule today
  // Use a list-all endpoint if available, otherwise just query DB
  const [emps] = await db.execute('SELECT id, employee_name, store_id, status FROM hr_employee WHERE status=1 LIMIT 20');
  log('Available employees: ' + emps.length);
  let chosenEmp = null;
  for (const emp of emps) {
    const [schedules] = await db.execute(
      'SELECT * FROM hr_employee_schedule WHERE employee_id=? AND work_date=? AND status=1 LIMIT 1',
      [emp.id, today]
    );
    if (schedules.length > 0) {
      chosenEmp = emp;
      chosenEmp.schedule = schedules[0];
      break;
    }
  }
  if (!chosenEmp) {
    log('No employee has a schedule today. Trying tomorrow or any upcoming day...');
    for (const emp of emps) {
      const [schedules] = await db.execute(
        'SELECT * FROM hr_employee_schedule WHERE employee_id=? AND work_date >= ? AND status=1 ORDER BY work_date LIMIT 1',
        [emp.id, today]
      );
      if (schedules.length > 0) {
        chosenEmp = emp;
        chosenEmp.schedule = schedules[0];
        break;
      }
    }
  }
  if (!chosenEmp) { log('FATAL: no employee with any schedule found'); process.exit(1); }
  log('Chosen employee: id=' + chosenEmp.id + ' name=' + chosenEmp.employee_name);
  log('  schedule: date=' + chosenEmp.schedule.work_date + ' start=' + chosenEmp.schedule.start_time + ' end=' + chosenEmp.schedule.end_time + ' maxBookings=' + chosenEmp.schedule.max_bookings);

  // 4. Find a room in same store
  step('3', 'Find room in same store');
  const [rooms] = await db.execute('SELECT id, room_name, store_id FROM pos_room WHERE store_id=? AND status=1 LIMIT 1', [chosenEmp.store_id]);
  let roomId = null, roomName = null;
  if (rooms.length > 0) {
    roomId = rooms[0].id;
    roomName = rooms[0].room_name;
    log('Found room: id=' + roomId + ' name=' + roomName);
  } else {
    log('No room in store ' + chosenEmp.store_id + ' -- proceeding without room');
  }

  // 5. Find a free 30-min slot
  step('4', 'Find a free 30-min slot');
  const avail = await call('GET', '/booking/available?employeeId=' + chosenEmp.id + '&date=' + chosenEmp.schedule.work_date, null, token);
  const slots = avail.json.data || [];
  log('Available slots count=' + slots.length);
  if (slots.length === 0) { log('FATAL: no available slots'); process.exit(1); }
  const chosenSlot = slots[0];
  const [sStart, sEnd] = chosenSlot.split('~');
  log('Chosen slot: ' + chosenSlot);

  // 6. CREATE booking
  step('5', 'CREATE booking (status=1 待确认)');
  const workDate = chosenEmp.schedule.work_date;
  const startDt = workDate + 'T' + sStart + ':00';
  const endDt = workDate + 'T' + sEnd + ':00';
  const createBody = {
    memberId, memberName, memberPhone: newPhone,
    employeeId: chosenEmp.id, employeeName: chosenEmp.employee_name,
    serviceItem: '测试服务-面部护理',
    roomId, roomName,
    storeId: chosenEmp.store_id,
    bookingDate: workDate,
    startTime: startDt, endTime: endDt,
    source: 2,
    remark: '运行时验证-流程测试'
  };
  const cb = await call('POST', '/booking', createBody, token);
  log('HTTP=' + cb.status + ' code=' + cb.json.code + ' msg="' + cb.json.message + '"');
  if (cb.json.code !== 200) { log('FATAL: create failed'); process.exit(1); }
  const bookingId = cb.json.data ? cb.json.data.id : null;
  const bookingNo = cb.json.data ? cb.json.data.bookingNo : null;
  log('bookingId=' + bookingId + ' bookingNo=' + bookingNo);

  // 7. Verify DB state
  step('6', 'Verify DB state after CREATE');
  const [r1] = await db.execute(
    'SELECT id, booking_no, status, member_id, employee_id, checkin_time, complete_time, cancel_time FROM biz_booking WHERE id=?',
    [bookingId]
  );
  log('DB row: id=' + r1[0].id + ' booking_no=' + r1[0].booking_no + ' status=' + r1[0].status + ' checkin=' + r1[0].checkin_time + ' complete=' + r1[0].complete_time);

  // 8. CONFIRM (status 1 -> 2)
  step('7', 'CONFIRM booking (status 1 -> 2 已确认)');
  await new Promise(r => setTimeout(r, 800)); // ensure timestamp difference
  const confirm = await call('PUT', '/booking/' + bookingId + '/status?status=2', null, token);
  log('HTTP=' + confirm.status + ' code=' + confirm.json.code + ' msg="' + confirm.json.message + '"');
  log('Returned booking.status=' + confirm.json.data.status);

  // Verify DB
  const [r2] = await db.execute(
    'SELECT id, status, cancel_time FROM biz_booking WHERE id=?', [bookingId]
  );
  log('DB row: status=' + r2[0].status + ' cancel_time=' + r2[0].cancel_time);

  // 9. CHECKIN (status 2 -> 3)
  step('8', 'CHECKIN booking (status 2 -> 3 已签到)');
  await new Promise(r => setTimeout(r, 800));
  const checkin = await call('POST', '/booking/' + bookingId + '/checkin', null, token);
  log('HTTP=' + checkin.status + ' code=' + checkin.json.code + ' msg="' + checkin.json.message + '"');
  log('Returned booking.status=' + checkin.json.data.status + ' checkinTime=' + checkin.json.data.checkinTime);

  // Verify DB
  const [r3] = await db.execute(
    'SELECT id, status, checkin_time FROM biz_booking WHERE id=?', [bookingId]
  );
  log('DB row: status=' + r3[0].status + ' checkin_time=' + r3[0].checkin_time);

  // 10. COMPLETE (status 3 -> 4)
  step('9', 'COMPLETE booking (status 3 -> 4 已完成)');
  await new Promise(r => setTimeout(r, 800));
  const complete = await call('POST', '/booking/' + bookingId + '/complete', null, token);
  log('HTTP=' + complete.status + ' code=' + complete.json.code + ' msg="' + complete.json.message + '"');
  log('Returned booking.status=' + complete.json.data.status + ' completeTime=' + complete.json.data.completeTime);

  // Verify DB
  const [r4] = await db.execute(
    'SELECT id, status, checkin_time, complete_time FROM biz_booking WHERE id=?', [bookingId]
  );
  log('DB row: status=' + r4[0].status + ' checkin_time=' + r4[0].checkin_time + ' complete_time=' + r4[0].complete_time);

  // 11. Test INVALID transitions
  step('10', 'Test INVALID transitions (should fail)');
  // Try to checkin again (4 -> 3 should fail)
  const failCheckin = await call('POST', '/booking/' + bookingId + '/checkin', null, token);
  log('CHECKIN on completed booking: HTTP=' + failCheckin.status + ' code=' + failCheckin.json.code + ' msg="' + failCheckin.json.message + '" (expected non-200)');
  // Try to cancel a completed booking (4 -> 5 not allowed)
  const failCancel = await call('PUT', '/booking/' + bookingId + '/status?status=5&reason=test', null, token);
  log('CANCEL completed booking: HTTP=' + failCancel.status + ' code=' + failCancel.json.code + ' msg="' + failCancel.json.message + '" (expected non-200)');

  // 12. Side effects check (member's booking count via DB)
  step('11', 'Check related module data');
  const [memberRow] = await db.execute('SELECT id, name, phone FROM crm_member WHERE id=?', [memberId]);
  log('Member: id=' + memberRow[0].id + ' name=' + memberRow[0].name);
  const [memberBookings] = await db.execute(
    'SELECT id, booking_no, status FROM biz_booking WHERE member_id=? ORDER BY id DESC LIMIT 5',
    [memberId]
  );
  log('Member bookings (latest 5):');
  for (const b of memberBookings) {
    log('  id=' + b.id + ' no=' + b.booking_no + ' status=' + b.status);
  }

  // 13. Frontend calendar reflects booking
  step('12', 'Check frontend calendar endpoint reflects the booking');
  const cal = await call('GET', '/booking/calendar', null, token);
  if (cal.json && cal.json.data && cal.json.data.days) {
    const dayKey = workDate;
    const dayStat = cal.json.data.days[dayKey];
    if (dayStat) {
      log('Day ' + dayKey + ' stats: ' + JSON.stringify(dayStat));
    } else {
      log('Day ' + dayKey + ' not in calendar response');
    }
  } else {
    log('Calendar response unexpected: ' + JSON.stringify(cal.json).slice(0, 200));
  }

  // 14. CLEANUP
  step('13', 'Cleanup: delete test booking and member');
  // Booking is now completed (status=4), cannot delete via API (only 1 or 5 allowed)
  // We will delete directly via DB
  await db.execute('DELETE FROM biz_booking WHERE id=?', [bookingId]);
  log('Deleted booking id=' + bookingId + ' from DB');
  // Member delete (via API)
  const delMem = await call('DELETE', '/member/' + memberId, null, token);
  log('Deleted member: HTTP=' + delMem.status + ' code=' + delMem.json.code + ' msg="' + delMem.json.message + '"');
  // Verify
  const [verifyBookings] = await db.execute('SELECT COUNT(*) AS cnt FROM biz_booking WHERE member_id=?', [memberId]);
  log('Remaining bookings for member: ' + verifyBookings[0].cnt);

  await db.end();

  console.log('\n========== FLOW COMPLETE ==========');
})();
