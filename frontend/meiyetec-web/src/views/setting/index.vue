<template>
  <div class="setting-container">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="门店管理" name="store">
        <StoreManage />
      </el-tab-pane>
      <el-tab-pane label="员工管理" name="employee">
        <EmployeeManage />
      </el-tab-pane>
      <el-tab-pane label="会员等级" name="level">
        <LevelManage />
      </el-tab-pane>
      <el-tab-pane label="分类管理" name="category">
        <CategoryManage />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import StoreManage from './store.vue'
import EmployeeManage from './employee.vue'
import LevelManage from './level.vue'
import CategoryManage from './category.vue'

const activeTab = ref('store')
</script>

<style scoped>
.setting-container {
  padding: 0;
}
</style>
