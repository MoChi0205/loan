package com.loan.infrastructure.logging;

import com.loan.infrastructure.filter.TraceIdFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 访问日志：记录接口耗时、HTTP 状态、业务 code、URI、方法与客户端 IP，并与 traceId 关联。
 *
 * <p>对齐《前后端小程序代码与交互优化计划》阶段2：
 * 「记录接口耗时、HTTP 状态、业务 code、traceId、缓存命中/回源、数据库查询次数和错误日志」。
 *
 * <p>输出走名为 {@value #LOGGER_NAME} 的独立 logger，由 log4j2 路由到 access.log，
 * 与业务日志（biz.log）物理分离。
 *
 * <p><b>性能考虑</b>：仅对小于 {@value #MAX_BODY} 字节的 JSON 响应尝试提取业务 code，
 * 避免缓存大响应体带来的内存与拷贝开销。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {

    /** 与 log4j2 配置中的 logger name 一一对应，改动需同步修改 log4j2-spring.xml */
    public static final String LOGGER_NAME = "ACCESS_LOG";

    private static final Logger ACCESS_LOG = LogManager.getLogger(LOGGER_NAME);

    /** 超过此大小的响应体不解析业务 code（8KB） */
    private static final int MAX_BODY = 8 * 1024;

    /** 从统一返回体 Result 中提取 code：形如 {"code":1002,...} */
    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*(-?\\d+)");

    /** 慢请求阈值（毫秒），超过则额外打点便于排查 */
    private static final long SLOW_MS = 1000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        int status = 200;
        try {
            filterChain.doFilter(request, wrapper);
            status = wrapper.getStatus();
        } finally {
            long cost = System.currentTimeMillis() - start;
            // ⚠️ 顺序不可调换：copyBodyToResponse() 内部会 content.reset() 清空缓存，
            // 必须在复制之前提取业务 code，否则永远拿到空字节数组（bizCode 恒为 "-"）。
            String bizCode = extractBizCode(wrapper);
            // 必须 copy 回原始 response，否则客户端拿不到响应体
            try {
                wrapper.copyBodyToResponse();
            } catch (IOException e) {
                // 复制失败只影响响应体，不影响已记录的访问日志
                // 注意：此处 logger 是 OncePerRequestFilter 继承的 commons-logging，不支持 {} 占位符
                logger.warn("access log copy body failed: " + e.getMessage());
            }
            writeLog(request, wrapper, status, cost, bizCode);
        }
    }

    private void writeLog(HttpServletRequest request, ContentCachingResponseWrapper wrapper,
                          int status, long cost, String bizCode) {
        String traceId = MDC.get(TraceIdFilter.CONTEXT_KEY);
        if (!StringUtils.hasText(traceId)) {
            traceId = "-";
        }
        // 字段以竖线分隔，便于后续按列解析；traceId 单独前置，与 %X{traceId} 冗余但保证 access 文件自包含
        String line = new StringBuilder(160)
                .append("traceId=").append(traceId)
                .append(" | ").append(request.getMethod())
                .append(" ").append(request.getRequestURI())
                .append(" | status=").append(status)
                .append(" | cost=").append(cost).append("ms")
                .append(" | bizCode=").append(bizCode)
                .append(" | ip=").append(clientIp(request))
                .append(" | ua=").append(shortUa(request))
                .toString();
        if (cost >= SLOW_MS) {
            ACCESS_LOG.warn("SLOW {}", line);
        } else {
            ACCESS_LOG.info(line);
        }
    }

    /** 仅在响应体为小体积 JSON 时提取业务 code */
    private String extractBizCode(ContentCachingResponseWrapper wrapper) {
        byte[] body = wrapper.getContentAsByteArray();
        if (body == null || body.length == 0 || body.length > MAX_BODY) {
            return "-";
        }
        String text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = CODE.matcher(text);
        return m.find() ? m.group(1) : "-";
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        return StringUtils.hasText(real) ? real : request.getRemoteAddr();
    }

    /** UA 截断，避免超长 UA 淹没日志 */
    private String shortUa(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return "-";
        }
        String clean = ua.replaceAll("[\\r\\n]", " ");
        return clean.length() <= 80 ? clean : clean.substring(0, 80) + "...";
    }
}
