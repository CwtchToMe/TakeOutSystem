<template>
  <div style="min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f7fa;">
    <el-card style="width: 400px; padding: 20px;">
      <h2 style="text-align: center; margin-bottom: 30px; color: #303133;">外卖平台管理后台</h2>
      <el-form :model="form" label-width="80px">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入管理员手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-input v-model="form.code" placeholder="请输入验证码" maxlength="6" style="flex: 1;" />
            <el-button :disabled="countdown > 0" @click="handleSendSms">
              {{ countdown > 0 ? `${countdown}s 后重试` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%;" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
      <p style="text-align: center; color: #909399; font-size: 13px; margin-top: 10px;">
        开发环境验证码固定为 <strong>123456</strong>
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendSms, login } from '../api/index'

const router = useRouter()
const form = ref({ phone: '', code: '' })
const loading = ref(false)
const countdown = ref(0)

const handleSendSms = async () => {
  if (!form.value.phone || form.value.phone.length !== 11) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  try {
    await sendSms(form.value.phone)
    ElMessage.success('验证码已发送（开发环境固定：123456）')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {}
}

const handleLogin = async () => {
  if (!form.value.phone || !form.value.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  loading.value = true
  try {
    const res = await login(form.value.phone, form.value.code)
    if (res.data.userInfo?.role !== 'ADMIN') {
      ElMessage.error('该账号不是管理员，无权访问')
      return
    }
    localStorage.setItem('admin_token', res.data.accessToken)
    localStorage.setItem('admin_userId', res.data.userInfo.id)
    ElMessage.success('登录成功')
    router.push('/')
  } catch {} finally {
    loading.value = false
  }
}
</script>
