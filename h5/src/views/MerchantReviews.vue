<template>
  <div class="reviews-page">
    <van-nav-bar :title="title" left-arrow @click-left="router.back()" />

    <div v-if="loading" class="loading-wrap">
      <van-skeleton v-for="i in 4" :key="i" title :row="3" style="margin-bottom: 10px; padding: 16px" />
    </div>

    <van-empty v-else-if="!reviews.length" image="search" description="暂无评价" style="padding-top: 80px" />

    <div v-else class="review-list">
      <!-- 评分汇总 -->
      <div class="score-summary">
        <div class="avg-score">{{ avgScore }}</div>
        <van-rate :model-value="Number(avgScore)" readonly allow-half color="#FFB800" void-color="#eee" size="18" />
        <div class="review-count">共 {{ reviews.length }} 条评价</div>
      </div>

      <div v-for="item in reviews" :key="item.id" class="review-card">
        <div class="rc-header">
          <div class="rc-user">
            <div class="rc-avatar">{{ item.nickname ? item.nickname.slice(0, 1) : '用' }}</div>
            <div class="rc-user-info">
              <div class="rc-nickname">{{ item.nickname || '用户' }}</div>
              <div class="rc-time">{{ item.createdAt }}</div>
            </div>
          </div>
          <van-rate :model-value="item.score" readonly color="#FFB800" void-color="#eee" size="14" />
        </div>
        <div v-if="item.content" class="rc-content">{{ item.content }}</div>
        <div v-else class="rc-no-content">（无文字评价）</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getMerchantReviews, getMerchantDetail } from '../api'

const route = useRoute()
const router = useRouter()
const merchantId = route.params.id
const loading = ref(true)
const reviews = ref([])
const merchantName = ref('')

const title = computed(() => merchantName.value ? `${merchantName.value} 的评价` : '商家评价')
const avgScore = computed(() => {
  if (!reviews.value.length) return '0.0'
  const avg = reviews.value.reduce((sum, r) => sum + r.score, 0) / reviews.value.length
  return avg.toFixed(1)
})

onMounted(async () => {
  try {
    const [mr, rv] = await Promise.allSettled([
      getMerchantDetail(merchantId),
      getMerchantReviews(merchantId)
    ])
    if (mr.status === 'fulfilled') merchantName.value = mr.value.data?.name || ''
    if (rv.status === 'fulfilled') reviews.value = rv.value.data || []
  } catch {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.reviews-page { min-height: 100vh; background: var(--bg); }
.loading-wrap { padding: 10px; }

.score-summary {
  background: #fff; padding: 20px; text-align: center; margin-bottom: 10px;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.avg-score { font-size: 40px; font-weight: 800; color: #FF9500; line-height: 1; }
.review-count { font-size: 13px; color: var(--text-4); }

.review-list { padding: 0; }
.review-card {
  background: #fff; padding: 14px 16px; margin-bottom: 1px;
}
.rc-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rc-user { display: flex; align-items: center; gap: 10px; }
.rc-avatar {
  width: 34px; height: 34px; border-radius: 50%; background: var(--primary-gradient);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; color: #fff; flex-shrink: 0;
}
.rc-nickname { font-size: 14px; font-weight: 600; color: var(--text-1); }
.rc-time { font-size: 11px; color: var(--text-4); margin-top: 2px; }
.rc-content { font-size: 14px; color: var(--text-2); line-height: 1.6; }
.rc-no-content { font-size: 13px; color: var(--text-5); font-style: italic; }
</style>
