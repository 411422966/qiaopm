// Real CRUD verification using Node.js (proper UTF-8 handling)
const http = require('http');

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

(async () => {
  console.log('========== MEMBER CRUD VERIFICATION ==========');

  const login = await call('POST', '/auth/login', { username: 'admin', password: '123456' });
  if (login.json.code !== 200) { console.log('LOGIN FAILED', login); return; }
  const token = login.json.data.token;
  console.log('Login: OK, token len=' + token.length);

  // 1. LIST - baseline
  const list1 = await call('POST', '/member/page', { pageNum: 1, pageSize: 5 }, token);
  const totalBefore = list1.json.data.total;
  console.log('\n[1] LIST (before)   HTTP=' + list1.status + ' total=' + totalBefore);

  // 2. CREATE
  const rand = Math.floor(Math.random() * 9000) + 1000;
  const phone = '139' + rand + '8888';
  const name = 'RuntimeTest' + rand;
  const create = await call('POST', '/member', { name, phone, gender: 1, levelId: 1, storeId: 1 }, token);
  console.log('[2] CREATE          HTTP=' + create.status + ' code=' + create.json.code + ' msg="' + create.json.message + '"');

  // Backend does not return new ID -- search by phone to get ID
  const search = await call('POST', '/member/page', { pageNum: 1, pageSize: 10, keyword: phone }, token);
  let newId = null;
  for (const r of search.json.data.records) {
    if (r.phone === phone) { newId = r.id; break; }
  }
  console.log('       newId=' + newId + ' (looked up by phone)');

  if (!newId) { console.log('FATAL: cannot find newly created member'); return; }

  // 3. READ
  const read = await call('GET', '/member/' + newId, null, token);
  const memberObj = read.json.data ? read.json.data.member : null;
  console.log('[3] READ            HTTP=' + read.status + ' name="' + (memberObj ? memberObj.name : '?') + '" phone="' + (memberObj ? memberObj.phone : '?') + '" memberNo="' + (memberObj ? memberObj.memberNo : '?') + '"');

  // 4. UPDATE
  const updatedName = name + '_Updated';
  const update = await call('PUT', '/member', { id: newId, name: updatedName, phone, gender: 1 }, token);
  console.log('[4] UPDATE          HTTP=' + update.status + ' code=' + update.json.code + ' msg="' + update.json.message + '"');
  const read2 = await call('GET', '/member/' + newId, null, token);
  const updated = read2.json.data ? read2.json.data.member : null;
  console.log('       verify: name="' + (updated ? updated.name : '?') + '" (expected "' + updatedName + '")');

  // 5. DELETE
  const del = await call('DELETE', '/member/' + newId, null, token);
  console.log('[5] DELETE          HTTP=' + del.status + ' code=' + del.json.code + ' msg="' + del.json.message + '"');
  const gone = await call('GET', '/member/' + newId, null, token);
  console.log('       verify: HTTP=' + gone.status + ' code=' + gone.json.code + ' msg="' + gone.json.message + '"');

  // 6. LIST - confirm count back to baseline (or close)
  const list2 = await call('POST', '/member/page', { pageNum: 1, pageSize: 5 }, token);
  const totalAfter = list2.json.data.total;
  console.log('[6] LIST (after)    total=' + totalAfter + ' (was ' + totalBefore + ' before this run, expected back to or 1 above from orphan)');

  // Also verify Search works
  const search2 = await call('POST', '/member/page', { pageNum: 1, pageSize: 10, keyword: phone }, token);
  console.log('[7] SEARCH phone=' + phone + '  HTTP=' + search2.status + ' results=' + search2.json.data.records.length);

  console.log('\n========== CRUD COMPLETE ==========');
})();
