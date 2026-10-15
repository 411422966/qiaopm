<template>
  <div class="compute-tab">
    <el-form :inline="true" class="compute-form">
      <el-form-item label="选择会员">
        <el-select
          v-model="memberId"
          filterable
          clearable
          remote
          :remote-method="onRemoteSearch"
          :loading="isLoadingList"
          placeholder="手机号 / 姓名 / 编号"
          style="width: 280px"
          @change="reset"
        >
          <el-option
            v-for="m in filteredMembers"
            :key="m.id"
            :label="m.name ? `${m.name}（${m.phone ?? m.memberNo ?? m.id}）` : `会员 ${m.id}`"
            :value="m.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :loading="isComputing"
          :disabled="!memberId"
          @click="compute"
        >
          计算 RFM
        </el-button>
      </el-form-item>
    </el-form>

    <ResultPanel :result="result" :error="error" :member-tags="memberTags" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import ResultPanel from './ResultPanel.vue'
import { useRfmCompute } from './useRfmCompute'

const {
  memberKeyword,
  filteredMembers,
  memberId,
  result,
  memberTags,
  isLoadingList,
  isComputing,
  error,
  loadMembers,
  compute,
  reset
} = useRfmCompute()

function onRemoteSearch(kw: string) { memberKeyword.value = kw }

onMounted(() => { loadMembers() })
</script>

<style scoped>
.compute-tab { display: flex; flex-direction: column; gap: 16px; }
.compute-form { background: #ffffff; padding: 12px 16px; border-radius: 8px; border: 1px solid #ebeef5; }
</style>