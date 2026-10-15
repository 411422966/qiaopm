<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { usePermissionStore } from '@/stores/permission'

const permission = usePermissionStore()

onMounted(async () => {
  // 仅登录后才拉取权限码 / 菜单；未登录交给 router 跳 /login
  if (!localStorage.getItem('token')) return
  // 启动时拉取权限码 / 菜单；失败使用缓存
  await permission.refresh()
})
</script>
