# CI/CD 流水线：从代码提交到测试可用的完整流程

> 每次 `git push` → 自动构建镜像 → 推送到 Docker Hub → 测试拉取即用

---

## 一、完整流程

```
开发者 git push
     │
     ▼
GitHub Actions 检测到代码改动
（路径匹配 src/** / pom.xml / h5/** / docker/** 等才触发）
     │
     ├─ backend（Maven 编译 → JRE 17 镜像）
     ├─ h5（npm build → Nginx 镜像）
     ├─ merchant-web（npm build → Nginx 镜像）
     └─ admin-web（npm build → Nginx 镜像）
     │
     ▼
4 个镜像推送到 Docker Hub
  cwtchtome/takeout-backend:latest
  cwtchtome/takeout-h5:latest
  cwtchtome/takeout-merchant:latest
  cwtchtome/takeout-admin:latest
```

## 二、测试拉取后发生了什么

测试执行 `docker compose up -d` 后：

```
MySQL 容器启动
     ↓ 首次启动时执行 init/sql/init.sql（建表 + 测试数据）
     ↓ 非首次启动则跳过（volume 持久化）
Redis 容器启动
     ↓
backend 容器启动
     ↓ Spring Boot 初始化
     ↓ Flyway 检测到迁移未执行
     ↓ 自动执行 V1__create_tables.sql（13 张表，CREATE TABLE IF NOT EXISTS）
     ↓ 自动执行 V2__seed_test_data.sql（幂等 REPLACE INTO）
     ↓ API 就绪（端口 8080）
     ↓
前端容器启动（h5:3001 / merchant-web:3002 / admin-web:3003）
     ↓ nginx 反向代理 /api/ 到 backend:8080
```

**关键**：即使 MySQL 是已有 volume（`init.sql` 不会重复跑），后端 Flyway 也能自动补全表结构。

## 三、涉及的文件

| 文件 | 作用 |
|------|------|
| `.github/workflows/docker-build.yml` | CI 工作流定义，每次 push 触发 |
| `docker-compose.yml` | 开发用编排，从源码构建 |
| `docker-compose.ci.yml` | CI/测试用编排，直接拉取 Docker Hub 镜像 |
| `src/main/resources/db/migration/V1__create_tables.sql` | Flyway V1：13 张表 DDL |
| `src/main/resources/db/migration/V2__seed_test_data.sql` | Flyway V2：测试数据 |
| `src/main/resources/application.yml` | Flyway 配置（`baseline-on-migrate: true`） |
| `src/main/resources/application-docker.yml` | Docker 环境 Flyway 配置 |

## 四、容器端口映射（常见疑问）

每个前端的 nginx 在容器内监听 **80** 端口，docker-compose 映射到宿主机不同端口：

```
host:3001 → 容器 80（H5 前端）
host:3002 → 容器 80（商家端）
host:3003 → 容器 80（管理后台）
host:3306 → 容器 3306（MySQL）
host:6379 → 容器 6379（Redis）
host:8080 → 容器 8080（后端 API）
```

**一律是 `host:外部端口 : 容器端口` 格式**，容器外用 `localhost:3001` 等访问。

## 五、测试快速验证步骤

```powershell
# 1. 拉取最新镜像
docker compose -f docker-compose.ci.yml pull

# 2. 启动所有服务
docker compose -f docker-compose.ci.yml up -d

# 3. 确认后端健康
curl http://localhost:8080/api/health

# 4. 运行 API 测试
# 38 个测试用例全部通过即验证完成
```

## 六、调试指南

### 6.1 测试说"数据库没表"

**可能原因**（按排查顺序）：

1. **镜像还没构建完成**（最常见） — push 后 CI 需要 3-5 分钟。检查：https://github.com/CwtchToMe/TakeOutSystem/actions
2. **测试拉的是旧镜像** — 检查镜像构建时间：`docker inspect cwtchtome/takeout-backend:latest --format '{{.Created}}'`
3. **MySQL volume 污染** — 重建：`docker compose down -v && docker compose up -d`

**修复命令：**
```powershell
docker compose -f docker-compose.ci.yml down -v
docker compose -f docker-compose.ci.yml pull
docker compose -f docker-compose.ci.yml up -d
```

### 6.2 测试说"接口返回 500"

1. 查后端日志：`docker compose logs backend`
2. 查健康检查：`curl http://localhost:8080/api/health`
3. 清 Redis 缓存：`docker compose exec redis redis-cli flushdb`
4. 重启后端：`docker compose restart backend`

### 6.3 修改了代码如何更新

```powershell
git add .
git commit -m "改了xxx"
git push          # 自动触发 CI 构建新镜像
```
