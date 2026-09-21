package com.loan.ocr.service;

import java.util.Map;

/**
 * OCR 提取器接口（可插拔：Mock / 腾讯云 / 通义 VL）。
 *
 * <p>仅借鉴 tse {@code OcrStrategyFactory} 的「按 provider 选实现」范式，
 * 不引入 tse 业务动作（外呼 / 拨号等）。实现类通过 Spring 条件装配切换。
 *
 * @author loan-platform
 */
public interface OcrExtractor {

    /**
     * 从文件提取原始字段。
     *
     * @param filePath 文件绝对路径（落盘后的本地路径）
     * @param bizType  经 {@code MaterialType} 白名单校验的资料类型
     * @return 原始字段（key 为 OCR 引擎返回的原始字段名，value 为字符串 / 数值）；识别失败返回空 Map
     */
    Map<String, Object> extract(String filePath, String bizType);

    /**
     * 提供方名称（mock / tencent / qwen_vl），用于埋点与配置切换。
     *
     * @return 提供方名称
     */
    String providerName();

    /** 当前实现是否已配置为可真实调用外部/本地 AI 识别服务。 */
    default boolean isAiRecognitionEnabled() {
        return false;
    }
}
