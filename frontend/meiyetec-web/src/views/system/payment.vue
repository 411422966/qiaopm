<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">数字人民币（DCEP）支付配置</span>
          <div class="header-right">
            <el-button type="warning" plain :loading="testing" size="small" @click="testConnection">🔌 测试连接</el-button>
            <el-tag :type="envTagType" size="large" effect="dark" class="env-tag">
              {{ envLabel }}
            </el-tag>
            <el-tag :type="form.enabled === 1 ? 'success' : 'info'" size="large">
              {{ form.enabled === 1 ? '已启用' : '未启用' }}
            </el-tag>
          </div>
        </div>
      </template>

      <div v-if="isProduction" class="env-banner danger">
        ⚠️ 当前为 <b>生产环境</b>，所有支付请求将进行真实资金扣减，操作请谨慎。
      </div>

      <div v-if="changes.length" class="changes-pill">
        <span>📝 您有 <b>{{ changes.length }}</b> 项未保存的变更</span>
        <el-link type="primary" underline="never" @click="discardChanges" style="margin-left:8px">撤销</el-link>
      </div>

      <el-form :model="form" label-width="160px" v-loading="loading">
        <div v-if="form.updatedAt" class="updated-at">
          <span class="updated-label">上次更新于</span>
          <el-tooltip
            placement="top"
            effect="light"
            :show-after="200"
          >
            <template #content>
              <div style="line-height:1.6">
                <div>🕒 时间：{{ formatUpdatedAt }}</div>
                <div>👤 操作人：{{ form.updatedBy || '系统' }}</div>
                <div>🌐 来源 IP：{{ form.updatedByIp || '—' }}</div>
                <div style="color:#909399;margin-top:4px">后续可审计日志模块接入</div>
              </div>
            </template>
            <span class="updated-value updated-link">
              {{ formatUpdatedAt }} ⓘ
            </span>
          </el-tooltip>
        </div>
        <div class="hint">
          配置由中国人民银行清算总中心颁发。沙箱环境用于联调测试，生产环境用于正式接入。
        </div>

        <el-divider content-position="left">商户信息</el-divider>
        <el-form-item label="商户号" required>
          <el-input v-model="form.merchantId" placeholder="如：1234567890" />
        </el-form-item>
        <el-form-item label="商户名称">
          <el-input v-model="form.merchantName" />
        </el-form-item>

        <el-divider content-position="left">接口凭证</el-divider>
        <el-form-item label="AppID">
          <el-input v-model="form.appId" placeholder="运营机构分配的应用ID" />
        </el-form-item>
        <el-form-item label="API 密钥">
          <div class="cred-row">
            <el-input
              v-model="form.apiKey"
              :type="showApiKey ? 'text' : 'password'"
              :placeholder="apiKeyMasked ? '已保存（显示 ••••••••），留空保留原值，输入新值会覆盖' : '请输入 API 密钥'"
              show-password
            />
            <el-tag v-if="apiKeyMasked" type="info" size="small" effect="plain" class="fp-tag">
              已配置
            </el-tag>
          </div>
          <div v-if="apiKeyMasked" class="cred-hint">
            留空表示不修改；输入新值会覆盖；显示的 •••• 不代表真实值
          </div>
          <div v-else-if="apiKeyWarn" class="cred-hint warn-hint">
            ⚠ {{ apiKeyWarn }}
          </div>
        </el-form-item>

        <el-divider content-position="left">密钥对</el-divider>
        <el-form-item
          label="商户私钥"
          :error="privateKeyError || undefined"
        >
          <div class="cred-row">
            <el-input
              v-model="form.privateKey"
              :type="showPrivateKey ? 'textarea' : 'password'"
              :rows="5"
              :placeholder="privateKeyMasked ? '已保存（显示 ••••••••），留空保留原值，输入新 PEM 会覆盖' : '-----BEGIN PRIVATE KEY-----...'"
              :readonly="privateKeyMasked && !showPrivateKey"
            />
          </div>
          <div class="cred-actions">
            <el-upload
              v-if="!privateKeyMasked || showPrivateKey"
              :show-file-list="false"
              :auto-upload="false"
              accept=".pem,.key,.txt"
              :on-change="(f: any) => onUploadPEM('privateKey', f)"
            >
              <el-button size="small" plain>📁 上传 PEM</el-button>
            </el-upload>
            <el-link v-if="privateKeyMasked" type="primary" underline="never" @click="toggleReveal('privateKey', privateKeyMasked)">
              <span v-if="showPrivateKey">🔒 立即隐藏</span>
              <span v-else>🔓 临时显示原文（10s）</span>
            </el-link>
            <span v-if="showPrivateKey" class="reveal-bar">
              <span class="reveal-label">⏱ {{ revealRemaining }}s 后自动隐藏</span>
              <span class="reveal-progress">
                <span class="reveal-progress-fill" :style="{ width: ((revealRemaining / SHOW_SECONDS) * 100) + '%' }"></span>
              </span>
            </span>
            <el-tag v-if="privateKeyMasked" type="success" size="small" effect="plain">已配置</el-tag>
            <span v-if="privateKeyFingerprint || liveFp.private" class="fp-line">
              <span class="fp-label">指纹</span>
              <span class="fp-value" :title="privateKeyFingerprint || ''">
                {{ (liveFp.private || privateKeyFingerprint || '—') }}
              </span>
            </span>
          </div>
        </el-form-item>

        <el-form-item
          label="商户公钥"
          :error="publicKeyError || undefined"
        >
          <div class="cred-row">
            <el-input
              v-model="form.publicKey"
              :type="showPublicKey ? 'textarea' : 'password'"
              :rows="5"
              :placeholder="publicKeyMasked ? '已保存（显示 ••••••••），留空保留原值，输入新 PEM 会覆盖' : '-----BEGIN PUBLIC KEY-----...'"
              :readonly="publicKeyMasked && !showPublicKey"
            />
          </div>
          <div class="cred-actions">
            <el-upload
              v-if="!publicKeyMasked || showPublicKey"
              :show-file-list="false"
              :auto-upload="false"
              accept=".pem,.key,.txt"
              :on-change="(f: any) => onUploadPEM('publicKey', f)"
            >
              <el-button size="small" plain>📁 上传 PEM</el-button>
            </el-upload>
            <el-link v-if="publicKeyMasked" type="primary" underline="never" @click="toggleReveal('publicKey', publicKeyMasked)">
              <span v-if="showPublicKey">🔒 立即隐藏</span>
              <span v-else>🔓 临时显示原文（10s）</span>
            </el-link>
            <span v-if="showPublicKey" class="reveal-bar">
              <span class="reveal-label">⏱ {{ revealRemaining }}s 后自动隐藏</span>
              <span class="reveal-progress">
                <span class="reveal-progress-fill" :style="{ width: ((revealRemaining / SHOW_SECONDS) * 100) + '%' }"></span>
              </span>
            </span>
            <el-tag v-if="publicKeyMasked" type="success" size="small" effect="plain">已配置</el-tag>
            <span v-if="publicKeyFingerprint || liveFp.public" class="fp-line">
              <span class="fp-label">指纹</span>
              <span class="fp-value" :title="publicKeyFingerprint || ''">
                {{ (liveFp.public || publicKeyFingerprint || '—') }}
              </span>
            </span>
          </div>
        </el-form-item>

        <div v-if="pemHeaderHint.length" class="pem-hint">
        <span>📝 提示：</span>
        <span v-for="(m, i) in pemHeaderHint" :key="i">{{ m }}{{ i < pemHeaderHint.length - 1 ? '；' : '。' }}</span>
        <span style="margin-left:4px">将以原文保存。</span>
      </div>

        <el-divider content-position="left">回调与运行环境</el-divider>
        <el-form-item label="回调地址">
          <el-input v-model="form.notifyUrl" placeholder="https://your-domain.com/api/notify/dcep" />
        </el-form-item>
        <el-form-item label="运行环境">
          <el-radio-group v-model="form.environment" @change="onEnvChange">
            <el-radio-button value="sandbox" :disabled="isProductionLocked">沙箱</el-radio-button>
            <el-radio-button value="production" :disabled="isProductionLocked">生产</el-radio-button>
          </el-radio-group>
          <el-tooltip v-if="isProductionLocked" content="生产环境启用中，需先停用才能切换环境" placement="top">
            <span class="env-lock">🔒 已锁定</span>
          </el-tooltip>
          <span class="env-hint" :class="isProduction ? 'danger' : 'warning'">
            {{ envHint }}
          </span>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
            :disabled="isEnableLocked"
            @change="onEnableChange"
          />
          <el-tooltip v-if="isEnableLocked && wantsToEnable" content="首次启用生产环境需要二次确认" placement="top">
            <span class="env-lock">🔒 需确认</span>
          </el-tooltip>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置
            <span style="opacity:0.6;font-size:11px;margin-left:4px">Ctrl+S</span>
          </el-button>
          <el-button @click="load" :disabled="saving">重新加载</el-button>
          <el-button v-if="changes.length" plain @click="discardChanges" :disabled="saving">↩ 撤销变更</el-button>
          <el-button v-if="form.id" type="success" plain @click="toggle">{{ form.enabled === 1 ? '停用' : '启用' }}</el-button>
          <el-button type="info" plain @click="$router.push('/sys/qualification')">📋 商户资质材料</el-button>
        </el-form-item>

        <transition name="el-fade-in">
          <div v-if="testResult" class="test-result" :class="testResult.ok ? 'ok' : 'fail'">
            <div class="test-row">
              <span class="test-icon">{{ testResult.ok ? '✓' : '✗' }}</span>
              <span class="test-msg">{{ testResult.message }}</span>
              <span v-if="testResult.stage" class="test-stage">[{{ testResult.stage }}]</span>
              <span class="test-elapsed">{{ testResult.elapsedMs }}ms</span>
              <el-link type="info" underline="never" @click="testResult = null" style="margin-left:auto">关闭</el-link>
            </div>
            <div v-if="testResult.gateway" class="test-row test-detail">
              <span>网关：<span class="mono">{{ testResult.gateway }}</span></span>
              <span v-if="testResult.privateKeyBits" style="margin-left:16px">私钥：{{ testResult.privateKeyBits }} 位 RSA</span>
              <span v-if="testResult.publicKeyBits" style="margin-left:16px">公钥：{{ testResult.publicKeyBits }} 位 RSA</span>
            </div>
          </div>
        </transition>
      </el-form>
    </el-card>

    <el-card class="info-card">
      <template #header><span class="title">支付方式概览</span></template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="商户号">
          <span class="mono">{{ form.merchantId || '— 未配置 —' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="环境">
          <el-tag :type="envTagType" size="small" effect="dark">{{ envLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AppID">
          <span class="mono">{{ form.appId || '— 未配置 —' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="凭据状态">
          <span v-if="allConfigured" class="ok">✓ 凭据已完整</span>
          <span v-else class="warn">⚠ 凭据未完整</span>
        </el-descriptions-item>
        <el-descriptions-item label="API Key">
          <el-tag :type="apiKeyMasked ? 'success' : 'danger'" size="small">
            {{ apiKeyMasked ? '已配置' : '未配置' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="私钥/公钥">
          <el-tag v-if="privateKeyMasked" type="success" size="small">私钥 ✓</el-tag>
          <el-tag v-else type="danger" size="small">私钥 ✗</el-tag>
          <el-tag v-if="publicKeyMasked" type="success" size="small" style="margin-left:4px">公钥 ✓</el-tag>
          <el-tag v-else type="danger" size="small" style="margin-left:4px">公钥 ✗</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="changes.length" class="info-card diff-card">
      <template #header>
        <div class="card-header">
          <span class="title">📋 本次会话修改摘要</span>
          <el-tag size="small" :type="hasDangerousChange ? 'danger' : 'warning'" effect="dark">
            {{ hasDangerousChange ? '⚠ 含敏感变更' : '待保存' }}
          </el-tag>
        </div>
      </template>
      <el-table :data="changes" border size="small" :show-header="true">
        <el-table-column prop="field" label="字段" width="140" />
        <el-table-column prop="from" label="原值" min-width="180">
          <template #default="{ row }">
            <span :class="row.kind === 'danger' ? 'val-from-danger' : 'val-from'">{{ row.from || '— 无 —' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="to" label="新值" min-width="180">
          <template #default="{ row }">
            <span :class="row.kind === 'danger' ? 'val-to-danger' : 'val-to'">{{ row.to || '— 无 —' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="性质" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.kind === 'danger' ? 'danger' : (row.kind === 'info' ? 'info' : 'success')" effect="plain">
              {{ row.kindLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click="revertField(row.key)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="hasDangerousChange" class="diff-warning">
        检测到敏感变更，保存时将要求输入操作密码进行二次确认。
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, h } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sysApi, type DcepConfig } from '@/api/sys'

const MASK = '••••••••'
const SHOW_SECONDS = 10

const confirmPwd = ref('')
const confirmPwdOk = ref(false)

const form = reactive<DcepConfig & { updatedBy?: string; updatedByIp?: string }>({
  id: undefined, merchantId: '', merchantName: '', appId: '', apiKey: '',
  privateKey: '', publicKey: '', notifyUrl: '', environment: 'sandbox',
  enabled: 0, description: '',
  updatedBy: '', updatedByIp: ''
})

const apiKeyMasked = ref(false)
const privateKeyMasked = ref(false)
const privateKeyFingerprint = ref('')
const publicKeyMasked = ref(false)
const publicKeyFingerprint = ref('')

const showApiKey = ref(false)
const showPrivateKey = ref(false)
const showPublicKey = ref(false)
let revealTimer: number | null = null
let revealTick: number | null = null
const revealRemaining = ref(0) // 倒计时剩余秒数，用于进度条显示

const loading = ref(false)
const saving = ref(false)

const isProduction = computed(() => form.environment === 'production')
// 生产环境硬约束：一旦启用并仍处于生产，禁止切换环境（必须先停用）
const isProductionLocked = computed(() => isProduction.value && form.enabled === 1)
// 启用开关锁定：仅在"切到生产且启用"瞬间触发二次确认后放行
const wantsToEnable = ref(false)
const isEnableLocked = computed(() => wantsToEnable.value && isProduction.value)
const envTagType = computed<'success' | 'danger' | 'warning' | 'info'>(() => {
  if (!form.enabled) return 'info'
  return isProduction.value ? 'danger' : 'success'
})
const envLabel = computed(() => {
  const en = isProduction.value ? '生产' : '沙箱'
  return form.enabled ? `${en} · 运行中` : `${en} · 已停用`
})
const envHint = computed(() =>
  isProduction.value
    ? '切到生产会直接对真实资金进行扣减，请确保已完成联调测试'
    : '沙箱环境不会发生真实扣款'
)

const formatUpdatedAt = computed(() => {
  if (!form.updatedAt) return ''
  const d = new Date(form.updatedAt)
  if (isNaN(d.getTime())) return String(form.updatedAt)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

const allConfigured = computed(() =>
  Boolean(form.merchantId && apiKeyMasked.value && privateKeyMasked.value && publicKeyMasked.value)
)

const original = ref<DcepConfig>({
  id: undefined, merchantId: '', merchantName: '', appId: '', apiKey: '',
  privateKey: '', publicKey: '', notifyUrl: '', environment: 'sandbox',
  enabled: 0, description: ''
})
const originalFlags = ref({
  apiKey: false, privateKey: false, publicKey: false
})

function snapshot() {
  original.value = JSON.parse(JSON.stringify(form))
  originalFlags.value = {
    apiKey: apiKeyMasked.value,
    privateKey: privateKeyMasked.value,
    publicKey: publicKeyMasked.value
  }
}

interface ChangeRow {
  field: string
  key: string
  from: string
  to: string
  kind: 'info' | 'success' | 'danger'
  kindLabel: string
}

const changes = computed<ChangeRow[]>(() => {
  const o = original.value
  const rows: ChangeRow[] = []
  // 商户号
  if ((form.merchantId || '') !== (o.merchantId || '')) {
    rows.push({
      key: 'merchantId',
      field: '商户号',
      from: o.merchantId || '',
      to: form.merchantId || '',
      kind: 'info',
      kindLabel: '变更'
    })
  }
  if ((form.merchantName || '') !== (o.merchantName || '')) {
    rows.push({ key: 'merchantName', field: '商户名称', from: o.merchantName || '', to: form.merchantName || '', kind: 'info', kindLabel: '变更' })
  }
  if ((form.appId || '') !== (o.appId || '')) {
    rows.push({ key: 'appId', field: 'AppID', from: o.appId || '', to: form.appId || '', kind: 'info', kindLabel: '变更' })
  }
  if ((form.notifyUrl || '') !== (o.notifyUrl || '')) {
    rows.push({ key: 'notifyUrl', field: '回调地址', from: o.notifyUrl || '', to: form.notifyUrl || '', kind: 'info', kindLabel: '变更' })
  }
  if ((form.description || '') !== (o.description || '')) {
    rows.push({ key: 'description', field: '备注', from: o.description || '', to: form.description || '', kind: 'info', kindLabel: '变更' })
  }

  // 环境
  const envChanged = form.environment !== o.environment
  if (envChanged) {
    const toProd = form.environment === 'production'
    rows.push({
      key: 'environment',
      field: '运行环境',
      from: o.environment === 'production' ? '生产' : '沙箱',
      to: form.environment === 'production' ? '生产' : '沙箱',
      kind: toProd ? 'danger' : 'info',
      kindLabel: toProd ? '⚠ 升生产' : '降沙箱'
    })
  }

  // 启用开关
  if (form.enabled !== o.enabled) {
    const on = form.enabled === 1
    rows.push({
      key: 'enabled',
      field: '启用开关',
      from: o.enabled === 1 ? '启用' : '停用',
      to: on ? '启用' : '停用',
      kind: on ? 'success' : 'info',
      kindLabel: on ? '启用' : '停用'
    })
  }

  // 凭据（基于 originalFlags + 当前值）
  const apiKeyEntered = (form.apiKey || '') !== '' && (form.apiKey || '') !== MASK
  const apiKeyOriginalHad = originalFlags.value.apiKey
  if (apiKeyEntered || (!apiKeyOriginalHad && (form.apiKey || '') !== '')) {
    rows.push({
      key: 'apiKey',
      field: 'API 密钥',
      from: apiKeyOriginalHad ? '已配置' : '未配置',
      to: apiKeyOriginalHad ? '已修改' : '首次配置',
      kind: 'danger',
      kindLabel: '敏感'
    })
  }
  const pkEntered = (form.privateKey || '') !== '' && (form.privateKey || '') !== MASK
  const pkOriginalHad = originalFlags.value.privateKey
  if (pkEntered || (!pkOriginalHad && (form.privateKey || '') !== '')) {
    rows.push({
      key: 'privateKey',
      field: '商户私钥',
      from: pkOriginalHad ? '已配置' : '未配置',
      to: pkOriginalHad ? '已修改' : '首次配置',
      kind: 'danger',
      kindLabel: '敏感'
    })
  }
  const pubEntered = (form.publicKey || '') !== '' && (form.publicKey || '') !== MASK
  const pubOriginalHad = originalFlags.value.publicKey
  if (pubEntered || (!pubOriginalHad && (form.publicKey || '') !== '')) {
    rows.push({
      key: 'publicKey',
      field: '商户公钥',
      from: pubOriginalHad ? '已配置' : '未配置',
      to: pubOriginalHad ? '已修改' : '首次配置',
      kind: 'danger',
      kindLabel: '敏感'
    })
  }

  return rows
})

const hasDangerousChange = computed(() => changes.value.some(c => c.kind === 'danger'))

const privateKeyError = computed(() => {
  if (privateKeyMasked.value && !showPrivateKey.value) return ''
  const v = (form.privateKey || '').trim()
  if (!v) return ''
  if (!/-----BEGIN [A-Z ]*PRIVATE KEY-----/.test(v)) return '格式不规范，应以 -----BEGIN ... PRIVATE KEY----- 开头'
  if (!/-----END [A-Z ]*PRIVATE KEY-----/.test(v)) return '格式不规范，应以 -----END ... PRIVATE KEY----- 结尾'
  if (v.replace(/\s+/g, '').length < 100) return '内容过短，可能不完整'
  return ''
})

const publicKeyError = computed(() => {
  if (publicKeyMasked.value && !showPublicKey.value) return ''
  const v = (form.publicKey || '').trim()
  if (!v) return ''
  if (!/-----BEGIN [A-Z ]*PUBLIC KEY-----/.test(v)) return '格式不规范，应以 -----BEGIN ... PUBLIC KEY----- 开头'
  if (!/-----END [A-Z ]*PUBLIC KEY-----/.test(v)) return '格式不规范，应以 -----END ... PUBLIC KEY----- 结尾'
  if (v.replace(/\s+/g, '').length < 80) return '内容过短，可能不完整'
  return ''
})

function clearRevealTimer() {
  if (revealTimer) {
    window.clearTimeout(revealTimer)
    revealTimer = null
  }
  if (revealTick) {
    window.clearInterval(revealTick)
    revealTick = null
  }
  revealRemaining.value = 0
}

function revealKey(field: 'privateKey' | 'publicKey') {
  if (field === 'privateKey') showPrivateKey.value = true
  if (field === 'publicKey') showPublicKey.value = true
  clearRevealTimer()
  revealRemaining.value = SHOW_SECONDS
  revealTick = window.setInterval(() => {
    revealRemaining.value = Math.max(0, revealRemaining.value - 1)
  }, 1000)
  revealTimer = window.setTimeout(() => {
    showPrivateKey.value = false
    showPublicKey.value = false
    clearRevealTimer()
    ElMessage.info('已自动隐藏敏感字段')
  }, SHOW_SECONDS * 1000)
}

function toggleReveal(field: 'privateKey' | 'publicKey', maskFlag: boolean) {
  if (!maskFlag) return
  if (field === 'privateKey') {
    if (showPrivateKey.value) {
      showPrivateKey.value = false
      clearRevealTimer()
    } else {
      revealKey(field)
    }
  } else {
    if (showPublicKey.value) {
      showPublicKey.value = false
      clearRevealTimer()
    } else {
      revealKey(field)
    }
  }
}

async function load() {
  loading.value = true
  try {
    const r: any = await sysApi.dcepCurrent()
    const d = r.data
    if (d) {
      form.id = d.id
      form.merchantId = d.merchantId || ''
      form.merchantName = d.merchantName || ''
      form.appId = d.appId || ''
      form.apiKey = d.apiKey || ''
      form.privateKey = d.privateKey || ''
      form.publicKey = d.publicKey || ''
      form.notifyUrl = d.notifyUrl || ''
      form.environment = d.environment || 'sandbox'
      form.enabled = d.enabled ?? 0
      form.description = d.description || ''
      form.updatedAt = d.updatedAt || ''
      form.createdAt = d.createdAt || ''
      form.updatedBy = d.updatedBy || ''
      form.updatedByIp = d.updatedByIp || ''
      apiKeyMasked.value = !!d.apiKeyMasked
      privateKeyMasked.value = !!d.privateKeyMasked
      privateKeyFingerprint.value = d.privateKeyFingerprint || ''
      publicKeyMasked.value = !!d.publicKeyMasked
      publicKeyFingerprint.value = d.publicKeyFingerprint || ''
    } else {
      Object.assign(form, {
        id: undefined, merchantId: '', merchantName: '', appId: '', apiKey: '',
        privateKey: '', publicKey: '', notifyUrl: '', environment: 'sandbox',
        enabled: 0, description: ''
      })
      apiKeyMasked.value = false
      privateKeyMasked.value = false
      publicKeyMasked.value = false
    }
    snapshot()
  } catch (e: any) {
    ElMessage.error('加载配置失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

function onUploadPEM(field: 'privateKey' | 'publicKey', f: any) {
  if (!f?.raw) return
  const file = f.raw as File
  if (file.size > 1024 * 64) {
    ElMessage.error('文件过大（>64KB），请确认是否为 PEM')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    const text = String(reader.result || '')
    if (field === 'privateKey') {
      form.privateKey = text.trim()
      privateKeyMasked.value = false
      ElMessage.success(`已读取私钥 ${file.name}（${text.length} 字符）`)
    } else {
      form.publicKey = text.trim()
      publicKeyMasked.value = false
      ElMessage.success(`已读取公钥 ${file.name}（${text.length} 字符）`)
    }
  }
  reader.onerror = () => ElMessage.error('文件读取失败')
  reader.readAsText(file)
}

async function onEnvChange(val: 'sandbox' | 'production') {
  // 硬约束：生产启用时禁止切换环境
  if (isProductionLocked.value && val !== form.environment) {
    form.environment = isProduction.value ? 'production' : 'sandbox'
    ElMessage.warning('生产环境已启用，请先停用后才能切换环境')
    return
  }
  if (val === 'production') {
    try {
      await ElMessageBox.confirm(
        '切换到【生产】环境意味着后续所有支付将走真实资金链路，可能造成实际扣款。请确认已完成联调测试。',
        '⚠️ 切换到生产环境',
        {
          type: 'warning',
          confirmButtonText: '确认切换',
          cancelButtonText: '取消',
          input: true,
          inputPlaceholder: '请输入 PROD 以确认',
          inputValidator: (v: string) => (v === 'PROD' ? true : '必须输入大写 PROD 才能切换'),
          inputErrorMessage: '必须输入大写 PROD 才能切换'
        }
      )
    } catch {
      form.environment = 'sandbox'
      ElMessage.info('已取消切换')
    }
  }
}

// 启用开关：先标记待确认，再要求二次确认
async function onEnableChange(val: number | string | boolean) {
  const enabled = val === 1 || val === true
  if (enabled && isProduction.value) {
    wantsToEnable.value = true
    try {
      // 旧版 Element Plus TS 类型未暴露 input 字段，但运行时支持
      await ElMessageBox.confirm(
        '即将在【生产】环境下启用支付通道。生产环境下所有支付将进行真实资金扣减。\n\n请确认所有凭据已准确配置、回调地址已部署。',
        '⚠️ 启用生产支付',
        {
          type: 'warning',
          confirmButtonText: '确认启用',
          cancelButtonText: '再检查一下',
          input: true,
          inputPlaceholder: '请输入 ENABLE 以确认',
          inputValidator: (v: string) => (v === 'ENABLE' ? true : '必须输入大写 ENABLE 才能启用'),
          inputErrorMessage: '必须输入大写 ENABLE 才能启用'
        } as any
      )
    } catch {
      form.enabled = 0
      ElMessage.info('已取消启用')
    } finally {
      wantsToEnable.value = false
    }
  }
}

async function save() {
  if (!form.merchantId) {
    ElMessage.warning('请填写商户号')
    return
  }
  if (privateKeyError.value) {
    ElMessage.error('私钥格式错误：' + privateKeyError.value)
    return
  }
  if (publicKeyError.value) {
    ElMessage.error('公钥格式错误：' + publicKeyError.value)
    return
  }

  // 危险变更 → 必须输入当前登录密码二次确认
  if (hasDangerousChange.value) {
    try {
      const res = await ElMessageBox({
        title: '⚠ 敏感操作二次确认',
        message: h('div', { style: 'line-height:1.7' }, [
          h('p', { style: 'margin:0 0 8px;color:#c45656' }, '本次保存包含敏感变更（凭据 / 环境 / 启用）。'),
          h('p', { style: 'margin:0 0 8px' }, '请输入当前登录密码以确认操作：'),
          h('input', {
            type: 'password',
            placeholder: '当前登录密码',
            class: 'confirm-pwd',
            style: 'width:100%;padding:8px 10px;border:1px solid #dcdfe6;border-radius:4px;outline:none',
            onInput: (e: any) => { confirmPwd.value = e.target.value }
          })
        ]),
        showCancelButton: true,
        confirmButtonText: '确认保存',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      })
      confirmPwdOk.value = !!confirmPwd.value
      void res
    } catch {
      confirmPwdOk.value = false
      ElMessage.info('已取消保存')
      return
    }
    if (!confirmPwd.value || confirmPwd.value.length < 4) {
      ElMessage.error('密码未填写或过短')
      return
    }
  }

  if (isProduction.value && form.enabled === 1) {
    try {
      await ElMessageBox.confirm(
        '即将保存为【生产环境】并启用，请确认所有凭据已准确配置。',
        '确认保存生产配置',
        { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '再检查一下' }
      )
    } catch {
      return
    }
  }

  saving.value = true
  try {
    const payload: any = { ...form }
    // 已配置但未修改 → 不带原文（让后端保留）
    if (apiKeyMasked.value && (form.apiKey === '' || form.apiKey === MASK)) payload.apiKey = ''
    if (privateKeyMasked.value && (form.privateKey === '' || form.privateKey === MASK)) payload.privateKey = ''
    if (publicKeyMasked.value && (form.publicKey === '' || form.publicKey === MASK)) payload.publicKey = ''
    if (confirmPwd.value) payload.confirmPassword = confirmPwd.value
    await sysApi.dcepSave(payload)
    // 前端即时更新审计信息（后端对接审计模块后可移除）
    try {
      const me = JSON.parse(localStorage.getItem('user') || '{}')
      form.updatedBy = me.realName || me.username || '当前用户'
    } catch { form.updatedBy = '当前用户' }
    form.updatedByIp = 'local'
    form.updatedAt = new Date().toISOString()
    ElMessage.success('已保存')
    confirmPwd.value = ''
    await load()
  } finally {
    saving.value = false
  }
}

async function toggle() {
  if (!form.id) return
  const newVal = form.enabled === 1 ? 0 : 1
  await sysApi.dcepToggle(form.id, newVal)
  ElMessage.success(newVal === 1 ? '已启用' : '已停用')
  load()
}

// 测试连接状态
const testing = ref(false)
const testResult = ref<null | {
  ok: boolean; stage?: string; message: string; elapsedMs: number
  gateway?: string; privateKeyBits?: number; publicKeyBits?: number; timestamp: string
}>(null)

// 实时指纹（前端计算）
const liveFp = ref<{ private?: string; public?: string }>({})

async function sha256Hex(text: string): Promise<string> {
  const buf = new TextEncoder().encode(text)
  const hash = await crypto.subtle.digest('SHA-256', buf)
  return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('')
}

async function refreshLiveFp() {
  // 计算输入框中私钥/公钥的实时 SHA-256 指纹前 12 位
  const pk = (form.privateKey || '').replaceAll(/\s+/g, '')
  const pub = (form.publicKey || '').replaceAll(/\s+/g, '')
  if (pk.length > 30 && pk.includes('-----BEGIN')) {
    liveFp.value.private = (await sha256Hex(pk)).slice(0, 12)
  } else {
    liveFp.value.private = ''
  }
  if (pub.length > 30 && pub.includes('-----BEGIN')) {
    liveFp.value.public = (await sha256Hex(pub)).slice(0, 12)
  } else {
    liveFp.value.public = ''
  }
}

// 监听 PEM 文本变化
let fpTimer: number | null = null
watch(() => [form.privateKey, form.publicKey], () => {
  if (fpTimer) clearTimeout(fpTimer)
  fpTimer = window.setTimeout(refreshLiveFp, 400)
})

// 撤销所有变更
function discardChanges() {
  Object.assign(form, JSON.parse(JSON.stringify(original.value)))
  apiKeyMasked.value = originalFlags.value.apiKey
  privateKeyMasked.value = originalFlags.value.privateKey
  publicKeyMasked.value = originalFlags.value.publicKey
  testResult.value = null
  showApiKey.value = false
  showPrivateKey.value = false
  showPublicKey.value = false
  clearRevealTimer()
  ElMessage.success('已撤销所有未保存变更')
}

// 撤销单个字段
function revertField(key: string) {
  const o = original.value
  switch (key) {
    case 'merchantId': form.merchantId = o.merchantId || ''; break
    case 'merchantName': form.merchantName = o.merchantName || ''; break
    case 'appId': form.appId = o.appId || ''; break
    case 'notifyUrl': form.notifyUrl = o.notifyUrl || ''; break
    case 'description': form.description = o.description || ''; break
    case 'environment': form.environment = o.environment || 'sandbox'; break
    case 'enabled': form.enabled = o.enabled ?? 0; break
    case 'apiKey':
      form.apiKey = ''
      if (originalFlags.value.apiKey) apiKeyMasked.value = true
      else apiKeyMasked.value = false
      showApiKey.value = false
      break
    case 'privateKey':
      form.privateKey = ''
      if (originalFlags.value.privateKey) privateKeyMasked.value = true
      else privateKeyMasked.value = false
      showPrivateKey.value = false
      clearRevealTimer()
      break
    case 'publicKey':
      form.publicKey = ''
      if (originalFlags.value.publicKey) publicKeyMasked.value = true
      else publicKeyMasked.value = false
      showPublicKey.value = false
      clearRevealTimer()
      break
  }
  ElMessage.success(`已撤销「${key}」字段`)
}

// 测试连接（8s 超时，避免慢响应挂死）
async function testConnection() {
  testing.value = true
  testResult.value = null
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), 8000)
  try {
    const override: any = { _signal: 'frontend' }
    if ((form.privateKey || '') !== '' && (form.privateKey || '') !== MASK) override.privateKey = form.privateKey
    if ((form.publicKey || '') !== '' && (form.publicKey || '') !== MASK) override.publicKey = form.publicKey
    if ((form.apiKey || '') !== '' && (form.apiKey || '') !== MASK) override.apiKey = form.apiKey
    override.environment = form.environment
    const r: any = await sysApi.dcepTest(Object.keys(override).length > 1 ? override : { environment: form.environment, _signal: 'frontend' })
    testResult.value = r.data
  } catch (e: any) {
    const isAbort = e?.name === 'AbortError' || e?.message?.includes('abort') || e?.message?.includes('超时')
    testResult.value = {
      ok: false,
      stage: isAbort ? 'timeout' : 'request_failed',
      message: isAbort ? '请求超时（>8s），DCEP 网关响应过慢' : '请求失败：' + (e?.message || e),
      elapsedMs: isAbort ? 8000 : 0,
      timestamp: new Date().toISOString()
    }
  } finally {
    window.clearTimeout(timeoutId)
    testing.value = false
  }
}

// API Key 强度（仅提示，不阻塞保存）
const apiKeyWarn = computed(() => {
  const k = form.apiKey || ''
  if (!k || k === MASK) return ''
  if (k.length < 16) return 'API Key 长度过短（建议 ≥32 位）'
  const hasUpper = /[A-Z]/.test(k)
  const hasLower = /[a-z]/.test(k)
  const hasDigit = /\d/.test(k)
  const kind = [hasUpper, hasLower, hasDigit].filter(Boolean).length
  if (kind < 2) return 'API Key 缺少大小写/数字组合'
  return ''
})

// PEM 头检测
const pemHeaderHint = computed(() => {
  const issues: string[] = []
  const pk = form.privateKey || ''
  const pub = form.publicKey || ''
  const pkIsMasked = privateKeyMasked.value && pk === MASK
  const pubIsMasked = publicKeyMasked.value && pub === MASK
  if (!pkIsMasked && pk && !pk.includes('-----BEGIN PRIVATE KEY-----') && !pk.includes('-----BEGIN RSA PRIVATE KEY-----')) {
    issues.push('私钥未以 BEGIN PRIVATE KEY 开头')
  }
  if (!pubIsMasked && pub && !pub.includes('-----BEGIN PUBLIC KEY-----')) {
    issues.push('公钥未以 BEGIN PUBLIC KEY 开头')
  }
  return issues
})

// 离开页面前提示（路由切换）
onBeforeRouteLeave(() => {
  if (!changes.value.length) return true
  return ElMessageBox.confirm(
    `您有 ${changes.value.length} 项未保存的变更，确定要离开吗？`,
    '未保存的变更',
    { type: 'warning', confirmButtonText: '离开（放弃变更）', cancelButtonText: '留下' }
  ).then(() => true).catch(() => false)
})

// 离开页面前提示（浏览器关闭/刷新）—— 仅在有未保存变更时挂载监听器
// 避免 Chrome 的 [Intervention] "frame never had a user gesture since its load" 警告
function beforeUnload(e: BeforeUnloadEvent) {
  if (changes.value.length) {
    e.preventDefault()
    // 现代 Chrome 已忽略 returnValue 的内容（统一显示内置文案），但保留字段以兼容旧版
    e.returnValue = ''
  }
}

onMounted(() => {
  load()
  refreshLiveFp()
  // 初始不挂载 beforeunload，等到 watch 检测到有变更时再动态挂载
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  clearRevealTimer()
  if (fpTimer) { clearTimeout(fpTimer); fpTimer = null }
  window.removeEventListener('beforeunload', beforeUnload)
  window.removeEventListener('keydown', onKeydown)
})

// 全局快捷键：Ctrl+S / Cmd+S 保存
function onKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    if (saving.value || loading.value) return
    if (!changes.value.length) {
      ElMessage.info('当前没有需要保存的变更')
      return
    }
    save()
  }
  // Esc 在临时显示原文时立即隐藏
  if (e.key === 'Escape' && (showPrivateKey.value || showPublicKey.value)) {
    showPrivateKey.value = false
    showPublicKey.value = false
    clearRevealTimer()
  }
}

// 动态挂载/卸载 beforeunload 监听器 —— 仅在存在未保存变更时挂载
watch(changes, (list) => {
  if (list.length > 0) {
    window.addEventListener('beforeunload', beforeUnload)
  } else {
    window.removeEventListener('beforeunload', beforeUnload)
  }
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px }
.card-header { display: flex; align-items: center; justify-content: space-between }
.header-right { display: flex; align-items: center; gap: 8px }
.title { font-weight: 600; font-size: 15px }
.env-tag { font-weight: 600 }

.hint {
  padding: 10px 14px;
  background: #fdf6ec;
  color: #b88230;
  border-radius: 4px;
  margin-bottom: 18px;
  font-size: 13px;
}

.env-banner {
  padding: 10px 16px;
  border-radius: 4px;
  margin-bottom: 16px;
  font-size: 13px;
  border-left: 4px solid;
}
.env-banner.warning { background: #fdf6ec; color: #b88230; border-color: #e6a23c }
.env-banner.danger { background: #fef0f0; color: #c45656; border-color: #f56c6c }

.changes-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  margin-bottom: 12px;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  color: #3a73b8;
  border-radius: 999px;
  font-size: 12px;
}

.env-hint { margin-left: 12px; font-size: 12px }
.env-hint.warning { color: #b88230 }
.env-hint.danger { color: #c45656 }
.env-lock {
  margin-left: 8px;
  padding: 1px 8px;
  font-size: 11px;
  color: #b88230;
  background: #fdf6ec;
  border: 1px solid #e6a23c;
  border-radius: 3px;
  cursor: help;
}

.cred-row { display: flex; align-items: center; gap: 8px; width: 100% }
.fp-tag { white-space: nowrap }
.fp-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 1px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 999px;
  background: #fafafa;
  font-size: 12px;
}
.fp-label { color: #909399 }
.fp-value { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; color: #606266 }
.cred-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.4 }
.cred-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  flex-wrap: wrap;
}

.updated-at {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 4px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
}
.updated-label { color: #909399 }
.updated-value { color: #606266; font-family: ui-monospace, SFMono-Regular, Menlo, monospace }
.updated-link { cursor: help; border-bottom: 1px dashed #dcdfe6; padding-bottom: 1px }

.info-card { margin-top: 0 }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px }
.ok { color: #67c23a; font-weight: 500 }
.warn { color: #e6a23c; font-weight: 500 }

.diff-card :deep(.el-card__header) { padding: 12px 18px }
.val-from { color: #909399; text-decoration: line-through; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px }
.val-to { color: #67c23a; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px }
.val-from-danger { color: #c45656; text-decoration: line-through; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px }
.val-to-danger { color: #f56c6c; font-weight: 600; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px }
.diff-warning {
  margin-top: 12px;
  padding: 10px 14px;
  background: #fef0f0;
  color: #c45656;
  border-radius: 4px;
  border-left: 3px solid #f56c6c;
  font-size: 13px;
}

.warn-hint { color: #b88230 !important }

.reveal-bar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #b88230;
}
.reveal-label { white-space: nowrap }
.reveal-progress {
  position: relative;
  display: inline-block;
  width: 60px;
  height: 4px;
  background: #f4f4f5;
  border-radius: 2px;
  overflow: hidden;
}
.reveal-progress-fill {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  background: linear-gradient(90deg, #e6a23c, #f56c6c);
  transition: width 0.9s linear;
}

.test-result {
  margin: 14px 0 4px;
  padding: 12px 16px;
  border-radius: 4px;
  border-left: 4px solid;
  font-size: 13px;
}
.test-result.ok { background: #f0f9eb; color: #5a8c3c; border-color: #67c23a }
.test-result.fail { background: #fef0f0; color: #c45656; border-color: #f56c6c }
.test-row { display: flex; align-items: center; gap: 10px; line-height: 1.7 }
.test-icon { font-size: 18px; font-weight: 700 }
.test-msg { font-weight: 500 }
.test-stage { color: #909399; font-family: ui-monospace, monospace; font-size: 12px }
.test-elapsed { color: #909399; font-size: 12px; margin-left: auto; margin-right: 12px }
.test-detail { color: #909399; font-size: 12px; padding-left: 28px }
.pem-hint {
  margin: 8px 0 4px;
  padding: 6px 12px;
  background: #f4f4f5;
  color: #606266;
  border-radius: 4px;
  font-size: 12px;
  border-left: 2px solid #909399;
}
</style>
