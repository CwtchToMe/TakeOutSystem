package com.takeout.auth;

import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "发送验证码、登录、退出、刷新 Token")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送短信验证码", description = "开发环境验证码固定为 123456，60 秒内不可重复发送")
    @PostMapping("/sms/send")
    public Result<Void> sendSms(@Valid @RequestBody SmsCodeRequest request) {
        authService.sendSmsCode(request.phone());
        return Result.success();
    }

    @Operation(summary = "手机号验证码登录", description = "返回 accessToken（短期）和 refreshToken（长期），accessToken 过期后用 /refresh 换新")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "退出登录", description = "使 refreshToken 失效，需在 Header 中携带 Bearer Token")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization);
        return Result.success();
    }

    @Operation(summary = "刷新 AccessToken", description = "用有效的 refreshToken 换取新的 accessToken")
    @PostMapping("/refresh")
    public Result<String> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request.refreshToken()));
    }
}
