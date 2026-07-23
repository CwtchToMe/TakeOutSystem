import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('h5_token') || '')
  const refreshToken = ref(localStorage.getItem('h5_refresh_token') || '')
  const userId = ref(localStorage.getItem('h5_userId') || '')
  const nickname = ref(localStorage.getItem('h5_nickname') || '')

  function setAuth(data) {
    token.value = data.accessToken
    refreshToken.value = data.refreshToken || ''
    userId.value = data.userId
    nickname.value = data.nickname || ''
    localStorage.setItem('h5_token', data.accessToken)
    if (data.refreshToken) localStorage.setItem('h5_refresh_token', data.refreshToken)
    localStorage.setItem('h5_userId', data.userId)
    localStorage.setItem('h5_nickname', data.nickname || '')
  }

  function clearAuth() {
    token.value = ''
    refreshToken.value = ''
    userId.value = ''
    nickname.value = ''
    localStorage.removeItem('h5_token')
    localStorage.removeItem('h5_refresh_token')
    localStorage.removeItem('h5_userId')
    localStorage.removeItem('h5_nickname')
  }

  const isLoggedIn = () => !!token.value

  function updateNickname(name) {
    nickname.value = name
    localStorage.setItem('h5_nickname', name)
  }

  function setToken(t) {
    token.value = t
    localStorage.setItem('h5_token', t)
  }

  return { token, refreshToken, userId, nickname, setAuth, clearAuth, isLoggedIn, updateNickname, setToken }
})
