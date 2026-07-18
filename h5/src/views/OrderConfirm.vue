<template>
  <div class="confirm-page">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />

    <!-- 收货地址 -->
    <div class="section address-section" @click="router.push(`/address?fromOrder=true${selectedAddress ? '&selectedId=' + selectedAddress.id : ''}`)">
      <div v-if="selectedAddress" class="address-card">
        <van-icon name="location" color="#FF6B35" size="20" class="addr-icon" />
        <div class="addr-info">
          <div class="addr-name-row">
            <span class="addr-name">{{ selectedAddress.receiver }}</span>
            <span class="addr-phone">{{ selectedAddress.phone }}</span>
          </div>
          <div class="addr-detail">
            {{ selectedAddress.province }}{{ selectedAddress.city }}{{ selectedAddress.district }}{{ selectedAddress.detail }}
          </div>
        </div>
        <van-icon name="arrow" color="#CCC" />
      </div>
      <div v-else class="no-address">
        <van-icon name="location-o" size="20" color="#CCC" />
        <span>添加收货地址</span>
        <van-icon name="arrow" color="#CCC" />
      </div>
    </div>

    <!-- 商家信息 -->
    <div class="section merchant-section">
      <div class="section-title">
        <van-icon name="shop-o" size="14" color="#FF6B35" />
        <span>{{ merchantName || '商家' }}</span>
      </div>

      <!-- 商品列表 -->
      <div class="order-items">
        <div
          v-for="item in cartStore.items"
          :key="item.id"
          class="order-item"
        >
          <div class="oi-img-wrap">
            <van-image
              :src="item.dishImage || item.imageUrl"
              fit="cover"
              class="oi-img"
            >
              <template #error><div class="oi-img-ph">🍽️</div></template>
            </van-image>
          </div>
          <div class="oi-info">
            <div class="oi-name">{{ item.dishName }}</div>
            <div class="oi-spec" v-if="item.spec">{{ item.spec }}</div>
          </div>
          <div class="oi-right">
            <span class="oi-price">¥{{ Number(item.unitPrice ?? item.price ?? 0).toFixed(2) }}</span>
            <span class="oi-qty">×{{ item.quantity }}</span>
          </div>
        </div>
      </div>

      <!-- 备注 -->
      <div class="remark-wrap">
        <van-field
          v-model="remark"
          label="备注"
          placeholder="口味、偏好等特殊要求（选填）"
          :border="false"
          rows="1"
          autosize
          type="textarea"
        />
      </div>
    </div>

    <!-- 优惠券 -->
    <div class="section coupon-section" @click="showCouponPicker = true">
      <div class="coupon-row">
        <span class="coupon-label">
          <span style="font-size: 18px; margin-right: 6px">🎫</span>优惠券
        </span>
        <div class="coupon-right">
          <span v-if="selectedCoupon" class="coupon-selected">-¥{{ Number(selectedCoupon.discount).toFixed(2) }}</span>
          <span v-else-if="usableCoupons.length" class="coupon-available">{{ usableCoupons.length }}张可用</span>
          <span v-else class="coupon-none">暂无可用</span>
          <van-icon name="arrow" color="#CCC" size="14" style="margin-left: 4px" />
        </div>
      </div>
    </div>

    <!-- 价格明细 -->
    <div class="section price-section">
      <div class="section-title">费用明细</div>
      <div class="price-rows">
        <div class="price-row">
          <span>商品金额</span>
          <span>¥{{ cartStore.totalPrice.toFixed(2) }}</span>
        </div>
        <div class="price-row">
          <span>配送费</span>
          <span>¥{{ deliveryFee.toFixed(2) }}</span>
        </div>
        <div v-if="selectedCoupon" class="price-row price-row--discount">
          <span>优惠券抵扣</span>
          <span>-¥{{ Number(selectedCoupon.discount).toFixed(2) }}</span>
        </div>
      </div>
      <div class="price-total-row">
        <span>实付金额</span>
        <span class="total-price">¥{{ totalAmount }}</span>
      </div>
    </div>

    <!-- 优惠券选择弹层 -->
    <van-popup v-model:show="showCouponPicker" position="bottom" round :style="{ maxHeight: '60vh' }">
      <div class="coupon-popup">
        <div class="cp-header">
          <span class="cp-title">选择优惠券</span>
          <van-icon name="cross" size="18" @click="showCouponPicker = false" />
        </div>
        <div class="cp-list">
          <div
            class="cp-item"
            :class="{ 'cp-item--selected': selectedCoupon === null }"
            @click="selectCoupon(null)"
          >
            <div class="cp-item-info">不使用优惠券</div>
            <van-icon v-if="!selectedCoupon" name="success" color="var(--primary)" size="18" />
          </div>
          <div
            v-for="c in usableCoupons"
            :key="c.id"
            class="cp-item"
            :class="{ 'cp-item--selected': selectedCoupon?.id === c.id }"
            @click="selectCoupon(c)"
          >
            <div class="cp-left">
              <div class="cp-discount">¥{{ c.discount }}</div>
              <div class="cp-min">满{{ c.minOrderPrice }}元</div>
            </div>
            <div class="cp-item-info">
              <div class="cp-item-title">{{ c.title }}</div>
              <div class="cp-item-valid">有效期至 {{ c.validEnd }}</div>
            </div>
            <van-icon v-if="selectedCoupon?.id === c.id" name="success" color="var(--primary)" size="18" />
          </div>
        </div>
      </div>
    </van-popup>

    <div style="height: 80px" />

    <!-- 底部提交栏 -->
    <div class="submit-bar">
      <div class="submit-price-wrap">
        <span class="submit-label">实付</span>
        <span class="submit-price">
          <span class="sym">¥</span>{{ totalAmount }}
        </span>
      </div>
      <van-button
        class="submit-btn"
        :loading="submitting"
        loading-text="提交中..."
        @click="handleSubmit"
      >
        提交订单
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { getAddresses, submitOrder, getMerchantDetail, getUsableCoupons } from '../api'
import { useCartStore } from '../store/cart'

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()

const addresses = ref([])
const selectedAddress = ref(null)
const remark = ref(route.query.remark || '')
const submitting = ref(false)
const deliveryFee = ref(3)
const merchantName = ref('')
const usableCoupons = ref([])
const selectedCoupon = ref(null)
const showCouponPicker = ref(false)

const totalAmount = computed(() => {
  const base = cartStore.totalPrice + deliveryFee.value
  const discount = selectedCoupon.value ? Number(selectedCoupon.value.discount) : 0
  return Math.max(0, base - discount).toFixed(2)
})

const selectCoupon = (coupon) => {
  selectedCoupon.value = coupon
  showCouponPicker.value = false
}

onMounted(async () => {
  try {
    const [addrRes] = await Promise.all([getAddresses()])
    addresses.value = addrRes.data || []
  } catch (e) {
    if (!(e && (e.response?.status === 401 || e.message?.includes('登录已过期') || e.message?.includes('未登录')))) {
      showToast('加载地址失败')
    }
  }

  // 从地址选择页返回时，优先使用 URL 中传入的 selectedAddressId（保持字符串比较，避免 Snowflake ID 精度丢失）
  const qSelectedIdStr = route.query.selectedAddressId || null
  if (qSelectedIdStr) {
    selectedAddress.value = addresses.value.find(a => String(a.id) === qSelectedIdStr) || null
  }
  if (!selectedAddress.value) {
    selectedAddress.value = addresses.value.find(a => a.isDefault) || addresses.value[0] || null
  }

  if (cartStore.currentMerchantId) {
    try {
      const mr = await getMerchantDetail(cartStore.currentMerchantId)
      merchantName.value = mr.data.name || ''
      deliveryFee.value = Number(mr.data.deliveryFee) || 3
    } catch (e) {
      if (!(e && (e.response?.status === 401 || e.message?.includes('登录已过期') || e.message?.includes('未登录')))) {
        showToast('加载商家信息失败')
      }
    }
  }
  // Load usable coupons after price is known
  try {
    const orderPrice = cartStore.totalPrice + deliveryFee.value
    const couponRes = await getUsableCoupons(orderPrice)
    usableCoupons.value = couponRes.data || []
    // Auto-select the one with the largest discount
    if (usableCoupons.value.length) {
      selectedCoupon.value = usableCoupons.value.reduce((best, c) =>
        Number(c.discount) > Number(best.discount) ? c : best
      )
    }
  } catch (e) {
    if (!(e && (e.response?.status === 401 || e.message?.includes('登录已过期') || e.message?.includes('未登录')))) {
      showToast('加载优惠券失败')
    }
  }
})

const handleSubmit = async () => {
  if (!selectedAddress.value) {
    showToast('请先添加收货地址')
    router.push('/address')
    return
  }
  if (cartStore.items.length === 0) {
    showToast('购物车为空')
    return
  }
  submitting.value = true
  try {
    const res = await submitOrder({
      merchantId: cartStore.currentMerchantId,
      addressId: selectedAddress.value.id,
      remark: remark.value,
      userCouponId: selectedCoupon.value ? String(selectedCoupon.value.id) : null,
      items: cartStore.items.map(i => ({
        dishId: i.dishId,
        quantity: i.quantity,
        spec: i.spec || null
      }))
    })
    const { orderNo } = res.data
    await cartStore.clearCart()
    showToast({ message: '下单成功！', icon: 'checked' })
    router.push(`/pay/${orderNo}`)
  } catch (e) {
    // 401 错误已在拦截器中处理
    if (!(e && (e.response?.status === 401 || e.message?.includes('登录已过期') || e.message?.includes('未登录')))) {
      showToast(e?.message || '提交失败，请重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.confirm-page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 80px;
}

/* 通用区块 */
.section {
  background: #fff;
  margin-top: 10px;
  padding: 16px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12px;
}

/* 地址区块 */
.address-section {
  margin-top: 0;
  cursor: pointer;
}
.address-card {
  display: flex;
  align-items: center;
  gap: 10px;
}
.addr-icon { flex-shrink: 0; }
.addr-info { flex: 1; min-width: 0; }
.addr-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.no-address {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--text-4);
  cursor: pointer;
}
.no-address span { flex: 1; }

/* 商品列表 */
.merchant-section {
  margin-top: 10px;
}
.order-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}
.order-item {
  display: flex;
  gap: 10px;
  align-items: center;
}
.oi-img-wrap { flex-shrink: 0; }
.oi-img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.oi-img-ph {
  width: 56px;
  height: 56px;
  background: #F5F5F5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  border-radius: var(--radius-sm);
}
.oi-info {
  flex: 1;
  min-width: 0;
}
.oi-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.oi-spec {
  font-size: 11px;
  color: var(--text-4);
  background: #F5F5F5;
  display: inline-block;
  padding: 1px 5px;
  border-radius: 3px;
  margin-top: 3px;
}
.oi-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
}
.oi-price {
  font-size: 14px;
  font-weight: 600;
  color: var(--price);
}
.oi-qty {
  font-size: 12px;
  color: var(--text-4);
}

.remark-wrap {
  border-top: 1px solid var(--border);
  margin-top: 12px;
  --van-cell-background: transparent;
}

/* 支付方式 */
.pay-section { margin-top: 10px; }
.pay-methods {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.pay-method {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
}
.pay-method:last-child { border-bottom: none; }
.pay-icon { font-size: 22px; }
.pay-name {
  flex: 1;
  font-size: 14px;
  color: var(--text-1);
}
.pay-check { margin-left: auto; }

/* 价格明细 */
.price-section { margin-top: 10px; }
.price-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 10px;
}
.price-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: var(--text-3);
}
.price-total-row {
  display: flex;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid var(--border);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}
.total-price {
  color: var(--price);
  font-size: 18px;
}

/* 优惠券 */
.coupon-section { margin-top: 10px; cursor: pointer; }
.coupon-row { display: flex; align-items: center; justify-content: space-between; }
.coupon-label { font-size: 15px; font-weight: 600; color: var(--text-1); display: flex; align-items: center; }
.coupon-right { display: flex; align-items: center; }
.coupon-selected { font-size: 15px; font-weight: 700; color: var(--price); }
.coupon-available { font-size: 13px; color: var(--primary); }
.coupon-none { font-size: 13px; color: var(--text-4); }
.price-row--discount { color: var(--price) !important; }
.price-row--discount span:last-child { font-weight: 600; }

/* 优惠券弹层 */
.coupon-popup { padding: 0 0 16px; }
.cp-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 16px; border-bottom: 1px solid var(--border);
}
.cp-title { font-size: 16px; font-weight: 700; color: var(--text-1); }
.cp-list { overflow-y: auto; max-height: calc(60vh - 50px); padding: 8px 0; }
.cp-item {
  display: flex; align-items: center; gap: 12px; padding: 12px 16px;
  cursor: pointer; transition: background 0.15s;
}
.cp-item:active { background: #fafafa; }
.cp-item--selected { background: #fff8f5; }
.cp-left {
  min-width: 60px; text-align: center;
  background: var(--primary-gradient); border-radius: 8px; padding: 6px 8px;
}
.cp-discount { font-size: 18px; font-weight: 700; color: #fff; }
.cp-min { font-size: 10px; color: rgba(255,255,255,0.85); white-space: nowrap; }
.cp-item-info { flex: 1; min-width: 0; }
.cp-item-title { font-size: 14px; font-weight: 600; color: var(--text-1); margin-bottom: 2px; }
.cp-item-valid { font-size: 11px; color: var(--text-4); }

/* 提交栏 */
.submit-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 480px;
  margin: 0 auto;
  height: 68px;
  background: #fff;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06);
}
.submit-price-wrap {
  display: flex;
  flex-direction: column;
}
.submit-label {
  font-size: 12px;
  color: var(--text-4);
}
.submit-price {
  font-size: 22px;
  font-weight: 800;
  color: var(--price);
}
.sym { font-size: 14px; }
.submit-btn {
  background: var(--primary-gradient) !important;
  border: none !important;
  color: #fff !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  border-radius: var(--radius-full) !important;
  padding: 0 32px !important;
  height: 46px !important;
}
</style>
