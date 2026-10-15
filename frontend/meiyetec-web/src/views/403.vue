<template>
  <div class="forbidden-container">
    <el-result
      icon="error"
      title="无访问权限"
      :subTitle="deniedText"
    >
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const denied = route.query.denied as string
const deniedText = computed(() =>
  denied ? `您没有访问该页面的权限（权限码：${denied}）` : '您没有访问该页面的权限'
)

const goHome = () => router.push('/dashboard')
const goBack = () => router.back()
</script>

<style scoped>
.forbidden-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f0f2f5;
}
</style>
