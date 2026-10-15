<template>
  <div class="record-form">
    <el-form :model="form" label-width="100px" size="small" label-position="top">
      <el-form-item v-for="f in fields" :key="f.key" :label="f.label" :required="f.required">
        <el-input v-if="f.type !== 'number'" v-model="form[f.key]"
          :placeholder="f.placeholder || `请输入${f.label}`" />
        <el-input-number v-else v-model="form[f.key]" :min="0" style="width: 100%" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="onSubmit" style="width: 100%">
          立即上链
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { blockchainApi } from '@/api/blockchain'

const props = defineProps<{
  type: 'PRODUCT' | 'DRUG' | 'DEVICE' | 'ORDER' | 'CONSENT'
  fields: Array<{ key: string; label: string; type?: string; required?: boolean; placeholder?: string }>
}>()

const emit = defineEmits<{ (e: 'saved', record: any): void }>()

const form = reactive<Record<string, any>>({})
const submitting = ref(false)

async function onSubmit() {
  for (const f of props.fields) {
    if (f.required && !form[f.key]) {
      ElMessage.warning(`请填写${f.label}`)
      return
    }
  }
  submitting.value = true
  try {
    const apiMap: any = {
      PRODUCT: blockchainApi.recordProduct,
      DRUG: blockchainApi.recordDrug,
      DEVICE: blockchainApi.recordDevice,
      ORDER: blockchainApi.recordOrder,
      CONSENT: blockchainApi.recordConsent
    }
    const res: any = await apiMap[props.type](form)
    const rec = (res as any)?.data || res
    emit('saved', rec)
    Object.keys(form).forEach(k => delete form[k])
  } catch (e: any) {
    ElMessage.error(`上链失败: ${e?.message || '未知错误'}`)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.record-form { padding: 8px 0; }
</style>
