# TakeoutSystem — 外卖订单系统

Spring Boot 3.2.5 单体应用（Java 17），MySQL + Redis，含消费者 H5、商家端、管理后台三个前端。

---

## 零、环境要求与首次安装

### 前置软件

| 软件 | 版本要求 | 说明 |
|---|---|---|
| JDK | 17+ | https://adoptium.net |
| Maven | 3.8+ | https://maven.apache.org |
| MySQL | 8.x | 默认账号 root / 密码 root |
| Redis | 6.x+ | 默认端口 6379 |
| Node.js | 18+ | https://nodejs.org |

### 首次初始化（clone 后只需做一次）

```powershell
# 1. 初始化数据库（确保 MySQL 已启动）
mysql -uroot -proot --default-character-set=utf8mb4 < init/sql/init.sql

# 2. 编译后端
mvn package -DskipTests

# 3. 安装前端依赖
cd h5 && npm install
cd ../merchant-web && npm install
cd ../admin-web && npm install
cd ..
```

完成后按「二、快速启动」正常启动即可。

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
> **控制台**：`scripts\console.ps1` — 交互式管理面板

---

## 二、快速启动

### 2.1 一键启动基础设施 + 后端

```powershell
powershell scripts\start-all.ps1
```

### 2.2 启动前端（各需单独终端）

```powershell
# 消费者 H5 → http://localhost:3001
cd h5
npm run dev
```

```powershell
# 商家端 → http://localhost:3002
cd merchant-web
npm run dev
```

```powershell
# 管理后台 → http://localhost:3003
cd admin-web
npm run dev
```

### 2.3 验证全部正常

```powershell
# 一键诊断
powershell scripts\diagnose.ps1
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
powershell scripts\diagnose.ps1

# 第二步：如果 Redis 缓存污染（重启后端后必做）
& ".\tools\redis\redis-cli.exe" flushdb
```

访问 http://localhost:8080/api/health 查看 MySQL/Redis 状态。

### 症状 2：登录收不到验证码

开发模式验证码固定为 `123456`，TTL 5 分钟，同一手机号 **60 秒内只能发一次**。

```powershell
# 查看当前 Redis 中的验证码
& ".\tools\redis\redis-cli.exe" get "sms:code:13800000003"
```

### 症状 3：重新部署后端

```
console.ps1 → 菜单 3 → 选 2（停止）
mvn package -DskipTests
console.ps1 → 菜单 3 → 选 1（启动）
# 启动后清 Redis 缓存！
& ".\tools\redis\redis-cli.exe" flushdb
```


### 症状 4：商家店名显示为 "?????" 或中文乱码

Windows 终端默认编码不是 UTF-8，执行 `init.sql` 时中文会写入成乱码。

**修复：**
```powershell
# 1. 带 UTF-8 参数重新初始化数据库
mysql -uroot -proot --default-character-set=utf8mb4 < init/sql/init.sql

# 2. 清 Redis 缓存（缓存中可能还有旧乱码）
& ".\tools\redis\redis-cli.exe" flushdb

# 3. 重启后端
```
确保始终用 `--default-character-set=utf8mb4` 参数执行 `init.sql`。

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
| 交互式控制台 | `scripts/console.ps1` |
| 后端 JAR | `target/takeout-app.jar` |
| 应用日志 | `logs/takeout-out.log` |
| MySQL 初始化脚本 | `init/sql/init.sql` |
| 消费者 H5 源码 | `h5/` |
| 商家端源码 | `merchant-web/` |
| 管理后台源码 | `admin-web/` |

---

## 十、Docker 容器化

项目已完整容器化，所有服务均可通过 Docker Compose 一键启动，无需在本机安装 JDK、Node、MySQL、Redis。

### 10.1 文件结构

```
TakeOutSystem/
├── docker-compose.yml               ← 编排 6 个服务
├── .dockerignore                    ← 排除 node_modules、target、.git
├── docker/
│   ├── backend/
│   │   └── Dockerfile               ← 后端：Maven 多阶段构建 → JRE 精简镜像
│   ├── h5/
│   │   ├── Dockerfile               ← H5 前端：npm build → Nginx 托管静态文件
│   │   └── nginx.conf
│   ├── admin-web/
│   │   ├── Dockerfile               ← 管理后台：同上
│   │   └── nginx.conf
│   └── merchant-web/
│       ├── Dockerfile               ← 商家端：同上
│       └── nginx.conf
└── src/main/resources/
    └── application-docker.yml       ← Docker 环境专用配置（MySQL/Redis 连容器名）
```

### 10.2 各服务说明

| 服务 | 容器名 | 端口 | 镜像（GHCR） |
|------|--------|------|-------------|
| MySQL 8.4 | `takeout-mysql` | 3306 | `mysql:8.4`（官方） |
| Redis 7 | `takeout-redis` | 6379 | `redis:7-alpine`（官方） |
| 后端 API | `takeout-backend` | 8080 | `ghcr.io/cwtochtome/takeout-backend` |
| H5 前端 | `takeout-h5` | 3001 | `ghcr.io/cwtochtome/takeout-h5` |
| 商家端 | `takeout-merchant` | 3002 | `ghcr.io/cwtochtome/takeout-merchant` |
| 管理后台 | `takeout-admin` | 3003 | `ghcr.io/cwtochtome/takeout-admin` |

**启动顺序**：MySQL → Redis → 后端（等待 DB 就绪）→ 前端（无依赖）。

### 10.3 Docker 化解决了什么

传统开发方式需要手动安装：

```
JDK 17 + Maven + Node 18 ×3 + MySQL 8.4 + Redis + 各种 npm install + mvn package
```

Docker 方式只需：

```
docker compose up -d
```

### 10.4 Docker 使用指南

#### 首次启动

```powershell
# 先登录 GHCR（拉取私有镜像需要）
echo ghp_你的Token | docker login ghcr.io -u CwtchToMe --password-stdin

# 构建并启动所有服务
docker compose up -d --build
```

> 提示：`--build` 只在首次或改代码后需要，平时直接 `docker compose up -d` 即可。

#### 常用命令

```powershell
# 查看所有容器状态
docker compose ps

# 查看实时日志
docker compose logs -f

# 只看后端日志
docker compose logs -f backend

# 重启某个服务（改配置后）
docker compose restart backend

# 重新构建某个服务（改代码后）
docker compose build backend
docker compose up -d

# 停止所有服务（数据保留）
docker compose stop

# 完全停止并清理
docker compose down

# 停止并删库（重置开发环境）
docker compose down -v
```

#### 健康检查

```powershell
# 查看各服务健康状态
docker compose ps

# 直接访问后端健康接口
curl http://localhost:8080/api/health
```

#### 前端访问地址

| 前端 | 地址 |
|------|------|
| 消费者 H5 | http://localhost:3001 |
| 商家端 | http://localhost:3002 |
| 管理后台 | http://localhost:3003 |
| API 文档 | http://localhost:8080/doc.html |

### 10.5 镜像仓库（GHCR）

构建好的 Docker 镜像存储在 **GitHub Container Registry（GHCR）**，与你 GitHub 仓库的访问权限一致。

| 镜像 | 拉取地址 |
|------|---------|
| 后端 | `docker pull ghcr.io/cwtochtome/takeout-backend:latest` |
| H5 前端 | `docker pull ghcr.io/cwtochtome/takeout-h5:latest` |
| 商家端 | `docker pull ghcr.io/cwtochtome/takeout-merchant:latest` |
| 管理后台 | `docker pull ghcr.io/cwtochtome/takeout-admin:latest` |

镜像列表页：https://github.com/CwtchToMe?tab=packages&repo_name=TakeOutSystem

> **注意**：镜像权限继承自 GitHub 仓库 — 有仓库访问权限的人才能拉取镜像。
> 首次拉取需 `docker login ghcr.io -u CwtchToMe --password-stdin` 并用 GitHub Token 认证。

---

## 十一、CI/CD —— GitHub Actions 自动构建

### 11.1 概述

项目配置了一个 GitHub Actions 工作流（`.github/workflows/docker-build.yml`），**每次 `git push` 到 `main` 分支时自动触发**，完成以下任务：

1. 检出代码
2. 构建 4 个 Docker 镜像
3. 推送到 GHCR

**最终效果**：代码推送到 GitHub 后，镜像自动更新，任何人 `docker compose pull` 即可获取最新版本。

### 11.2 工作流文件拆解

```yaml
# 文件：.github/workflows/docker-build.yml

name: Build & Push Docker Images

# 触发条件
on:
  push:
    branches: [ main ]                    # 只有 push 到 main 分支
    paths:                                # 只有改动代码相关文件才触发
      - 'src/**'                          #   后端代码
      - 'pom.xml'                         #   Maven 依赖
      - 'h5/**'                           #   H5 前端
      - 'admin-web/**'                    #   管理后台
      - 'merchant-web/**'                 #   商家端
      - 'docker/**'                       #   Docker 配置
      - 'docker-compose.yml'
      - '.github/workflows/docker-build.yml'
  workflow_dispatch:                      # 也支持在网页手动触发
```

**路径过滤**：改 README、文档、.gitignore 等不会触发构建，节省 CI 时间。

```yaml
# 权限声明
permissions:
  contents: read        # 读取代码
  packages: write       # 写入 GHCR（推送镜像）
```

`secrets.GITHUB_TOKEN` 是 GitHub CI 内置的临时 Token，每次运行自动生成、跑完销毁，无需手动管理。

```yaml
# 每个服务独立 Job，并行构建
jobs:
  backend:              # 后端 → Maven 编译 → JRE 镜像
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set owner lowercase
        run: echo "LOWER_OWNER=${GITHUB_REPOSITORY_OWNER,,}" >> $GITHUB_ENV
      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build & push backend
        uses: docker/build-push-action@v6
        with:
          context: .
          file: docker/backend/Dockerfile
          push: true
          tags: |
            ghcr.io/${{ env.LOWER_OWNER }}/takeout-backend:latest
            ghcr.io/${{ env.LOWER_OWNER }}/takeout-backend:${{ github.sha }}

  h5:                   # H5 前端 → npm build → Nginx 镜像
  merchant-web:         # 商家端 → 同上
  admin-web:            # 管理后台 → 同上
```

**`latest` 标签**：始终指向最新版本。  
**`commit SHA` 标签**：精确关联某次提交，支持回滚。

### 11.3 Job 间的关系

四个 Job **互不依赖，并行执行**：

```
backend    ─── checkout → 登录 → Maven 编译 → 构建镜像 → push
h5         ─── checkout → 登录 → npm build  → 构建镜像 → push     ← 同时进行
merchant   ─── checkout → 登录 → npm build  → 构建镜像 → push
admin      ─── checkout → 登录 → npm build  → 构建镜像 → push
```

总耗时 ≈ 最慢的 Job（后端 Maven 编译约 2-3 分钟）。

### 11.4 CI/CD 完整流程

```
你 git push 到 main
       │
       ▼
GitHub Actions 检测到代码改动
 （路径匹配才触发）
       │
       ├─ backend：    JDK 17 + Maven → mvn package → JRE 17 镜像 → push
       ├─ h5：         Node 20 → npm ci → npm run build → Nginx 镜像 → push
       ├─ merchant：   Node 20 → npm ci → npm run build → Nginx 镜像 → push
       └─ admin：      Node 20 → npm ci → npm run build → Nginx 镜像 → push
       │
       ▼
4 个镜像推送到 ghcr.io/cwtochtome/*:latest
下次任何人 docker compose pull 即可获取最新版本
```

### 11.5 查看构建状态

- **CI 运行列表**：https://github.com/CwtchToMe/TakeOutSystem/actions
- **镜像列表**：https://github.com/CwtchToMe?tab=packages&repo_name=TakeOutSystem

每次构建完成，镜像页面会同步更新。

---

## 十二、技术栈

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
