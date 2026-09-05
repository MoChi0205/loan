package com.loan.mini.controller;

import com.loan.common.Result;
import com.loan.infrastructure.wechat.WxJsSdkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微信 JS-SDK 签名接口（H5 在微信浏览器内 {@code wx.config} 前置调用）。
 *
 * <p>路径沿用小程序域 {@code /api/mini/wechat/*}（H5 与小程序同源），返回
 * {@code {appId, timestamp, nonceStr, signature}}，前端配合公众号 jsApiList 注入。
 * 该接口对页面 URL 签名，<strong>无需登录</strong>；若网关对 {@code /api/mini} 有登录拦截，
 * 需在网关将 {@code /api/mini/wechat/jssdk/signature} 加入白名单。</p>
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/wechat")
@RequiredArgsConstructor
public class WxJsSdkController {

    private final WxJsSdkService wxJsSdkService;

    /**
     * 获取 JS-SDK 签名。
     *
     * @param url 当前页面 URL（不含 # 及其后部分）
     * @return 签名参数
     */
    @GetMapping("/jssdk/signature")
    public Result<Map<String, String>> jssdkSignature(@RequestParam String url) {
        return Result.ok(wxJsSdkService.signature(url));
    }
}
