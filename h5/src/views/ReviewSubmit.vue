<template>
  <div class="review-page">
    <van-nav-bar title="评价订单" left-arrow @click-left="router.back()" />

    <div v-if="loading" class="loading-wrap">
      <van-skeleton title :row="4" style="padding: 20px" />
    </div>

    <template v-else-if="existing">
      <div class="already-reviewed">
        <div class="ar-icon">✅</div>
        <div class="ar-title">已评价</div>
        <div class="ar-stars">
          <van-rate :model-value="existing.score" readonly color="#FFB800" void-color="#eee" size="22" />
        </div>
        <div class="ar-content" v-if="existing.content">{{ existing.content }}</div>
        <div class="ar-time">{{ existing.createdAt }}</div>
      </div>
    </template>

    <template v-else>
      <!-- 订单信息摘要 -->
      <div class="order-summary" v-if="order">
        <div class="os-row">
          <span class="os-label">商家</span>
          <span class="os-val">{{ order.merchantName || '外卖商家' }}</span>
        </div>
        <div class="os-row">
          <span class="os-label">订单号</span>
          <span class="os-val">{{ order.orderNo }}</span>
        </div>
      </div>

      <!-- 评分 -->
      <div class="score-section">
        <div class="score-title">总体评分</div>
        <van-rate v-model="score" size="30" color="#FFB800" void-color="#eee" />
        <div class="score-label">{{ scoreLabel }}</div>
      </div>

      <!-- 评价内容 -->
      <div class="content-section">
        <van-field
          v-model="content"
          type="textarea"
          rows="4"
          placeholder="分享您的用餐体验，帮助更多人做出选择..."
          maxlength="200"
          show-word-limit
          :border="false"
          style="background: #f8f8f8; border-radius: 8px"
        />
      </div>

      <div style="height: 80px" />

      <div class="submit-bar">
        <van-button block class="submit-btn" :loading="submitting" loading-text="提交中..." @click="handleSubmit">
          提交评价
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getOrderDetail, getOrderReview, submitReview } from '../api'

const route = useRoute()
const router = useRouter()
const orderNo = route.params.orderNo
const loading = ref(true)
const order = ref(null)
const existing = ref(null)
const score = ref(5)
const content = ref('')
const submitting = ref(false)

const scoreLabel = computed(() => {
  const labels = ['', '很差', '较差', '一般', '较好', '非常好']
  return labels[score.value] || '非常好'
})

onMounted(async () => {
  try {
    const [orderRes, reviewRes] = await Promise.allSettled([
      getOrderDetail(orderNo),
      getOrderReview(orderNo)
    ])
    if (orderRes.status === 'fulfilled') order.value = orderRes.value.data
    if (reviewRes.status === 'fulfilled' && reviewRes.value.data) existing.value = reviewRes.value.data
  } finally {
    loading.value = false
  }
})

const handleSubmit = async () => {
  if (score.value < 1) { showToast('请选择评分'); return }
  submitting.value = true
  try {
    await submitReview({ orderNo, score: score.value, content: content.value })
    showToast({ message: '评价成功', icon: 'checked' })
    router.back()
  } catch (e) {
    showToast('提交失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.review-page { min-height: 100vh; background: var(--bg); }
.loading-wrap { padding: 20px; }

.already-reviewed {
  display: flex; flex-direction: column; align-items: center;
  padding: 60px 20px; gap: 12px; background: #fff; margin: 10px 0;
}
.ar-icon { font-size: 48px; }
.ar-title { font-size: 18px; font-weight: 700; color: var(--text-1); }
.ar-content { font-size: 14px; color: var(--text-3); text-align: center; max-width: 280px; }
.ar-time { font-size: 12px; color: var(--text-4); }

.order-summary {
  background: #fff; padding: 14px 16px; margin-bottom: 10px;
}
.os-row { display: flex; justify-content: space-between; font-size: 13px; padding: 4px 0; }
.os-label { color: var(--text-4); }
.os-val { color: var(--text-2); }

.score-section {
  background: #fff; padding: 24px 16px; text-align: center; margin-bottom: 10px;
}
.score-title { font-size: 16px; font-weight: 600; color: var(--text-1); margin-bottom: 16px; }
.score-label { font-size: 13px; color: var(--primary); margin-top: 10px; font-weight: 500; }

.content-section { background: #fff; padding: 16px; margin-bottom: 10px; }

.submit-bar {
  position: fixed; bottom: 0; left: 0; right: 0; max-width: 480px; margin: 0 auto;
  padding: 12px 16px 24px; background: #fff;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06);
}
.submit-btn {
  height: 46px; font-size: 15px; font-weight: 600;
  background: var(--primary-gradient) !important; border: none !important;
  border-radius: var(--radius-full) !important; color: #fff !important;
}
</style>
