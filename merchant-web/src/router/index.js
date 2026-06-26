import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import Dashboard from '../views/Dashboard.vue'
import OrderManage from '../views/OrderManage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login },
    {
      path: '/',
      component: Layout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/orders' },
        { path: 'dashboard', component: Dashboard },
        { path: 'orders', component: OrderManage }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('merchant_token')
  if (to.meta.requiresAuth && !token) next('/login')
  else next()
})

export default router
