<template>
  <div id="app">
    <router-view v-slot="{ Component, route }">
      <transition :name="route.meta.transition || 'fade'" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>
</template>

<script setup></script>

<style>
:root {
  --primary: #FF6B35;
  --primary-light: #FF8C5A;
  --primary-dark: #E55A26;
  --primary-bg: #FFF3EE;
  --primary-gradient: linear-gradient(135deg, #FF6B35 0%, #FF4500 100%);

  --danger: #FF3B30;
  --success: #07C160;
  --warning: #FF9500;
  --info: #007AFF;
  --star: #FFB800;

  --bg: #F5F5F5;
  --bg-white: #FFFFFF;
  --border: #EBEBEB;

  --shadow-xs: 0 1px 4px rgba(0,0,0,0.04);
  --shadow-sm: 0 2px 8px rgba(0,0,0,0.06);
  --shadow: 0 4px 16px rgba(0,0,0,0.08);
  --shadow-lg: 0 8px 32px rgba(0,0,0,0.12);

  --text-1: #1A1A1A;
  --text-2: #333333;
  --text-3: #666666;
  --text-4: #999999;
  --text-5: #BBBBBB;
  --price: #FF3B30;

  --radius-xs: 4px;
  --radius-sm: 8px;
  --radius: 12px;
  --radius-lg: 16px;
  --radius-full: 999px;

  --header-height: 52px;
  --tabbar-height: 56px;

  --van-primary-color: #FF6B35;
  --van-button-primary-background: #FF6B35;
  --van-button-primary-border-color: #FF6B35;
  --van-tabs-bottom-bar-color: #FF6B35;
  --van-tab-active-text-color: #FF6B35;
  --van-checkbox-checked-icon-color: #FF6B35;
  --van-radio-checked-icon-color: #FF6B35;
  --van-stepper-button-icon-color: #FF6B35;
  --van-swipe-indicator-active-background: #FF6B35;
  --van-tabbar-item-active-color: #FF6B35;
  --van-nav-bar-icon-color: #1A1A1A;
  --van-nav-bar-title-font-size: 16px;
  --van-nav-bar-title-text-color: #1A1A1A;
  --van-nav-bar-background: #FFFFFF;
}

*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
  -webkit-tap-highlight-color: transparent;
  -webkit-font-smoothing: antialiased;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  color: var(--text-1);
  background: var(--bg);
  overflow-x: hidden;
}

#app {
  min-height: 100vh;
  max-width: 480px;
  margin: 0 auto;
  background: var(--bg);
  position: relative;
  box-shadow: 0 0 40px rgba(0,0,0,0.1);
}

::-webkit-scrollbar { display: none; }
* { scrollbar-width: none; -ms-overflow-style: none; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.18s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.slide-enter-active, .slide-leave-active { transition: transform 0.22s ease, opacity 0.22s ease; }
.slide-enter-from { transform: translateX(30px); opacity: 0; }
.slide-leave-to { transform: translateX(-20px); opacity: 0; }

/* Vant overrides */
.van-nav-bar { box-shadow: 0 1px 6px rgba(0,0,0,0.06) !important; }
.van-nav-bar__title { font-weight: 600 !important; }
.van-tabbar { box-shadow: 0 -1px 12px rgba(0,0,0,0.08) !important; }
.van-tabbar-item__text { font-size: 11px !important; }
.van-button--primary { background: var(--primary-gradient) !important; border: none !important; }
.van-button--round { border-radius: var(--radius-full) !important; }

/* 工具类 */
.price-red { color: var(--price); font-weight: 600; }
.price-red::before { content: '¥'; font-size: 0.8em; }
.text-clamp1 { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.text-clamp2 { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
</style>
