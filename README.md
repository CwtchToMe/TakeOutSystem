# TakeoutSystem — 外卖订单系统

Spring Boot 3.2.5 单体应用（Java 17），MySQL + Redis，含消费者 H5、商家端、管理后台三个前端。

---

## 一、服务一览

| # | 服务 | 端口 | 检查 | 启动方式 |
|---|---|---|---|---|
| 1 | **MySQL** | 3306 | `scripts/diagnose.ps1` | `scripts/start-all.ps1` 或 `console.ps1 → 菜单 2` |
| 2 | **Redis** | 6379 | `scripts/diagnose.ps1` | 同上 |
| 3 | **后端 API** | 8080 | http://localhost:8080/api/health | `scripts/start-all.ps1` 或 `console.ps1 → 菜单 3` |
| 4 | **消费者 H5** | 3001 | http://localhost:3001 | `cd h5 && npm run dev` |
| 5 | **商家端** | 3002 | http://localhost:3002 | `cd merchant-web && npm run dev` |
| 6 | **管理后台** | 3003 | http://localhost:3003 | `cd admin-web && npm run dev` |

> **推荐**：`scripts\start-all.ps1` — 一键启动 MySQL + Redis + 后端（前端各需单独终端）  
> **诊断**：`scripts\diagnose.ps1` — 自动检测所有服务并给出修复建议  
> **控制台**：`C:\workD\software\console.ps1` — 交互式管理面板

---

## 二、快速启动

### 2.1 一键启动基础设施 + 后端

```powershell
powershell C:\workD\code\TakeoutSystem\scripts\start-all.ps1
```

### 2.2 启动前端（各需单独终端）

```powershell
# 消费者 H5 → http://localhost:3001
cd C:\workD\code\TakeoutSystem\h5
npm run dev
```

```powershell
# 商家端 → http://localhost:3002
cd C:\workD\code\TakeoutSystem\merchant-web
npm run dev
```

```powershell
# 管理后台 → http://localhost:3003
cd C:\workD\code\TakeoutSystem\admin-web
npm run dev
```

### 2.3 验证全部正常

```powershell
# 一键诊断
powershell C:\workD\code\TakeoutSystem\scripts\diagnose.ps1
```

| 检查项 | 地址 |
|---|---|
| 后端健康 | http://localhost:8080/api/health |
| API 文档 | http://localhost:8080/doc.html |
| 消费者 H5 | http://localhost:3001 |
| 商家端 | http://localhost:3002 |
| 管理后台 | http://localhost:3003 |

---

## 三、停止所有服务

```powershell
$pid8080 = (netstat -ano | Select-String ":8080\s.*LISTENING")[0] -replace '.*\s(\d+)$','$1'
if ($pid8080) { Stop-Process -Id ([int]$pid8080.Trim()) -Force }
Get-Process -Name "redis-server" -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process -Name "mysqld"       -ErrorAction SilentlyContinue | Stop-Process -Force
```

---

## 四、测试账号

| 角色 | 手机号 | 管理的商家 | 可用功能 |
|---|---|---|---|
| 管理员 ADMIN | 13800000001 | — | 用户管理、商家审核、订单总览（管理后台 3003） |
| 商家 MERCHANT | 13800000002 | 香辣料理（id=1） | 接单、菜品管理（商家端 3002） |
| 商家 MERCHANT | 13800000004 | 快乐汉堡（id=2） | 接单、菜品管理（商家端 3002） |
| 消费者 CUSTOMER | 13800000003 | — | 下单、支付、评价（消费者 H5 3001） |

验证码（开发环境固定）：**`123456`**

---

## 五、核心业务流程

### 下单 → 支付 → 接单 → 完成

```
消费者下单 → status=1(待支付)
  ↓ POST /api/pay/create + /api/pay/callback
商家待接单 → status=2(待接单)
  ↓ 商家接单
备餐中    → status=3(备餐中)
  ↓ 商家出餐
配送中    → status=5(配送中)
  ↓ 用户确认收货
已完成    → status=6
```

取消：status=1 或 2 时用户可取消 → status=7（库存自动回滚）

### 模拟支付（开发环境）

```bash
# 1. 创建支付单（返回 paymentNo）
POST /api/pay/create   {"orderNo":"xxx","payType":1}

# 2. 模拟支付成功（订单 1→2）
POST /api/pay/callback {"paymentNo":"PAYxxx","success":true}

# 查询支付状态
GET  /api/pay/status/{orderNo}
```

---

## 六、常见问题排查

### 症状 1：前端报"服务器异常"

```powershell
# 第一步：一键诊断
powershell C:\workD\code\TakeoutSystem\scripts\diagnose.ps1

# 第二步：如果 Redis 缓存污染（重启后端后必做）
& "C:\workD\software\redis\redis-cli.exe" flushdb
```

访问 http://localhost:8080/api/health 查看 MySQL/Redis 状态。

### 症状 2：登录收不到验证码

开发模式验证码固定为 `123456`，TTL 5 分钟，同一手机号 **60 秒内只能发一次**。

```powershell
# 查看当前 Redis 中的验证码
& "C:\workD\software\redis\redis-cli.exe" get "sms:code:13800000003"
```

### 症状 3：重新部署后端

```
console.ps1 → 菜单 3 → 选 2（停止）
mvn package -DskipTests
console.ps1 → 菜单 3 → 选 1（启动）
# 启动后清 Redis 缓存！
& "C:\workD\software\redis\redis-cli.exe" flushdb
```

---

## 七、console.ps1 菜单参考

| 菜单 | 功能 |
|---|---|
| **1 Monitor** | 实时监控：每 2s 刷新状态 + SMS 验证码 + ERROR 日志 |
| **2 Infra** | 启动 Redis / MySQL / 初始化数据库 |
| **3 App** | 启动/停止后端、查看日志 |
| **4 Query** | 查用户/订单/商家/购物车/自定义 SQL |

---

## 八、API 调试

- **健康检查**：http://localhost:8080/api/health（无需登录）
- **Knife4j 文档**：http://localhost:8080/doc.html
- **HTTP 测试文件**：`tests/api-test.http`（VS Code REST Client / IntelliJ）

---

## 九、路径参考

| 组件 | 路径 |
|---|---|
| 一键启动脚本 | `scripts/start-all.ps1` |
| 一键诊断脚本 | `scripts/diagnose.ps1` |
| 交互式控制台 | `C:\workD\software\console.ps1` |
| 后端 JAR | `target/takeout-app.jar` |
| 应用日志 | `logs/takeout-out.log` |
| MySQL 初始化脚本 | `init/sql/init.sql` |
| 消费者 H5 源码 | `h5/` |
| 商家端源码 | `merchant-web/` |
| 管理后台源码 | `admin-web/` |

---

## 十、技术栈

| 层 | 组件 |
|---|---|
| 后端 | Spring Boot 3.2.5 · Java 17 · MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8.4（localhost:3306，`db_takeout`） |
| 缓存 | Redis（localhost:6379） |
| 前端 H5 | Vue 3 + Vite + Vant 4（移动端，port 3001） |
| 前端商家端 | Vue 3 + Vite + Element Plus（port 3002） |
| 前端管理后台 | Vue 3 + Vite + Element Plus（port 3003） |
| 认证 | JWT（JJWT 0.12.5），手机号 + 短信验证码 |
| ID 生成 | Snowflake（序列化为 String 防 JS 精度丢失） |
