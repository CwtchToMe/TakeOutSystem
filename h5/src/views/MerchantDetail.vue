<template>
  <div class="merchant-detail">
    <!-- 顶部导航栏（滚动后显示） -->
    <div class="nav-bar" :class="{ scrolled: scrolled }">
      <button class="back-btn" @click="router.back()">
        <van-icon name="arrow-left" size="20" />
      </button>
      <span class="nav-title" v-show="scrolled">{{ merchant?.name }}</span>
      <div class="nav-right">
        <van-icon
          :name="isFavorited ? 'like' : 'like-o'"
          size="22"
          :color="isFavorited ? '#FF6B35' : (scrolled ? '#333' : '#fff')"
          :style="{ filter: scrolled ? 'none' : 'drop-shadow(0 1px 3px rgba(0,0,0,0.3))' }"
          @click="toggleFavorite"
        />
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="pageLoading" class="page-loading">
      <van-loading size="32" color="#FF6B35" />
    </div>

    <template v-else>
      <!-- 商家封面区 -->
      <div class="hero-section" ref="heroRef">
        <div class="hero-bg" :style="heroStyle">
          <div class="hero-overlay"></div>
        </div>
        <!-- 商家核心信息卡片 -->
        <div class="merchant-card">
          <div class="card-left">
            <div class="merchant-logo-wrap">
              <van-image
                :src="merchant?.logoUrl || merchant?.logo"
                fit="cover"
                class="merchant-logo"
              >
                <template #error>
                  <div class="logo-placeholder">{{ merchant?.name?.slice(0, 1) }}</div>
                </template>
              </van-image>
            </div>
          </div>
          <div class="card-right">
            <h1 class="merchant-name">{{ merchant?.name }}</h1>
            <div class="merchant-score">
              <van-icon name="star" color="#FFB800" size="14" />
              <span class="score-num">{{ merchant?.score ? Number(merchant.score).toFixed(1) : '暂无' }}</span>
              <span class="score-sales">月销 {{ merchant?.salesCount || 0 }}</span>
            </div>
            <div class="merchant-desc" v-if="merchant?.description">
              {{ merchant.description }}
            </div>
          </div>
        </div>

        <!-- 配送信息行 -->
        <div class="delivery-row">
          <div class="delivery-item">
            <van-icon name="logistics" size="14" color="#FF6B35" />
            <span>配送费 ¥{{ merchant?.deliveryFee }}</span>
          </div>
          <div class="divider-v"></div>
          <div class="delivery-item">
            <van-icon name="cart-o" size="14" color="#FF6B35" />
            <span>起送 ¥{{ merchant?.minOrderPrice }}</span>
          </div>
          <div class="divider-v"></div>
          <div class="delivery-item">
            <van-icon name="clock-o" size="14" color="#FF6B35" />
            <span>约 {{ merchant?.deliveryTime || 30 }} 分钟</span>
          </div>
        </div>
      </div>

      <!-- 评价入口 -->
      <div class="review-entry" @click="router.push(`/merchant-reviews/${merchantId}`)">
        <div class="re-left">
          <van-icon name="star" color="#FFB800" size="15" />
          <span class="re-score">{{ merchant?.score ? Number(merchant.score).toFixed(1) : '暂无评分' }}</span>
          <span class="re-label">用户评价</span>
        </div>
        <van-icon name="arrow" color="#CCC" size="14" />
      </div>

      <!-- 菜单区域（左侧分类 + 右侧菜品） -->
      <div class="menu-wrap">
        <!-- 左侧分类导航 -->
        <div class="category-nav" ref="categoryNavRef">
          <div
            v-for="(cat, idx) in menu"
            :key="cat.id ?? cat.categoryId ?? idx"
            class="cat-item"
            :class="{ active: activeCatIdx === idx }"
            @click="selectCategory(idx)"
          >
            <span class="cat-name">{{ cat.name || cat.categoryName }}</span>
            <span class="cat-count">{{ cat.dishes?.length || 0 }}</span>
          </div>
        </div>

        <!-- 右侧菜品列表 -->
        <div class="dish-scroll" ref="dishScrollRef" @scroll="onDishScroll">
          <div
            v-for="(cat, idx) in menu"
            :key="cat.id ?? cat.categoryId ?? idx"
            :ref="el => catRefs[idx] = el"
          >
            <!-- 分类标题 -->
            <div class="cat-section-title">{{ cat.name || cat.categoryName }}</div>

            <!-- 菜品卡片 -->
            <div
              v-for="dish in cat.dishes"
              :key="dish.id"
              class="dish-card"
              :class="{ 'dish-sold-out': dish.stock === 0 }"
            >
              <!-- 菜品图片 -->
              <div class="dish-img-wrap">
                <van-image
                  :src="dish.imageUrl || dish.dishImage"
                  fit="cover"
                  class="dish-img"
                >
                  <template #error>
                    <div class="dish-img-placeholder">🍽️</div>
                  </template>
                </van-image>
                <div v-if="dish.stock === 0" class="sold-out-mask">已售罄</div>
              </div>

              <!-- 菜品信息 -->
              <div class="dish-info">
                <div class="dish-name">{{ dish.name || dish.dishName }}</div>
                <div class="dish-desc" v-if="dish.description">{{ dish.description }}</div>
                <div class="dish-meta">
                  <van-icon name="fire-o" size="11" color="#FF9500" v-if="(dish.sales || 0) > 50" />
                  <span class="dish-sales" v-if="(dish.sales || 0) > 0">月售 {{ dish.sales }}</span>
                </div>
                <div class="dish-bottom">
                  <div class="dish-price">
                    <span class="price-symbol">¥</span>
                    <span class="price-num">{{ Number(dish.price || dish.dishPrice || 0).toFixed(2) }}</span>
                  </div>
                  <!-- 规格/数量控制 -->
                  <div class="dish-action">
                    <template v-if="dish.stock === 0">
                      <span class="sold-out-text">售罄</span>
                    </template>
                    <template v-else-if="getCartCount(dish.id || dish.dishId) > 0">
                      <div class="stepper-wrap">
                        <button class="step-btn minus" @click="decreaseDish(dish)">
                          <van-icon name="minus" size="14" />
                        </button>
                        <span class="step-count">{{ getCartCount(dish.id || dish.dishId) }}</span>
                        <button class="step-btn plus" @click="increaseDish(dish)">
                          <van-icon name="plus" size="14" />
                        </button>
                      </div>
                    </template>
                    <template v-else>
                      <button class="add-btn" @click="increaseDish(dish)">
                        <van-icon name="plus" size="16" color="#fff" />
                      </button>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部间距（避免被购物车栏遮挡） -->
          <div style="height: 80px" />
        </div>
      </div>
    </template>

    <!-- 浮动购物车栏 -->
    <transition name="cart-bar-trans">
      <div class="cart-bar" v-if="cartStore.totalCount > 0">
        <div class="cart-icon-wrap" @click="router.push('/cart')">
          <div class="cart-icon-bg">
            <van-icon name="cart" size="24" color="#fff" />
          </div>
          <van-badge :content="cartStore.totalCount" max="99" class="cart-badge" />
        </div>
        <div class="cart-price-wrap" @click="router.push('/cart')">
          <span class="cart-total">
            <span class="total-symbol">¥</span>
            <span class="total-num">{{ cartStore.totalPrice.toFixed(2) }}</span>
          </span>
          <span class="cart-min" v-if="merchant?.minOrderPrice">
            还差¥{{ Math.max(0, Number(merchant.minOrderPrice) - cartStore.totalPrice).toFixed(0) }}起送
          </span>
        </div>
        <button class="checkout-btn" @click="router.push('/order/confirm')">去结算</button>
      </div>
    </transition>

    <!-- 规格选择弹窗 -->
    <van-action-sheet v-model:show="specSheetShow" :title="specDish?.name">
      <div class="spec-sheet">
        <div class="spec-dish-info">
          <van-image :src="specDish?.imageUrl" fit="cover" width="60" height="60" radius="8" />
          <div style="flex: 1">
            <div class="spec-name">{{ specDish?.name }}</div>
            <div class="spec-price">¥{{ specDish?.price }}</div>
          </div>
        </div>
        <div class="spec-section">
          <div class="spec-label">规格</div>
          <div class="spec-options">
            <div
              v-for="sp in specDish?.specs"
              :key="sp.id"
              class="spec-opt"
              :class="{ selected: selectedSpec?.id === sp.id }"
              @click="selectedSpec = sp"
            >
              {{ sp.value || sp.name }}
              <span v-if="sp.price > 0">+¥{{ sp.price }}</span>
            </div>
          </div>
        </div>
        <van-button block type="primary" round class="spec-confirm-btn" @click="confirmSpec">
          加入购物车
        </van-button>
      </div>
    </van-action-sheet>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getMerchantDetail, getMenu, checkFavorite, addFavorite, removeFavorite } from '../api'
import { useCartStore } from '../store/cart'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const merchantId = Number(route.params.id)
const merchant = ref(null)
const menu = ref([])
const activeCatIdx = ref(0)
const pageLoading = ref(true)
const scrolled = ref(false)
const heroRef = ref(null)
const categoryNavRef = ref(null)
const dishScrollRef = ref(null)
const catRefs = reactive([])
let scrolling = false

// 收藏
const isFavorited = ref(false)
let togglingFav = false

// 规格弹窗
const specSheetShow = ref(false)
const specDish = ref(null)
const selectedSpec = ref(null)

const heroStyle = computed(() => {
  const url = merchant.value?.logoUrl || merchant.value?.logo
  if (url) return { backgroundImage: `url(${url})`, backgroundSize: 'cover', backgroundPosition: 'center' }
  return { background: 'var(--primary-gradient)' }
})

onMounted(async () => {
  try {
    const [mr, menuRes] = await Promise.all([
      getMerchantDetail(merchantId),
      getMenu(merchantId)
    ])
    merchant.value = mr.data
    menu.value = menuRes.data || []
    await cartStore.loadCart(merchantId)
  } catch (e) {
    if (e && (e.response?.status === 401 || e.message?.includes('登录已过期') || e.message?.includes('未登录'))) {
      return
    }
    showToast('加载商家信息失败')
  } finally {
    pageLoading.value = false
  }
  try {
    const favRes = await checkFavorite(merchantId)
    isFavorited.value = !!favRes.data
  } catch {
    // 收藏状态查询失败不影响主流程
  }

  // 监听页面滚动判断是否显示 NavBar 标题
  const container = document.querySelector('.merchant-detail')
  if (container) {
    container.addEventListener('scroll', () => {
      scrolled.value = container.scrollTop > 120
    })
  }
})

const toggleFavorite = async () => {
  if (togglingFav) return
  togglingFav = true
  const prev = isFavorited.value
  isFavorited.value = !prev
  try {
    if (prev) {
      await removeFavorite(merchantId)
      showToast('已取消收藏')
    } else {
      await addFavorite(merchantId)
      showToast({ message: '已收藏', icon: 'like-o' })
    }
  } catch {
    isFavorited.value = prev
    showToast('操作失败，请重试')
  } finally {
    togglingFav = false
  }
}

const getCartCount = (dishId) => {
  const item = cartStore.items.find(i => i.dishId === dishId)
  return item ? item.quantity : 0
}

const increaseDish = async (dish) => {
  const id = dish.id || dish.dishId
  const specs = dish.specs || []
  if (specs.length > 0) {
    specDish.value = dish
    selectedSpec.value = specs[0]
    specSheetShow.value = true
    return
  }
  const existing = cartStore.items.find(i => i.dishId === id)
  const qty = existing ? existing.quantity + 1 : 1
  if (!existing) {
    await cartStore.addItem({
      merchantId,
      dishId: id,
      dishName: dish.name || dish.dishName,
      dishImage: dish.imageUrl || dish.dishImage,
      unitPrice: dish.price,
      quantity: 1
    })
  } else {
    await cartStore.changeQuantity(existing.id, qty)
  }
}

const decreaseDish = async (dish) => {
  const id = dish.id || dish.dishId
  const existing = cartStore.items.find(i => i.dishId === id)
  if (!existing) return
  await cartStore.changeQuantity(existing.id, Math.max(0, existing.quantity - 1))
}

const confirmSpec = async () => {
  if (!specDish.value) return
  const dish = specDish.value
  const id = dish.id || dish.dishId
  const existing = cartStore.items.find(i => i.dishId === id)
  const qty = existing ? existing.quantity + 1 : 1
  if (!existing) {
    await cartStore.addItem({
      merchantId,
      dishId: id,
      dishName: dish.name || dish.dishName,
      dishImage: dish.imageUrl,
      unitPrice: Number(dish.price) + (selectedSpec.value?.price || 0),
      quantity: 1,
      spec: selectedSpec.value?.value || selectedSpec.value?.name
    })
  } else {
    await cartStore.changeQuantity(existing.id, qty)
  }
  specSheetShow.value = false
  showToast({ message: '已加入购物车', icon: 'checked', duration: 800 })
}

const selectCategory = (idx) => {
  activeCatIdx.value = idx
  scrolling = true
  const el = catRefs[idx]
  if (el && dishScrollRef.value) {
    dishScrollRef.value.scrollTo({ top: el.offsetTop, behavior: 'smooth' })
  }
  setTimeout(() => { scrolling = false }, 500)

  // 同步分类导航滚动
  const catNav = categoryNavRef.value
  if (catNav) {
    const items = catNav.querySelectorAll('.cat-item')
    if (items[idx]) {
      items[idx].scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }
}

const onDishScroll = () => {
  if (scrolling) return
  const scrollTop = dishScrollRef.value?.scrollTop || 0
  for (let i = catRefs.length - 1; i >= 0; i--) {
    if (catRefs[i] && catRefs[i].offsetTop <= scrollTop + 60) {
      if (activeCatIdx.value !== i) {
        activeCatIdx.value = i
        // 同步左侧分类导航
        const catNav = categoryNavRef.value
        if (catNav) {
          const items = catNav.querySelectorAll('.cat-item')
          if (items[i]) items[i].scrollIntoView({ behavior: 'smooth', block: 'nearest' })
        }
      }
      break
    }
  }
}
</script>

<style scoped>
.merchant-detail {
  min-height: 100vh;
  background: var(--bg);
  position: relative;
  overflow-y: auto;
}

/* 顶部导航栏 */
.nav-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  max-width: 480px;
  margin: 0 auto;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  z-index: 200;
  transition: background 0.25s, box-shadow 0.25s;
}
.nav-bar.scrolled {
  background: #fff;
  box-shadow: 0 1px 8px rgba(0,0,0,0.08);
}
.back-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255,255,255,0.92);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
  backdrop-filter: blur(4px);
}
.nav-bar.scrolled .back-btn {
  background: transparent;
  box-shadow: none;
}
.nav-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.nav-right {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

/* 英雄区域 */
.hero-section {
  padding-top: 52px;
}
.hero-bg {
  height: 180px;
  position: relative;
  overflow: hidden;
  background: var(--primary-gradient);
}
.hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgba(0,0,0,0) 40%, rgba(0,0,0,0.4) 100%);
}

/* 商家信息卡 */
.merchant-card {
  background: #fff;
  margin: -2px 0 0;
  padding: 16px 16px 12px;
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.merchant-logo-wrap {
  flex-shrink: 0;
  margin-top: -32px;
  position: relative;
}
.merchant-logo {
  width: 68px;
  height: 68px;
  border-radius: 14px;
  border: 3px solid #fff;
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
  overflow: hidden;
}
.logo-placeholder {
  width: 68px;
  height: 68px;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 800;
  color: #fff;
}
.card-right {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}
.merchant-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
  line-height: 1.3;
}
.merchant-score {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
}
.score-num {
  font-size: 13px;
  font-weight: 700;
  color: #FF9500;
}
.score-sales {
  font-size: 12px;
  color: var(--text-4);
  margin-left: 4px;
}
.merchant-desc {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 配送信息行 */
.delivery-row {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 0 14px;
  border-top: 1px solid var(--border);
  margin-top: 0;
}
.delivery-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-3);
  flex: 1;
  justify-content: center;
}
.divider-v {
  width: 1px;
  height: 14px;
  background: var(--border);
}

/* 评价入口 */
.review-entry {
  background: #fff; padding: 10px 16px; margin-top: 1px;
  display: flex; align-items: center; justify-content: space-between;
  cursor: pointer; border-top: 1px solid var(--border);
}
.re-left { display: flex; align-items: center; gap: 6px; }
.re-score { font-size: 14px; font-weight: 700; color: #FF9500; }
.re-label { font-size: 13px; color: var(--text-3); }

/* 菜单主体 */
.menu-wrap {
  display: flex;
  height: calc(100vh - 52px - 180px - 90px - 60px);
  min-height: 400px;
  margin-top: 8px;
  overflow: hidden;
}

/* 左侧分类导航 */
.category-nav {
  width: 82px;
  background: #F5F5F5;
  overflow-y: auto;
  flex-shrink: 0;
}
.cat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 6px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: all 0.15s;
  position: relative;
}
.cat-item.active {
  background: #fff;
  border-left-color: var(--primary);
}
.cat-item.active .cat-name {
  color: var(--primary);
  font-weight: 600;
}
.cat-name {
  font-size: 12px;
  color: var(--text-3);
  text-align: center;
  line-height: 1.3;
  word-break: break-all;
}
.cat-count {
  font-size: 10px;
  color: var(--text-5);
  margin-top: 2px;
}

/* 右侧菜品列表 */
.dish-scroll {
  flex: 1;
  background: #fff;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.cat-section-title {
  padding: 12px 14px 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-3);
  background: #F7F8FA;
  border-top: 1px solid var(--border);
}
.cat-section-title:first-child {
  border-top: none;
}

.dish-card {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-light);
  transition: background 0.15s;
}
.dish-card:active {
  background: #FAFAFA;
}
.dish-sold-out {
  opacity: 0.6;
}

.dish-img-wrap {
  flex-shrink: 0;
  position: relative;
}
.dish-img {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.dish-img-placeholder {
  width: 80px;
  height: 80px;
  background: #F5F5F5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  border-radius: var(--radius-sm);
}
.sold-out-mask {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  border-radius: var(--radius-sm);
}

.dish-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.dish-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.4;
}
.dish-desc {
  font-size: 12px;
  color: var(--text-4);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.dish-meta {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-top: 4px;
}
.dish-sales {
  font-size: 11px;
  color: var(--text-4);
}
.dish-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 6px;
}
.dish-price {
  display: flex;
  align-items: baseline;
  gap: 1px;
}
.price-symbol {
  font-size: 12px;
  font-weight: 600;
  color: var(--price);
}
.price-num {
  font-size: 18px;
  font-weight: 700;
  color: var(--price);
}
.sold-out-text {
  font-size: 12px;
  color: var(--text-5);
}

/* 加减控件 */
.stepper-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.step-btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.1s;
}
.step-btn:active { transform: scale(0.9); }
.step-btn.minus {
  background: #fff;
  border: 1.5px solid var(--primary);
  color: var(--primary);
}
.step-btn.plus {
  background: var(--primary);
  color: #fff;
}
.step-count {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
  min-width: 20px;
  text-align: center;
}

.add-btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--primary-gradient);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(255,107,53,0.4);
  transition: transform 0.1s;
}
.add-btn:active { transform: scale(0.9); }

/* 购物车栏 */
.cart-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 480px;
  margin: 0 auto;
  height: 64px;
  background: #2C2C2C;
  border-radius: 16px 16px 0 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  z-index: 150;
}

.cart-icon-wrap {
  position: relative;
  cursor: pointer;
}
.cart-icon-bg {
  width: 48px;
  height: 48px;
  background: var(--primary-gradient);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: -20px;
  box-shadow: 0 4px 16px rgba(255,107,53,0.5);
}
.cart-badge {
  position: absolute;
  top: -18px;
  right: -4px;
}

.cart-price-wrap {
  flex: 1;
  cursor: pointer;
}
.cart-total {
  display: flex;
  align-items: baseline;
  gap: 1px;
}
.total-symbol {
  font-size: 13px;
  color: #fff;
  font-weight: 600;
}
.total-num {
  font-size: 22px;
  font-weight: 800;
  color: #fff;
}
.cart-min {
  font-size: 11px;
  color: rgba(255,255,255,0.6);
  margin-top: 1px;
  display: block;
}

.checkout-btn {
  background: var(--primary-gradient);
  color: #fff;
  border: none;
  border-radius: var(--radius-full);
  padding: 10px 20px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(255,107,53,0.4);
}

.cart-bar-trans-enter-active, .cart-bar-trans-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}
.cart-bar-trans-enter-from, .cart-bar-trans-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

/* 规格弹窗 */
.spec-sheet {
  padding: 16px 20px 32px;
}
.spec-dish-info {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}
.spec-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}
.spec-price {
  font-size: 18px;
  font-weight: 700;
  color: var(--price);
  margin-top: 4px;
}
.spec-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 10px;
}
.spec-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}
.spec-opt {
  padding: 6px 14px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-full);
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.spec-opt.selected {
  border-color: var(--primary);
  background: var(--primary-bg);
  color: var(--primary);
}
.spec-confirm-btn {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

.page-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60vh;
}
</style>
