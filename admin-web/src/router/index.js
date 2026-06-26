import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import MerchantManage from '../views/MerchantManage.vue'
import UserManage from '../views/UserManage.vue'
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
        { path: '', redirect: '/merchants' },
        { path: 'merchants', component: MerchantManage },
        { path: 'users', component: UserManage },
        { path: 'orders', component: OrderManage }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.meta.requiresAuth && !token) next('/login')
  else next()
})

export default router
