<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">数字人民币 · 商户资质材料</span>
          <div class="header-right">
            <el-tag :type="auditTagType" size="large" effect="dark">{{ auditLabel }}</el-tag>
          </div>
        </div>
      </template>

      <div v-if="form.auditStatus === 1" class="env-banner warning">
        ⏳ 资质材料已提交，正在审核中。审核通过后方可正式开通生产环境支付。
      </div>
      <div v-else-if="form.auditStatus === 3" class="env-banner danger">
        ❌ 审核未通过：{{ form.auditRemark || '请联系管理员' }}
      </div>
      <div v-else-if="form.auditStatus === 2" class="env-banner" style="background:#f0f9eb;color:#67c23a;border-color:#67c23a">
        ✅ 资质审核已通过（{{ form.auditAt }}）。
      </div>

      <div v-if="hasUnsaved" class="changes-pill">
        <span>📝 您有 <b>{{ unsavedCount }}</b> 项未保存的变更</span>
        <el-link type="primary" underline="never" @click="discard" style="margin-left:8px">撤销</el-link>
      </div>

      <el-form :model="form" label-width="140px" v-loading="loading" class="qual-form">
        <!-- 法人信息 -->
        <el-divider content-position="left">法人信息</el-divider>
        <el-form-item label="法人姓名">
          <el-input v-model="form.legalPerson" placeholder="如：张三" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="法人身份证号">
          <el-input v-model="form.legalIdNo" placeholder="18 位身份证号" maxlength="32" />
          <div class="cred-hint">入库前自动掩码（前4后4，中间 10 个 *），仅作展示</div>
        </el-form-item>
        <el-form-item label="身份证正面">
          <MaterialUploader
            material-type="legal_id_front"
            :qualification-id="form.id"
            :existing="attachmentByType('legal_id_front')"
            :disabled="isAuditing"
            v-model="form.legalIdFront"
            @uploaded="onUploaded"
            @removed="onRemoved"
          />
        </el-form-item>
        <el-form-item label="身份证反面">
          <MaterialUploader
            material-type="legal_id_back"
            :qualification-id="form.id"
            :existing="attachmentByType('legal_id_back')"
            :disabled="isAuditing"
            v-model="form.legalIdBack"
            @uploaded="onUploaded"
            @removed="onRemoved"
          />
        </el-form-item>

        <!-- 营业执照 -->
        <el-divider content-position="left">营业执照</el-divider>
        <el-form-item label="营业执照注册号">
          <el-input v-model="form.businessLicenseNo" placeholder="如：91110000XXXXXXXXXX" maxlength="64" />
        </el-form-item>
        <el-form-item label="营业执照图片">
          <MaterialUploader
            material-type="business_license"
            :qualification-id="form.id"
            :existing="attachmentByType('business_license')"
            :disabled="isAuditing"
            v-model="form.businessLicenseImg"
            @uploaded="onUploaded"
            @removed="onRemoved"
          />
        </el-form-item>
        <el-form-item label="组织机构代码">
          <el-input v-model="form.orgCode" placeholder="(可选)" maxlength="64" />
        </el-form-item>
        <el-form-item label="税务登记号">
          <el-input v-model="form.taxNo" placeholder="(可选)" maxlength="64" />
        </el-form-item>

        <!-- 银行账户 -->
        <el-divider content-position="left">银行账户</el-divider>
        <el-form-item label="账户名">
          <el-input v-model="form.bankAccountName" placeholder="如：张三" maxlength="64" />
        </el-form-item>
        <el-form-item label="银行账号">
          <el-input v-model="form.bankAccountNo" placeholder="对公账户" maxlength="64" />
          <div class="cred-hint">入库前自动掩码（4 位 + 4 位 + 4 位 + 末 4 位）</div>
        </el-form-item>
        <el-form-item label="开户行">
          <el-input v-model="form.bankName" placeholder="如：中国工商银行" maxlength="64" />
        </el-form-item>
        <el-form-item label="开户支行">
          <el-input v-model="form.bankBranch" placeholder="如：北京海淀分行" maxlength="128" />
        </el-form-item>

        <!-- 联系方式 -->
        <el-divider content-position="left">联系方式</el-divider>
        <el-form-item label="联系人">
          <el-input v-model="form.contactPerson" placeholder="如：李四" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="11 位手机号" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.contactEmail" placeholder="如：biz@example.com" maxlength="128" />
        </el-form-item>
        <el-form-item label="经营地址">
          <el-input v-model="form.address" placeholder="如：北京市朝阳区..." maxlength="256" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="(可选)" maxlength="512" show-word-limit />
        </el-form-item>

        <el-divider />

        <div class="updated-at">
          <span class="updated-label">上次更新于</span>
          <span class="updated-value">{{ formatTime(form.updatedAt) || '—' }}</span>
        </div>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save" :disabled="isAuditing">保存资质材料</el-button>
          <el-button @click="load" :disabled="saving">重新加载</el-button>
          <el-button v-if="hasUnsaved" plain @click="discard" :disabled="saving">↩ 撤销变更</el-button>
          <el-button
            type="success"
            :disabled="!canSubmit || saving"
            :loading="submitting"
            @click="submit"
          >📤 提交审核</el-button>
        </el-form-item>

        <div v-if="form.auditStatus === 3" class="env-banner danger" style="margin-top:8px">
          驳回原因：<b>{{ form.auditRemark }}</b>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sysApi, type DcepQualification } from '@/api/sys'
import MaterialUploader from './components/MaterialUploader.vue'

const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)

const form = reactive<DcepQualification>({
  id: undefined,
  configId: undefined,
  legalPerson: '',
  legalIdNo: '',
  legalIdFront: '',
  legalIdBack: '',
  businessLicenseNo: '',
  businessLicenseImg: '',
  orgCode: '',
  taxNo: '',
  bankAccountName: '',
  bankAccountNo: '',
  bankName: '',
  bankBranch: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  address: '',
  remark: '',
  auditStatus: 0,
  attachments: []
})

const original = ref<DcepQualification>(JSON.parse(JSON.stringify(form)))

async function load() {
  loading.value = true
  try {
    const r = await sysApi.dcepQualCurrent()
    const d = (r as any).data || {}
    Object.assign(form, d)
    if (!form.attachments) form.attachments = []
    // 同步原值用于变更检测
    original.value = JSON.parse(JSON.stringify({ ...form }))
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const r = await sysApi.dcepQualSave({ ...form })
    const d = (r as any).data
    if (d && d.id) form.id = d.id
    ElMessage.success('保存成功')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function discard() {
  Object.assign(form, JSON.parse(JSON.stringify(original.value)))
  ElMessage.info('已撤销未保存的变更')
}

async function submit() {
  if (hasUnsaved.value) {
    ElMessage.warning('请先保存当前修改，再提交审核')
    return
  }
  try {
    await ElMessageBox.confirm(
      '提交后状态将变为「审核中」，审核期间不可修改。是否继续？',
      '提交审核',
      { type: 'warning', confirmButtonText: '提交', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await sysApi.dcepQualSubmit()
    ElMessage.success('已提交审核')
    await load()
  } finally {
    submitting.value = false
  }
}

// 变更检测
const watchedKeys: (keyof DcepQualification)[] = [
  'legalPerson','legalIdNo','legalIdFront','legalIdBack',
  'businessLicenseNo','businessLicenseImg','orgCode','taxNo',
  'bankAccountName','bankAccountNo','bankName','bankBranch',
  'contactPerson','contactPhone','contactEmail','address','remark'
]
const unsavedCount = ref(0)
const hasUnsaved = computed(() => unsavedCount.value > 0)
function recomputeChanges() {
  let n = 0
  for (const k of watchedKeys) {
    if ((form[k] || '') !== (original.value[k] || '')) n++
  }
  unsavedCount.value = n
}
watch(form, () => recomputeChanges(), { deep: true })

// 离开守卫
function beforeUnload(e: BeforeUnloadEvent) {
  if (hasUnsaved.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
onMounted(async () => {
  await load()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))

// 状态映射
const auditLabel = computed(() => ['待提交','审核中','已通过','已驳回'][form.auditStatus || 0])
const auditTagType = computed(() => ['info','warning','success','danger'][form.auditStatus || 0])
const isAuditing = computed(() => form.auditStatus === 1)
const canSubmit = computed(() => !!form.id && (form.auditStatus === 0 || form.auditStatus === 3))

// 工具
function attachmentByType(type: string) {
  return (form.attachments || []).find((a: any) => a.materialType === type)
}

function onUploaded() {
  load()
}
function onRemoved() {
  load()
}

function formatTime(s?: string) {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.page { padding: 16px; max-width: 1100px; margin: 0 auto }
.card-header { display: flex; align-items: center; justify-content: space-between }
.title { font-size: 16px; font-weight: 600 }
.header-right { display: flex; align-items: center; gap: 8px }
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

.cred-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.4 }

.qual-form { padding: 0 8px }
</style>