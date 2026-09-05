package com.loan.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * XXL-JOB 执行器配置（对齐 tse XxlJobConfig）。
 *
 * <p>读取 Nacos 配置 {@code xxl.job.*}（appname=loan-platform-executor），
 * 注册 {@link XxlJobSpringExecutor} 供 {@code @XxlJob} 注解的 JobHandler 被调度中心调用。
 *
 * @author loan-platform
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobConfig {

    /** 调度中心地址 */
    private String adminAddresses;

    /** 执行器应用名称 */
    private String appname;

    /** 执行器 IP（空表示自动获取） */
    private String ip;

    /** 执行器端口（默认 9999） */
    private int port = 9999;

    /** 访问令牌 */
    private String accessToken;

    /** 日志路径 */
    private String logPath = "/app/logs/xxl-job";

    /** 日志保留天数 */
    private int logRetentionDays = 10;

    /**
     * 注册 XXL-JOB 执行器。
     *
     * @return 执行器
     */
    @Bean
    @Conditional(XxlJobConfiguredCondition.class)
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info("初始化 XXL-JOB 执行器：admin={}, appname={}", adminAddresses, appname);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }

    /**
     * 只有在 XXL-JOB 关键配置已完整且不是上线占位值时才创建执行器。
     *
     * <p>本地联调直接读取 Nacos prd；若 prd 尚未注入真实 IP/token，
     * 启动执行器只会持续向调度中心发送无效注册请求。此条件仅抑制占位配置，
     * 上线注入真实值后会自动恢复 XXL-JOB 注册。
     */
    static class XxlJobConfiguredCondition implements Condition {

        private static final String PLACEHOLDER = "PLACEHOLDER_";
        private static final String CHANGE_ME = "CHANGE_ME_";

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String admin = context.getEnvironment().getProperty("xxl.job.admin-addresses");
            String appname = context.getEnvironment().getProperty("xxl.job.appname");
            String ip = context.getEnvironment().getProperty("xxl.job.ip");
            String token = context.getEnvironment().getProperty("xxl.job.access-token");
            return StringUtils.hasText(admin)
                    && StringUtils.hasText(appname)
                    && !isPlaceholder(admin)
                    && !isPlaceholder(ip)
                    && !isPlaceholder(token);
        }

        /**
         * 判断是否为待注入的敏感配置占位值。
         *
         * @param value 配置值
         * @return true 表示不可启用执行器
         */
        private static boolean isPlaceholder(String value) {
            return StringUtils.hasText(value)
                    && (value.contains(PLACEHOLDER) || value.contains(CHANGE_ME));
        }
    }
}
