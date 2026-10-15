<template>
  <div class="page-fiscal-config">
    <el-page-header @back="$router.push('/fiscal')" content="渠道配置" />

    <el-row :gutter="16" class="action-row">
      <el-col :span="6">
        <el-select v-model="selectedChannel" placeholder="选择通道" style="width:100%">
          <el-option v-for="c in channels" :key="c.code"
                     :label="c.name + '（' + c.code + '）'" :value="c.code"/>
        </el-select>
      </el-col>
      <el-col :span="6">
        <el-button type="primary" @click="loadCurrent">加载当前配置</el-button>
      </el-col>
      <el-col :span="6">
        <el-button type="warning" @click="testConnectivity" :loading="testing">测试连通性</el-button>
      </el-col>
      <el-col :span="6">
        <el-button type="success" @click="saveConfig" :loading="saving">保存配置</el-button>
      </el-col>
    </el-row>

    <el-card class="form-card">
      <el-form :model="form" label-width="140px" v-loading="loading">
        <el-form-item label="通道">
          <el-input v-model="form.channel" disabled />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="enabledSwitch" :active-value="1" :inactive-value="0"
                     active-text="启用" inactive-text="停用"
                     @change="onToggle"/>
        </el-form-item>
        <el-form-item label="环境">
          <el-radio-group v-model="form.environment">
            <el-radio-button value="sandbox">沙箱</el-radio-button>
            <el-radio-button value="production">生产</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="销方税号">
          <el-input v-model="form.taxNo" placeholder="如：91110000XXXXXXXXXX"/>
        </el-form-item>
        <el-form-item label="销方名称">
          <el-input v-model="form.merchantName" placeholder="如：美业科技有限公司"/>
        </el-form-item>
        <el-form-item label="发票类型">
          <el-radio-group v-model="form.invoiceType">
            <el-radio-button value="GENERAL">普票</el-radio-button>
            <el-radio-button value="SPECIAL">专票</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-divider content-position="left">渠道凭据</el-divider>
        <el-form-item label="appKey">
          <el-input v-model="form.appKey" placeholder="留空表示不修改；如已存值将显示脱敏">
            <template #append v-if="form.appKeyMasked">
              <el-tag size="small" type="success">已配置</el-tag>
            </template>
          </el-input>
          <div class="form-hint" v-if="form.appKeyMasked">已配置；输入新值会覆盖</div>
        </el-form-item>
        <el-form-item label="appSecret">
          <el-input v-model="form.appSecret" type="password" show-password
                    placeholder="留空表示不修改">
            <template #append v-if="form.appSecretMasked">
              <el-tag size="small" type="success">已配置</el-tag>
            </template>
          </el-input>
          <div class="form-hint" v-if="form.appSecretMasked">已配置；输入新值会覆盖</div>
        </el-form-item>
        <el-form-item label="平台 URL">
          <el-input v-model="form.platformUrl" placeholder="覆盖 SDK 默认 URL"/>
        </el-form-item>
        <el-form-item label="回调 URL">
          <el-input v-model="form.notifyUrl" placeholder="如：https://api.example.com/webhook/fiscal"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="2"/>
        </el-form-item>

        <el-divider content-position="left">
          <span style="color:#e6a23c">⚠️ 敏感操作二次确认</span>
        </el-divider>
        <el-form-item label="二次确认密码">
          <el-input v-model="confirmPassword" type="password" show-password
                    placeholder="敏感操作（修改凭据 / 启用）要求输入当前登录密码"/>
          <div class="form-hint">保存时若涉及敏感字段，会校验当前用户密码</div>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 连通性测试结果 -->
    <el-card v-if="testResult" class="test-card">
      <template #header>
        <span>连通性测试结果</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="结果">
          <el-tag :type="testResult.ok ? 'success' : 'danger'">{{ testResult.ok ? 'OK' : 'FAILED' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="阶段">{{ testResult.stage }}</el-descriptions-item>
        <el-descriptions-item label="通道">{{ testResult.channel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="环境">{{ testResult.environment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="销方税号">{{ testResult.taxNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ testResult.elapsedMs }} ms</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ testResult.message }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { fiscalApi, fiscalConfigApi } from '@/api/fiscal'

const router = useRouter()
const channels = ref<any[]>([])
const selectedChannel = ref('BAI_WANG')
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const confirmPassword = ref('')
const testResult = ref<any>(null)

const form = reactive<any>({
  channel: 'BAI_WANG',
  appKey: '',
  appSecret: '',
  appKeyMasked: false,
  appSecretMasked: false,
  taxNo: '',
  invoiceType: 'GENERAL',
  merchantName: '',
  platformUrl: '',
  notifyUrl: '',
  environment: 'sandbox',
  enabled: 0,
  description: '',
  extraJson: ''
})

const enabledSwitch = computed({
  get() { return form.enabled ? 1 : 0 },
  set(v: number) { form.enabled = v }
})

watch(selectedChannel, async (v) => {
  if (v) await loadCurrent()
})

async function loadChannels() {
  const { data } = await fiscalApi.listChannels()
  channels.value = data || []
}
async function loadCurrent() {
  if (!selectedChannel.value) return
  loading.value = true
  try {
    const { data } = await fiscalConfigApi.getByChannel(selectedChannel.value)
    if (data) {
      Object.assign(form, data)
    } else {
      // 没配置过 — 重置为默认
      Object.assign(form, {
        channel: selectedChannel.value,
        appKey: '', appSecret: '',
        appKeyMasked: false, appSecretMasked: false,
        taxNo: '', merchantName: '',
        invoiceType: 'GENERAL',
        platformUrl: '', notifyUrl: '',
        environment: 'sandbox',
        enabled: 0, description: '', extraJson: ''
      })
    }
  } finally { loading.value = false }
}

async function onToggle(val: number) {
  if (!form.id) {
    enabledSwitch.value = 0
    form.enabled = 0
    return ElMessage.warning('请先保存配置后再启停')
  }
  // 启用需要二次确认密码
  if (val === 1 && !confirmPassword.value) {
    enabledSwitch.value = 0
    form.enabled = 0
    return ElMessage.warning('启用操作需要输入二次确认密码')
  }
  const action = val === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确认${action}通道「${form.channel}」？${val === 1 ? '启用后将允许通过该通道开票。' : '停用后通过该通道的开票请求会失败。'}`,
      `${action}确认`,
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    enabledSwitch.value = val === 1 ? 0 : 1
    form.enabled = enabledSwitch.value
    return
  }
  try {
    await fiscalConfigApi.toggle(form.id, val as 0 | 1)
    ElMessage.success(val === 1 ? '已启用' : '已停用')
    if (val === 1) confirmPassword.value = ''
  } catch (e) {
    // 回滚 UI
    enabledSwitch.value = val === 1 ? 0 : 1
    form.enabled = enabledSwitch.value
  }
}

async function saveConfig() {
  saving.value = true
  try {
    const body = { ...form, confirmPassword: confirmPassword.value || null }
    await fiscalConfigApi.save(body)
    ElMessage.success('已保存')
    confirmPassword.value = ''
    await loadCurrent()
  } finally { saving.value = false }
}

async function testConnectivity() {
  testing.value = true
  try {
    const override = (form.appKey || form.appSecret || form.platformUrl)
      ? {
          appKey: form.appKey && !form.appKeyMasked ? form.appKey : null,
          appSecret: form.appSecret && !form.appSecretMasked ? form.appSecret : null,
          platformUrl: form.platformUrl || null
        }
      : undefined
    const { data } = await fiscalConfigApi.test(override)
    testResult.value = data
    if (data.ok) ElMessage.success(`链路通畅 (${data.elapsedMs}ms)`)
    else ElMessage.warning(`链路异常：${data.message}`)
  } finally { testing.value = false }
}

onMounted(async () => {
  await loadChannels()
  await loadCurrent()
})
</script>

<style scoped>
.page-fiscal-config { padding: 16px; }
.action-row { margin: 16px 0; }
.form-card { margin-bottom: 16px; }
.test-card { margin-bottom: 16px; }
.form-hint { color: #909399; font-size: 12px; margin-top: 4px; }
</style>