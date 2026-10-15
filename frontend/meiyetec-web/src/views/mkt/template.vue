<template>
  <div class="template-page">
    <el-card class="search-bar">
      <el-select v-model="filter.channelType" placeholder="渠道" clearable style="width: 140px" @change="loadData">
        <el-option label="短信" value="SMS" />
        <el-option label="小程序" value="MINI_PROGRAM" />
        <el-option label="公众号" value="MP" />
      </el-select>
      <el-select v-model="filter.scene" placeholder="场景" clearable style="width: 140px" @change="loadData">
        <el-option label="生日" value="BIRTHDAY" />
        <el-option label="到期" value="EXPIRE" />
        <el-option label="评价" value="EVALUATION" />
        <el-option label="营销" value="MARKETING" />
      </el-select>
      <el-button type="primary" @click="loadData">刷新</el-button>
      <el-button type="primary" @click="openCreate">+ 新增模板</el-button>
    </el-card>

    <el-card class="table-card">
      <el-table :data="rows" border stripe>
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.channelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="templateCode" label="编码" width="140" />
        <el-table-column prop="templateName" label="名称" min-width="200" />
        <el-table-column prop="scene" label="场景" width="120" />
        <el-table-column prop="externalTemplateId" label="外部模板ID" width="160" />
        <el-table-column prop="subject" label="主题" width="160" />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="variables" label="变量" width="160" />
        <el-table-column label="审核" width="100">
          <template #default="{ row }">
            <el-tag :type="auditTag(row.auditStatus)" size="small">{{ auditLabel(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openAudit(row, 2)" :disabled="row.auditStatus === 2">通过</el-button>
            <el-button link size="small" type="danger" @click="openAudit(row, 3)" :disabled="row.auditStatus === 3">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" title="新增模板" width="700px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="渠道" required>
              <el-select v-model="form.channelType" style="width: 100%">
                <el-option label="短信" value="SMS" />
                <el-option label="小程序订阅消息" value="MINI_PROGRAM" />
                <el-option label="公众号" value="MP" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="场景" required>
              <el-select v-model="form.scene" style="width: 100%">
                <el-option label="生日" value="BIRTHDAY" />
                <el-option label="到期" value="EXPIRE" />
                <el-option label="评价" value="EVALUATION" />
                <el-option label="营销" value="MARKETING" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="编码" required><el-input v-model="form.templateCode" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" required><el-input v-model="form.templateName" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="主题"><el-input v-model="form.subject" /></el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="支持变量 ${name}, ${amount}, ${date}" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="外部模板ID"><el-input v-model="form.externalTemplateId" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="跳转类型">
              <el-select v-model="form.linkType" style="width: 100%">
                <el-option label="小程序" value="MINI" />
                <el-option label="小程序页" value="MINI_PAGE" />
                <el-option label="H5" value="H5" />
                <el-option label="不跳转" value="IMAGE_NOPAGE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="跳转链接"><el-input v-model="form.linkUrl" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const filter = reactive({ channelType: undefined as string | undefined, scene: undefined as string | undefined })
const rows = ref<any[]>([])

async function loadData() {
  const r = await http.get('/mkt/template/list', { params: filter })
  rows.value = r.data || []
}

function auditLabel(s: number) {
  return ({ 1: '待审核', 2: '已审核', 3: '已驳回' }[s] || '—')
}
function auditTag(s: number) {
  return ({ 1: 'warning', 2: 'success', 3: 'danger' } as any)[s] || 'info'
}

const dialog = reactive({ visible: false })
const form = reactive({
  channelType: 'SMS', scene: 'BIRTHDAY',
  templateCode: '', templateName: '', subject: '', content: '',
  externalTemplateId: '', linkType: 'MINI', linkUrl: ''
})

function openCreate() { dialog.visible = true }
function resetDialog() {
  Object.assign(form, { channelType: 'SMS', scene: 'BIRTHDAY', templateCode: '', templateName: '', subject: '', content: '', externalTemplateId: '', linkType: 'MINI', linkUrl: '' })
}

async function save() {
  if (!form.templateCode || !form.content) return ElMessage.warning('编码 / 内容 必填')
  await http.post('/mkt/template', form)
  ElMessage.success('已创建')
  dialog.visible = false
  loadData()
}

async function openAudit(row: any, status: number) {
  await http.put(`/mkt/template/${row.id}/audit`, { auditStatus: status, auditRemark: status === 2 ? '审核通过' : '驳回' })
  ElMessage.success(status === 2 ? '已通过' : '已驳回')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-select { margin-right: 8px; }
</style>
