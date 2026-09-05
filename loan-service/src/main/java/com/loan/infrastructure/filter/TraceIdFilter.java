package com.loan.infrastructure.filter;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * HTTP 链路追踪过滤器：复用网关传入的合法 traceId，否则生成新值，并写入响应头和日志上下文。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String CONTEXT_KEY = "traceId";
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = normalize(request.getHeader(HEADER));
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        ThreadContext.put(CONTEXT_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            ThreadContext.remove(CONTEXT_KEY);
        }
    }

    static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String traceId = value.trim();
        if (traceId.length() > MAX_LENGTH || !traceId.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return traceId;
    }
}
