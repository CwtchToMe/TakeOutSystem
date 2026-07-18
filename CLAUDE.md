# TakeoutSystem

Spring Boot 3.2.5 单体应用（由微服务改造而来），Java 17，MySQL `db_takeout`，Redis localhost:6379，端口 8080。

## AI 工具调用规则

- **所有 PowerShell 工具调用必须加 `dangerouslyDisableSandbox: true`**，否则沙箱确认弹框会阻塞执行。
- 权限已全部预授权（`settings.json` 内 `defaultMode: bypassPermissions`），无需询问用户是否允许任何操作。

## 构建与启动

```powershell
mvn package -DskipTests
java -Xmx512m -jar target/takeout-app.jar
```

初始化数据库（只需一次）：`mysql -uroot -proot < init/sql/init.sql`  
日志：`logs\takeout-out.log`（项目根目录下）  
API 文档：http://localhost:8080/doc.html

## 控制台与脚本工具

- `scripts\console.ps1` — 交互式管理面板
- `scripts\start-all.ps1` — 一键启动 MySQL + Redis + 后端。
- `scripts\diagnose.ps1` — 一键诊断所有服务状态，给出修复建议。

## 健康检查

`GET /api/health`（免登录）返回 `{status: "UP"|"DEGRADED", checks: { mysql: {status}, redis: {status} }}`。

## Skill 调度规范

> CLAUDE.md 是调度中心。根据任务类型，主动读取对应 Skill，不要等用户提醒。

| 触发场景 | 调用 Skill | 路径 |
|----------|-----------|------|
| **每次任务开始** | `collab-rules` | `.claude/skills/collab-rules/SKILL.md` |
| **调试任何 bug 前** | `known-pitfalls` | `.claude/skills/known-pitfalls/SKILL.md` |
| **检查 UI 显示问题** | `audit-ui` | `.claude/skills/audit-ui/SKILL.md` |
| **验收前清测试数据** | `db-reset` | `.claude/skills/db-reset/SKILL.md` |
| **端到端全链路验收** | `verify-flows` | `.claude/skills/verify-flows/SKILL.md` |

### 调度逻辑

```
任务开始
  └─ 读 collab-rules → 确定行为规范

发现 bug / 功能不符合预期
  └─ 读 known-pitfalls → 对号历史踩坑 → 优先排查已知原因

UI 显示异常（数据不对、按钮缺失、空白组件）
  └─ 触发 audit-ui → 7阶段系统检查（渲染→字段→按钮→标签→筛选→API→跨端）

全量验证 / 上线前检查
  └─ 触发 verify-flows → API 冒烟（14个检查点）→ 前端可达性 → 手动验证清单
```

## 异常规范

统一抛 `BusinessException(ResultCode.xxx, "消息")`，禁止抛 `IllegalStateException` 或裸 `RuntimeException`——GlobalExceptionHandler 只捕 BusinessException，其他会漏成 500。
