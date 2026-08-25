package com.loan;

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
     * @param args 启动参数（Nacos 地址与命名空间经 -Dnacos.server-addr / -Dnacos.namespace 指定）
     */
    public static void main(String[] args) {
        SpringApplication.run(LoanApplication.class, args);
    }
}
