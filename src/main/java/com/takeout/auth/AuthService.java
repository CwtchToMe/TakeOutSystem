package com.takeout.auth;

import com.takeout.common.enums.UserRole;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.ResultCode;
import com.takeout.common.util.JwtUtil;
import com.takeout.common.util.RedisUtil;
import com.takeout.config.JwtProperties;
import com.takeout.user.User;
import com.takeout.user.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RedisUtil redisUtil;
    private final JwtProperties jwtProperties;
    private final UserService userService;

    private static final String SMS_CODE_KEY = "sms:code:";
    private static final String SMS_LIMIT_KEY = "sms:limit:";
    private static final String TOKEN_KEY = "user:token:";
    private static final String BEARER_PREFIX = "Bearer ";

    public void sendSmsCode(String phone) {
        log.info("发送短信验证码，手机号: {}", phone);
        if (redisUtil.hasKey(SMS_LIMIT_KEY + phone)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "操作太频繁，请60秒后再试");
        }
        // 开发环境固定验证码 123456，生产环境替换为真实短信服务
        String code = "123456";
        redisUtil.set(SMS_CODE_KEY + phone, code, Duration.ofMinutes(5));
        redisUtil.set(SMS_LIMIT_KEY + phone, "1", Duration.ofSeconds(60));
        log.info("[模拟短信] 手机号: {}，验证码: {}，有效期: 5分钟", phone, code);
    }

    public LoginVO login(LoginRequest request) {
        log.info("用户登录，手机号: {}", request.phone());

        String codeKey = SMS_CODE_KEY + request.phone();
        // 开发环境万能码：123456 直接跳过 Redis 校验
        if (!"123456".equals(request.code())) {
            Object rawCode = redisUtil.get(codeKey);
            if (rawCode == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "验证码已过期，请重新获取");
            }
            if (!rawCode.toString().equals(request.code())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "验证码错误");
            }
            redisUtil.delete(codeKey);
        }

        User user = userService.getOrCreate(request.phone());

        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        UserRole userRole;
        try {
            userRole = UserRole.valueOf(user.getRole());
        } catch (IllegalArgumentException e) {
            userRole = UserRole.CUSTOMER;
        }

        String accessToken = JwtUtil.generateToken(user.getId(), userRole,
                jwtProperties.getSecret(), jwtProperties.getAccessTokenExpire());
        String refreshToken = JwtUtil.generateToken(user.getId(), userRole,
                jwtProperties.getSecret(), jwtProperties.getRefreshTokenExpire());

        redisUtil.set(TOKEN_KEY + user.getId(), refreshToken,
                Duration.ofMillis(jwtProperties.getRefreshTokenExpire()));

        log.info("登录成功，userId: {}", user.getId());
        return new LoginVO(accessToken, refreshToken,
                new UserInfoVO(user.getId(), user.getPhone(), user.getNickname(),
                        user.getAvatarUrl(), user.getRole()));
    }

    public void logout(String accessToken) {
        String token = extractToken(accessToken);
        if (token == null) return;
        Long userId = JwtUtil.getUserId(token, jwtProperties.getSecret());
        if (userId != null) {
            redisUtil.delete(TOKEN_KEY + userId);
            log.info("用户退出登录，userId: {}", userId);
        }
    }

    public String refreshToken(String refreshToken) {
        Claims claims = JwtUtil.parseToken(refreshToken, jwtProperties.getSecret());
        if (claims == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 无效或已过期");
        }

        Object userIdObj = claims.get("userId");
        Long userId;
        if (userIdObj instanceof Integer i) userId = i.longValue();
        else if (userIdObj instanceof Long l) userId = l;
        else throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 解析失败");

        String stored = redisUtil.get(TOKEN_KEY + userId, String.class);
        if (!refreshToken.equals(stored)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已失效，请重新登录");
        }

        // 刷新时检查用户状态（防止禁用用户长期持有有效 token）
        User user = userService.getById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            redisUtil.delete(TOKEN_KEY + userId);
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请重新登录");
        }

        UserRole role = JwtUtil.getUserRole(refreshToken, jwtProperties.getSecret());
        if (role == null) role = UserRole.CUSTOMER;

        log.info("刷新 AccessToken 成功，userId: {}", userId);
        return JwtUtil.generateToken(userId, role,
                jwtProperties.getSecret(), jwtProperties.getAccessTokenExpire());
    }

    private String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization)) return null;
        return authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()) : authorization;
    }
}
