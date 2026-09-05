package com.loan.ocr.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.ocr.service.OcrExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型视觉抽取器（VLM，provider=vlm）。
 *
 * <p>遵循设计：OCR 引擎仅「按 provider 选实现」范式可插拔，本类为视觉大模型实现。
 * 调用方（{@link com.loan.ocr.service.OcrService#recognize}）不感知具体引擎，
 * 仅通过 {@code loan.ocr.provider=vlm} 切换装配。</p>
 *
 * <p>协议：OpenAI 兼容的 chat/completions 多模态接口（通义千问 VL / GLM-4V / 本地 vLLM 等均兼容）。
 * 读取落盘文件 → base64 → 作为 image_url（图片）或 file_data（PDF）传入，携带结构化抽取提示词，
 * 要求模型仅返回 JSON；解析后映射为原始字段 Map（key 为模型返回的原始字段名）。</p>
 *
 * <p><b>优雅降级：</b>当 {@code loan.ocr.vlm.base-url} 或 {@code loan.ocr.vlm.api-key} 未配置时，
 * 本抽取器不抛异常，返回空 Map（与 Mock 行为一致），保证应用在无密钥环境下仍正常启动与上传。
 * 配置就绪后无需改代码即自动激活真实识别。</p>
 *
 * <p><b>JDK 8 约束（红线）：</b>后端编译固定 JDK 8，本类不得使用 {@code java.net.http}、
 * {@code List.of}、{@code String.isBlank}、{@code Path.of}、{@code Files.readString}
 * 等 Java 9+ API；HTTP 调用统一走 {@link HttpURLConnection}。</p>
 *
 * @author loan-platform
 */
@Component
@ConditionalOnProperty(name = "loan.ocr.provider", havingValue = "vlm")
public class VlmOcrExtractor implements OcrExtractor {

    private static final Logger log = LoggerFactory.getLogger(VlmOcrExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 支持直接作为多模态内容传入的 MIME（图片 / PDF）。 */
    private static final List<String> MULTIMODAL_MIME = Arrays.asList(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/bmp", "application/pdf");

    @Value("${loan.ocr.vlm.base-url:}")
    private String baseUrl;

    @Value("${loan.ocr.vlm.api-key:}")
    private String apiKey;

    @Value("${loan.ocr.vlm.model:qwen-vl-max}")
    private String model;

    @Value("${loan.ocr.vlm.timeout-ms:60000}")
    private long timeoutMs;

    @Value("${loan.ocr.vlm.system-prompt:}")
    private String systemPrompt;

    @Override
    public Map<String, Object> extract(String filePath, String bizType) {
        if (!isConfigured()) {
            log.warn("VLM 未配置（loan.ocr.vlm.base-url / api-key 缺失），降级返回空事实");
            return Collections.emptyMap();
        }
        Path path = filePath == null ? null : Paths.get(filePath);
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            log.warn("VLM 输入文件不存在或不可读: {}", filePath);
            return Collections.emptyMap();
        }
        String mime = probeMime(path);
        if (!MULTIMODAL_MIME.contains(mime)) {
            // 非多模态可识别类型（如纯文本/表格）尝试直接读文本；否则降级为空
            if (mime.startsWith("text/") || mime.equals("application/json")
                    || mime.equals("application/vnd.ms-excel")
                    || mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                return extractFromTextFile(path);
            }
            log.warn("VLM 暂不支持的 MIME 类型: {}，降级返回空事实", mime);
            return Collections.emptyMap();
        }
        try {
            String dataUri = "data:" + mime + ";base64,"
                    + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            String json = callVisionModel(dataUri, bizType);
            return parseModelJson(json);
        } catch (Exception e) {
            log.warn("VLM 识别失败（不影响上传主流程）: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public String providerName() {
        return "vlm";
    }

    /** 配置是否就绪。 */
    private boolean isConfigured() {
        return !isBlank(baseUrl) && !isBlank(apiKey);
    }

    /** JDK 8 兼容的空白判断（替代 String#isBlank）。 */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** MIME 探测（Files.probeContentType 可能为空，按扩展名兜底）。 */
    private String probeMime(Path path) {
        try {
            String p = Files.probeContentType(path);
            if (!isBlank(p)) {
                return p;
            }
        } catch (IOException ignored) {
            // 忽略，走扩展名兜底
        }
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (name.endsWith(".json")) {
            return "application/json";
        }
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return "text/plain";
        }
        if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (name.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        return "application/octet-stream";
    }

    /** 文本 / 表格类文件：直接整文件作为文本传给模型抽取（避免 base64 体积浪费）。 */
    private Map<String, Object> extractFromTextFile(Path path) {
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            if (isBlank(text)) {
                return Collections.emptyMap();
            }
            String json = callTextModel(text, path.getFileName().toString());
            return parseModelJson(json);
        } catch (Exception e) {
            log.warn("VLM 文本文件识别失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 调用视觉多模态模型，要求仅返回 JSON。 */
    private String callVisionModel(String dataUri, String bizType) throws Exception {
        String prompt = buildPrompt(bizType);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        Map<String, Object> userMsg = new LinkedHashMap<String, Object>();
        userMsg.put("role", "user");
        Map<String, Object> contentImg = new LinkedHashMap<String, Object>();
        contentImg.put("type", "image_url");
        Map<String, Object> imgUrl = new LinkedHashMap<String, Object>();
        imgUrl.put("url", dataUri);
        contentImg.put("image_url", imgUrl);
        Map<String, Object> contentText = new LinkedHashMap<String, Object>();
        contentText.put("type", "text");
        contentText.put("text", prompt);
        userMsg.put("content", Arrays.asList(contentText, contentImg));
        body.put("messages", Arrays.asList(buildSystemMessage(), userMsg));
        body.put("temperature", 0.1);
        return postChatCompletions(body);
    }

    /** 调用文本模型（用于 CSV/JSON/文本）。 */
    private String callTextModel(String text, String fileName) throws Exception {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        Map<String, Object> userMsg = new LinkedHashMap<String, Object>();
        userMsg.put("role", "user");
        userMsg.put("content", "以下是客户上传的文件（" + fileName + "）原文：\n\n" + text
                + "\n\n请从中抽取客户经营事实并以 JSON 返回。");
        body.put("messages", Arrays.asList(buildSystemMessage(), userMsg));
        body.put("temperature", 0.1);
        return postChatCompletions(body);
    }

    private Map<String, Object> buildSystemMessage() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", "system");
        m.put("content", defaultSystemPromptIfBlank());
        return m;
    }

    /** 构造抽取提示词：明确目标字段与 JSON 约束。 */
    private String buildPrompt(String bizType) {
        return "请从该客户材料中抽取以下经营事实字段，仅以 JSON 对象返回（不要 markdown 代码块）："
                + "entName(企业名称), creditCode(统一社会信用代码), industry(所属行业), "
                + "foundYears(成立年限/年), annualTaxAmount(年纳税额/元), annualInvoiceAmount(年开票额/元), "
                + "annualRevenue(年营收/元), employeeCount(从业人数), registeredCapital(注册资本/元)。"
                + "无法确认的字段请勿臆造，直接省略该 key。资料类型=" + (bizType == null ? "OTHER" : bizType) + "。";
    }

    private String defaultSystemPromptIfBlank() {
        if (!isBlank(systemPrompt)) {
            return systemPrompt;
        }
        return "你是信贷风控材料结构化抽取助手。只输出可被 JSON.parse 解析的纯 JSON 对象，"
                + "字段值为字符串或数字，禁止输出任何解释性文字或代码块标记。";
    }

    /**
     * POST 到 chat/completions 并提取首条 assistant 文本。
     *
     * <p>JDK 8 环境统一使用 {@link HttpURLConnection}（无第三方 HTTP 依赖）。</p>
     */
    private String postChatCompletions(Map<String, Object> body) throws Exception {
        String payload = MAPPER.writeValueAsString(body);
        String endpoint = baseUrl.endsWith("/")
                ? baseUrl + "v1/chat/completions"
                : baseUrl + "/v1/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout((int) Math.min(timeoutMs, 10000L));
            conn.setReadTimeout((int) timeoutMs);

            OutputStream os = conn.getOutputStream();
            try {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            } finally {
                closeQuietly(os);
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String respBody = is == null ? "" : readAll(is);
            if (status < 200 || status >= 300) {
                throw new IOException("VLM HTTP " + status + ": " + respBody);
            }

            JsonNode root = MAPPER.readTree(respBody);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw new IOException("VLM 返回无 choices");
            }
            JsonNode msg = choices.get(0).get("message");
            if (msg == null) {
                throw new IOException("VLM 返回无 message");
            }
            JsonNode content = msg.get("content");
            if (content == null) {
                throw new IOException("VLM 返回无 content");
            }
            return content.asText();
        } finally {
            conn.disconnect();
        }
    }

    /** 读取流全部内容（UTF-8）。 */
    private static String readAll(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            closeQuietly(reader);
        }
        return sb.toString();
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
            // 关闭失败不影响主流程
        }
    }

    /** 将模型文本解析为字段 Map（兼容 ```json 代码块包裹）。 */
    private Map<String, Object> parseModelJson(String text) {
        if (isBlank(text)) {
            return Collections.emptyMap();
        }
        String cleaned = text.trim();
        // 去 markdown 代码块
        if (cleaned.startsWith("```")) {
            int firstNL = cleaned.indexOf('\n');
            int last = cleaned.lastIndexOf("```");
            if (firstNL > 0 && last > firstNL) {
                cleaned = cleaned.substring(firstNL + 1, last).trim();
            }
        }
        try {
            JsonNode node = MAPPER.readTree(cleaned);
            if (node.isObject()) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                java.util.Iterator<Map.Entry<String, JsonNode>> it = node.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    JsonNode v = e.getValue();
                    if (v.isNumber()) {
                        map.put(e.getKey(), v.numberValue());
                    } else if (!v.isNull()) {
                        map.put(e.getKey(), v.asText());
                    }
                }
                return map;
            }
        } catch (Exception e) {
            log.warn("VLM 返回非 JSON，已忽略: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }
}
