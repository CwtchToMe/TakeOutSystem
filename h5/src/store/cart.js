import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getCart, addToCart, updateCartItem, removeCartItem, clearCart as clearCartApi } from '../api'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const currentMerchantId = ref(null)

  async function loadCart(merchantId) {
    currentMerchantId.value = merchantId
    const res = await getCart(merchantId)
    items.value = res.data || []
  }

  async function addItem(data) {
    await addToCart(data)
    await loadCart(data.merchantId)
  }

  async function changeQuantity(cartItemId, quantity) {
    if (quantity <= 0) {
      await removeCartItem(cartItemId)
    } else {
      await updateCartItem(cartItemId, quantity)
    }
    if (currentMerchantId.value) {
      await loadCart(currentMerchantId.value)
    }
  }

  async function removeItem(cartItemId) {
    await removeCartItem(cartItemId)
    if (currentMerchantId.value) {
      await loadCart(currentMerchantId.value)
    }
  }

  async function clearCart() {
    if (currentMerchantId.value) {
      try { await clearCartApi(currentMerchantId.value) } catch (e) {}
    }
    items.value = []
    currentMerchantId.value = null
  }

  const totalCount = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))
  // 兼容 unitPrice / price 两种字段名
  const totalPrice = computed(() =>
    items.value.reduce((sum, i) => sum + (i.unitPrice ?? i.price ?? 0) * i.quantity, 0)
  )

  return { items, currentMerchantId, loadCart, addItem, changeQuantity, removeItem, clearCart, totalCount, totalPrice }
})
