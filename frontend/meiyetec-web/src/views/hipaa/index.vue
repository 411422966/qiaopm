<template>
  <div class="hipaa-page">
    <el-row :gutter="16" class="mb16">
      <el-col :span="8"><el-card><div class="stat"><span>加密密钥</span><strong>{{ keys.length }}</strong></div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat"><span>PHI 访问日志</span><strong>{{ phi.length }}</strong></div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat"><span>需轮换</span><strong>{{ expiredKeys.length }}</strong></div></el-card></el-col>
    </el-row>
    <el-tabs v-model="tab">
      <el-tab-pane label="加密密钥" name="key">
        <el-button type="primary" @click="genDialog = true">生成密钥</el-button>
        <el-table :data="keys" style="width:100%; margin-top: 12px;">
          <el-table-column prop="id" label="ID" width="60"/>
          <el-table-column prop="keyAlias" label="别名"/>
          <el-table-column prop="algorithm" label="算法" width="100"/>
          <el-table-column prop="keyVersion" label="版本" width="80"/>
          <el-table-column prop="keyStatus" label="状态" width="100"/>
          <el-table-column prop="nextRotateAt" label="下次轮换" width="180"/>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="PHI 访问" name="phi">
        <el-table :data="phi" style="width:100%;">
          <el-table-column prop="memberId" label="会员" width="80"/>
          <el-table-column prop="fieldName" label="字段"/>
          <el-table-column prop="accessType" label="访问类型" width="100"/>
          <el-table-column prop="actorName" label="操作者" width="120"/>
          <el-table-column prop="actorRole" label="角色" width="100"/>
          <el-table-column prop="purpose" label="目的"/>
          <el-table-column label="破玻" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isBreakGlass === 1" type="danger">是</el-tag>
              <el-tag v-else type="info">否</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tabs>
        </el-tabs>
    </el-tabs>

    <el-dialog v-model="genDialog" title="生成加密密钥" width="400px">
      <el-form :model="genForm" label-width="100px">
        <el-form-item label="别名"><el-input v-model="genForm.alias" /></el-form-item>
        <el-form-item label="算法">
          <el-select v-model="genForm.algorithm">
            <el-option label="AES" value="AES"/>
            <el-option label="RSA" value="RSA"/>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genDialog = false">取消</el-button>
        <el-button type="primary" @click="doGen">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { hipaaApi } from '@/api/hipaa'

const tab = ref('key')
const keys = ref<any[]>([])
const phi = ref<any[]>([])
const genDialog = ref(false)
const genForm = ref({ alias: '', algorithm: 'AES' })
const expiredKeys = computed(() => keys.value.filter(k => k.nextRotateAt && new Date(k.nextRotateAt) < new Date()))

async function loadKeys() { const { data } = await hipaaApi.listKeys(); keys.value = data || [] }
async function loadPhi() { const { data } = await hipaaApi.listPhiAccess(); phi.value = data || [] }
async function doGen() {
  await hipaaApi.generateKey(genForm.value)
  ElMessage.success('已生成')
  genDialog.value = false
  loadKeys()
}

onMounted(() => { loadKeys(); loadPhi() })
</script>

<style scoped>
.stat { display:flex; justify-content:space-between; align-items:center; }
.mb16 { margin-bottom:16px; }
</style>
