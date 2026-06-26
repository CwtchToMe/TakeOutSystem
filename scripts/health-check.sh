#!/usr/bin/env bash
# 外卖系统健康检查脚本

set -e

BASE_URL="${BASE_URL:-http://localhost}"

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

FAILED=0

check_service() {
    local name=$1
    local url=$2
    local expected=${3:-200}

    http_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "$url" 2>/dev/null || echo "000")
    if [ "$http_code" = "$expected" ]; then
        pass "$name ($url) → HTTP $http_code"
    else
        fail "$name ($url) → HTTP $http_code (expected $expected)"
        FAILED=$((FAILED + 1))
    fi
}

echo ""
info "========== 基础设施健康检查 =========="
check_service "Nacos"       "http://localhost:8848/nacos/actuator/health"
check_service "MinIO"       "http://localhost:9000/minio/health/live"

echo ""
info "========== 应用服务健康检查 =========="
check_service "Gateway"      "${BASE_URL}:8080/actuator/health"
check_service "Auth"         "${BASE_URL}:8081/actuator/health"
check_service "User"         "${BASE_URL}:8082/actuator/health"
check_service "Merchant"     "${BASE_URL}:8083/actuator/health"
check_service "Product"      "${BASE_URL}:8084/actuator/health"
check_service "Order"        "${BASE_URL}:8085/actuator/health"
check_service "Pay"          "${BASE_URL}:8086/actuator/health"
check_service "Delivery"     "${BASE_URL}:8087/actuator/health"
check_service "Notification" "${BASE_URL}:8088/actuator/health"
check_service "File"         "${BASE_URL}:8090/actuator/health"

echo ""
info "========== API 冒烟测试 =========="

# 发送短信验证码
SMS_RESULT=$(curl -s -X POST "${BASE_URL}:8080/api/auth/sms/send" \
    -H "Content-Type: application/json" \
    -d '{"phone":"13800000001"}' 2>/dev/null || echo '{"code":500}')
SMS_CODE=$(echo "$SMS_RESULT" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)
if [ "$SMS_CODE" = "200" ]; then
    pass "发送短信验证码"
else
    fail "发送短信验证码 → $SMS_RESULT"
    FAILED=$((FAILED + 1))
fi

# 用户登录
LOGIN_RESULT=$(curl -s -X POST "${BASE_URL}:8080/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"phone":"13800000001","code":"123456"}' 2>/dev/null || echo '{"code":500}')
LOGIN_CODE=$(echo "$LOGIN_RESULT" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)
ACCESS_TOKEN=$(echo "$LOGIN_RESULT" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ "$LOGIN_CODE" = "200" ] && [ -n "$ACCESS_TOKEN" ]; then
    pass "用户登录并获取 token"
else
    fail "用户登录 → $LOGIN_RESULT"
    FAILED=$((FAILED + 1))
fi

# 查询附近商家
if [ -n "$ACCESS_TOKEN" ]; then
    MERCHANT_RESULT=$(curl -s "${BASE_URL}:8080/api/merchant/nearby?longitude=113.9305&latitude=22.5292&radius=5000&page=1&size=10" \
        -H "Authorization: Bearer $ACCESS_TOKEN" 2>/dev/null || echo '{"code":500}')
    MERCHANT_CODE=$(echo "$MERCHANT_RESULT" | grep -o '"code":[0-9]*' | head -1 | cut -d: -f2)
    if [ "$MERCHANT_CODE" = "200" ]; then
        pass "查询附近商家"
    else
        fail "查询附近商家 → HTTP $MERCHANT_CODE"
        FAILED=$((FAILED + 1))
    fi
fi

echo ""
if [ $FAILED -eq 0 ]; then
    info "所有检查通过！系统运行正常。"
    exit 0
else
    info "有 ${FAILED} 项检查未通过，请查看上方详情。"
    exit 1
fi
