<template>
  <div class="h5-wrap">
    <header class="h5-header">
      <div class="h5-title">知情同意签署</div>
      <div class="h5-sub">{{ loadedInfo?.templateName || '加载中...' }}</div>
    </header>

    <main class="h5-main" v-if="pending">
      <el-skeleton :rows="6" animated />
    </main>

    <main class="h5-main" v-else-if="errorMsg">
      <el-result icon="error" :title="errorMsg" sub-title="链接可能已过期或被使用，请联系门店重新生成。">
      </el-result>
    </main>

    <main class="h5-main" v-else-if="signSuccess">
      <el-result icon="success" title="签署完成" sub-title="已为您留存电子档案，可在门店端查询。">
        <template #extra>
          <el-alert type="success" :closable="false" show-icon>
            <template #title>签署编号</template>
            <div class="sig-id">{{ signSuccess.signatureId }}</div>
          </el-alert>
        </template>
      </el-result>
    </main>

    <main class="h5-main" v-else-if="loadedInfo">
      <el-card shadow="never" class="info-card">
        <div class="meta-row">
          <el-tag size="small" type="info">{{ loadedInfo.templateCode }}</el-tag>
          <el-tag size="small" effect="plain">v{{ loadedInfo.templateVersion }}</el-tag>
          <span class="expire-tip">链接有效期至 {{ loadedInfo.expireAt }}</span>
        </div>
        <h3 class="tpl-title">{{ loadedInfo.templateName }}</h3>
        <pre class="tpl-body">{{ loadedInfo.contentMd }}</pre>

        <el-alert v-if="loadedInfo.requiredItems" type="warning" :closable="false" show-icon style="margin: 16px 0"
          title="必勾选条款">
          <template #default>
            <el-checkbox-group v-model="form.checkedItems">
              <el-checkbox v-for="(item, idx) in parseRequiredItems(loadedInfo.requiredItems)" :key="idx" :value="String(idx)">
                {{ item }}
              </el-checkbox>
            </el-checkbox-group>
          </template>
        </el-alert>

        <el-form label-position="top" size="large">
          <el-form-item label="签署人姓名" required>
            <el-input v-model="form.signerName" :placeholder="loadedInfo.memberName || '请输入真实姓名'" />
          </el-form-item>
          <el-form-item label="身份证号（可选）">
            <el-input v-model="form.signerIdNo" placeholder="用于合规留档" />
          </el-form-item>
          <el-form-item label="签署关系">
            <el-radio-group v-model="form.signerRelation">
              <el-radio value="SELF">本人签署</el-radio>
              <el-radio value="PARENT">监护人签署</el-radio>
              <el-radio value="AGENT">代理人签署</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注（可选）">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="如有特殊说明请填写" />
          </el-form-item>
        </el-form>

        <el-button type="primary" size="large" class="submit-btn" :loading="submitting" @click="submit">
          我已阅读并同意签署
        </el-button>
        <div class="legal-tip">
          {{ loadedInfo.legalBasis || '依据《医师法》《医疗机构管理条例》等法规' }}
        </div>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const route = useRoute()
const token = route.params.token
const pending = ref(true)
const errorMsg = ref('')
const loadedInfo = ref(null)
const signSuccess = ref(null)
const submitting = ref(false)

const form = reactive({
  signerName: '',
  signerIdNo: '',
  signerRelation: 'SELF',
  checkedItems: [],
  remark: ''
})

function parseRequiredItems(items) {
  if (!items) return []
  return items.split(/[,\n;]/).map(s => s.trim()).filter(Boolean)
}

onMounted(async () => {
  try {
    const { data } = await http.get(`/mh/consent/sign-link/${token}`)
    loadedInfo.value = data
    if (data.memberName) form.signerName = data.memberName
  } catch (e) {
    errorMsg.value = e?.message || '链接不可用'
  } finally {
    pending.value = false
  }
})

async function submit() {
  if (!form.signerName?.trim()) return ElMessage.warning('请输入签署人姓名')
  if (loadedInfo.value?.requiredItems && !form.checkedItems.length) {
    return ElMessage.warning('请勾选必读条款')
  }
  submitting.value = true
  try {
    const { data } = await http.post(`/mh/consent/sign-link/${token}/submit`, {
      ...form,
      checkedItems: form.checkedItems.join(','),
      signIp: '',
      signDevice: navigator.userAgent
    })
    signSuccess.value = data
    ElMessage.success('签署成功')
  } catch (e) {
    ElMessage.error(e?.message || '签署失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.h5-wrap { min-height: 100vh; background: linear-gradient(180deg, #f5f7fa 0%, #fff 100%); padding-bottom: 24px; }
.h5-header {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: #fff;
  padding: 32px 20px 24px;
  text-align: center;
}
.h5-title { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
.h5-sub { font-size: 14px; opacity: 0.9; }
.h5-main { padding: 16px; max-width: 640px; margin: 0 auto; }
.info-card { border-radius: 12px; }
.meta-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.expire-tip { font-size: 12px; color: #909399; margin-left: auto; }
.tpl-title { font-size: 18px; margin: 0 0 12px; color: #303133; }
.tpl-body {
  white-space: pre-wrap; word-break: break-all;
  background: #f5f7fa; padding: 12px;
  border-radius: 8px; font-size: 14px; line-height: 1.7;
  color: #303133;
  max-height: 360px; overflow: auto;
}
.submit-btn { width: 100%; margin-top: 12px; font-size: 16px; }
.legal-tip { font-size: 12px; color: #909399; text-align: center; margin-top: 8px; }
.sig-id { font-family: ui-monospace, monospace; font-size: 13px; margin-top: 4px; }
</style>
