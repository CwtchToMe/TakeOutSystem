<template>
  <div class="reviews-page">
    <van-nav-bar title="我的评价" left-arrow @click-left="router.back()" />

    <div v-if="loading" class="loading-wrap">
      <van-skeleton v-for="i in 3" :key="i" title :row="3" style="margin-bottom: 10px; padding: 16px" />
    </div>

    <van-empty v-else-if="!reviews.length" image="search" description="暂无评价记录" style="padding-top: 80px" />

    <div v-else class="review-list">
      <div v-for="item in reviews" :key="item.id" class="review-card">
        <div class="rc-header">
          <div class="rc-meta">
            <div class="rc-order">订单 {{ item.orderNo }}</div>
            <div class="rc-time">{{ item.createdAt }}</div>
          </div>
          <van-rate :model-value="item.score" readonly color="#FFB800" void-color="#eee" size="16" />
        </div>
        <div v-if="item.content" class="rc-content">{{ item.content }}</div>
        <div v-else class="rc-no-content">（无文字评价）</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getMyReviews } from '../api'

const router = useRouter()
const loading = ref(true)
const reviews = ref([])

onMounted(async () => {
  try {
    const res = await getMyReviews()
    reviews.value = res.data || []
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

.review-list { padding: 10px; display: flex; flex-direction: column; gap: 10px; }

.review-card {
  background: #fff; border-radius: var(--radius-md); padding: 14px 16px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.06);
}
.rc-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
.rc-meta { flex: 1; min-width: 0; margin-right: 10px; }
.rc-order { font-size: 12px; color: var(--text-4); margin-bottom: 2px; }
.rc-time { font-size: 12px; color: var(--text-4); }
.rc-content { font-size: 14px; color: var(--text-2); line-height: 1.6; }
.rc-no-content { font-size: 13px; color: var(--text-5); font-style: italic; }
</style>
