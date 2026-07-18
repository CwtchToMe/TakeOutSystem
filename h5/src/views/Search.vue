<template>
  <div class="search-page">
    <!-- 搜索栏 -->
    <div class="search-header">
      <div class="search-bar">
        <van-search
          v-model="keyword"
          placeholder="搜索商家、菜品"
          show-action
          action-text="取消"
          autofocus
          @search="doSearch"
          @cancel="router.back()"
          @input="onInput"
        />
      </div>
    </div>

    <!-- 搜索历史（未搜索时显示） -->
    <div v-if="!searched" class="history-section">
      <div v-if="history.length > 0">
        <div class="section-header">
          <span class="section-title">搜索历史</span>
          <van-icon name="delete-o" size="16" color="#999" @click="clearHistory" />
        </div>
        <div class="history-tags">
          <div
            v-for="(item, idx) in history"
            :key="idx"
            class="history-tag"
            @click="selectHistory(item)"
          >
            {{ item }}
          </div>
        </div>
      </div>

      <!-- 热门搜索 -->
      <div class="hot-section">
        <div class="section-header">
          <span class="section-title">热门搜索</span>
          <span class="section-sub">🔥 大家都在搜</span>
        </div>
        <div class="hot-tags">
          <div
            v-for="(tag, idx) in hotTags"
            :key="idx"
            class="hot-tag"
            :class="{ 'hot-tag-top': idx < 3 }"
            @click="selectHistory(tag)"
          >
            <span v-if="idx < 3" class="hot-rank">{{ idx + 1 }}</span>
            {{ tag }}
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div v-else class="result-section">
      <!-- 加载中骨架屏 -->
      <template v-if="loading">
        <div class="result-skeleton" v-for="i in 3" :key="i">
          <van-skeleton :row="3" />
        </div>
      </template>

      <!-- 无结果 -->
      <div v-else-if="merchants.length === 0 && dishes.length === 0" class="empty-result">
        <div class="empty-icon">🔍</div>
        <div class="empty-text">没有找到"{{ keyword }}"相关结果</div>
        <div class="empty-tip">换个关键词试试吧</div>
      </div>

      <template v-else>
        <!-- 结果 Tab -->
        <van-tabs v-model:active="resultTab" class="result-tabs" sticky offset-top="52px">
          <van-tab :title="`商家 ${merchants.length > 0 ? '(' + merchants.length + ')' : ''}`" name="merchant" />
          <van-tab :title="`菜品 ${dishes.length > 0 ? '(' + dishes.length + ')' : ''}`" name="dish" />
        </van-tabs>

        <!-- 商家结果 -->
        <div v-if="resultTab === 'merchant'">
          <div v-if="merchants.length === 0" class="no-tab-result">未找到相关商家</div>
          <div
            v-for="m in merchants"
            :key="m.id"
            class="merchant-result-card"
            @click="router.push(`/merchant/${m.id}`)"
          >
            <div class="mrc-logo">
              <img v-if="m.logoUrl" :src="m.logoUrl" class="mrc-logo-img" />
              <div v-else class="mrc-logo-fallback">{{ (m.name || '店')[0] }}</div>
            </div>
            <div class="mrc-info">
              <div class="mrc-name" v-html="highlightKeyword(m.name)"></div>
              <div class="mrc-meta">
                <span class="mrc-star">⭐ {{ m.score ? Number(m.score).toFixed(1) : '4.9' }}</span>
                <span class="mrc-dot">·</span>
                <span class="mrc-sales">月售{{ m.salesCount || 0 }}+</span>
              </div>
              <div class="mrc-tags">
                <span class="mrc-tag" v-if="m.description">{{ m.description }}</span>
                <span class="mrc-tag mrc-tag-delivery">{{ m.deliveryTime || 30 }}分钟</span>
              </div>
            </div>
            <div class="mrc-right">
              <div class="mrc-fee">¥{{ m.deliveryFee || 0 }}配送</div>
              <div class="mrc-min">¥{{ m.minOrderPrice || 0 }}起</div>
            </div>
          </div>
        </div>

        <!-- 菜品结果 -->
        <div v-if="resultTab === 'dish'">
          <div v-if="dishes.length === 0" class="no-tab-result">未找到相关菜品</div>
          <div
            v-for="d in dishes"
            :key="d.id"
            class="dish-result-card"
            @click="router.push(`/merchant/${d.merchantId}`)"
          >
            <div class="drc-img">
              <img v-if="d.imageUrl" :src="d.imageUrl" class="drc-img-real" />
              <div v-else class="drc-img-placeholder">🍽️</div>
            </div>
            <div class="drc-info">
              <div class="drc-name" v-html="highlightKeyword(d.name)"></div>
              <div class="drc-merchant">{{ d.merchantName }}</div>
              <div class="drc-bottom">
                <span class="drc-price">¥{{ d.price }}</span>
                <span class="drc-sales" v-if="d.monthlySales">月售{{ d.monthlySales }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { searchMerchants, searchDishes } from '../api'

const router = useRouter()
const route = useRoute()
const keyword = ref(route.query.keyword || '')
const searched = ref(false)
const loading = ref(false)
const merchants = ref([])
const dishes = ref([])
const resultTab = ref('merchant')

const HISTORY_KEY = 'search_history'
const history = ref(JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'))

const hotTags = ['川菜', '粤式早茶', '麻婆豆腐', '虾饺', '叉烧包', '外卖', '早茶', '好味道', '点心', '茶水']

const saveHistory = (kw) => {
  const arr = history.value.filter(i => i !== kw)
  arr.unshift(kw)
  history.value = arr.slice(0, 10)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
}

const clearHistory = () => {
  history.value = []
  localStorage.removeItem(HISTORY_KEY)
}

const selectHistory = (kw) => {
  keyword.value = kw
  doSearch()
}

const onInput = () => {
  if (!keyword.value.trim()) {
    searched.value = false
  }
}

const doSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw) return
  saveHistory(kw)
  searched.value = true
  loading.value = true
  merchants.value = []
  dishes.value = []
  try {
    const [mRes, dRes] = await Promise.allSettled([
      searchMerchants(kw, 1, 20),
      searchDishes(kw, 1, 20)
    ])
    if (mRes.status === 'fulfilled') {
      const data = mRes.value.data
      merchants.value = data.records || data || []
    }
    if (dRes.status === 'fulfilled') {
      const data = dRes.value.data
      dishes.value = data.records || data || []
    }
    resultTab.value = merchants.value.length > 0 ? 'merchant' : 'dish'
  } catch (e) {
    showToast('搜索失败，请重试')
  } finally {
    loading.value = false
  }
}

const highlightKeyword = (text) => {
  if (!text || !keyword.value) return text
  const kw = keyword.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return text.replace(new RegExp(kw, 'gi'), match => `<span class="highlight">${match}</span>`)
}

if (keyword.value) {
  doSearch()
}
</script>

<style scoped>
.search-page {
  min-height: 100vh;
  background: var(--bg);
}

/* 搜索头部 */
.search-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

/* 历史区域 */
.history-section {
  padding: 16px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
}
.section-sub {
  font-size: 12px;
  color: var(--text-4);
}

/* 历史标签 */
.history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}
.history-tag {
  padding: 6px 14px;
  background: var(--bg);
  border-radius: var(--radius-full);
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid var(--border);
}
.history-tag:active { background: var(--primary-bg); color: var(--primary); border-color: var(--primary); }

/* 热门搜索 */
.hot-section {
  margin-top: 4px;
}
.hot-tags {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.hot-tag {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
  border-bottom: 1px solid var(--border);
  transition: color 0.15s;
}
.hot-tag:last-child { border-bottom: none; }
.hot-tag:active { color: var(--primary); }
.hot-rank {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: var(--border);
  color: var(--text-4);
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.hot-tag-top .hot-rank {
  background: var(--primary);
  color: #fff;
}

/* 搜索结果 */
.result-section {}
.result-skeleton {
  background: #fff;
  padding: 16px;
  margin-bottom: 8px;
}
.result-tabs {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.no-tab-result {
  text-align: center;
  padding: 40px 0;
  font-size: 14px;
  color: var(--text-4);
}

/* 空结果 */
.empty-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0 40px;
}
.empty-icon { font-size: 64px; opacity: 0.4; margin-bottom: 12px; }
.empty-text { font-size: 16px; color: var(--text-3); font-weight: 500; }
.empty-tip { font-size: 13px; color: var(--text-4); margin-top: 6px; }

/* 商家结果卡片 */
.merchant-result-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s;
}
.merchant-result-card:active { background: #fafafa; }

.mrc-logo {
  width: 64px;
  height: 64px;
  border-radius: var(--radius);
  overflow: hidden;
  flex-shrink: 0;
}
.mrc-logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mrc-logo-fallback {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: #fff;
}
.mrc-info { flex: 1; min-width: 0; }
.mrc-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 4px;
}
.mrc-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-3);
  margin-bottom: 6px;
}
.mrc-star { color: var(--star); }
.mrc-dot { color: var(--text-5); }
.mrc-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.mrc-tag {
  font-size: 11px;
  padding: 2px 6px;
  background: var(--bg);
  border-radius: 4px;
  color: var(--text-4);
}
.mrc-tag-delivery { color: var(--primary); background: var(--primary-bg); }
.mrc-right {
  text-align: right;
  flex-shrink: 0;
}
.mrc-fee { font-size: 12px; color: var(--text-4); }
.mrc-min { font-size: 12px; color: var(--text-3); margin-top: 2px; }

/* 菜品结果卡片 */
.dish-result-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s;
}
.dish-result-card:active { background: #fafafa; }
.drc-img {
  width: 72px;
  height: 72px;
  border-radius: var(--radius);
  overflow: hidden;
  flex-shrink: 0;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.drc-img-real { width: 100%; height: 100%; object-fit: cover; }
.drc-img-placeholder { font-size: 32px; }
.drc-info { flex: 1; min-width: 0; }
.drc-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
  margin-bottom: 4px;
}
.drc-merchant {
  font-size: 12px;
  color: var(--text-4);
  margin-bottom: 8px;
}
.drc-bottom { display: flex; align-items: center; gap: 8px; }
.drc-price { font-size: 16px; font-weight: 700; color: var(--price); }
.drc-sales { font-size: 11px; color: var(--text-4); }

/* 高亮关键词 */
:deep(.highlight) {
  color: var(--primary);
  font-weight: 600;
}
</style>
