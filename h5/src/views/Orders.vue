<template>
  <div class="orders-page">
    <!-- 顶部 -->
    <div class="page-header">
      <span class="page-title">我的订单</span>
    </div>

    <!-- Tab 切换 -->
    <van-tabs
      v-model:active="activeTab"
      class="order-tabs"
      sticky
      offset-top="52px"
      @change="onTabChange"
    >
      <van-tab v-for="tab in tabs" :key="tab.status" :title="tab.label" :name="tab.status" />
    </van-tabs>

    <!-- 下拉刷新 + 订单列表 -->
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh" class="list-wrap">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多订单了"
        @load="onLoad"
        class="order-list"
      >
        <!-- 骨架屏 -->
        <template v-if="loading && orders.length === 0">
          <div v-for="i in 3" :key="i" class="order-skeleton">
            <van-skeleton title :row="3" />
          </div>
        </template>

        <!-- 订单卡片 -->
        <div
          v-for="order in orders"
          :key="order.orderNo"
          class="order-card"
          @click="router.push(`/order/${order.orderNo}`)"
        >
          <!-- 卡片头部：商家名 + 状态 -->
          <div class="card-header">
            <div class="merchant-row">
              <van-icon name="shop-o" size="14" color="#666" />
              <span class="card-merchant">{{ order.merchantName || '外卖商家' }}</span>
            </div>
            <span class="order-status" :class="getStatusClass(order.status)">
              {{ orderStatusLabel(order.status) }}
            </span>
          </div>

          <!-- 菜品列表（最多显示3个） -->
          <div class="card-items">
            <div
              v-for="(item, idx) in (order.items || []).slice(0, 3)"
              :key="item.id || idx"
              class="card-item-row"
            >
              <div class="dish-img-placeholder-sm">🍽️</div>
              <span class="dish-name-sm">{{ item.dishName }}{{ item.spec ? `(${item.spec})` : '' }}</span>
              <span class="dish-qty-sm">×{{ item.quantity }}</span>
            </div>
            <div v-if="(order.items || []).length > 3" class="more-items">
              +{{ order.items.length - 3 }}件商品
            </div>
          </div>

          <!-- 卡片底部：价格 + 操作 -->
          <div class="card-footer">
            <span class="footer-count">共 {{ totalItemCount(order) }} 件</span>
            <span class="footer-price">
              实付 <b class="price-b">¥{{ order.actualPrice }}</b>
            </span>
          </div>

          <!-- 操作按钮 -->
          <div class="card-actions" v-if="[1, 2, 6].includes(order.status)">
            <van-button
              v-if="order.status === 1"
              size="small"
              class="action-btn action-pay"
              @click.stop="router.push(`/pay/${order.orderNo}`)"
            >
              去支付
            </van-button>
            <van-button
              v-if="order.status === 1 || order.status === 2"
              size="small"
              class="action-btn action-cancel"
              @click.stop="handleCancel(order)"
            >
              取消订单
            </van-button>
            <van-button
              v-if="order.status === 6"
              size="small"
              class="action-btn action-rate"
              @click.stop="router.push(`/review/${order.orderNo}`)"
            >
              去评价
            </van-button>
            <van-button
              v-if="order.status === 6"
              size="small"
              class="action-btn action-reorder"
              @click.stop="handleReorder(order)"
            >
              再来一单
            </van-button>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && orders.length === 0" class="empty-orders">
          <div class="empty-icon">📦</div>
          <div class="empty-text">暂无订单</div>
          <van-button round type="primary" class="empty-btn" @click="router.push('/')">
            去点餐
          </van-button>
        </div>
      </van-list>
    </van-pull-refresh>

    <!-- 底部导航 -->
    <van-tabbar v-model="navTab" fixed safe-area-inset-bottom>
      <van-tabbar-item icon="home-o" to="/">首页</van-tabbar-item>
      <van-tabbar-item icon="search" to="/search">搜索</van-tabbar-item>
      <van-tabbar-item icon="orders-o" to="/orders">订单</van-tabbar-item>
      <van-tabbar-item icon="user-o" to="/profile">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getMyOrders, cancelOrder, addToCart } from '../api'
import { useCartStore } from '../store/cart'

const router = useRouter()
const cartStore = useCartStore()
const activeTab = ref(null)
const orders = ref([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const page = ref(1)
const navTab = ref(2)

const tabs = [
  { label: '全部', status: null },
  { label: '待支付', status: 1 },
  { label: '待接单', status: 2 },
  { label: '进行中', status: 'in_progress' },
  { label: '已完成', status: 6 },
  { label: '已取消', status: 7 }
]

const resetList = () => {
  orders.value = []
  page.value = 1
  finished.value = false
}

const onTabChange = () => {
  resetList()
}

const onRefresh = async () => {
  resetList()
  await loadData()
  refreshing.value = false
}

const loadData = async () => {
  try {
    let records = []
    let total = 0
    if (activeTab.value === 'in_progress') {
      const [r3, r5] = await Promise.all([
        getMyOrders({ status: 3, page: page.value, size: 10 }),
        getMyOrders({ status: 5, page: page.value, size: 10 })
      ])
      records = [...(r3.data.records || []), ...(r5.data.records || [])]
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      total = (r3.data.total || 0) + (r5.data.total || 0)
    } else {
      const res = await getMyOrders({ status: activeTab.value, page: page.value, size: 10 })
      records = res.data.records || []
      total = res.data.total || 0
    }
    orders.value.push(...records)
    page.value++
    if (orders.value.length >= total || records.length < 10) {
      finished.value = true
    }
  } finally {
    loading.value = false
  }
}

const onLoad = async () => {
  await loadData()
}

const orderStatusLabel = (s) => {
  const m = {
    1: '待支付', 2: '待接单', 3: '备餐中',
    4: '待取餐', 5: '配送中', 6: '已完成',
    7: '已取消', 8: '退款中', 9: '已退款'
  }
  return m[s] ?? '未知'
}

const getStatusClass = (s) => {
  if (s === 6) return 'status-done'
  if (s === 7 || s === 9) return 'status-cancel'
  if (s === 1) return 'status-pay'
  return 'status-progress'
}

const totalItemCount = (order) => {
  return (order.items || []).reduce((sum, i) => sum + i.quantity, 0)
}

const handleCancel = async (order) => {
  try {
    await showConfirmDialog({ title: '确认取消该订单？', message: '取消后无法恢复' })
    await cancelOrder(order.orderNo)
    showToast({ message: '订单已取消', icon: 'checked' })
    resetList()
    await loadData()
  } catch (e) {}
}

const handleReorder = async (order) => {
  try {
    const merchantId = order.merchantId
    await Promise.all((order.items || []).map(item =>
      addToCart({
        merchantId,
        dishId: item.dishId,
        dishName: item.dishName,
        dishImage: item.dishImage,
        unitPrice: item.unitPrice,
        spec: item.spec,
        quantity: item.quantity
      })
    ))
    await cartStore.loadCart(merchantId)
    router.push('/cart')
  } catch (e) {
    showToast('操作失败，请重试')
  }
}
</script>

<style scoped>
.orders-page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: var(--tabbar-height);
}

/* 顶部 */
.page-header {
  background: #fff;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--border);
}
.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
}

/* Tab */
.order-tabs {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

/* 列表区域 */
.list-wrap {
  min-height: calc(100vh - 52px - 44px - var(--tabbar-height));
}
.order-list {
  padding: 10px 0;
}

/* 骨架屏 */
.order-skeleton {
  background: #fff;
  margin: 0 14px 10px;
  border-radius: var(--radius);
  padding: 16px;
}

/* 订单卡片 */
.order-card {
  background: #fff;
  margin: 0 14px 10px;
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
  cursor: pointer;
  transition: transform 0.15s;
}
.order-card:active { transform: scale(0.99); }

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--border);
}
.merchant-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-merchant {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}

/* 状态标签 */
.order-status {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-full);
}
.status-pay { color: var(--primary); background: var(--primary-bg); }
.status-progress { color: var(--warning); background: #FFF7E6; }
.status-done { color: var(--success); background: #E8F9EF; }
.status-cancel { color: var(--text-4); background: #F5F5F5; }

/* 菜品列表 */
.card-items {
  padding: 10px 14px;
}
.card-item-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.dish-img-placeholder-sm {
  font-size: 16px;
  flex-shrink: 0;
}
.dish-name-sm {
  flex: 1;
  font-size: 13px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dish-qty-sm {
  font-size: 12px;
  color: var(--text-4);
}
.more-items {
  font-size: 12px;
  color: var(--text-4);
  padding: 4px 0;
}

/* 底部 */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
}
.footer-count {
  font-size: 12px;
  color: var(--text-4);
}
.footer-price {
  font-size: 13px;
  color: var(--text-3);
}
.price-b {
  font-size: 16px;
  font-weight: 700;
  color: var(--price);
}

/* 操作按钮 */
.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
}
.action-btn {
  border-radius: var(--radius-full) !important;
  height: 32px !important;
  font-size: 12px !important;
  padding: 0 14px !important;
}
.action-cancel { border-color: var(--border) !important; color: var(--text-3) !important; }
.action-pay { background: var(--primary-gradient) !important; border: none !important; color: #fff !important; }
.action-rate { border-color: var(--border) !important; color: var(--text-3) !important; }
.action-reorder { background: var(--primary-gradient) !important; border: none !important; color: #fff !important; }

/* 空状态 */
.empty-orders {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 0 40px;
  gap: 10px;
}
.empty-icon { font-size: 64px; opacity: 0.4; }
.empty-text { font-size: 16px; color: var(--text-4); font-weight: 500; }
.empty-btn {
  margin-top: 8px;
  background: var(--primary-gradient) !important;
  border: none !important;
  padding: 0 32px !important;
}
</style>
