<template>
  <div class="plugin-page">
    <el-tabs v-model="tab">
      <el-tab-pane label="插件市场" name="market">
        <el-button type="primary" @click="pubDialog = true">发布插件</el-button>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col v-for="p in markets" :key="p.id" :span="6" style="margin-bottom: 16px;">
            <el-card>
              <h3>{{ p.pluginName }}</h3>
              <p><el-tag size="small">{{ p.category }}</el-tag> <el-tag size="small" type="info">v{{ p.version }}</el-tag></p>
              <p>{{ p.shortDesc }}</p>
              <p>⭐ {{ p.rating }} | 安装 {{ p.installCount }}</p>
              <el-button size="small" type="primary" @click="install(p.id)">安装</el-button>
              <el-button size="small" @click="review(p.id)">评价</el-button>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
      <el-tab-pane label="已安装" name="install">
        <el-table :data="installs" style="width:100%;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="pluginCode" label="编码" width="150"/>
          <el-table-column prop="version" label="版本" width="100"/>
          <el-table-column prop="installedAt" label="安装时间" width="180"/>
          <el-table-column prop="installStatus" label="状态" width="100"/>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="uninstall(row.id)">卸载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="运行时" name="runtime">
        <el-row :gutter="12" style="margin-bottom: 16px;">
          <el-col :span="6"><el-statistic title="总插件数" :value="dashboard.totalPlugins || 0" /></el-col>
          <el-col :span="6"><el-statistic title="运行中" :value="dashboard.startedPlugins || 0" /></el-col>
          <el-col :span="6"><el-statistic title="已停用" :value="dashboard.stoppedPlugins || 0" /></el-col>
          <el-col :span="6"><el-statistic title="失败" :value="dashboard.failedPlugins || 0" /></el-col>
        </el-row>
        <el-table :data="runtimeList" style="width:100%;">
          <el-table-column prop="pluginCode" label="编码" width="180"/>
          <el-table-column prop="displayName" label="名称" width="180"/>
          <el-table-column prop="version" label="版本" width="100"/>
          <el-table-column prop="state" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="stateTagType(row.state)">{{ row.state }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastError" label="最近错误" min-width="200"/>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button v-if="row.state === 'NOT_FOUND' || row.state === 'UNLOADED' || row.state === 'STOPPED'"
                size="small" type="primary" @click="doLoad(row.pluginCode)">加载</el-button>
              <el-button v-if="row.state === 'CREATED' || row.state === 'STOPPED'"
                size="small" type="success" @click="doEnable(row.pluginCode)">启用</el-button>
              <el-button v-if="row.state === 'STARTED'"
                size="small" type="warning" @click="doDisable(row.pluginCode)">停用</el-button>
              <el-button v-if="row.state === 'CREATED' || row.state === 'STOPPED' || row.state === 'DISABLED'"
                size="small" type="danger" @click="doUnload(row.pluginCode)">卸载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="pubDialog" title="发布插件" width="500px">
      <el-form :model="pubForm" label-width="120px">
        <el-form-item label="编码"><el-input v-model="pubForm.pluginCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="pubForm.pluginName" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="pubForm.category">
            <el-option label="MARKETING" value="MARKETING"/>
            <el-option label="PAYMENT" value="PAYMENT"/>
            <el-option label="AI" value="AI"/>
          </el-select>
        </el-form-item>
        <el-form-item label="版本"><el-input v-model="pubForm.version" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="pubForm.shortDesc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pubDialog = false">取消</el-button>
        <el-button type="primary" @click="doPub">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewDialog" title="评价插件" width="400px">
      <el-form :model="reviewForm" label-width="100px">
        <el-form-item label="评分"><el-rate v-model="reviewForm.rating" /></el-form-item>
        <el-form-item label="评价"><el-input v-model="reviewForm.comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" @click="doReview">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pluginApi } from '@/api/plugin'

const tab = ref('market')
const markets = ref<any[]>([])
const installs = ref<any[]>([])
const runtimeList = ref<any[]>([])
const dashboard = ref<any>({})
const pubDialog = ref(false)
const reviewDialog = ref(false)
const pubForm = ref<any>({ pluginCode: 'P' + Date.now(), pluginName: '示例插件', category: 'MARKETING', version: '1.0.0', shortDesc: '功能简介' })
const reviewForm = ref({ pluginId: 0, rating: 5, comment: '' })

function stateTagType(state: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  if (state === 'STARTED') return 'success'
  if (state === 'CREATED') return 'info'
  if (state === 'FAILED') return 'danger'
  if (state === 'DISABLED') return 'warning'
  return ''
}

async function loadMarket() { const { data } = await pluginApi.listMarket(); markets.value = data || [] }
async function loadInstalls() { const { data } = await pluginApi.listInstalls(); installs.value = data || [] }
async function loadRuntime() { const { data } = await pluginApi.listRuntime(); runtimeList.value = data || [] }
async function loadDashboard() { const { data } = await pluginApi.dashboard(); dashboard.value = data || {} }

async function doLoad(code: string) {
  await pluginApi.loadPlugin(code)
  ElMessage.success('加载成功')
  loadRuntime(); loadDashboard()
}
async function doEnable(code: string) {
  await pluginApi.enablePlugin(code)
  ElMessage.success('已启用')
  loadRuntime(); loadDashboard()
}
async function doDisable(code: string) {
  await pluginApi.disablePlugin(code)
  ElMessage.success('已停用')
  loadRuntime(); loadDashboard()
}
async function doUnload(code: string) {
  await pluginApi.unloadPlugin(code)
  ElMessage.success('已卸载')
  loadRuntime(); loadDashboard()
}
async function doPub() {
  await pluginApi.publish(pubForm.value)
  ElMessage.success('已发布')
  pubDialog.value = false
  loadMarket()
}
async function install(id: number) {
  await pluginApi.install({ pluginId: id })
  ElMessage.success('已安装')
  loadInstalls()
}
async function uninstall(id: number) {
  await pluginApi.uninstall(id)
  ElMessage.success('已卸载')
  loadInstalls()
}
async function review(id: number) {
  reviewForm.value.pluginId = id
  reviewDialog.value = true
}
async function doReview() {
  await pluginApi.review(reviewForm.value)
  ElMessage.success('已评价')
  reviewDialog.value = false
}

onMounted(() => { loadMarket(); loadInstalls() })

watch(tab, (val) => { if (val === 'runtime') { loadRuntime(); loadDashboard() } })
</script>
