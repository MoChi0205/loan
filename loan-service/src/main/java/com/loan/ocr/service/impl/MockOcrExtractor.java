package com.loan.ocr.service.impl;

import com.loan.ocr.service.OcrExtractor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Mock OCR 提取器（默认实现，provider=mock）。
 *
 * <p>阶段一不接入真实引擎：返回空 Map，由调用方决定回灌与否
 * （Mock 下 {@code ocrApplied=false}，不报错）。真实引擎（腾讯云 / 通义 VL）
 * 通过 {@code loan.ocr.provider=vlm} 切换为 {@link VlmOcrExtractor} 接入，
 * 按「按 provider 选实现」范式，不改动本文件之外的业务流程。
 *
 * <p>装配：{@code loan.ocr.provider=mock}（缺省即 mock）时生效；设为 vlm 时由
 * {@link VlmOcrExtractor} 接管。两者均实现 {@link OcrExtractor}，调用方无感知。
 *
 * @author loan-platform
 */
@Component
@ConditionalOnProperty(name = "loan.ocr.provider", havingValue = "mock", matchIfMissing = true)
public class MockOcrExtractor implements OcrExtractor {

    @Override
    public Map<String, Object> extract(String filePath, String bizType) {
        // 阶段一：不调用任何外部引擎，返回空事实
        return Collections.emptyMap();
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
