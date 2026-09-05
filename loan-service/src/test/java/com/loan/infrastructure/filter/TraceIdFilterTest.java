package com.loan.infrastructure.filter;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TraceIdFilterTest {

    @Test
    void shouldReuseValidTraceIdAndClearContext() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "gateway_trace-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertEquals("gateway_trace-01",
                ThreadContext.get(TraceIdFilter.CONTEXT_KEY));

        filter.doFilter(request, response, chain);

        assertEquals("gateway_trace-01", response.getHeader(TraceIdFilter.HEADER));
        assertFalse(ThreadContext.containsKey(TraceIdFilter.CONTEXT_KEY));
    }

    @Test
    void shouldReplaceUnsafeTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "bad trace\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertNotNull(
                ThreadContext.get(TraceIdFilter.CONTEXT_KEY)));

        assertEquals(32, response.getHeader(TraceIdFilter.HEADER).length());
    }
}
