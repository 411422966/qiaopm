<!--
  旧入口兼容 shim：路由 /intel/rfm 仍然指向 @/views/intel/rfm.vue，
  真实实现已迁移到 ./rfm/index.vue（拆分为 composables + 子组件）。
  本文件只做 re-export，禁止在此添加业务逻辑。
-->
<script setup lang="ts">
import IntelRfm from './rfm/index.vue'
</script>

<template>
  <IntelRfm />
</template>