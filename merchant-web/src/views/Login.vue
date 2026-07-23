<template>
  <div class="login-container">
    <el-card class="login-card">
      <div class="logo">
        <h2>🍜 商家管理端</h2>
        <p>外卖系统 · 商家工作台</p>
      </div>
      <el-form :model="form" label-width="80px">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入商家手机号" />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display: flex; gap: 10px">
            <el-input v-model="form.code" placeholder="请输入验证码" />
            <el-button :disabled="countdown > 0" @click="handleSendSms">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleLogin" style="width: 100%">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendSms, login } from '../api'

const router = useRouter()
const form = ref({ phone: '', code: '' })
const loading = ref(false)
const countdown = ref(0)

const handleSendSms = async () => {
  if (!form.value.phone) { ElMessage.warning('请输入手机号'); return }
  await sendSms(form.value.phone)
  ElMessage.success('验证码已发送（开发环境固定为 123456）')
  countdown.value = 60
  const t = setInterval(() => { countdown.value--; if (countdown.value <= 0) clearInterval(t) }, 1000)
}

const handleLogin = async () => {
  loading.value = true
  try {
    const res = await login(form.value.phone, form.value.code)
    localStorage.setItem('merchant_token', res.data.accessToken)
    if (res.data.refreshToken) localStorage.setItem('merchant_refresh_token', res.data.refreshToken)
    localStorage.setItem('merchant_userId', res.data.userInfo?.id)
    ElMessage.success('登录成功')
    router.push('/orders')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f7fa; }
.login-card { width: 400px; }
.logo { text-align: center; margin-bottom: 24px; }
.logo h2 { font-size: 24px; color: #409eff; }
.logo p { color: #909399; margin-top: 4px; }
</style>
