package com.loan.config.controller;

import com.loan.common.Result;
import com.loan.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 配置中心 HTTP 接口（配置向导完成度）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    /**
     * 配置完成度总览。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(configService.status());
    }
}
