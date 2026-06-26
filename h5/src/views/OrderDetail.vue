<template>
  <div class="order-detail-page">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />

    <!-- 加载中 -->
    <div v-if="!order" class="loading-wrap">
      <van-skeleton title :row="6" style="padding: 20px" />
    </div>

    <template v-else>
      <!-- 状态顶部区域 -->
      <div class="status-header" :class="getStatusBg(order.status)">
        <div class="status-icon-wrap">
          <span class="status-emoji">{{ getStatusEmoji(order.status) }}</span>
        </div>
        <div class="status-info">
          <div class="status-label">{{ orderStatusLabel(order.status) }}</div>
          <div class="status-desc">{{ getStatusDesc(order.status) }}</div>
        </div>
      </div>

      <!-- 配送进度（进行中订单） -->
      <div class="progress-section" v-if="order.status >= 2 && order.status <= 5">
        <div class="progress-steps">
          <div
            v-for="(step, idx) in progressSteps"
            :key="idx"
            class="progress-step"
          >
            <div
              class="step-dot"
              :class="{
                done: step.status <= order.status,
                current: step.status === order.status
              }"
            ></div>
            <div class="step-label" :class="{ active: step.status <= order.status }">
              {{ step.label }}
            </div>
            <div class="step-line" v-if="idx < progressSteps.length - 1"
              :class="{ done: step.status < order.status }"></div>
          </div>
        </div>
      </div>

      <!-- 收货地址 -->
      <div class="detail-section">
        <div class="section-head">
          <van-icon name="location" color="#FF6B35" size="16" />
          <span>收货地址</span>
        </div>
        <div class="address-block">
          <div class="addr-name-row">
            <span class="addr-name">{{ order.receiver }}</span>
            <span class="addr-phone">{{ order.phone }}</span>
          </div>
          <div class="addr-detail">{{ order.address }}</div>
        </div>
      </div>

      <!-- 商品列表 -->
      <div class="detail-section">
        <div class="section-head">
          <van-icon name="shop-o" color="#FF6B35" size="16" />
          <span>订单商品</span>
        </div>
        <div class="item-list">
          <div v-for="item in order.items" :key="item.id" class="order-item">
            <div class="oi-img">🍽️</div>
            <div class="oi-info">
              <div class="oi-name">
                {{ item.dishName }}{{ item.spec ? `（${item.spec}）` : '' }}
              </div>
              <div class="oi-unit">¥{{ item.unitPrice }} × {{ item.quantity }}</div>
            </div>
            <div class="oi-subtotal">¥{{ item.subtotal }}</div>
          </div>
        </div>
      </div>

      <!-- 价格明细 -->
      <div class="detail-section">
        <div class="section-head">
          <van-icon name="bill-o" color="#FF6B35" size="16" />
          <span>费用明细</span>
        </div>
        <div class="price-rows">
          <div class="price-row">
            <span>商品金额</span>
            <span>¥{{ itemsTotal }}</span>
          </div>
          <div class="price-row">
            <span>配送费</span>
            <span>¥{{ order.deliveryFee }}</span>
          </div>
          <div v-if="order.discount > 0" class="price-row" style="color: var(--price)">
            <span>优惠券抵扣</span>
            <span>-¥{{ order.discount }}</span>
          </div>
          <div class="price-divider"></div>
          <div class="price-row total">
            <span>实付金额</span>
            <span class="total-price">¥{{ order.actualPrice }}</span>
          </div>
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="detail-section">
        <div class="section-head">
          <van-icon name="description" color="#FF6B35" size="16" />
          <span>订单信息</span>
        </div>
        <div class="info-rows">
          <div class="info-row">
            <span class="info-key">订单编号</span>
            <span class="info-val">{{ order.orderNo }}</span>
          </div>
          <div class="info-row" v-if="order.remark">
            <span class="info-key">订单备注</span>
            <span class="info-val">{{ order.remark }}</span>
          </div>
          <div class="info-row" v-if="order.createdAt">
            <span class="info-key">下单时间</span>
            <span class="info-val">{{ order.createdAt }}</span>
          </div>
        </div>
      </div>

      <!-- 操作按钮区域 -->
      <div class="action-area" v-if="[1,2,5,6].includes(order.status)">
        <van-button
          v-if="order.status === 1"
          class="action-primary-btn"
          @click="router.push(`/pay/${orderNo}`)"
        >
          去支付
        </van-button>
        <van-button
          v-if="order.status === 1 || order.status === 2"
          plain
          class="action-cancel-btn"
          @click="handleCancel"
        >
          取消订单
        </van-button>
        <van-button
          v-if="order.status === 5"
          class="action-primary-btn"
          :loading="receiving"
          loading-text="确认中..."
          @click="handleReceive"
        >
          确认收货
        </van-button>
        <van-button
          v-if="order.status === 6"
          plain
          class="action-cancel-btn"
          :loading="reordering"
          loading-text="加入中..."
          @click="handleReorder"
        >
          再来一单
        </van-button>
        <van-button
          v-if="order.status === 6"
          class="action-primary-btn"
          :disabled="reviewed"
          @click="reviewed ? null : router.push(`/review/${orderNo}`)"
        >
          {{ reviewed ? '已评价' : '去评价' }}
        </van-button>
      </div>

      <div style="height: 32px" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getOrderDetail, cancelOrder, receiveOrder, addToCart, getOrderReview } from '../api'
import { useCartStore } from '../store/cart'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const orderNo = route.params.orderNo
const order = ref(null)
const receiving = ref(false)
const reordering = ref(false)
const reviewed = ref(false)

const progressSteps = [
  { label: '待接单', status: 2 },
  { label: '备餐中', status: 3 },
  { label: '待取餐', status: 4 },
  { label: '配送中', status: 5 }
]

const itemsTotal = computed(() => {
  if (!order.value?.items) return '0.00'
  return order.value.items.reduce((sum, i) => sum + Number(i.subtotal || 0), 0).toFixed(2)
})

onMounted(async () => {
  const res = await getOrderDetail(orderNo)
  order.value = res.data
  if (res.data?.status === 6) {
    try {
      const rv = await getOrderReview(orderNo)
      reviewed.value = !!rv.data
    } catch {}
  }
})

const orderStatusLabel = (s) => {
  const m = {
    1: '待支付', 2: '待接单', 3: '备餐中',
    4: '待取餐', 5: '配送中', 6: '已完成',
    7: '已取消', 8: '退款中', 9: '已退款'
  }
  return m[s] ?? '未知'
}

const getStatusEmoji = (s) => {
  if (s === 1) return '⏰'
  if (s === 2) return '📋'
  if (s === 3) return '👨‍🍳'
  if (s === 4) return '📦'
  if (s === 5) return '🛵'
  if (s === 6) return '✅'
  if (s === 7 || s === 9) return '❌'
  if (s === 8) return '💰'
  return '📝'
}

const getStatusDesc = (s) => {
  const m = {
    1: '请尽快完成支付，超时将自动取消',
    2: '商家正在确认您的订单',
    3: '商家正在为您精心制作',
    4: '餐品已准备好，骑手即将取餐',
    5: '骑手正在赶往配送地址',
    6: '订单已完成，感谢您的光临',
    7: '订单已取消',
    8: '退款申请处理中',
    9: '退款已完成'
  }
  return m[s] ?? ''
}

const getStatusBg = (s) => {
  if (s === 6) return 'bg-success'
  if (s === 7 || s === 9) return 'bg-cancel'
  if (s === 1) return 'bg-pay'
  return 'bg-progress'
}

const handleCancel = async () => {
  try {
    await showConfirmDialog({
      title: '确认取消订单？',
      message: '取消后该订单将无法恢复'
    })
    await cancelOrder(orderNo)
    showToast({ message: '订单已取消', icon: 'checked' })
    const res = await getOrderDetail(orderNo)
    order.value = res.data
  } catch (e) {}
}

const handleReceive = async () => {
  try {
    await showConfirmDialog({ title: '确认收货？', message: '请确认已收到外卖' })
    receiving.value = true
    await receiveOrder(orderNo)
    showToast({ message: '确认收货成功', icon: 'checked' })
    const res = await getOrderDetail(orderNo)
    order.value = res.data
  } catch (e) {
  } finally {
    receiving.value = false
  }
}

const handleReorder = async () => {
  try {
    reordering.value = true
    const merchantId = order.value.merchantId
    await Promise.all((order.value.items || []).map(item =>
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
  } finally {
    reordering.value = false
  }
}
</script>

<style scoped>
.order-detail-page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 32px;
}

.loading-wrap {
  padding: 20px;
}

/* 状态头部 */
.status-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 20px;
}
.bg-pay { background: var(--primary-gradient); }
.bg-progress { background: linear-gradient(135deg, #FF9500 0%, #FFAD33 100%); }
.bg-success { background: linear-gradient(135deg, #07C160 0%, #0ABF52 100%); }
.bg-cancel { background: linear-gradient(135deg, #AAAAAA 0%, #888888 100%); }

.status-icon-wrap {
  width: 56px;
  height: 56px;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.status-emoji {
  font-size: 28px;
}
.status-label {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.status-desc {
  font-size: 12px;
  color: rgba(255,255,255,0.85);
  margin-top: 4px;
  line-height: 1.4;
}

/* 进度条 */
.progress-section {
  background: #fff;
  padding: 16px 20px;
}
.progress-steps {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  position: relative;
}
.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  position: relative;
}
.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--border);
  z-index: 1;
  transition: all 0.2s;
}
.step-dot.done { background: var(--primary); }
.step-dot.current {
  background: var(--primary);
  box-shadow: 0 0 0 4px rgba(255,107,53,0.2);
  width: 12px;
  height: 12px;
}
.step-label {
  font-size: 11px;
  color: var(--text-4);
  margin-top: 6px;
  text-align: center;
}
.step-label.active { color: var(--primary); font-weight: 600; }
.step-line {
  position: absolute;
  top: 5px;
  left: calc(50% + 8px);
  right: calc(-50% + 8px);
  height: 2px;
  background: var(--border);
}
.step-line.done { background: var(--primary); }

/* 通用 Section */
.detail-section {
  background: #fff;
  margin-top: 10px;
  padding: 14px 16px;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 12px;
}

/* 地址 */
.address-block { padding-left: 22px; }
.addr-name-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.addr-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}
.addr-phone {
  font-size: 13px;
  color: var(--text-3);
}
.addr-detail {
  font-size: 13px;
  color: var(--text-3);
  margin-top: 4px;
}

/* 商品列表 */
.item-list { display: flex; flex-direction: column; gap: 10px; }
.order-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.oi-img { font-size: 24px; }
.oi-info { flex: 1; }
.oi-name { font-size: 14px; font-weight: 500; color: var(--text-1); }
.oi-unit { font-size: 12px; color: var(--text-4); margin-top: 2px; }
.oi-subtotal { font-size: 14px; font-weight: 600; color: var(--text-1); }

/* 价格 */
.price-rows { display: flex; flex-direction: column; gap: 8px; }
.price-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: var(--text-3);
}
.price-row.total { font-size: 15px; font-weight: 600; color: var(--text-1); }
.price-divider { height: 1px; background: var(--border); margin: 4px 0; }
.total-price { color: var(--price); font-size: 18px; font-weight: 700; }

/* 订单信息 */
.info-rows { display: flex; flex-direction: column; gap: 8px; }
.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  gap: 16px;
}
.info-key { color: var(--text-4); flex-shrink: 0; }
.info-val { color: var(--text-2); text-align: right; word-break: break-all; }

/* 操作区域 */
.action-area {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: #fff;
  margin-top: 10px;
}
.action-cancel-btn {
  flex: 1;
  height: 46px;
  border-radius: var(--radius-full) !important;
  border-color: var(--border) !important;
  color: var(--text-3) !important;
  font-size: 15px !important;
}
.action-primary-btn {
  flex: 1;
  height: 46px;
  border-radius: var(--radius-full) !important;
  background: var(--primary-gradient) !important;
  border: none !important;
  color: #fff !important;
  font-size: 15px !important;
  font-weight: 600 !important;
}
</style>
