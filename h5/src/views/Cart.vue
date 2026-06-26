<template>
  <div class="cart-page">
    <van-nav-bar title="购物车" left-arrow @click-left="router.back()" />

    <!-- 空购物车 -->
    <div v-if="cartStore.items.length === 0" class="empty-cart">
      <div class="empty-icon">🛒</div>
      <div class="empty-text">购物车空空如也</div>
      <div class="empty-sub">快去挑选美味吧~</div>
      <van-button round type="primary" class="go-shop-btn" @click="router.push('/')">
        去选购
      </van-button>
    </div>

    <template v-else>
      <!-- 商家标题 -->
      <div class="shop-header">
        <van-icon name="shop-o" size="16" color="#FF6B35" />
        <span class="shop-name">{{ merchantName }}</span>
      </div>

      <!-- 商品列表 -->
      <div class="item-list">
        <van-swipe-cell
          v-for="item in cartStore.items"
          :key="item.id"
          class="swipe-cell"
        >
          <div class="cart-item">
            <!-- 菜品图片 -->
            <div class="item-img-wrap">
              <van-image
                :src="item.dishImage || item.imageUrl"
                fit="cover"
                class="item-img"
              >
                <template #error>
                  <div class="item-img-placeholder">🍽️</div>
                </template>
              </van-image>
            </div>

            <!-- 菜品信息 -->
            <div class="item-info">
              <div class="item-name">{{ item.dishName }}</div>
              <div class="item-spec" v-if="item.spec">{{ item.spec }}</div>
              <div class="item-price-row">
                <span class="item-price">
                  <span class="price-sym">¥</span>
                  <span>{{ Number(item.unitPrice ?? item.price ?? 0).toFixed(2) }}</span>
                </span>
                <!-- 加减控件 -->
                <div class="item-stepper">
                  <button class="step-minus" @click="handleMinus(item)">
                    <van-icon name="minus" size="12" />
                  </button>
                  <span class="step-qty">{{ item.quantity }}</span>
                  <button class="step-plus" @click="handlePlus(item)">
                    <van-icon name="plus" size="12" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 左滑删除 -->
          <template #right>
            <div class="delete-btn" @click="cartStore.removeItem(item.id)">
              <van-icon name="delete-o" size="20" color="#fff" />
              <span>删除</span>
            </div>
          </template>
        </van-swipe-cell>
      </div>

      <!-- 价格明细 -->
      <div class="price-detail">
        <div class="price-row">
          <span>商品合计</span>
          <span>¥{{ cartStore.totalPrice.toFixed(2) }}</span>
        </div>
        <div class="price-row">
          <span>配送费</span>
          <span class="price-free">¥{{ deliveryFee.toFixed(2) }}</span>
        </div>
        <div class="price-divider"></div>
        <div class="price-row total">
          <span>实付金额</span>
          <span class="total-price">¥{{ (cartStore.totalPrice + deliveryFee).toFixed(2) }}</span>
        </div>
      </div>

      <!-- 备注 -->
      <div class="remark-section">
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
    </template>

    <!-- 底部结算栏 -->
    <div class="checkout-bar" v-if="cartStore.items.length > 0">
      <div class="price-info">
        <span class="checkout-label">合计</span>
        <span class="checkout-price">
          <span class="sym">¥</span>{{ (cartStore.totalPrice + deliveryFee).toFixed(2) }}
        </span>
      </div>
      <van-button
        class="checkout-btn"
        :disabled="cartStore.totalPrice < minOrder"
        @click="goConfirm"
      >
        <span v-if="cartStore.totalPrice < minOrder">
          还差¥{{ (minOrder - cartStore.totalPrice).toFixed(0) }}起送
        </span>
        <span v-else>去结算 ({{ cartStore.totalCount }})</span>
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useCartStore } from '../store/cart'
import { getMerchantDetail } from '../api'

const router = useRouter()
const cartStore = useCartStore()

const remark = ref('')
const merchantName = ref('')
const deliveryFee = ref(3)
const minOrder = ref(0)

onMounted(async () => {
  if (cartStore.currentMerchantId) {
    try {
      const res = await getMerchantDetail(cartStore.currentMerchantId)
      const m = res.data
      merchantName.value = m.name || ''
      deliveryFee.value = Number(m.deliveryFee) || 3
      minOrder.value = Number(m.minOrderPrice) || 0
    } catch (e) {}
  }
})

const handleMinus = async (item) => {
  const qty = item.quantity - 1
  await cartStore.changeQuantity(item.id, qty)
}

const handlePlus = async (item) => {
  await cartStore.changeQuantity(item.id, item.quantity + 1)
}

const goConfirm = () => {
  router.push({ path: '/order/confirm', query: { remark: remark.value } })
}
</script>

<style scoped>
.cart-page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 80px;
}

/* 空状态 */
.empty-cart {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 100px;
  gap: 8px;
}
.empty-icon { font-size: 72px; opacity: 0.5; }
.empty-text { font-size: 16px; font-weight: 600; color: var(--text-3); }
.empty-sub { font-size: 13px; color: var(--text-5); }
.go-shop-btn {
  margin-top: 16px;
  padding: 0 32px;
  background: var(--primary-gradient) !important;
  border: none !important;
}

/* 商家标题 */
.shop-header {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  border-bottom: 1px solid var(--border);
}

/* 商品列表 */
.item-list {
  background: #fff;
  margin-top: 10px;
}

.swipe-cell {
  border-bottom: 1px solid var(--border);
}
.swipe-cell:last-child {
  border-bottom: none;
}

.cart-item {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
}

.item-img-wrap {
  flex-shrink: 0;
}
.item-img {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.item-img-placeholder {
  width: 72px;
  height: 72px;
  background: #F5F5F5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  border-radius: var(--radius-sm);
}

.item-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.item-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-spec {
  font-size: 12px;
  color: var(--text-4);
  background: #F5F5F5;
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  align-self: flex-start;
}
.item-price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}
.item-price {
  color: var(--price);
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: baseline;
  gap: 1px;
}
.price-sym {
  font-size: 12px;
}

/* 加减控件 */
.item-stepper {
  display: flex;
  align-items: center;
  gap: 8px;
}
.step-minus, .step-plus {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.1s;
}
.step-minus:active, .step-plus:active { transform: scale(0.9); }
.step-minus {
  background: #fff;
  border: 1.5px solid var(--primary);
  color: var(--primary);
}
.step-plus {
  background: var(--primary-gradient);
  border: none;
  color: #fff;
}
.step-qty {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
  min-width: 20px;
  text-align: center;
}

/* 左滑删除 */
.delete-btn {
  width: 72px;
  height: 100%;
  background: var(--danger);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
  color: #fff;
  cursor: pointer;
}

/* 价格明细 */
.price-detail {
  background: #fff;
  margin-top: 10px;
  padding: 14px 16px;
}
.price-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: var(--text-3);
  padding: 4px 0;
}
.price-row.total {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  padding-top: 8px;
}
.price-free { color: var(--success); }
.price-divider {
  height: 1px;
  background: var(--border);
  margin: 8px 0;
}
.total-price {
  color: var(--price);
  font-size: 18px;
  font-weight: 700;
}

/* 备注 */
.remark-section {
  background: #fff;
  margin-top: 10px;
  --van-cell-background: #fff;
}

/* 结算栏 */
.checkout-bar {
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
.price-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.checkout-label {
  font-size: 12px;
  color: var(--text-4);
}
.checkout-price {
  font-size: 22px;
  font-weight: 800;
  color: var(--price);
}
.sym {
  font-size: 14px;
}
.checkout-btn {
  background: var(--primary-gradient) !important;
  border: none !important;
  color: #fff !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  border-radius: var(--radius-full) !important;
  padding: 0 28px !important;
  height: 46px !important;
}
.checkout-btn:disabled {
  background: #CCC !important;
  box-shadow: none !important;
}
</style>
