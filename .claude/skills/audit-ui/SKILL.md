---
name: audit-ui
description: 对 TakeoutSystem 三端前端（H5/merchant-web/admin-web）做完整 UI 与接口一致性审计
---

# UI 审计流程

> **核心原则：不能只看接口，必须看用户实际看到的东西。** API 能通不代表 UI 正确。

## 审计范围

| 前端 | 端口 | 框架 | 账号 |
|------|------|------|------|
| h5 (用户端) | 3001 | Vue3 + Vant4 | 13800000003 |
| merchant-web (商家端) | 3002 | Vue3 + Element Plus | 13800000002 |
| admin-web (管理后台) | 3003 | Vue3 + Element Plus | 13800000001 |

---

## Phase 1：渲染检查（空白组件检测）

**用 Playwright 实际渲染每个页面，不要只读代码。**

```javascript
// 检查某个 tab/容器是否渲染了真实内容
// 如果看到 <!-- --> (空注释节点)，说明组件没有渲染
const pane = await page.$('#pane-pending')
const html = await pane.innerHTML()
if (html.trim() === '<!---->') {
  // 触发 Vue runtime-only 编译器缺失 bug ← 最常见原因
}
```

**Vite 运行时编译器缺失的判断方式：**
- 组件对象里有 `template: '...'`（字符串模板）
- Vite 默认使用 `vue.runtime.esm-bundler.js`（无编译器）
- 症状：组件渲染为 `<!---->`，无报错
- **修复：把 string template 改为 SFC `<template>` 块**

---

## Phase 2：数据绑定检查

对每个页面，检查 `{{ variable.fieldName }}` 是否与 API 返回的真实字段对应。

**已知易错点（本项目历史问题）：**

| 页面 | 错误绑定 | 正确字段 |
|------|---------|---------|
| merchant-web Dashboard | `merchant.rating` | `merchant.score` |
| merchant-web Dashboard | `merchant.sales` | `merchant.salesCount` |
| h5 Home | 坐标 `longitude: 113.9305` | 应为 `116.39`（北京，测试数据在北京） |
| h5 Profile 评价数 | `stats.orderCount > 0 ? '>' : 0` | `stats.reviewCount`（需调 `/review/my`） |

**检查方法：**
1. 读 API 接口返回的 JSON 字段名（或看 VO 类）
2. 搜索前端中对应字段的所有用法
3. 对比是否一致

---

## Phase 3：操作按钮完整性检查

**检查每个状态下是否有正确的操作按钮。**

### 订单状态按钮对应关系

**H5 用户端（Orders.vue 卡片操作区）：**
| 状态 | 应有按钮 |
|------|---------|
| 1=待支付 | 去支付 + 取消订单 |
| 2=待接单 | 取消订单 |
| 6=已完成 | 去评价 + 再来一单 |
| 其他 | 无按钮 |

**H5 OrderDetail.vue 详情页：**
| 状态 | 应有按钮 |
|------|---------|
| 1 或 2 | 取消订单 |
| 5=配送中 | 确认收货 |
| 6=已完成 | 去评价 |

**merchant-web 订单管理：**
| 状态 | 应有按钮 |
|------|---------|
| 2=待接单 | 接单 + 拒单 |
| 3=备餐中 | 出餐完成 |
| 5=配送中 | 完成配送 ← 常被遗漏 |

---

## Phase 4：Status 标签完整性检查

**检查每个前端的状态标签 map 是否覆盖所有值。**

### 商家状态（Merchant.status）
```
0=待审核  1=营业中  2=打烊  3=封禁  4=审核拒绝
```
Dashboard 和 admin-web 都要映射 0/1/2/3/4，缺了显示"其他"是 bug。

### 订单状态（Order.status）
```
1=待支付  2=待接单  3=备餐中  4=待取餐(未使用)  5=配送中  6=已完成  7=已取消
```
注意：**status=4 后端从不设置**，但标签要定义，不然显示"未知"。

---

## Phase 5：Tab 筛选覆盖检查

**检查每个 Tab 传给接口的 status 是否正确覆盖了所有目标状态。**

| 页面 | Tab 名 | 应传参数 | 易漏 |
|------|--------|---------|------|
| h5 Orders | 进行中 | status=3 AND status=5（两次请求合并） | status=5（配送中）易漏 |
| h5 Orders | 待支付 | status=1 | 旧版本没有这个 tab |
| merchant-web Orders | 备餐中 | effectiveStatus=null（fetchAll 后 client filter 3,5） | status=5 订单易漏 |

---

## Phase 6：API 调用方式检查

**检查 HTTP 方法、URL、参数传递方式。**

常见模式错误：
```javascript
// 错：原因用 query param，后端读 @RequestBody
request.post(`/order/merchant/reject/${orderNo}`, null, { params: { reason } })

// 对：用 JSON body
request.post(`/order/merchant/reject/${orderNo}`, { reason })

// 错：URL 不对
request.put('/merchant/status', data)

// 对：
request.put('/merchant/my/status', data)
```

**检查要点：**
1. HTTP 方法（GET/POST/PUT/DELETE）
2. URL 路径（`/merchant/my/xxx` vs `/merchant/xxx`）
3. 参数位置（`@RequestBody` → JS 里要传 data 对象；`@RequestParam` → 传 `{ params: {} }`）

---

## Phase 7：跨端数据同步验证

在一个端修改数据后，检查其他端是否及时看到变化。

| 操作 | 触发方 | 另外两端看到变化 | 延迟 |
|------|--------|----------------|------|
| 商家更新信息 | merchant-web | Redis 缓存失效，立即生效 | 无 |
| 管理员审核商家 | admin-web | merchant-web 刷新后看到 | Redis TTL 最大 30min |
| 管理员禁用用户 | admin-web | H5 下次登录被阻止 | 立即（login 时检查） |
| 商家更新菜单 | merchant-web | H5 菜单缓存失效 | 无 |

---

## 常用检查脚本

```powershell
# 检查前端是否能访问
Test-NetConnection -ComputerName localhost -Port 3001 -InformationLevel Quiet
Test-NetConnection -ComputerName localhost -Port 3002 -InformationLevel Quiet
Test-NetConnection -ComputerName localhost -Port 3003 -InformationLevel Quiet

# 启动前端（分别在各目录下运行）
# cd h5;          npm run dev  (port 3001)
# cd merchant-web; npm run dev  (port 3002)
# cd admin-web;   npm run dev  (port 3003)
```

---

## 历次审计 Bug 记录

> 规则：发现新 bug 时追加到"待修复"；修完后移到"已修复"。已修复条目只保留对 `known-pitfalls` 有参考价值的典型案例，其余删除。

### 待修复
_（当前无）_

### 已修复（典型案例，对应 known-pitfalls 条目）

| 文件 | 问题 | 对应坑位 |
|------|------|---------|
| merchant-web/OrderManage.vue | string template → Vite runtime 无编译器 → `<!---->` | 坑1 |
| merchant-web/api/index.js | rejectOrder reason 用 query param，后端读 body → null | 坑5 |
| merchant-web/Dashboard.vue | `merchant.rating`/`merchant.sales` → 应为 `score`/`salesCount` | 坑2（字段绑定） |
| h5/Home.vue | 坐标硬编码深圳，测试数据在北京 | 坑3 |
| h5/Orders.vue | "进行中" tab 漏 status=5（配送中） | 坑4 |
