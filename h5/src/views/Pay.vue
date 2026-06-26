<template>
  <div class="pay-page">
    <van-nav-bar title="收银台" left-arrow @click-left="handleBack" />

    <div class="pay-content">
      <!-- 支付金额 -->
      <div class="amount-section">
        <div class="amount-label">需付金额</div>
        <div class="amount-value">
          <span class="amount-sym">¥</span>
          <span class="amount-num">{{ amount }}</span>
        </div>
        <div class="order-no-text">订单号：{{ orderNo }}</div>
        <!-- 倒计时 -->
        <div class="countdown" v-if="remainSeconds > 0">
          <van-icon name="clock-o" size="14" color="#FF9500" />
          <span>请在 <b class="count-time">{{ formatTime(remainSeconds) }}</b> 内完成支付</span>
        </div>
        <div class="countdown expired" v-else>
          <van-icon name="warning-o" size="14" color="#FF3B30" />
          <span>支付已超时，请重新下单</span>
        </div>
      </div>

      <!-- 支付方式选择 -->
      <div class="pay-methods-section">
        <div class="section-label">选择支付方式</div>
        <div class="pay-method-list">
          <div
            v-for="m in payMethods"
            :key="m.type"
            class="pay-method-item"
            :class="{ selected: selectedPay === m.type }"
            @click="selectedPay = m.type"
          >
            <span class="pm-icon">{{ m.icon }}</span>
            <div class="pm-info">
              <div class="pm-name">{{ m.name }}</div>
              <div class="pm-desc">{{ m.desc }}</div>
            </div>
            <div class="pm-radio" :class="{ active: selectedPay === m.type }"></div>
          </div>
        </div>
      </div>

      <!-- 注意事项 -->
      <div class="notice">
        <van-icon name="info-o" size="13" color="#FF9500" />
        <span>当前为模拟支付环境，点击"立即支付"将直接完成支付</span>
      </div>
    </div>

    <!-- 底部按钮 -->
    <div class="pay-footer">
      <van-button
        block
        :loading="paying"
        loading-text="支付中..."
        class="pay-btn"
        :disabled="remainSeconds <= 0"
        @click="handlePay"
      >
        <span>立即支付 ¥{{ amount }}</span>
      </van-button>
      <div class="cancel-link" @click="handleBack">稍后支付</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { createPayment, mockCallback, getPayStatus } from '../api'

const route = useRoute()
const router = useRouter()
const orderNo = route.params.orderNo
const amount = ref('--')
const paymentNo = ref('')
const paying = ref(false)
const selectedPay = ref(1)
const remainSeconds = ref(15 * 60) // 15分钟倒计时
let timer = null

const payMethods = [
  { type: 1, icon: '💚', name: '微信支付', desc: '推荐使用，安全快捷' },
  { type: 2, icon: '💙', name: '支付宝', desc: '花呗/余额/银行卡' },
  { type: 3, icon: '💳', name: '银行卡支付', desc: '支持各大银行借记卡' }
]

const formatTime = (s) => {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

onMounted(async () => {
  try {
    // 创建或幂等获取支付单
    const payRes = await createPayment({ orderNo, payType: selectedPay.value })
    paymentNo.value = payRes.data.paymentNo
  } catch (e) {
    // 支付单已存在，忽略报错继续
  }
  // 无论成功还是幂等，都通过查询状态获取金额
  try {
    const statusRes = await getPayStatus(orderNo)
    if (statusRes.data) {
      paymentNo.value = statusRes.data.paymentNo || paymentNo.value
      amount.value = statusRes.data.amount
    }
  } catch (e2) {}


  // 倒计时
  timer = setInterval(() => {
    if (remainSeconds.value > 0) {
      remainSeconds.value--
    } else {
      clearInterval(timer)
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const handlePay = async () => {
  if (!paymentNo.value) {
    showToast('支付信息加载中，请稍后')
    return
  }
  paying.value = true
  try {
    await mockCallback({ paymentNo: paymentNo.value, success: true })
    showSuccessToast('支付成功！')
    setTimeout(() => {
      router.replace(`/order/${orderNo}`)
    }, 1200)
  } finally {
    paying.value = false
  }
}

const handleBack = () => {
  router.push('/orders')
}
</script>

<style scoped>
.pay-page {
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
}

.pay-content {
  flex: 1;
  padding: 0 0 100px;
}

/* 金额区域 */
.amount-section {
  background: var(--primary-gradient);
  padding: 32px 20px 28px;
  text-align: center;
}
.amount-label {
  font-size: 14px;
  color: rgba(255,255,255,0.85);
}
.amount-value {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 2px;
  margin-top: 8px;
}
.amount-sym {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
}
.amount-num {
  font-size: 48px;
  font-weight: 800;
  color: #fff;
  line-height: 1.1;
}
.order-no-text {
  font-size: 12px;
  color: rgba(255,255,255,0.7);
  margin-top: 8px;
}
.countdown {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(255,255,255,0.2);
  border-radius: var(--radius-full);
  padding: 4px 12px;
  margin-top: 12px;
  font-size: 12px;
  color: rgba(255,255,255,0.9);
}
.countdown.expired {
  background: rgba(255,59,48,0.2);
}
.count-time {
  font-weight: 700;
  color: #FFD700;
}

/* 支付方式 */
.pay-methods-section {
  background: #fff;
  margin-top: 10px;
  padding: 16px;
}
.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 12px;
}
.pay-method-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.pay-method-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
}
.pay-method-item:last-child { border-bottom: none; }
.pm-icon { font-size: 28px; }
.pm-info { flex: 1; }
.pm-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}
.pm-desc {
  font-size: 12px;
  color: var(--text-4);
  margin-top: 2px;
}
.pm-radio {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--border);
  transition: all 0.15s;
}
.pm-radio.active {
  border-color: var(--primary);
  background: var(--primary);
  box-shadow: 0 0 0 3px rgba(255,107,53,0.2);
}

/* 注意事项 */
.notice {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 12px 16px;
  padding: 10px 12px;
  background: #FFFBF0;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
}

/* 底部按钮 */
.pay-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 480px;
  margin: 0 auto;
  padding: 12px 16px 24px;
  background: #fff;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06);
}
.pay-btn {
  height: 50px;
  font-size: 17px;
  font-weight: 700;
  background: var(--primary-gradient) !important;
  border: none !important;
  border-radius: var(--radius-full) !important;
  box-shadow: 0 6px 20px rgba(255,107,53,0.4);
}
.pay-btn:disabled {
  background: #CCC !important;
  box-shadow: none !important;
}
.cancel-link {
  text-align: center;
  font-size: 13px;
  color: var(--text-4);
  margin-top: 10px;
  cursor: pointer;
}
</style>
