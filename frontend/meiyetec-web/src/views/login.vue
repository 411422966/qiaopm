<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span>美业SaaS系统</span>
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="tips">
        <p>默认账号：admin / 123456</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/api/request'
import { usePermissionStore } from '@/stores/permission'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res: any = await request.post('/auth/login', form)
        const payload = res.data || {}
        localStorage.setItem('token', payload.token)
        const userInfo = {
          id: payload.id,
          username: payload.username,
          realName: payload.realName
        }
        localStorage.setItem('user', JSON.stringify(userInfo))

        // P0-4：登录响应已含 menus + permissions，直接写入 Pinia
        usePermissionStore().setFromLogin(payload)

        ElMessage.success('登录成功')
        // 若路由 query 带 redirect（来自 beforeEach 守卫），登录后回到原页面
        const redirect = (route.query.redirect as string | undefined) || '/dashboard'
        router.replace(redirect)
      } catch {
        // 错误已在拦截器处理
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
}
.card-header {
  text-align: center;
  font-size: 24px;
  font-weight: bold;
  color: #333;
}
.tips {
  text-align: center;
  color: #999;
  font-size: 12px;
  margin-top: 10px;
}
</style>
