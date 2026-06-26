package com.takeout.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.takeout.common.context.UserContext;
import com.takeout.common.enums.UserRole;
import com.takeout.common.result.Result;
import com.takeout.common.result.ResultCode;
import com.takeout.common.util.JwtUtil;
import com.takeout.config.JwtProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String BEARER_PREFIX = "Bearer ";

    private static final List<String> WHITELIST = List.of(
            "/api/health",
            "/api/health/**",
            "/api/auth/**",
            "/api/merchant/nearby",
            "/api/merchant/search",
            "/api/product/menu/**",
            "/api/review/merchant/**",
            "/doc.html", "/doc.html/**", "/v3/api-docs/**",
            "/swagger-ui/**", "/webjars/**", "/favicon.ico"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        if (WHITELIST.stream().anyMatch(p -> PATH_MATCHER.match(p, path))) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, ResultCode.UNAUTHORIZED, "请先登录");
            return false;
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        Claims claims = JwtUtil.parseToken(token, jwtProperties.getSecret());
        if (claims == null) {
            writeError(response, ResultCode.UNAUTHORIZED, "Token 无效或已过期，请重新登录");
            return false;
        }

        Long userId = extractUserId(claims);
        if (userId == null) {
            writeError(response, ResultCode.UNAUTHORIZED, "Token 解析失败");
            return false;
        }

        String roleStr = claims.get("userRole", String.class);
        UserRole role;
        try {
            role = UserRole.valueOf(StringUtils.hasText(roleStr) ? roleStr : "CUSTOMER");
        } catch (IllegalArgumentException e) {
            role = UserRole.CUSTOMER;
        }

        UserContext.setUserId(userId);
        UserContext.setUserRole(role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private Long extractUserId(Claims claims) {
        Object userId = claims.get("userId");
        if (userId instanceof Integer i) return i.longValue();
        if (userId instanceof Long l) return l;
        return null;
    }

    private void writeError(HttpServletResponse response, ResultCode code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(code, message)));
    }
}
