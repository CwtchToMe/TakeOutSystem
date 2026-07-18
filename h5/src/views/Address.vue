<template>
  <div class="address-page">
    <van-nav-bar title="收货地址" left-arrow @click-left="router.back()">
      <template #right>
        <span class="nav-add-btn" @click="openAdd">+ 新增</span>
      </template>
    </van-nav-bar>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrap">
      <van-skeleton v-for="i in 3" :key="i" title :row="2" style="padding: 16px; margin-bottom: 8px; background: #fff;" />
    </div>

    <!-- 地址列表 -->
    <div v-else-if="addresses.length > 0" class="address-list">
      <van-swipe-cell
        v-for="addr in addresses"
        :key="addr.id"
        class="swipe-cell"
      >
        <div
          class="address-card"
          :class="{ 'address-selected': selectedId === addr.id }"
          @click="handleSelect(addr)"
        >
          <!-- 默认标签 -->
          <div class="addr-top">
            <van-tag v-if="addr.isDefault" type="primary" class="default-tag">默认</van-tag>
            <div class="addr-name-phone">
              <span class="addr-name">{{ addr.receiver }}</span>
              <span class="addr-phone">{{ addr.phone }}</span>
            </div>
            <van-icon
              name="edit"
              size="18"
              color="#999"
              class="addr-edit-btn"
              @click.stop="openEdit(addr)"
            />
          </div>
          <div class="addr-detail">
            {{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detail }}
          </div>
          <!-- 设为默认 -->
          <div
            v-if="!addr.isDefault"
            class="addr-set-default"
            @click.stop="handleSetDefault(addr)"
          >
            设为默认
          </div>
          <!-- 选中勾 -->
          <div v-if="selectedId === addr.id" class="addr-check">
            <van-icon name="success" size="16" color="#fff" />
          </div>
        </div>

        <template #right>
          <van-button
            square
            type="danger"
            text="删除"
            class="delete-btn"
            @click="handleDelete(addr)"
          />
        </template>
      </van-swipe-cell>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-address">
      <div class="empty-icon">📍</div>
      <div class="empty-text">还没有收货地址</div>
      <van-button round type="primary" class="empty-add-btn" @click="openAdd">
        添加新地址
      </van-button>
    </div>

    <!-- 底部添加按钮（有地址时显示） -->
    <div v-if="addresses.length > 0" class="add-footer">
      <van-button round block class="add-btn" @click="openAdd">
        <van-icon name="plus" />
        添加新地址
      </van-button>
    </div>

    <!-- 新增/编辑弹窗 -->
    <van-popup
      v-model:show="showForm"
      position="bottom"
      round
      :style="{ maxHeight: '90vh', overflow: 'auto' }"
      @close="resetForm"
    >
      <div class="form-popup">
        <div class="form-header">
          <span class="form-title">{{ editId ? '编辑地址' : '新增地址' }}</span>
          <van-icon name="cross" size="18" color="#666" @click="showForm = false" />
        </div>

        <div class="form-body">
          <van-field
            v-model="form.receiver"
            label="收货人"
            placeholder="请输入收货人姓名"
            :rules="[{ required: true, message: '请填写收货人' }]"
            class="form-field"
          />
          <van-field
            v-model="form.phone"
            label="手机号"
            type="tel"
            placeholder="请输入手机号"
            maxlength="11"
            :rules="[{ required: true, message: '请填写手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]"
            class="form-field"
          />
          <van-field
            v-model="form.province"
            label="省份"
            placeholder="如：广东省"
            class="form-field"
          />
          <van-field
            v-model="form.city"
            label="城市"
            placeholder="如：深圳市"
            class="form-field"
          />
          <van-field
            v-model="form.district"
            label="区/县"
            placeholder="如：南山区"
            class="form-field"
          />
          <van-field
            v-model="form.detail"
            label="详细地址"
            type="textarea"
            rows="2"
            placeholder="街道、楼牌号等详细信息"
            maxlength="100"
            show-word-limit
            class="form-field"
          />
          <van-cell center title="设为默认地址">
            <template #right-icon>
              <van-switch v-model="form.isDefault" size="24px" />
            </template>
          </van-cell>
        </div>

        <div class="form-footer">
          <van-button
            round
            block
            :loading="saving"
            loading-text="保存中..."
            class="save-btn"
            @click="handleSave"
          >
            保存地址
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getAddresses, addAddress, updateAddress, deleteAddress, setDefaultAddress } from '../api'

const router = useRouter()
const route = useRoute()
const addresses = ref([])
const loading = ref(true)
const showForm = ref(false)
const saving = ref(false)
const editId = ref(null)
const selectedId = ref(route.query.selectedId || null)
const fromOrderConfirm = !!route.query.fromOrder

const form = ref({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

const resetForm = () => {
  editId.value = null
  form.value = { receiver: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: false }
}

const openAdd = () => {
  resetForm()
  showForm.value = true
}

const openEdit = (addr) => {
  editId.value = addr.id
  form.value = {
    receiver: addr.receiver || '',
    phone: addr.phone || '',
    province: addr.province || '',
    city: addr.city || '',
    district: addr.district || '',
    detail: addr.detail || '',
    isDefault: !!addr.isDefault
  }
  showForm.value = true
}

const loadAddresses = async () => {
  loading.value = true
  try {
    const res = await getAddresses()
    addresses.value = res.data || []
    if (!selectedId.value && addresses.value.length > 0) {
      const def = addresses.value.find(a => a.isDefault) || addresses.value[0]
      selectedId.value = def.id
    }
  } catch (e) {
    showToast('加载地址失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadAddresses)

const handleSelect = (addr) => {
  selectedId.value = addr.id
  if (fromOrderConfirm) {
    // 用 replace 跳回确认订单页，并携带选中的地址 ID，让 OrderConfirm 重新渲染时能读取
    router.replace({ path: '/order/confirm', query: { selectedAddressId: addr.id } })
  }
}

const handleSave = async () => {
  const { receiver, phone, detail } = form.value
  if (!receiver.trim()) { showToast('请填写收货人'); return }
  if (!/^1[3-9]\d{9}$/.test(phone)) { showToast('手机号格式不正确'); return }
  if (!detail.trim()) { showToast('请填写详细地址'); return }

  saving.value = true
  try {
    // 经纬度前端暂无定位，传 0 让后端存储（已移除 @NotNull 校验）
    const payload = { ...form.value, longitude: 0, latitude: 0 }
    if (editId.value) {
      await updateAddress(editId.value, payload)
      showToast('地址已更新')
    } else {
      await addAddress(payload)
      showToast('地址已添加')
    }
    showForm.value = false
    await loadAddresses()
  } catch (e) {
    showToast('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (addr) => {
  try {
    await showConfirmDialog({ title: '删除地址', message: '确定删除该收货地址吗？' })
    await deleteAddress(addr.id)
    showToast('地址已删除')
    await loadAddresses()
  } catch (e) {
    if (e && e.message && e.message !== 'cancel') {
      showToast(e.message || '删除失败')
    }
  }
}

const handleSetDefault = async (addr) => {
  try {
    await setDefaultAddress(addr.id)
    showToast('已设为默认地址')
    await loadAddresses()
  } catch (e) {
    showToast('操作失败，请重试')
  }
}
</script>

<style scoped>
.address-page {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 100px;
}

.nav-add-btn {
  font-size: 14px;
  color: var(--primary);
  font-weight: 500;
}

.loading-wrap {
  padding-top: 8px;
}

/* 地址列表 */
.address-list {
  padding: 10px 0;
}
.swipe-cell {
  margin: 0 14px 10px;
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
}

.address-card {
  background: #fff;
  padding: 14px 16px;
  position: relative;
  cursor: pointer;
  transition: background 0.15s;
}
.address-card:active { background: #fafafa; }
.address-selected {
  border: 2px solid var(--primary);
}

.addr-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.default-tag {
  flex-shrink: 0;
  border-radius: var(--radius-full) !important;
  font-size: 11px !important;
}
.addr-name-phone {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
}
.addr-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}
.addr-phone {
  font-size: 13px;
  color: var(--text-3);
}
.addr-edit-btn {
  flex-shrink: 0;
  padding: 4px;
}
.addr-detail {
  font-size: 13px;
  color: var(--text-3);
  line-height: 1.5;
  padding-right: 24px;
}

.addr-set-default {
  display: inline-block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--primary);
  border: 1px solid var(--primary);
  border-radius: var(--radius-full);
  padding: 1px 10px;
  cursor: pointer;
}

.addr-check {
  position: absolute;
  bottom: 12px;
  right: 14px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.delete-btn {
  height: 100%;
  width: 70px;
  border-radius: 0 var(--radius) var(--radius) 0;
}

/* 空状态 */
.empty-address {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 0 40px;
  gap: 12px;
}
.empty-icon { font-size: 64px; opacity: 0.4; }
.empty-text { font-size: 16px; color: var(--text-4); font-weight: 500; }
.empty-add-btn {
  margin-top: 8px;
  background: var(--primary-gradient) !important;
  border: none !important;
  padding: 0 32px !important;
}

/* 底部按钮 */
.add-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 480px;
  margin: 0 auto;
  padding: 12px 16px 24px;
  background: #fff;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06);
}
.add-btn {
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  background: var(--primary-gradient) !important;
  border: none !important;
  border-radius: var(--radius-full) !important;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #fff !important;
}

/* 表单弹窗 */
.form-popup {
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}
.form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--border);
}
.form-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.form-body {
  flex: 1;
  overflow-y: auto;
}
.form-field {
  border-bottom: 1px solid var(--border);
}
.form-footer {
  padding: 16px;
  border-top: 1px solid var(--border);
  background: #fff;
}
.save-btn {
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  background: var(--primary-gradient) !important;
  border: none !important;
  border-radius: var(--radius-full) !important;
  color: #fff !important;
}
</style>
