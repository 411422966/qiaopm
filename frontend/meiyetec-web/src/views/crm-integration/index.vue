<template>
  <div class="crm-int-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="集成配置" name="list">
        <el-button type="primary" @click="addDialog = true">新建集成</el-button>
        <el-table :data="integrations" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="platform" label="平台" width="120"/>
          <el-table-column prop="shopName" label="店铺"/>
          <el-table-column prop="syncDirection" label="方向" width="100"/>
          <el-table-column prop="lastSyncAt" label="最近同步" width="180"/>
          <el-table-column prop="status" label="状态" width="160">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" style="margin-right: 8px;">{{ row.status }}</el-tag>
              <el-switch v-model="row.status" active-value="ACTIVE" inactive-value="DISABLED" @change="toggle(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" type="success" :loading="syncLoading[row.id]" @click="sync(row)">同步</el-button>
              <el-button size="small" type="danger" :loading="forceLoading[row.id]" plain @click="forceSync(row)" style="margin-left: 6px;">强制同步</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="同步日志" name="log">
        <el-table :data="logs" style="width:100%;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="platform" label="平台" width="100"/>
          <el-table-column prop="syncType" label="类型" width="100"/>
          <el-table-column prop="status" label="状态" width="100"/>
          <el-table-column prop="createdAt" label="同步时间" width="180"/>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="addDialog" title="新建 CRM 集成" width="700px" :style="{ maxHeight: '620px' }">
      <el-form :model="addForm" label-width="140px" style="max-height: 540px; overflow-y: auto;">
        <el-form-item label="平台">
          <el-select v-model="addForm.platform" @change="onPlatformChange">
            <el-option label="Salesforce" value="SALESFORCE"/>
            <el-option label="HubSpot" value="HUBSPOT"/>
            <el-option label="Zoho" value="ZOHO"/>
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="addForm.shopName" placeholder="如：Demo 店铺" /></el-form-item>
        <el-form-item label="同步方向">
          <el-select v-model="addForm.syncDirection">
            <el-option label="PUSH" value="PUSH"/>
            <el-option label="PULL" value="PULL"/>
            <el-option label="BIDIRECTIONAL" value="BIDIRECTIONAL"/>
          </el-select>
        </el-form-item>
        <el-form-item label="自动同步">
          <el-switch v-model="addForm.autoSync" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <!-- SALESFORCE 才显示：clientId / username / loginUrl / privateKey（JWT 流程，不需 clientSecret） -->
        <template v-if="addForm.platform === 'SALESFORCE'">
          <el-divider content-position="left">Salesforce 凭据（OAuth2 JWT 流程）</el-divider>
          <el-form-item label="Client ID"><el-input v-model="addForm.clientId" placeholder="Connected App Consumer Key" /></el-form-item>
          <el-form-item label="Username"><el-input v-model="addForm.username" placeholder="Salesforce 用户名（JWT subject）" /></el-form-item>
          <el-form-item label="Login URL">
            <el-input v-model="addForm.loginUrl" placeholder="https://login.salesforce.com" />
          </el-form-item>
          <el-form-item label="Private Key (PEM)">
            <el-input v-model="addForm.privateKey" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }"
              placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----" />
            <div style="color: #909399; font-size: 12px; margin-top: 4px;">⚠️ 保存后不回显（WRITE_ONLY），请妥善保管原文件</div>
          </el-form-item>
        </template>

        <!-- HUBSPOT / ZOHO 才显示：clientId / clientSecret（OAuth2 Refresh Token 流程） -->
        <template v-else-if="addForm.platform === 'HUBSPOT' || addForm.platform === 'ZOHO'">
          <el-divider content-position="left">{{ addForm.platform }} 凭据（OAuth2 Refresh Token）</el-divider>
          <el-form-item label="Client ID"><el-input v-model="addForm.clientId" placeholder="OAuth2 client_id" /></el-form-item>
          <el-form-item label="Client Secret">
            <el-input v-model="addForm.clientSecret" type="password" show-password placeholder="OAuth2 client_secret" />
          </el-form-item>
        </template>

        <el-divider content-position="left">同步配置</el-divider>
        <el-form-item label="同步窗口（分钟）">
          <el-input-number v-model="addForm.syncWindowMinutes" :min="1" :max="1440" :step="5" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">默认 30；与上次同步时间差小于此值时手动同步会被 skipped（可用「强制同步」绕过）</span>
        </el-form-item>

        <el-divider content-position="left">字段映射（可选）</el-divider>
        <el-form-item label="Object Mappings">
          <el-input v-model="addForm.objectMappings" type="textarea" :autosize="{ minRows: 4, maxRows: 10 }"
            placeholder='{"externalId":"Id","firstName":"FirstName","lastName":"LastName","email":"Email","phone":"Phone","title":"Title","updatedAtMs":"SystemModstamp"}' />
          <div v-if="objectMappingsError" style="color: #f56c6c; font-size: 12px; margin-top: 4px;">
            JSON 格式错误：{{ objectMappingsError }}
          </div>
          <div v-else style="color: #909399; font-size: 12px; margin-top: 4px;">
            留空将使用 Salesforce 默认字段映射（后端 parseObjectMappings 降级）；格式错误后端也会拒绝
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialog = false">取消</el-button>
        <el-button type="primary" @click="doAdd">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { crmIntegrationApi } from '@/api/crm-integration'

const tab = ref('list')
const integrations = ref<any[]>([])
const logs = ref<any[]>([])
const addDialog = ref(false)
// I005-C4：表单新增 7 字段（clientId / clientSecret / username / loginUrl / privateKey / syncWindowMinutes / objectMappings）
// - SALESFORCE：JWT 流程（clientId + username + loginUrl + privateKey）
// - HUBSPOT/ZOHO：OAuth2 Refresh Token（clientId + clientSecret）
// - 通用：syncWindowMinutes（默认 30）+ objectMappings（可选 JSON）
const addForm = ref<any>({
  platform: 'SALESFORCE',
  shopName: 'Demo 店铺',
  syncDirection: 'PUSH',
  autoSync: 1,
  clientId: '',
  clientSecret: '',
  username: '',
  loginUrl: 'https://login.salesforce.com',
  privateKey: '',
  syncWindowMinutes: 30,
  objectMappings: ''
})
// I005-C4：objectMappings JSON 实时校验错误信息（空字符串 = 通过；非空 = 红字提示）
const objectMappingsError = ref('')

// 平台切换时清空不适用字段，避免脏数据残留（如 SALESFORCE → HUBSPOT 后 clientSecret 仍是空但 username 残留）
function onPlatformChange() {
  addForm.value.clientId = ''
  addForm.value.clientSecret = ''
  addForm.value.username = ''
  addForm.value.loginUrl = addForm.value.platform === 'SALESFORCE' ? 'https://login.salesforce.com' : ''
  addForm.value.privateKey = ''
}

// I005-C4：objectMappings 实时 JSON 校验；空字符串视为通过（后端降级默认映射）
watch(() => addForm.value.objectMappings, (val) => {
  if (!val || val.trim() === '') {
    objectMappingsError.value = ''
    return
  }
  try {
    const parsed = JSON.parse(val)
    if (typeof parsed !== 'object' || Array.isArray(parsed) || parsed === null) {
      objectMappingsError.value = '必须是 JSON 对象（不能是数组或 null）'
    } else {
      objectMappingsError.value = ''
    }
  } catch (e: any) {
    objectMappingsError.value = e?.message || '解析失败'
  }
})

// I005-C1：行级 loading 状态（普通同步 / 强制同步 独立）
const syncLoading = ref<Record<number, boolean>>({})
const forceLoading = ref<Record<number, boolean>>({})

function statusType(status: string): 'success' | 'info' | 'danger' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DISABLED') return 'info'
  // ERROR / PAUSED / 其他
  return 'danger'
}

async function loadList() { const { data } = await crmIntegrationApi.list(); integrations.value = data || [] }
async function loadLogs() { const { data } = await crmIntegrationApi.listSyncLogs(); logs.value = data || [] }
async function doAdd() {
  // I005-C4：保存前再校验一次 objectMappings（前端实时校验可能未触发）
  if (objectMappingsError.value) {
    ElMessage.error('Object Mappings JSON 格式错误：' + objectMappingsError.value)
    return
  }
  try {
    await crmIntegrationApi.create(addForm.value)
    ElMessage.success('已创建')
    addDialog.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.message || '未知错误'))
    addDialog.value = false
  }
}
async function toggle(row: any) {
  await crmIntegrationApi.toggle(row.id, { status: row.status })
  ElMessage.success('已切换')
}
async function sync(row: any) {
  const id: number = row.id
  syncLoading.value[id] = true
  try {
    const res = await crmIntegrationApi.syncNow(id)
    handleSyncResult(res?.data, '同步')
    loadLogs()
  } catch {
    // 错误已被 request 拦截器弹窗
  } finally {
    syncLoading.value[id] = false
  }
}

async function forceSync(row: any) {
  const id: number = row.id
  try {
    await ElMessageBox.confirm(
      '强制同步将忽略 syncWindowMinutes 窗口限制，立即执行一次全量同步，可能消耗较多 API 配额。是否继续？',
      '强制同步确认',
      {
        type: 'warning',
        confirmButtonText: '确认强制',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return  // 用户取消
  }
  forceLoading.value[id] = true
  try {
    const res = await crmIntegrationApi.syncNow(id, true)
    handleSyncResult(res?.data, '强制同步')
    loadLogs()
  } catch {
    // 错误已被 request 拦截器弹窗
  } finally {
    forceLoading.value[id] = false
  }
}

// I005-C2：区分 skipped / SUCCESS / FAILED / 未知响应
function handleSyncResult(data: any, prefix: string) {
  if (!data) {
    ElMessage.warning(`${prefix}：响应为空`)
    return
  }
  if (data.skipped === true) {
    const reason = data.reason ? `（${data.reason}）` : ''
    ElMessage.warning(`${prefix}已跳过${reason}。如需立即执行，请使用「强制同步」按钮`)
    return
  }
  if (data.status === 'SUCCESS') {
    const total = data.recordsTotal ?? data.successCount ?? 0
    const dur = data.durationMs ? `，耗时 ${data.durationMs}ms` : ''
    const forced = data.forced ? '（强制）' : ''
    ElMessage.success(`${prefix}成功${forced}：${total} 条记录${dur}`)
    return
  }
  if (data.status === 'FAILED') {
    ElMessage.error(`${prefix}失败：${data.errorMessage || '未知错误'}`)
    return
  }
  // 兜底：未识别结构
  ElMessage.info(`${prefix}已提交，状态未知`)
}

onMounted(() => { loadList(); loadLogs() })
</script>
