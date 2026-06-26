---
name: db-reset
description: 清空测试数据、恢复库存和商家/用户状态，用于每次验证前的环境初始化
---

# 测试数据重置

> 每次跑 `verify-flows` 前执行，确保没有脏数据干扰结果。

---

## 完整重置脚本（单次粘贴执行）

```powershell
$mysql = "& 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' -uroot -proot db_takeout -e"
$redis = "& 'C:\Program Files\Redis\redis-cli.exe'"

# ── 1. 清订单相关表 ──────────────────────────────────────
Invoke-Expression "$mysql `"DELETE FROM t_order_item`""
Invoke-Expression "$mysql `"DELETE FROM t_order`""
Invoke-Expression "$mysql `"DELETE FROM t_cart`""
Write-Host "Orders/cart cleared"

# ── 2. 恢复菜品库存（初始值来自 init.sql）────────────────
Invoke-Expression "$mysql `"UPDATE t_dish SET stock=100 WHERE id=1`""  # 宫保鸡丁
Invoke-Expression "$mysql `"UPDATE t_dish SET stock=100 WHERE id=2`""  # 鱼香肉丝
Invoke-Expression "$mysql `"UPDATE t_dish SET stock=50  WHERE id=3`""  # 麻婆豆腐
Invoke-Expression "$mysql `"UPDATE t_dish SET stock=200 WHERE id=4`""  # 米饭
Invoke-Expression "$mysql `"UPDATE t_dish SET stock=50  WHERE id=5`""  # 可乐
Invoke-Expression "$mysql `"UPDATE t_dish SET stock=80  WHERE id=6`""  # 啤酒
Write-Host "Dish stock restored"

# ── 3. 恢复商家状态（status=1 营业中）────────────────────
Invoke-Expression "$mysql `"UPDATE t_merchant SET status=1 WHERE id IN (1,2)`""
# 确保 owner 正确（13800000002→香辣料理, 13800000004→快乐汉堡）
Invoke-Expression "$mysql `"UPDATE t_merchant SET owner_id=(SELECT id FROM t_user WHERE phone='13800000002') WHERE id=1`""
Invoke-Expression "$mysql `"UPDATE t_merchant SET owner_id=(SELECT id FROM t_user WHERE phone='13800000004') WHERE id=2`""
Write-Host "Merchant status reset to 1"

# ── 4. 恢复用户状态（status=1 正常）──────────────────────
Invoke-Expression "$mysql `"UPDATE t_user SET status=1`""
Write-Host "User status reset to 1"

# ── 5. Redis FLUSHDB ──────────────────────────────────────
Invoke-Expression "$redis FLUSHDB"
Write-Host "Redis flushed"

Write-Host "`n=== DB reset complete ==="
```

---

## 验证重置结果

```powershell
$mysql = "& 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' -uroot -proot db_takeout -e"

# 订单数应为 0
Invoke-Expression "$mysql `"SELECT COUNT(*) as order_count FROM t_order`""

# 商家状态应全为 1
Invoke-Expression "$mysql `"SELECT id, name, status FROM t_merchant`""

# 库存恢复情况
Invoke-Expression "$mysql `"SELECT id, name, stock FROM t_dish`""
```

---

## 说明

| 步骤 | 原因 |
|------|------|
| 清订单表 | 上次验证留下的订单会干扰 nearby/count 等统计接口 |
| 清购物车 | 防止"商品已不在该商家"类报错 |
| 恢复库存 | Lua 脚本扣库存不随事务回滚，脏数据导致下单失败 |
| 恢复商家状态 | status=2（打烊）时 nearby 只返回 1 个商家 |
| 恢复用户状态 | 禁用测试后忘恢复会导致后续登录失败 |
| FLUSHDB | 清除旧 token 缓存、菜单缓存，避免拿到过期数据 |
