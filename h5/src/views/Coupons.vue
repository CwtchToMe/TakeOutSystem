<template>
  <div class="coupons-page">
    <van-nav-bar title="优惠券" left-arrow @click-left="router.back()" />

    <!-- Tabs: 领券中心 / 我的优惠券 -->
    <van-tabs v-model:active="activeTab" sticky offset-top="46" class="tabs">
      <!-- 领券中心 -->
      <van-tab title="领券中心" name="center">
        <div v-if="loadingAvailable" class="loading-wrap">
          <van-skeleton v-for="i in 3" :key="i" title :row="2" style="margin-bottom: 12px; padding: 16px" />
        </div>
        <van-empty v-else-if="!available.length" image="coupon" description="暂无可领取的优惠券" style="padding-top: 60px" />
        <div v-else class="coupon-list" style="padding: 12px">
          <div v-for="c in available" :key="c.id" class="coupon-item">
            <div class="ci-left">
              <div class="ci-discount">
                <span class="ci-currency">¥</span>
                <span class="ci-amount">{{ c.discount }}</span>
              </div>
              <div class="ci-min">满 {{ c.minOrderPrice }} 可用</div>
            </div>
            <div class="ci-mid">
              <div class="ci-title">{{ c.title }}</div>
              <div class="ci-validity">{{ c.validStart }} - {{ c.validEnd }}</div>
            </div>
            <div class="ci-right">
              <van-button
                size="small"
                class="ci-receive-btn"
                :loading="receiving[c.id]"
                :disabled="received.has(c.id)"
                @click="handleReceive(c)"
              >
                {{ received.has(c.id) ? '已领取' : '立即领取' }}
              </van-button>
            </div>
          </div>
        </div>
      </van-tab>

      <!-- 我的优惠券 -->
      <van-tab title="我的优惠券" name="my">
        <div v-if="loadingMy" class="loading-wrap">
          <van-skeleton v-for="i in 3" :key="i" title :row="2" style="margin-bottom: 12px; padding: 16px" />
        </div>
        <van-empty v-else-if="!myCoupons.length" image="coupon" description="暂无优惠券，快去领取吧" style="padding-top: 60px" />
        <div v-else>
          <!-- 过滤状态 -->
          <div class="filter-bar">
            <span
              v-for="f in filters"
              :key="f.val"
              :class="['filter-tag', activeFilter === f.val && 'filter-tag--active']"
              @click="activeFilter = f.val"
            >{{ f.label }}</span>
          </div>
          <div class="coupon-list" style="padding: 0 12px 12px">
            <div
              v-for="uc in filteredMyCoupons"
              :key="uc.id"
              :class="['coupon-item', uc.status !== 0 && 'coupon-item--used']"
            >
              <div class="ci-left">
                <div class="ci-discount">
                  <span class="ci-currency">¥</span>
                  <span class="ci-amount">{{ uc.discount }}</span>
                </div>
                <div class="ci-min">满 {{ uc.minOrderPrice }} 可用</div>
              </div>
              <div class="ci-mid">
                <div class="ci-title">{{ uc.title }}</div>
                <div class="ci-validity">有效期至 {{ uc.validEnd }}</div>
                <div v-if="uc.status === 1" class="ci-status-tag ci-status-used">已使用</div>
                <div v-else-if="isExpired(uc.validEnd)" class="ci-status-tag ci-status-expired">已过期</div>
              </div>
              <div class="ci-right">
                <div v-if="uc.status === 0 && !isExpired(uc.validEnd)" class="ci-usable-badge">可使用</div>
              </div>
            </div>
          </div>
        </div>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getAvailableCoupons, receiveCoupon, getMyCoupons } from '../api'

const router = useRouter()
const activeTab = ref('center')

// 领券中心
const loadingAvailable = ref(false)
const available = ref([])
const receiving = reactive({})
const received = reactive(new Set())

// 我的优惠券
const loadingMy = ref(false)
const myCoupons = ref([])
const activeFilter = ref('all')
const filters = [
  { val: 'all', label: '全部' },
  { val: 'usable', label: '可用' },
  { val: 'used', label: '已用' },
]

const filteredMyCoupons = computed(() => {
  if (activeFilter.value === 'usable') return myCoupons.value.filter(c => c.status === 0 && !isExpired(c.validEnd))
  if (activeFilter.value === 'used') return myCoupons.value.filter(c => c.status === 1 || isExpired(c.validEnd))
  return myCoupons.value
})

const isExpired = (validEnd) => {
  if (!validEnd) return false
  return new Date(validEnd) < new Date()
}

onMounted(() => {
  loadAvailable()
})

watch(activeTab, (tab) => {
  if (tab === 'my' && !myCoupons.value.length) loadMy()
})

const loadAvailable = async () => {
  loadingAvailable.value = true
  try {
    const res = await getAvailableCoupons()
    available.value = res.data || []
  } catch {
    showToast('加载失败')
  } finally {
    loadingAvailable.value = false
  }
}

const loadMy = async () => {
  loadingMy.value = true
  try {
    const res = await getMyCoupons()
    myCoupons.value = res.data || []
  } catch {
    showToast('加载失败')
  } finally {
    loadingMy.value = false
  }
}

const handleReceive = async (coupon) => {
  receiving[coupon.id] = true
  try {
    await receiveCoupon(coupon.id)
    received.add(coupon.id)
    showToast({ message: '领取成功！', icon: 'checked' })
    if (myCoupons.value.length) loadMy()
  } catch (e) {
    const msg = e?.response?.data?.msg || '领取失败'
    showToast(msg)
  } finally {
    receiving[coupon.id] = false
  }
}
</script>

<style scoped>
.coupons-page { min-height: 100vh; background: var(--bg); }
.loading-wrap { padding: 12px; }

.coupon-list { display: flex; flex-direction: column; gap: 10px; }

.coupon-item {
  display: flex; align-items: center; background: #fff;
  border-radius: var(--radius-md); overflow: hidden;
  box-shadow: 0 1px 6px rgba(0,0,0,0.06);
}
.coupon-item--used { opacity: 0.55; filter: grayscale(0.4); }

.ci-left {
  background: var(--primary-gradient); color: #fff;
  padding: 14px 14px; min-width: 88px; text-align: center;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  align-self: stretch;
}
.ci-currency { font-size: 13px; vertical-align: top; margin-top: 6px; }
.ci-amount { font-size: 28px; font-weight: 700; line-height: 1; }
.ci-min { font-size: 11px; opacity: 0.85; margin-top: 4px; white-space: nowrap; }

.ci-mid {
  flex: 1; padding: 12px 12px; min-width: 0; position: relative;
}
.ci-title { font-size: 14px; font-weight: 600; color: var(--text-1); margin-bottom: 4px; }
.ci-validity { font-size: 11px; color: var(--text-4); }

.ci-status-tag { font-size: 11px; font-weight: 600; margin-top: 4px; }
.ci-status-used { color: #999; }
.ci-status-expired { color: #ccc; }

.ci-right { padding: 12px 12px; flex-shrink: 0; display: flex; align-items: center; }
.ci-receive-btn {
  font-size: 12px; font-weight: 600;
  background: var(--primary-gradient) !important; border: none !important;
  border-radius: 20px !important; color: #fff !important; padding: 0 12px !important;
}
.ci-receive-btn:disabled { background: #ccc !important; }
.ci-usable-badge { font-size: 12px; color: var(--primary); font-weight: 600; }

.filter-bar {
  display: flex; gap: 8px; padding: 10px 12px;
  background: #fff; border-bottom: 1px solid var(--divider);
}
.filter-tag {
  font-size: 12px; padding: 4px 12px; border-radius: 20px;
  background: #f5f5f5; color: var(--text-3); cursor: pointer;
  transition: all 0.2s;
}
.filter-tag--active {
  background: var(--primary); color: #fff;
}
</style>
