<template>
  <div class="favorites-page">
    <van-nav-bar title="我的收藏" left-arrow @click-left="router.back()" />

    <div v-if="loading" class="loading-wrap">
      <van-skeleton v-for="i in 3" :key="i" title :row="2" style="margin-bottom: 10px; padding: 16px" />
    </div>

    <van-empty v-else-if="!favorites.length" image="records" description="暂无收藏的商家" style="padding-top: 80px" />

    <div v-else class="fav-list">
      <div
        v-for="item in favorites"
        :key="item.id"
        class="fav-card"
        @click="router.push(`/merchant/${item.merchantId}`)"
      >
        <img :src="item.logoUrl || '/placeholder-shop.png'" class="fav-logo" alt="logo" />
        <div class="fav-info">
          <div class="fav-name">{{ item.merchantName }}</div>
          <div class="fav-desc" v-if="item.description">{{ item.description }}</div>
          <div class="fav-meta">
            <span v-if="item.score">⭐ {{ item.score }}</span>
            <span v-if="item.deliveryFee != null">配送费 ¥{{ item.deliveryFee }}</span>
            <span v-if="item.deliveryTime">约 {{ item.deliveryTime }} 分钟</span>
          </div>
        </div>
        <van-button
          size="small"
          plain
          class="fav-remove-btn"
          :loading="removing[item.merchantId]"
          @click.stop="handleRemove(item)"
        >取消收藏</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getMyFavorites, removeFavorite } from '../api'

const router = useRouter()
const loading = ref(true)
const favorites = ref([])
const removing = reactive({})

onMounted(async () => {
  await loadFavorites()
})

const loadFavorites = async () => {
  try {
    loading.value = true
    const res = await getMyFavorites()
    favorites.value = res.data || []
  } catch {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

const handleRemove = async (item) => {
  try {
    await showConfirmDialog({ title: '取消收藏', message: `确定取消收藏"${item.merchantName}"？` })
    removing[item.merchantId] = true
    await removeFavorite(item.merchantId)
    showToast('已取消收藏')
    favorites.value = favorites.value.filter(f => f.merchantId !== item.merchantId)
  } catch {
    // user cancelled or request failed
  } finally {
    removing[item.merchantId] = false
  }
}
</script>

<style scoped>
.favorites-page { min-height: 100vh; background: var(--bg); }
.loading-wrap { padding: 10px; }

.fav-list { padding: 10px; display: flex; flex-direction: column; gap: 10px; }

.fav-card {
  background: #fff; border-radius: var(--radius-md); padding: 14px 16px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.06); cursor: pointer;
}
.fav-logo {
  width: 54px; height: 54px; border-radius: 8px; object-fit: cover; flex-shrink: 0;
  background: #f5f5f5;
}
.fav-info { flex: 1; min-width: 0; }
.fav-name { font-size: 15px; font-weight: 600; color: var(--text-1); margin-bottom: 4px; }
.fav-desc { font-size: 12px; color: var(--text-4); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 4px; }
.fav-meta { display: flex; gap: 8px; font-size: 12px; color: var(--text-4); flex-wrap: wrap; }
.fav-remove-btn {
  flex-shrink: 0; font-size: 12px; color: var(--text-4) !important;
  border-color: #ddd !important; border-radius: 20px !important;
}
</style>
