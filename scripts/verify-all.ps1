$base = "http://localhost:8080/api"
$passed = 0; $failed = 0; $allOk = $true

function C($m, $u, $b, $h) {
    $hdr = if ($h) { "-H `"$($h[0])`"" } else { "" }
    if ($b) {
        $bfile = [System.IO.Path]::GetTempFileName()
        [System.IO.File]::WriteAllText($bfile, $b, [System.Text.Encoding]::UTF8)
        $bdy = "-H `"Content-Type: application/json`" --data-binary `"@$bfile`""
    } else { $bdy = "" }
    $tmp = [System.IO.Path]::GetTempFileName() + ".txt"
    cmd /c "curl -s -X $m $hdr $bdy `"$base$u`" 1> `"$tmp`"" 2>$null
    if ($b) { Remove-Item $bfile -Force -ErrorAction SilentlyContinue }
    if (Test-Path $tmp) { $result = [System.IO.File]::ReadAllText($tmp, [System.Text.Encoding]::UTF8) } else { $result = "" }
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    return $result
}

function CJ($m, $u, $b, $h) {
    $raw = C $m $u $b $h
    if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
    try { return $raw | ConvertFrom-Json } catch { return $null }
}

function CK($m, $u, $b, $h, $msg, $cond) {
    $r = CJ $m $u $b $h
    if ($r -eq $null) {
        $raw = C $m $u $b $h
        Write-Host "  [FAIL] $msg (JSON error: $raw)" -ForegroundColor Red
        $script:failed++; $script:allOk = $false; return
    }
    $ok = $r | ForEach-Object $cond
    if ($ok) { Write-Host "  [PASS] $msg" -ForegroundColor Green; $script:passed++ }
    else {
        $raw = C $m $u $b $h
        Write-Host "  [FAIL] $msg -> $raw" -ForegroundColor Red
        $script:allOk = $false; $script:failed++
    }
}

function Login($p) {
    return (CJ POST "/auth/login" ('{"phone":"'+$p+'","code":"123456"}') $null).data
}

function Status { Write-Host "`n=== $passed PASSED, $failed FAILED ===`n" -ForegroundColor Cyan }

# ── M1: Auth ──
Write-Host "========== M1: Auth ==========" -ForegroundColor Yellow
CK POST "/auth/sms/send" '{"phone":"13800000003"}' $null "SMS send" { $_.code -eq 200 }
CK POST "/auth/login" '{"phone":"13800000003","code":"000000"}' $null "Invalid code" { $_.code -eq 400 }

$u = Login "13800000003"; $m = Login "13800000002"; $a = Login "13800000001"
$uH = @("Authorization: Bearer $($u.accessToken)"); $mH = @("Authorization: Bearer $($m.accessToken)"); $aH = @("Authorization: Bearer $($a.accessToken)")
CK POST "/auth/login" '{"phone":"13800000003","code":"123456"}' $null "Customer login" { $_.data.accessToken.Length -gt 20 }
CK POST "/auth/login" '{"phone":"13800000002","code":"123456"}' $null "Merchant login" { $_.data.accessToken.Length -gt 20 }
CK POST "/auth/login" '{"phone":"13800000001","code":"123456"}' $null "Admin login" { $_.data.accessToken.Length -gt 20 }
CK POST "/auth/refresh" ('{"refreshToken":"'+$u.refreshToken+'"}') $uH "Token refresh" { $_.code -eq 200 }
CK POST "/auth/logout" $null $uH "Logout" { $_.code -eq 200 }

$u = Login "13800000003"; $uH = @("Authorization: Bearer $($u.accessToken)")
Status

# ── M2: User ──
Write-Host "========== M2: User ==========" -ForegroundColor Yellow
CK GET "/user/profile" $null $uH "Get profile" { $_.data.phone -eq "13800000003" }
CK PUT "/user/profile" '{"nickname":"测试用户"}' $uH "Update nickname" { $_.code -eq 200 }

$r = CJ GET "/user/address" $null $uH
$addrId = $r.data[0].id
CK GET "/user/address" $null $uH "Address list" { $_.data.Count -ge 1 }
$body = '{"receiver":"测试","phone":"13800000003","province":"北京市","city":"北京市","district":"朝阳区","detail":"测试路100号","longitude":116.39,"latitude":39.95}'
CK POST "/user/address" $body $uH "Add address" { $_.code -eq 200 }
$body2 = '{"receiver":"更新姓名"}'
CK PUT "/user/address/$addrId" $body2 $uH "Update address" { $_.code -eq 200 }
CK PUT "/user/address/$addrId/default" $null $uH "Set default" { $_.code -eq 200 }
CK DELETE "/user/address/$addrId" $null $uH "Delete address" { $_.code -eq 200 }
Status

# ── M3: Merchant ──
Write-Host "========== M3: Merchant ==========" -ForegroundColor Yellow
$nearby = CJ GET "/merchant/nearby?longitude=116.39&latitude=39.95&radius=20000" $null $uH
$mid = $nearby.data.records[0].id
CK GET "/merchant/nearby?longitude=116.39&latitude=39.95&radius=20000" $null $uH "Nearby merchants" { $_.data.total -ge 1 }
CK GET "/merchant/search?keyword=%E9%A6%96" $null $uH "Search merchant" { $_.data.total -ge 1 }
CK GET "/merchant/$mid" $null $uH "Merchant detail" { $_.data.id -eq $mid }
CK GET "/merchant/my" $null $mH "Get my merchant" { $_.code -eq 200 }
CK PUT "/merchant/my" '{"notice":"欢迎光临"}' $mH "Update merchant" { $_.code -eq 200 }
CK PUT "/merchant/my/status" '{"status":2}' $mH "Close shop" { $_.code -eq 200 }
CK PUT "/merchant/my/status" '{"status":1}' $mH "Reopen" { $_.code -eq 200 }
Status

# ── M4: Product ──
Write-Host "========== M4: Product ==========" -ForegroundColor Yellow
$menu = CJ GET "/product/menu/$mid" $null $uH
$dishId = $menu.data[0].dishes[0].id; $catId = $menu.data[0].id
CK GET "/product/menu/$mid" $null $uH "Public menu" { $_.data.Count -ge 1 }
CK GET "/product/dish?merchantId=$mid" $null $mH "Dish list" { $_.code -eq 200 }
CK PUT "/product/dish/$dishId/status" '{"status":0}' $mH "Off shelf" { $_.code -eq 200 }
CK PUT "/product/dish/$dishId/status" '{"status":1}' $mH "On shelf" { $_.code -eq 200 }
CK GET "/product/category?merchantId=$mid" $null $mH "Category list" { $_.code -eq 200 }

$body = '{"merchantId":' + $mid + ',"name":"测试分类","sort":99}'
CK POST "/product/category" $body $mH "Add category" { $_.data -gt 0 }
$newCatId = (CJ POST "/product/category" $body $mH).data
CK PUT "/product/category/$newCatId" '{"name":"测试分类2"}' $mH "Update category" { $_.code -eq 200 }
CK DELETE "/product/category/$newCatId" $null $mH "Delete category" { $_.code -eq 200 }

$body = '{"merchantId":' + $mid + ',"categoryId":' + $catId + ',"name":"测试菜","price":1.00,"stock":1}'
CK POST "/product/dish" $body $mH "Add dish" { $_.data -gt 0 }
$newDishId = (CJ POST "/product/dish" $body $mH).data
CK DELETE "/product/dish/$newDishId" $null $mH "Delete dish" { $_.code -eq 200 }
Status

# ── M5: Cart ──
Write-Host "========== M5: Cart ==========" -ForegroundColor Yellow
$body = '{"merchantId":' + $mid + ',"dishId":' + $dishId + ',"quantity":2}'
CK POST "/cart/add" $body $uH "Add to cart" { $_.code -eq 200 }
$cartList = CJ GET "/cart/$mid" $null $uH
$cartId = $cartList.data[0].id
CK GET "/cart/$mid" $null $uH "Get cart" { $_.data.Count -ge 1 }
$r = C PUT "/cart/$cartId?quantity=3" $null $uH
if ($r -match '"code":200') { Write-Host "  [PASS] Update cart qty" -ForegroundColor Green; $passed++ } else { Write-Host "  [FAIL] Update cart qty -> $r" -ForegroundColor Red; $failed++; $allOk=$false }
CK DELETE "/cart/$cartId" $null $uH "Delete cart item" { $_.code -eq 200 }
CK DELETE "/cart/clear/$mid" $null $uH "Clear cart" { $_.code -eq 200 }
Status

# ── M6: Order + Pay ──
Write-Host "========== M6: Order + Pay ==========" -ForegroundColor Yellow
$cartBody = '{"merchantId":' + $mid + ',"dishId":' + $dishId + ',"quantity":1}'
C POST "/cart/add" $cartBody $uH | Out-Null

$orderBody = '{"merchantId":' + $mid + ',"addressId":' + $addrId + ',"items":[{"dishId":' + $dishId + ',"quantity":1}],"payType":1}'
$orderResp = CJ POST "/order/submit" $orderBody $uH
$orderNo = $orderResp.data.orderNo
CK POST "/order/submit" $orderBody $uH "Submit order" { $_.data.orderNo.Length -gt 5 }
CK GET "/order/$orderNo" $null $uH "Order detail" { $_.data.orderNo -eq $orderNo }
CK GET "/order/$orderNo" $null $uH "Status=1" { $_.data.status -eq 1 }

$payBody = '{"orderNo":"' + $orderNo + '","payType":1}'
$pn = (CJ POST "/pay/create" $payBody $uH).data.paymentNo
CK POST "/pay/create" $payBody $uH "Create payment" { $_.data.paymentNo.Length -gt 5 }
CK GET "/pay/status/$orderNo" $null $uH "Payment status" { $_.data.paymentNo -eq $pn }
$cbBody = '{"paymentNo":"' + $pn + '","success":true}'
CK POST "/pay/callback" $cbBody $uH "Pay callback" { $_.code -eq 200 }
CK GET "/order/$orderNo" $null $uH "Status=2" { $_.data.status -eq 2 }
CK POST "/order/merchant/accept/$orderNo" $null $mH "Accept" { $_.code -eq 200 }
CK GET "/order/$orderNo" $null $uH "Status=3" { $_.data.status -eq 3 }
CK POST "/order/merchant/ready/$orderNo" $null $mH "Ready" { $_.code -eq 200 }
CK GET "/order/$orderNo" $null $uH "Status=5" { $_.data.status -eq 5 }
CK POST "/order/merchant/complete/$orderNo" $null $mH "Complete" { $_.code -eq 200 }
CK GET "/order/$orderNo" $null $uH "Status=6" { $_.data.status -eq 6 }
CK GET "/order/list?status=6" $null $uH "Order list" { $_.data.records.Count -ge 1 }
CK GET "/order/merchant/list?merchantId=$mid&status=6" $null $mH "Merchant order list" { $_.code -eq 200 }
Status

# ── M7: Coupon + Review + Favorite ──
Write-Host "========== M7: Coupon+Review+Fav ==========" -ForegroundColor Yellow
CK GET "/coupon/available" $null $uH "Available coupons" { $_.code -eq 200 }
$avail = CJ GET "/coupon/available" $null $uH
if ($avail.data.Count -gt 0) { CK POST "/coupon/receive/$($avail.data[0].id)" $null $uH "Receive coupon" { $_.code -eq 200 } }
CK GET "/coupon/my" $null $uH "My coupons" { $_.code -eq 200 }
CK GET "/coupon/usable?amount=30&merchantId=$mid" $null $uH "Usable coupons" { $_.code -eq 200 }

$reviewBody = '{"orderNo":"' + $orderNo + '","score":5,"content":"很好吃"}'
CK POST "/review" $reviewBody $uH "Submit review" { $_.code -eq 200 }
CK GET "/review/order/$orderNo" $null $uH "Order review" { $_.data.score -eq 5 }
CK GET "/review/my" $null $uH "My reviews" { $_.data.Count -ge 1 }
CK GET "/review/merchant/$mid" $null $uH "Merchant reviews" { $_.data.Count -ge 1 }
CK POST "/favorite/$mid" $null $uH "Add favorite" { $_.code -eq 200 }
CK GET "/favorite/check/$mid" $null $uH "Check fav" { $_.data -eq $true }
CK GET "/favorite" $null $uH "Fav list" { $_.data.Count -ge 1 }
CK DELETE "/favorite/$mid" $null $uH "Remove fav" { $_.code -eq 200 }
Status

# ── M8: Cancel + Admin + Role + Frontend ──
Write-Host "========== M8: Cancel+Admin+Role+F/E ==========" -ForegroundColor Yellow
$r = CJ POST "/order/submit" $orderBody $uH; $on2 = $r.data.orderNo
CK POST "/order/cancel/$on2" $null $uH "Cancel" { $_.code -eq 200 }
CK GET "/order/$on2" $null $uH "Status=7" { $_.data.status -eq 7 }

$r = CJ POST "/order/submit" $orderBody $uH; $on3 = $r.data.orderNo
$pn3 = (CJ POST "/pay/create" ('{"orderNo":"'+$on3+'","payType":1}') $uH).data.paymentNo
CJ POST "/pay/callback" ('{"paymentNo":"'+$pn3+'","success":true}') $uH | Out-Null
CK POST "/order/merchant/reject/$on3" '{"reason":"食材不足"}' $mH "Reject" { $_.code -eq 200 }
CK GET "/order/$on3" $null $uH "Rejected=7" { $_.data.status -eq 7 }

CK GET "/admin/user/list" $null $aH "Admin user list" { $_.data.records.Count -ge 3 }
CK GET "/admin/merchant/list" $null $aH "Admin merchant list" { $_.data.records.Count -ge 1 }
CK GET "/admin/order/list" $null $aH "Admin order list" { $_.code -eq 200 }
CK PUT "/admin/user/3/status" '{"status":0}' $aH "Disable user" { $_.code -eq 200 }
CK POST "/auth/login" '{"phone":"13800000003","code":"123456"}' $null "Disabled blocked" { $_.code -eq 403 }
CK PUT "/admin/user/3/status" '{"status":1}' $aH "Re-enable user" { $_.code -eq 200 }
CK GET "/admin/user/list" $null $uH "User 403 admin" { $_.code -eq 403 }
CK GET "/order/merchant/list?merchantId=$mid" $null $uH "User 403 merchant" { $_.code -eq 403 }

foreach ($p in 3001,3002,3003) {
    $up = $null -ne (netstat -an 2>$null | Select-String ":$p\s.*LISTENING" | Select-Object -First 1)
    if ($up) { Write-Host "  [PASS] Port $p UP" -ForegroundColor Green; $passed++ } else { Write-Host "  [FAIL] Port $p DOWN" -ForegroundColor Red; $failed++; $allOk=$false }
}
Status

Write-Host "========================================================" -ForegroundColor Cyan
if ($allOk) { Write-Host "  ALL PASSED! Total: $passed" -ForegroundColor Green }
else { Write-Host "  $passed PASSED, $failed FAILED" -ForegroundColor Red }
exit $(if ($allOk){0}else{1})
