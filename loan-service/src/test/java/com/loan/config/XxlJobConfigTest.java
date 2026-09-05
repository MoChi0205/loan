package com.loan.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * XXL-JOB 占位配置防误注册测试。
 */
class XxlJobConfigTest {

    /**
     * Nacos 尚未注入真实 IP/token 时不启动执行器。
     */
    @Test
    @DisplayName("XXL-JOB 占位配置不创建执行器")
    void placeholderConfigDoesNotMatch() {
        assertFalse(matches("http://127.0.0.1:8080/xxl-job-admin", "loan-platform-executor",
                "PLACEHOLDER_XXL_EXECUTOR_IP", "PLACEHOLDER_XXL_ACCESS_TOKEN"));
    }

    /**
     * 真实配置齐全时保持原有注册能力。
     */
    @Test
    @DisplayName("XXL-JOB 真实配置创建执行器")
    void realConfigMatches() {
        assertTrue(matches("http://127.0.0.1:8080/xxl-job-admin", "loan-platform-executor",
                "10.0.0.8", "real-token"));
    }

    /**
     * 构造条件上下文并计算是否启用。
     */
    private boolean matches(String admin, String appname, String ip, String token) {
        ConditionContext context = mock(ConditionContext.class);
        Environment environment = mock(Environment.class);
        when(context.getEnvironment()).thenReturn(environment);
        when(environment.getProperty("xxl.job.admin-addresses")).thenReturn(admin);
        when(environment.getProperty("xxl.job.appname")).thenReturn(appname);
        when(environment.getProperty("xxl.job.ip")).thenReturn(ip);
        when(environment.getProperty("xxl.job.access-token")).thenReturn(token);
        return new XxlJobConfig.XxlJobConfiguredCondition().matches(context, null);
    }
}
