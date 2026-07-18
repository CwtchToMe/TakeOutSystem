<template>
  <div class="home-page">
    <!-- 顶部固定头部 -->
    <div class="home-header">
      <div class="location-btn" @click="showToast('定位功能开发中')">
        <van-icon name="location" color="#FF6B35" size="16" />
        <span class="location-text">北京市</span>
        <van-icon name="arrow-down" size="12" color="#999" />
      </div>
      <div class="search-bar" @click="router.push('/search')">
        <van-icon name="search" size="16" color="#BBBBBB" />
        <span>搜索餐厅、菜品</span>
      </div>
      <van-icon name="bell-o" size="22" color="#1A1A1A" style="flex-shrink:0" />
    </div>

    <!-- 主内容区域（可滚动） -->
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" class="scroll-wrap">
      <div class="content">

        <!-- 轮播 Banner -->
        <div class="banner-wrap">
          <van-swipe :autoplay="3500" indicator-color="#FF6B35" lazy-render class="banner">
            <van-swipe-item v-for="b in banners" :key="b.id">
              <div class="banner-item" :style="{ background: b.bg }">
                <div class="banner-content">
                  <div class="banner-tag">{{ b.tag }}</div>
                  <div class="banner-title">{{ b.title }}</div>
                  <div class="banner-sub">{{ b.sub }}</div>
                  <div class="banner-btn">立即抢购</div>
                </div>
                <div class="banner-emoji">{{ b.emoji }}</div>
              </div>
            </van-swipe-item>
          </van-swipe>
        </div>

        <!-- 分类入口 -->
        <div class="section-card">
          <div class="category-grid">
            <div
              v-for="cat in categories"
              :key="cat.id"
              class="category-item"
              @click="router.push('/search?keyword=' + cat.keyword)"
            >
              <div class="cat-icon" :style="{ background: cat.bg }">{{ cat.icon }}</div>
              <span class="cat-name">{{ cat.name }}</span>
            </div>
          </div>
        </div>

        <!-- 活动Banner -->
        <div class="activity-banner" @click="showToast('活动详情开发中')">
          <div class="act-left">
            <span class="act-hot">🔥 限时特惠</span>
            <div class="act-title">满 20 元减 5 元</div>
            <div class="act-sub">新用户专享，今日有效</div>
          </div>
          <div class="act-right">
            <span class="act-coupon">领取优惠券</span>
          </div>
        </div>

        <!-- 商家列表 -->
        <div class="section-header">
          <span class="section-title">附近好店</span>
          <span class="section-sub">{{ merchants.length }}家餐厅为您服务</span>
        </div>

        <!-- 骨架屏加载 -->
        <template v-if="loading">
          <div v-for="i in 4" :key="i" class="merchant-skeleton">
            <van-skeleton-image class="skeleton-img" />
            <div class="skeleton-info">
              <van-skeleton title :row="3" />
            </div>
          </div>
        </template>

        <!-- 商家卡片列表 -->
        <template v-else>
          <div
            v-for="m in merchants"
            :key="m.id"
            class="merchant-card"
            @click="router.push(`/merchant/${m.id}`)"
          >
            <!-- 商家图片 -->
            <div class="merchant-img-wrap">
              <van-image
                :src="m.logoUrl || m.logo"
                fit="cover"
                class="merchant-img"
                :error-icon="'photo-fail'"
              >
                <template #error>
                  <div class="img-placeholder">{{ m.name?.slice(0, 1) }}</div>
                </template>
              </van-image>
              <div v-if="m.salesCount > 100" class="merchant-badge">热门</div>
            </div>

            <!-- 商家信息 -->
            <div class="merchant-info">
              <div class="merchant-name text-clamp1">{{ m.name }}</div>

              <!-- 评分 + 月销量 -->
              <div class="merchant-meta">
                <span class="score-wrap">
                  <van-icon name="star" color="#FFB800" size="12" />
                  <span class="score">{{ m.score ? Number(m.score).toFixed(1) : '暂无' }}</span>
                </span>
                <span class="dot">·</span>
                <span class="sales">月销{{ m.salesCount || 0 }}</span>
                <span class="dot">·</span>
                <span class="distance" v-if="m.distance != null">{{ (m.distance / 1000).toFixed(1) }}km</span>
              </div>

              <!-- 标签 -->
              <div class="merchant-tags" v-if="m.description">
                <span class="tag-item">{{ m.description.slice(0, 8) }}</span>
              </div>

              <!-- 配送信息 -->
              <div class="delivery-info">
                <van-icon name="logistics" size="12" color="#999" />
                <span>{{ m.deliveryTime || 30 }}分钟</span>
                <span class="info-sep">|</span>
                <span>起送¥{{ m.minOrderPrice }}</span>
                <span class="info-sep">|</span>
                <span class="delivery-fee">配送¥{{ m.deliveryFee }}</span>
              </div>
            </div>
          </div>

          <van-empty
            v-if="!loading && merchants.length === 0"
            image="https://fastly.jsdelivr.net/npm/@vant/assets/custom-empty-image.png"
            description="附近暂无商家"
            style="padding: 40px 0"
          />
        </template>

        <!-- 加载更多 -->
        <van-list
          v-if="!loading"
          v-model:loading="loadingMore"
          :finished="finished"
          finished-text="已加载全部商家"
          @load="loadMore"
        />

        <div style="height: 20px" />
      </div>
    </van-pull-refresh>

    <!-- 底部导航 -->
    <van-tabbar v-model="activeTab" fixed safe-area-inset-bottom>
      <van-tabbar-item icon="home-o" to="/">首页</van-tabbar-item>
      <van-tabbar-item icon="search" to="/search">搜索</van-tabbar-item>
      <van-tabbar-item icon="orders-o" to="/orders">订单</van-tabbar-item>
      <van-tabbar-item icon="user-o" to="/profile">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getNearbyMerchants } from '../api'

const router = useRouter()
const activeTab = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const merchants = ref([])
const page = ref(1)

const banners = [
  {
    id: 1,
    bg: 'linear-gradient(135deg, #FF6B35 0%, #FF4500 100%)',
    tag: '限时特惠',
    title: '满20减5元',
    sub: '新用户专属福利',
    emoji: '🍜'
  },
  {
    id: 2,
    bg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    tag: '超级快送',
    title: '30分钟必达',
    sub: '急速配送 准时到门',
    emoji: '🚀'
  },
  {
    id: 3,
    bg: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
    tag: '品质优选',
    title: '精选好店推荐',
    sub: '每日严选 品质保障',
    emoji: '⭐'
  }
]

const categories = [
  { id: 1, icon: '🍜', name: '精选外卖', bg: '#FFF3EE', keyword: '外卖' },
  { id: 2, icon: '🏪', name: '超市便利', bg: '#EFF9F4', keyword: '超市' },
  { id: 3, icon: '🍔', name: '汉堡炸鸡', bg: '#FFF8E7', keyword: '汉堡' },
  { id: 4, icon: '🧋', name: '奶茶饮品', bg: '#F3EFFF', keyword: '奶茶' },
  { id: 5, icon: '🍕', name: '披萨西餐', bg: '#FFF0F0', keyword: '西餐' },
  { id: 6, icon: '🥗', name: '健康轻食', bg: '#EFFFF3', keyword: '轻食' },
  { id: 7, icon: '🎂', name: '蛋糕甜品', bg: '#FFF5FF', keyword: '甜品' },
  { id: 8, icon: '···', name: '更多分类', bg: '#F5F5F5', keyword: '' }
]

const fetchMerchants = async (p = 1) => {
  const res = await getNearbyMerchants({
    longitude: 116.3900,
    latitude: 39.9520,
    radius: 20000,
    page: p,
    size: 10
  })
  const data = res.data
  const records = data.records || data || []
  return { records, total: data.total || records.length }
}

onMounted(async () => {
  loading.value = true
  try {
    const { records, total } = await fetchMerchants(1)
    merchants.value = records
    page.value = 2
    finished.value = merchants.value.length >= total
  } catch (e) {
    showToast('加载商家列表失败')
    finished.value = true
  } finally {
    loading.value = false
  }
})

const loadMore = async () => {
  if (finished.value) return
  try {
    const { records, total } = await fetchMerchants(page.value)
    merchants.value.push(...records)
    page.value++
    if (merchants.value.length >= total || records.length === 0) {
      finished.value = true
    }
  } catch (e) {
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

const onRefresh = async () => {
  try {
    page.value = 1
    finished.value = false
    const { records, total } = await fetchMerchants(1)
    merchants.value = records
    page.value = 2
    finished.value = merchants.value.length >= total
  } catch (e) {
    finished.value = true
  } finally {
    refreshing.value = false
  }
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  padding-bottom: var(--tabbar-height);
}

/* 固定头部 */
.home-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.06);
}

.location-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  cursor: pointer;
}
.location-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-bar {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  background: #F5F5F5;
  border-radius: var(--radius-full);
  padding: 8px 14px;
  font-size: 13px;
  color: #BBBBBB;
  cursor: pointer;
}

/* 滚动内容区 */
.scroll-wrap {
  flex: 1;
  overflow-y: auto;
}

.content {
  padding-bottom: 16px;
}

/* Banner */
.banner-wrap {
  padding: 12px 14px 0;
}
.banner {
  border-radius: var(--radius);
  overflow: hidden;
}
.banner-item {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: relative;
  overflow: hidden;
}
.banner-content {
  z-index: 1;
}
.banner-tag {
  display: inline-block;
  background: rgba(255,255,255,0.25);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  margin-bottom: 6px;
}
.banner-title {
  font-size: 22px;
  font-weight: 800;
  color: #fff;
  line-height: 1.2;
}
.banner-sub {
  font-size: 12px;
  color: rgba(255,255,255,0.85);
  margin-top: 4px;
}
.banner-btn {
  display: inline-block;
  background: #fff;
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  margin-top: 10px;
}
.banner-emoji {
  font-size: 72px;
  line-height: 1;
  opacity: 0.9;
}

/* 分类格子 */
.section-card {
  background: #fff;
  margin: 10px 14px 0;
  border-radius: var(--radius);
  padding: 16px 0;
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
}
.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  cursor: pointer;
  transition: transform 0.15s;
}
.category-item:active {
  transform: scale(0.93);
}
.cat-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}
.cat-name {
  font-size: 12px;
  color: var(--text-2);
  font-weight: 500;
}

/* 活动Banner */
.activity-banner {
  margin: 10px 14px 0;
  background: linear-gradient(135deg, #FFF3EE 0%, #FFE8DC 100%);
  border-radius: var(--radius);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid rgba(255,107,53,0.15);
  cursor: pointer;
}
.act-hot {
  font-size: 11px;
  font-weight: 600;
  color: var(--primary);
}
.act-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-1);
  margin-top: 3px;
}
.act-sub {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
}
.act-coupon {
  background: var(--primary-gradient);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: var(--radius-full);
  white-space: nowrap;
}

/* 区块标题 */
.section-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 16px 14px 8px;
}
.section-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-1);
}
.section-sub {
  font-size: 12px;
  color: var(--text-4);
}

/* 骨架屏 */
.merchant-skeleton {
  display: flex;
  gap: 12px;
  background: #fff;
  margin: 0 14px 10px;
  border-radius: var(--radius);
  padding: 14px;
}
.skeleton-img {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
.skeleton-info {
  flex: 1;
}

/* 商家卡片 */
.merchant-card {
  display: flex;
  gap: 12px;
  background: #fff;
  margin: 0 14px 10px;
  border-radius: var(--radius);
  padding: 14px;
  box-shadow: var(--shadow-xs);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  position: relative;
  overflow: hidden;
}
.merchant-card:active {
  transform: scale(0.98);
  box-shadow: var(--shadow-sm);
}

/* 商家图片 */
.merchant-img-wrap {
  position: relative;
  flex-shrink: 0;
}
.merchant-img {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.img-placeholder {
  width: 88px;
  height: 88px;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  border-radius: var(--radius-sm);
}
.merchant-badge {
  position: absolute;
  top: -1px;
  left: -1px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: var(--radius-sm) 0 var(--radius-sm) 0;
}

/* 商家信息 */
.merchant-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.merchant-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.merchant-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-3);
}
.score-wrap {
  display: flex;
  align-items: center;
  gap: 2px;
}
.score {
  font-weight: 600;
  color: #FF9500;
}
.dot { color: var(--text-5); }
.distance { color: var(--info); }

.merchant-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.tag-item {
  font-size: 11px;
  color: var(--primary);
  background: var(--primary-bg);
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.delivery-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-3);
  margin-top: auto;
}
.info-sep {
  color: var(--border);
}
.delivery-fee {
  color: var(--success);
}
</style>
