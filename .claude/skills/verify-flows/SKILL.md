---
name: verify-flows
description: 端到端验证 TakeoutSystem 所有核心流程是否正常运行（PowerShell 版）
---

# TakeoutSystem 流程验证

对外卖系统所有核心流程做端到端冒烟测试。**使用 PowerShell + Invoke-RestMethod**（Windows 环境，jq 不可用）。

> 验证前先执行 `db-reset` skill 清空测试数据，避免脏数据干扰结果。

---

## 完整验证脚本（单次粘贴执行）

```powershell
$base = "http://localhost:8080/api"

# ── 1. 健康检查 ──────────────────────────────────────────
$health = Invoke-RestMethod "$base/health"
Write-Host "Health: $($health.status)"  # 期望 UP

# ── 2. 三角色登录 ─────────────────────────────────────────
$userToken     = (Invoke-RestMethod -Method Post "$base/auth/login" -ContentType "application/json" -Body '{"phone":"13800000003","code":"123456"}').data.accessToken
$merchantToken = (Invoke-RestMethod -Method Post "$base/auth/login" -ContentType "application/json" -Body '{"phone":"13800000002","code":"123456"}').data.accessToken
$adminToken    = (Invoke-RestMethod -Method Post "$base/auth/login" -ContentType "application/json" -Body '{"phone":"13800000001","code":"123456"}').data.accessToken
Write-Host "Tokens OK: user=$($userToken.Substring(0,10))..."

$userH  = @{ Authorization = "Bearer $userToken" }
$merH   = @{ Authorization = "Bearer $merchantToken" }
$adminH = @{ Authorization = "Bearer $adminToken" }

# ── 3. 附近商家（北京坐标，应返回 total=2）────────────────
$nearby = Invoke-RestMethod "$base/merchant/nearby?longitude=116.39&latitude=39.95&radius=20000" -Headers $userH
Write-Host "Nearby merchants: $($nearby.data.total)"  # 期望 2
$merchantId = $nearby.data.records[0].id

# ── 4. 菜单 + 地址 ────────────────────────────────────────
$menu   = Invoke-RestMethod "$base/product/menu/$merchantId"
$dishId = $menu.data[0].dishes[0].id
$addrs  = Invoke-RestMethod "$base/user/address" -Headers $userH
$addrId = $addrs.data[0].id
Write-Host "dishId=$dishId  addrId=$addrId"

# ── 5. 完整订单流程 1→2→3→5→6 ───────────────────────────
$order = Invoke-RestMethod -Method Post "$base/order/submit" -Headers $userH `
  -ContentType "application/json" `
  -Body "{`"merchantId`":`"$merchantId`",`"addressId`":`"$addrId`",`"items`":[{`"dishId`":`"$dishId`",`"quantity`":1}],`"payType`":1}"
$orderNo = $order.data.orderNo
Write-Host "OrderNo=$orderNo  status=$((Invoke-RestMethod "$base/order/$orderNo" -Headers $userH).data.status)"  # 期望 1

$pay = Invoke-RestMethod -Method Post "$base/pay/create" -Headers $userH `
  -ContentType "application/json" -Body "{`"orderNo`":`"$orderNo`",`"payType`":1}"
$paymentNo = $pay.data.paymentNo

Invoke-RestMethod -Method Post "$base/pay/callback" -Headers $userH `
  -ContentType "application/json" -Body "{`"paymentNo`":`"$paymentNo`",`"success`":true}" | Out-Null
Write-Host "After pay: status=$((Invoke-RestMethod "$base/order/$orderNo" -Headers $userH).data.status)"  # 期望 2

Invoke-RestMethod -Method Post "$base/order/merchant/accept/$orderNo" -Headers $merH | Out-Null
Write-Host "After accept: status=$((Invoke-RestMethod "$base/order/$orderNo" -Headers $userH).data.status)"  # 期望 3

Invoke-RestMethod -Method Post "$base/order/merchant/ready/$orderNo" -Headers $merH | Out-Null
Write-Host "After ready: status=$((Invoke-RestMethod "$base/order/$orderNo" -Headers $userH).data.status)"  # 期望 5

Invoke-RestMethod -Method Post "$base/order/merchant/complete/$orderNo" -Headers $merH | Out-Null
Write-Host "After complete: status=$((Invoke-RestMethod "$base/order/$orderNo" -Headers $userH).data.status)"  # 期望 6

# ── 6. 取消订单（新提交，status=1 直接取消）──────────────
$order2 = Invoke-RestMethod -Method Post "$base/order/submit" -Headers $userH `
  -ContentType "application/json" `
  -Body "{`"merchantId`":`"$merchantId`",`"addressId`":`"$addrId`",`"items`":[{`"dishId`":`"$dishId`",`"quantity`":1}],`"payType`":1}"
Invoke-RestMethod -Method Post "$base/order/cancel/$($order2.data.orderNo)" -Headers $userH | Out-Null
Write-Host "Cancel: status=$((Invoke-RestMethod "$base/order/$($order2.data.orderNo)" -Headers $userH).data.status)"  # 期望 7

# ── 7. 管理员：禁用用户→验证拒绝→恢复 ───────────────────
Invoke-RestMethod -Method Put "$base/admin/user/3/status" -Headers $adminH `
  -ContentType "application/json" -Body '{"status":0}' | Out-Null
try {
  Invoke-RestMethod -Method Post "$base/auth/login" -ContentType "application/json" `
    -Body '{"phone":"13800000003","code":"123456"}' | Out-Null
  Write-Host "FAIL: disabled user should not login"
} catch { Write-Host "Disabled user blocked: OK ($($_.Exception.Response.StatusCode.Value__))" }
Invoke-RestMethod -Method Put "$base/admin/user/3/status" -Headers $adminH `
  -ContentType "application/json" -Body '{"status":1}' | Out-Null

# ── 8. 角色隔离（普通用户访问 admin → 403）────────────────
try {
  Invoke-RestMethod "$base/admin/user/list" -Headers $userH | Out-Null
  Write-Host "FAIL: should be 403"
} catch { Write-Host "Role isolation OK ($($_.Exception.Response.StatusCode.Value__))" }

# ── 9. 前端可达性 ─────────────────────────────────────────
foreach ($port in 3001, 3002, 3003) {
  $ok = (Test-NetConnection localhost -Port $port -InformationLevel Quiet)
  Write-Host "Port $port`: $(if($ok){'UP'}else{'DOWN'})"
}

Write-Host "`n=== 验证完成 ==="
```

---

## 期望输出

```
Health: UP
Tokens OK: user=eyJhbGci...
Nearby merchants: 2
dishId=1  addrId=1
OrderNo=...  status=1
After pay: status=2
After accept: status=3
After ready: status=5
After complete: status=6
Cancel: status=7
Disabled user blocked: OK (403)
Role isolation OK (403)
Port 3001: UP
Port 3002: UP
Port 3003: UP

=== 验证完成 ===
```

---

## 单点排查命令

```powershell
# 仅测健康
Invoke-RestMethod "http://localhost:8080/api/health"

# 仅测登录
(Invoke-RestMethod -Method Post "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" -Body '{"phone":"13800000003","code":"123456"}').data.accessToken
```

---

## 常见失败原因

| 现象 | 原因 | 解法 |
|------|------|------|
| Health DEGRADED | MySQL/Redis 未启动 | `scripts\start-all.ps1` |
| Nearby merchants: 1 | 某商家 status=2（打烊） | 执行 `db-reset` skill |
| status 卡在某步 | 订单状态流转 bug | 查日志 `logs\takeout-out.log` |
| 403 角色验证失败 | Token 过期或角色不对 | 重新登录取新 token |
