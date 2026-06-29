---
name: known-pitfalls
description: TakeoutSystem 项目踩过的所有代码坑位，按层次分类，调试时快速参考
---

# TakeoutSystem — 已知坑位全记录

> 每条都是真实踩坑。调试时先过一遍这里，节省大量时间。

---

## 一、Vue / Vite 前端（最重要）

### 坑1：Vite 运行时无编译器 → string template 静默渲染空白

**症状：** 组件存在、数据有值，但页面显示 `<!---->`（空注释），无任何报错。

**原因：** Vite 默认 import vue 时走 `vue.runtime.esm-bundler.js`（仅运行时，无编译器）。
组件对象用 `template: '...'`（字符串）→ 运行时无法编译 → 静默渲染空节点。

**复现代码（错误写法）：**
```javascript
const OrderTable = {
  props: ['data'],
  template: '<el-table :data="data">...</el-table>',  // ← 字符串模板！
}
```

**修复：** 把 template 移进 SFC `<template>` 块，用 `@vitejs/plugin-vue` 在构建时编译。

**如何排查：** 用 Playwright 检查目标容器的 `innerHTML`，若为 `<!---->` 立即检查是否有 string template 组件。

---

### 坑2：Element Plus 分页 @current-change 事件名变更

**症状：** 点击分页按钮不触发任何事件，页面不刷新。

**原因：** Element Plus 2.x 将 `@current-change` 改为 `@change`。

**修复：**
```html
<!-- 错 -->
<el-pagination @current-change="loadOrders" />

<!-- 对 -->
<el-pagination @change="loadOrders" />
```

---

### 坑3：前端硬编码坐标与测试数据不匹配

**症状：** 首页商家列表永远为空。

**原因：** `Home.vue` 硬编码深圳坐标（113.9305, 22.5292），测试商家数据在北京。

**正确坐标（北京，覆盖两个测试商家）：**
```javascript
longitude: 116.39,
latitude: 39.952,
radius: 20000  // 20km
```

**记住：** 每次 reset 数据库后，检查测试商家的 longitude/latitude 值（`init/sql/init.sql`）。

---

### 坑4：Tab status 筛选覆盖不完整

**症状：** 特定状态订单在"进行中"等 tab 里消失不见。

**H5 Orders.vue "进行中" tab 正确实现：**
```javascript
// 错：只传 status=3，漏掉配送中（status=5）
{ label: '进行中', status: 3 }

// 对：并发请求 3 和 5，合并后排序
if (activeTab.value === 'in_progress') {
  const [r3, r5] = await Promise.all([
    getMyOrders({ status: 3, page, size }),
    getMyOrders({ status: 5, page, size })
  ])
  records = [...r3.data.records, ...r5.data.records]
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
}
```

---

### 坑5：API 参数位置混淆（body vs query param）

**症状：** 后端收到 null，功能静默失效（如拒单原因始终为 null）。

| 后端注解 | 前端写法 |
|---------|---------|
| `@RequestBody` | `request.post(url, { reason })` |
| `@RequestParam` | `request.get(url, { params: { status } })` |

**错误示范：**
```javascript
// 后端用 @RequestBody 读，但前端把数据放 params
request.post(`/order/merchant/reject/${orderNo}`, null, { params: { reason } })
// 后端收到 reason = null ← 静默失效
```

---

## 二、Java / MyBatis-Plus 后端

### 坑6：Java record + MyBatis XML resultMap 静默返回空列表

**症状：** SQL 日志显示 `Total: 1`，但 service 层得到空列表。无异常。

**原因：** MyBatis `<constructor>` 映射 + Java record（canonical constructor）组合失效。

**修复：** 用 `@Data @NoArgsConstructor @AllArgsConstructor` 普通类 + `<id>/<result>` 映射。

---

### 坑7：Java record + Redis DefaultTyping.NON_FINAL 序列化崩溃

**症状：** 读 Redis 缓存报 `MismatchedInputException`，cache miss 时（读 DB）正常。

**原因：** record 是 `final` 类。Jackson `DefaultTyping.NON_FINAL` 对 final 类不加外层类型包装，
反序列化时格式不匹配 → 崩溃。

**修复：** Redis 缓存对象一律用 `@Data` 普通类（non-final），不用 record。

---

### 坑8：MyBatis-Plus `eq(column, null)` 生成 `= NULL`

**症状：** 用同样参数插入购物车，每次都新增一行，不走"更新数量"逻辑。

**原因：** `spec=null` 时，`.eq(Cart::getSpec, null)` 生成 `AND spec = NULL`。
SQL 中 `NULL = NULL` 永远为 false，selectOne 找不到记录，每次都 insert。

**修复：**
```java
.and(w -> {
    if (spec != null) w.eq(Cart::getSpec, spec);
    else w.isNull(Cart::getSpec);
})
```

---

### 坑9b：BusinessException → HTTP 始终 200，body.code 才是真实码

**症状：** 测试角色隔离时 catch 块不执行，误判为"验证通过"。

**原因：** `GlobalExceptionHandler.handleBusiness()` 没有 `@ResponseStatus`，HTTP 永远 200。
403/404 只在 JSON body `{"code": 403}` 里，只有 `AuthInterceptor` 会真正返回 HTTP 401。

**正确测试方式：**
```powershell
$resp = Invoke-RestMethod "$base/admin/user/list" -Headers $userH
if ($resp.code -eq 403) { "OK" } else { "FAIL: $($resp.code)" }
```

---

### 坑9：异常类型必须是 BusinessException

**症状：** 业务错误返回 HTTP 500，而不是预期的 4xx。

**原因：** `GlobalExceptionHandler` 只捕获 `BusinessException`。
`IllegalStateException` / `RuntimeException` 会漏成 500。

**规范：**
```java
// 永远不要这样：
throw new IllegalStateException("未登录");
throw new RuntimeException("商家不存在");

// 必须这样：
throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
throw new BusinessException(ResultCode.NOT_FOUND, "商家不存在");
```

---

### 坑10：Spring AntPathMatcher 不支持 {id} 语法

**症状：** 白名单加了路径，但请求仍然被拦截（或不该放行的被放行）。

**原因：** `AntPathMatcher` 使用 `*` / `**` 通配符，不支持 Spring MVC 的 `{variable}` 占位符。

```java
// 错：{id} 不匹配任何真实路径
WHITELIST.add("/api/merchant/{id}");

// 对：
WHITELIST.add("/api/merchant/*");
```

---

### 坑11：Redis 序列化模板混用

**场景：** 两种 RedisTemplate 不可互换。

| Key 前缀 | 必须用 | 原因 |
|---------|--------|------|
| `dish:stock:*` | `StringRedisTemplate` | Lua 脚本要求 String 序列化 |
| 其他所有缓存 | `RedisUtil`（Jackson） | 支持对象类型信息 |

---

### 坑12：JWT secret 长度不足

**症状：** 首次 `generateToken` 时抛 `IllegalArgumentException`。

**原因：** HMAC-SHA256 要求密钥 ≥ 32 字节，`application.yml` 里的 `jwt.secret` 若太短则崩溃。
`JwtUtil.buildKey()` 现在主动校验，不再静默补零。

---

### 坑13：validate-before-deduct 顺序

**症状：** 优惠券/地址校验失败时，DB 回滚正常，但 Redis 库存被永久扣减（永久 undercount）。

**原因：** `checkAndDeduct`（Redis）在地址/优惠券校验**之前**执行；业务异常触发 DB 事务回滚但 Lua 操作不回滚。

**规则：** 所有可能抛 BusinessException 的校验，必须在任何 Redis/不可逆操作之前完成。

---

## 三、安全相关

### 坑14：Admin 接口忘记加 ADMIN 角色检查

**症状：** 普通用户能调用 admin 接口（商家审核、用户列表等）。

**规范：** 每个 `/api/admin/**` controller 方法开头必须调用：
```java
private void requireAdmin() {
    if (UserContext.getUserRole() != UserRole.ADMIN) {
        throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问");
    }
}
```

---

### 坑15：地址/订单归属未校验

**症状：** 用户 A 能用用户 B 的地址下单；商家 A 能操作商家 B 的订单。

**规范：**
- 用地址前：`if (!address.getUserId().equals(userId)) throw FORBIDDEN`
- 商家操作订单前：`checkMerchantOwner(ownerId, merchantId)`（`OrderService` 中已有此方法）

---

## 四、构建与运维

### 坑16：Maven 构建时 JAR 被占用

```powershell
# 先停进程，再构建
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
mvn package -DskipTests
java -Xmx512m -jar target/takeout-app.jar
```

### 坑17：Redis 端口检测用 127.0.0.1

Redis 绑定 `127.0.0.1:6379`，旧的 `Check-Port` 只匹配 `0.0.0.0:port`。
正确检测：用 `Test-NetConnection localhost 6379` 或匹配 `:6379\s`。

---

## 五、业务逻辑

### 坑18：status=4（待取餐）从未被设置

订单流程：`1→2→3→5→6`，status=4 在数据库定义中保留但代码里从不设置（直接 ready 3→5）。
- 前端进度条包含 status=4 步骤是"视觉上正常"（因为 4≤5 条件满足），但实际数据从不经过 4。
- 在 tab 筛选里查 status=4 永远为空。

### 坑19：无退款机制

`cancel()` 和 `reject()` 只恢复库存 + 退优惠券，不退款。
`MockPayController` 只有支付回调，无退款端点。
付款后 Redis key 已删除，无追溯路径。
这是 mock 支付的设计局限，不是 bug。

### 坑20：禁用用户 Token 不立即失效

管理员禁用用户后，该用户已有的 AccessToken（TTL=2h）仍然有效。
`AuthService.login()` 已校验 status=0 会拒绝登录，但已登录的 session 不会被踢。
**完整修复需要：** 在 `updateStatus(id, 0)` 时同步 `redisUtil.delete("user:token:" + id)`，强制下次刷新 token 时失效。
