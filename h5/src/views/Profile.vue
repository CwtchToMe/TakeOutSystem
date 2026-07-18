<template>
  <div class="profile-page">
    <!-- 渐变头部 -->
    <div class="profile-header">
      <div class="header-bg"></div>
      <div class="header-content">
        <!-- 设置按钮 -->
        <div class="header-actions">
          <van-icon name="setting-o" size="22" color="#fff" @click="showToast('版本 1.0.0')" />
        </div>
        <!-- 用户信息 -->
        <div class="user-info-row">
          <div class="avatar-wrap" @click="handleAvatarClick">
            <div class="avatar-inner">
              <span class="avatar-text">{{ avatarText }}</span>
            </div>
            <div class="avatar-edit-tag">编辑</div>
          </div>
          <div class="user-meta">
            <div class="user-nickname">{{ profile?.nickname || ('用户' + authStore.userId) }}</div>
            <div class="user-phone">
              <van-icon name="phone-o" size="12" />
              <span>{{ maskedPhone }}</span>
            </div>
          </div>
        </div>
        <!-- 统计数据 -->
        <div class="stats-row">
          <div class="stat-item" @click="router.push('/orders')">
            <div class="stat-num">{{ stats.orderCount }}</div>
            <div class="stat-label">订单</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item" @click="router.push('/coupons')">
            <div class="stat-num">{{ stats.couponCount }}</div>
            <div class="stat-label">优惠券</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item" @click="router.push('/favorites')">
            <div class="stat-num">{{ stats.favoriteCount }}</div>
            <div class="stat-label">收藏</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item" @click="router.push('/reviews')">
            <div class="stat-num">{{ stats.reviewCount }}</div>
            <div class="stat-label">评价</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 功能快捷入口 -->
    <div class="feature-grid-section">
      <div class="feature-grid">
        <div class="feature-item" @click="router.push('/orders?status=2')">
          <div class="fi-icon fi-pending">⏰</div>
          <div class="fi-label">待接单</div>
        </div>
        <div class="feature-item" @click="router.push('/orders?status=in_progress')">
          <div class="fi-icon fi-cooking">👨‍🍳</div>
          <div class="fi-label">进行中</div>
        </div>
        <div class="feature-item" @click="router.push('/orders?status=6')">
          <div class="fi-icon fi-done">✅</div>
          <div class="fi-label">已完成</div>
        </div>
        <div class="feature-item" @click="router.push('/orders')">
          <div class="fi-icon fi-all">📋</div>
          <div class="fi-label">全部订单</div>
        </div>
      </div>
    </div>

    <!-- 我的服务 -->
    <div class="section-card">
      <div class="section-title">我的服务</div>
      <div class="service-grid">
        <div class="service-item" @click="router.push('/address')">
          <div class="si-icon">📍</div>
          <div class="si-label">收货地址</div>
        </div>
        <div class="service-item" @click="router.push('/coupons')">
          <div class="si-icon">🎫</div>
          <div class="si-label">优惠券</div>
        </div>
        <div class="service-item" @click="router.push('/favorites')">
          <div class="si-icon">❤️</div>
          <div class="si-label">我的收藏</div>
        </div>
        <div class="service-item" @click="router.push('/reviews')">
          <div class="si-icon">📝</div>
          <div class="si-label">我的评价</div>
        </div>
      </div>
    </div>

    <!-- 设置菜单 -->
    <div class="section-card">
      <van-cell-group :border="false">
        <van-cell
          title="账号安全"
          icon="shield-o"
          is-link
          @click="showToast('账号安全功能开发中')"
        >
          <template #icon><span class="cell-icon">🔒</span></template>
        </van-cell>
        <van-cell
          title="消息通知"
          icon="bell"
          is-link
          @click="showToast('消息通知功能开发中')"
        >
          <template #icon><span class="cell-icon">🔔</span></template>
        </van-cell>
        <van-cell
          title="联系客服"
          is-link
          @click="showToast('客服功能开发中')"
        >
          <template #icon><span class="cell-icon">💬</span></template>
        </van-cell>
        <van-cell
          title="帮助中心"
          is-link
          @click="showToast('帮助中心开发中')"
        >
          <template #icon><span class="cell-icon">❓</span></template>
        </van-cell>
        <van-cell
          title="关于我们"
          is-link
          @click="showToast('版本 1.0.0')"
        >
          <template #icon><span class="cell-icon">ℹ️</span></template>
        </van-cell>
      </van-cell-group>
    </div>

    <!-- 退出登录 -->
    <div class="logout-section">
      <van-button
        round
        block
        class="logout-btn"
        @click="handleLogout"
      >
        退出登录
      </van-button>
    </div>

    <div style="height: 72px" />

    <!-- 底部导航 -->
    <van-tabbar v-model="navTab" fixed safe-area-inset-bottom>
      <van-tabbar-item icon="home-o" to="/">首页</van-tabbar-item>
      <van-tabbar-item icon="search" to="/search">搜索</van-tabbar-item>
      <van-tabbar-item icon="orders-o" to="/orders">订单</van-tabbar-item>
      <van-tabbar-item icon="user-o" to="/profile">我的</van-tabbar-item>
    </van-tabbar>

    <!-- 编辑昵称弹窗 -->
    <van-dialog
      v-model:show="showNicknameEdit"
      title="修改昵称"
      show-cancel-button
      @confirm="saveNickname"
    >
      <div style="padding: 16px">
        <van-field
          v-model="newNickname"
          placeholder="请输入新昵称"
          maxlength="20"
          show-word-limit
          :border="false"
          style="background: #f5f5f5; border-radius: 8px; padding: 10px 12px"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getMyProfile, updateProfile, getMyOrders, getMyCoupons, getMyFavorites, getMyReviews } from '../api'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const authStore = useAuthStore()
const profile = ref(null)
const navTab = ref(3)
const showNicknameEdit = ref(false)
const newNickname = ref('')

const stats = ref({
  orderCount: 0,
  couponCount: 0,
  favoriteCount: 0,
  reviewCount: 0
})

const avatarText = computed(() => {
  const name = profile.value?.nickname || ''
  return name ? name.slice(0, 1).toUpperCase() : '用'
})

const maskedPhone = computed(() => {
  const phone = profile.value?.phone || ''
  if (phone.length >= 11) {
    return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
  }
  return phone || '未绑定手机'
})

onMounted(async () => {
  try {
    const res = await getMyProfile()
    profile.value = res.data
  } catch (e) {
    if (e && (e.response?.status === 401 || e.message?.includes('登录已过期') || e.message?.includes('未登录'))) {
      return
    }
  }
  try {
    const [ordersRes, couponsRes, favoritesRes, reviewsRes] = await Promise.allSettled([
      getMyOrders({ page: 1, size: 1 }),
      getMyCoupons(),
      getMyFavorites(),
      getMyReviews()
    ])
    if (ordersRes.status === 'fulfilled') stats.value.orderCount = ordersRes.value.data?.total || 0
    if (couponsRes.status === 'fulfilled') stats.value.couponCount = (couponsRes.value.data || []).filter(c => c.status === 0).length
    if (favoritesRes.status === 'fulfilled') stats.value.favoriteCount = (favoritesRes.value.data || []).length
    if (reviewsRes.status === 'fulfilled') stats.value.reviewCount = (reviewsRes.value.data || []).length
  } catch (e) {
    // Promise.allSettled 不会抛出异常，但保留 catch 以防意外
  }
})

const handleAvatarClick = () => {
  newNickname.value = profile.value?.nickname || ''
  showNicknameEdit.value = true
}

const saveNickname = async () => {
  if (!newNickname.value.trim()) return
  try {
    await updateProfile({ nickname: newNickname.value.trim() })
    const name = newNickname.value.trim()
    if (profile.value) profile.value.nickname = name
    authStore.updateNickname(name)
    showToast('昵称已更新')
  } catch (e) {
    showToast('更新失败，请重试')
  }
}

const handleLogout = async () => {
  try {
    await showConfirmDialog({
      title: '确认退出',
      message: '退出后需重新登录才能使用完整功能'
    })
    authStore.clearAuth()
    showToast('已退出登录')
    router.push('/login')
  } catch (e) {}
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: var(--tabbar-height);
}

/* 头部渐变区域 */
.profile-header {
  position: relative;
  overflow: hidden;
}
.header-bg {
  position: absolute;
  inset: 0;
  background: var(--primary-gradient);
  border-radius: 0 0 32px 32px;
}
.header-content {
  position: relative;
  z-index: 1;
  padding: 52px 20px 28px;
}
.header-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

/* 用户信息行 */
.user-info-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.avatar-wrap {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
}
.avatar-inner {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: rgba(255,255,255,0.3);
  border: 3px solid rgba(255,255,255,0.8);
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-text {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
}
.avatar-edit-tag {
  position: absolute;
  bottom: 0;
  right: 0;
  background: rgba(0,0,0,0.45);
  color: #fff;
  font-size: 9px;
  padding: 1px 5px;
  border-radius: var(--radius-full);
}
.user-meta {
  flex: 1;
}
.user-nickname {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 6px;
}
.user-phone {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: rgba(255,255,255,0.85);
}

/* 统计行 */
.stats-row {
  display: flex;
  align-items: center;
  background: rgba(255,255,255,0.15);
  border-radius: var(--radius);
  padding: 14px 0;
}
.stat-item {
  flex: 1;
  text-align: center;
  cursor: pointer;
}
.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.stat-label {
  font-size: 11px;
  color: rgba(255,255,255,0.8);
  margin-top: 3px;
}
.stat-divider {
  width: 1px;
  height: 28px;
  background: rgba(255,255,255,0.3);
}

/* 功能网格 */
.feature-grid-section {
  background: #fff;
  margin-top: 12px;
  padding: 16px;
}
.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
}
.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  cursor: pointer;
  transition: opacity 0.15s;
}
.feature-item:active { opacity: 0.7; }
.fi-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.fi-pending { background: #FFF3E0; }
.fi-cooking { background: #FCE4EC; }
.fi-done { background: #E8F5E9; }
.fi-all { background: #E3F2FD; }
.fi-label {
  font-size: 12px;
  color: var(--text-2);
}

/* 我的服务 */
.section-card {
  background: #fff;
  margin-top: 10px;
  padding: 16px;
}
.section-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
  margin-bottom: 14px;
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
}
.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
  cursor: pointer;
  transition: opacity 0.15s;
}
.service-item:active { opacity: 0.7; }
.si-icon {
  font-size: 26px;
}
.si-label {
  font-size: 11px;
  color: var(--text-3);
}

/* 设置菜单 */
.cell-icon {
  font-size: 18px;
  margin-right: 8px;
  display: inline-flex;
  align-items: center;
}

/* 退出按钮 */
.logout-section {
  padding: 20px 16px;
}
.logout-btn {
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  color: var(--danger) !important;
  border-color: var(--danger) !important;
  border-radius: var(--radius-full) !important;
}
</style>
