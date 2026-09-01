package com.loan;

import com.loan.config.NacosJvmBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 企业贷款咨询服务产品系统启动类。
 *
 * <p>一后端 + 两前端 + 三方角色（我司 / 合作渠道银行 / 双客群客户）。
 * 核心链路：认证 → 提取 → 规则引擎匹配（参考 mds V2）→ 档位聚合 → 报告 → 审计。
 *
 * @author loan-platform
 */
@SpringBootApplication
@EnableScheduling
public class LoanApplication {

    /**
     * 应用入口。
     *
     * <p>对齐 tse：先调用 {@link NacosJvmBootstrap#prepare} 校验并注入 Nacos 连接参数
     * （VM -D + System.setProperty + setDefaultProperties），保证 {@code @EnableNacos} 注解阶段
     * 的占位符能正确解析；NacosEnvironmentPostProcessor（spring.factories 注册）会在 Spring
     * 上下文创建前直接 HTTP 拉取 Nacos 配置注入 Environment。
     *
     * @param args 启动参数（Nacos 地址与命名空间经 -Dnacos.server-addr / -Dnacos.namespace 指定）
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LoanApplication.class);
        NacosJvmBootstrap.prepare(application);
        application.run(args);
    }
}
