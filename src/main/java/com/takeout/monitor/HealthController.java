package com.takeout.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", "TakeoutSystem");
        result.put("status", "UP");

        Map<String, Object> checks = new LinkedHashMap<>();

        checks.put("mysql", checkMysql());
        checks.put("redis", checkRedis());

        result.put("checks", checks);

        long downCount = checks.values().stream()
                .filter(v -> v instanceof Map && !"UP".equals(((Map<?, ?>) v).get("status")))
                .count();
        result.put("status", downCount == 0 ? "UP" : "DEGRADED");
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> checkMysql() {
        Map<String, Object> info = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            info.put("status", "UP");
            info.put("database", conn.getCatalog());
            info.put("url", conn.getMetaData().getURL());
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
            info.put("hint", "MySQL 未启动或连接失败。运行 netstat -an | Select-String \":3306\" 检查端口，或用 console 菜单 2 启动");
            log.warn("健康检查: MySQL 不可用", e);
        }
        return info;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                info.put("status", "UP");
            } else {
                info.put("status", "DOWN");
                info.put("error", "ping 返回: " + pong);
            }
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
            info.put("hint", "Redis 未启动或连接失败。运行 netstat -an | Select-String \":6379\" 检查端口，或用 console 菜单 2 启动");
            log.warn("健康检查: Redis 不可用", e);
        }
        return info;
    }
}
