package com.loan.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
