---
description: 检查 TakeoutSystem 当前运行状态（MySQL、Redis、App 端口）
allowed-tools: PowerShell, Bash
---

检查外卖系统（单体版）的运行状态，给出清晰的状态报告。

## 检查步骤

### 1. 端口检查
用 `Test-NetConnection -ComputerName localhost -Port <端口> -InformationLevel Quiet` 依次检查：
- MySQL: 3306
- Redis: 6379
- App: 8080

### 2. App 健康验证（如果 8080 在线）
```
curl -s http://localhost:8080/api/auth/sms/send -X POST -H "Content-Type: application/json" -d "{\"phone\":\"13800138000\"}"
```
有响应（无论成功失败）说明 App 正常运行。

### 3. Java 进程检查
```powershell
Get-Process -Name "java" -ErrorAction SilentlyContinue | Select-Object Id, CPU, WorkingSet
```
显示 PID 和内存占用（WorkingSet 单位字节，除以 1MB 换算）。

## 输出格式

```
=== TakeoutSystem 运行状态 ===

[基础设施]
  ✓/✗  MySQL  :3306
  ✓/✗  Redis  :6379

[应用]
  ✓/✗  App    :8080
  进程: PID=xxxxx  内存=xxxMB

[API 文档] http://localhost:8080/doc.html

[建议] 如有未启动的服务，给出对应启动提示
```

若 MySQL 或 Redis 未启动，提示使用 console.ps1 的对应选项启动。
若 App 未启动，提示运行 `java -Xmx512m -jar target/takeout-app.jar`。
