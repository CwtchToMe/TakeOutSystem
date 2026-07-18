<template>
  <div class="login-page">
    <!-- 顶部渐变背景区 -->
    <div class="login-bg">
      <div class="brand">
        <div class="brand-logo">🍜</div>
        <h1 class="brand-name">外卖点餐</h1>
        <p class="brand-slogan">好吃的，立刻送到</p>
      </div>
      <div class="wave"></div>
    </div>

    <!-- 登录表单卡片 -->
    <div class="form-card">
      <h2 class="form-title">手机号登录 / 注册</h2>
      <p class="form-subtitle">未注册手机号验证后自动创建账号</p>

      <van-form @submit="handleLogin" class="login-form">
        <!-- 手机号输入 -->
        <div class="input-wrap">
          <div class="input-prefix">+86</div>
          <van-field
            v-model="phone"
            name="phone"
            type="tel"
            maxlength="11"
            placeholder="请输入手机号"
            :rules="[{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }]"
            class="custom-field"
          />
        </div>

        <!-- 验证码输入 -->
        <div class="input-wrap">
          <van-icon name="shield-o" class="input-icon" />
          <van-field
            v-model="code"
            name="code"
            type="number"
            maxlength="6"
            placeholder="请输入验证码"
            :rules="[{ required: true, message: '请输入验证码' }]"
            class="custom-field"
          />
          <button
            type="button"
            class="sms-btn"
            :class="{ disabled: countdown > 0 || sending }"
            :disabled="countdown > 0 || sending"
            @click="handleSendSms"
          >
            <span v-if="sending">发送中...</span>
            <span v-else-if="countdown > 0">{{ countdown }}s后重发</span>
            <span v-else>获取验证码</span>
          </button>
        </div>

        <!-- 登录按钮 -->
        <van-button
          round
          block
          native-type="submit"
          :loading="loading"
          loading-text="登录中..."
          class="login-btn"
        >
          登录 / 注册
        </van-button>
      </van-form>

      <!-- 隐私协议 -->
      <p class="terms">
        登录即代表同意
        <span class="link">《用户服务协议》</span>
        和
        <span class="link">《隐私政策》</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { sendSms, login } from '../api'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const authStore = useAuthStore()

const phone = ref('')
const code = ref('')
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)

const handleSendSms = async () => {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    showToast('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    await sendSms(phone.value)
    showToast({ message: '验证码已发送', icon: 'checked' })
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    // 错误已在拦截器中处理（401/网络错误等），无需额外提示
  } finally {
    sending.value = false
  }
}

const handleLogin = async () => {
  loading.value = true
  try {
    const res = await login(phone.value, code.value)
    authStore.setAuth({
      accessToken: res.data.accessToken,
      userId: String(res.data.userInfo?.id || ''),
      nickname: res.data.userInfo?.nickname || ''
    })
    showToast({ message: '登录成功', icon: 'checked' })
    router.replace('/')
  } catch {
    // 错误已在拦截器中处理（验证码错误等），无需额外提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #F5F5F5;
  display: flex;
  flex-direction: column;
}

/* 顶部渐变背景 */
.login-bg {
  background: var(--primary-gradient);
  padding: 60px 0 0;
  position: relative;
  flex-shrink: 0;
}

.brand {
  text-align: center;
  padding-bottom: 40px;
}
.brand-logo {
  font-size: 64px;
  filter: drop-shadow(0 4px 12px rgba(0,0,0,0.15));
  animation: float 3s ease-in-out infinite;
}
.brand-name {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin-top: 12px;
  letter-spacing: 2px;
}
.brand-slogan {
  font-size: 14px;
  color: rgba(255,255,255,0.85);
  margin-top: 6px;
}

/* 波浪分割 */
.wave {
  height: 32px;
  background: #F5F5F5;
  border-radius: 32px 32px 0 0;
  margin-top: -1px;
}

/* 表单卡片 */
.form-card {
  background: #fff;
  flex: 1;
  padding: 28px 24px 32px;
}

.form-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-1);
}
.form-subtitle {
  font-size: 13px;
  color: var(--text-4);
  margin-top: 6px;
}

.login-form {
  margin-top: 28px;
}

/* 输入框封装 */
.input-wrap {
  display: flex;
  align-items: center;
  background: #F7F8FA;
  border-radius: 12px;
  margin-bottom: 14px;
  overflow: hidden;
  border: 1.5px solid transparent;
  transition: border-color 0.2s;
}
.input-wrap:focus-within {
  border-color: var(--primary);
  background: #fff;
}
.input-prefix {
  padding: 0 12px 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-2);
  border-right: 1px solid #EBEBEB;
  white-space: nowrap;
  line-height: 52px;
}
.input-icon {
  padding: 0 12px 0 16px;
  font-size: 18px;
  color: var(--text-4);
  line-height: 52px;
}

.custom-field {
  flex: 1;
  background: transparent !important;
  --van-field-input-min-height: 52px;
  --van-cell-background: transparent;
  --van-field-placeholder-text-color: #BBBBBB;
}

/* SMS按钮 */
.sms-btn {
  flex-shrink: 0;
  padding: 0 14px;
  height: 52px;
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--primary);
  cursor: pointer;
  white-space: nowrap;
}
.sms-btn.disabled {
  color: var(--text-4);
  cursor: not-allowed;
}

/* 登录按钮 */
.login-btn {
  margin-top: 28px;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
  background: var(--primary-gradient) !important;
  border: none !important;
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

.terms {
  text-align: center;
  font-size: 12px;
  color: var(--text-4);
  margin-top: 20px;
  line-height: 1.8;
}
.link {
  color: var(--primary);
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}
</style>
