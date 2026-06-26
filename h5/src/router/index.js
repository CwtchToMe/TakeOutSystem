import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/Login.vue'), meta: { transition: 'fade' } },
    { path: '/', component: () => import('../views/Home.vue'), meta: { requiresAuth: true, transition: 'fade' } },
    { path: '/search', component: () => import('../views/Search.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/merchant/:id', component: () => import('../views/MerchantDetail.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/cart', component: () => import('../views/Cart.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/order/confirm', component: () => import('../views/OrderConfirm.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/orders', component: () => import('../views/Orders.vue'), meta: { requiresAuth: true, transition: 'fade' } },
    { path: '/order/:orderNo', component: () => import('../views/OrderDetail.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/profile', component: () => import('../views/Profile.vue'), meta: { requiresAuth: true, transition: 'fade' } },
    { path: '/address', component: () => import('../views/Address.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/pay/:orderNo', component: () => import('../views/Pay.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/review/:orderNo', component: () => import('../views/ReviewSubmit.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/reviews', component: () => import('../views/MyReviews.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/favorites', component: () => import('../views/Favorites.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/coupons', component: () => import('../views/Coupons.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/merchant-reviews/:id', component: () => import('../views/MerchantReviews.vue'), meta: { requiresAuth: true, transition: 'slide' } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('h5_token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
