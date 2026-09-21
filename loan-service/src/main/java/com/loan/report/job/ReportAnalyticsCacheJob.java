package com.loan.report.job;

import com.loan.report.service.ReportAnalyticsCacheService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 经营概览缓存预热任务；建议每分钟执行一次。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAnalyticsCacheJob {

    private final ReportAnalyticsCacheService cacheService;

    @XxlJob("reportAnalyticsCacheWarmJob")
    public void warm() {
        long startedAt = System.currentTimeMillis();
        try {
            cacheService.warmGlobalDashboard();
            String message = "经营概览缓存预热完成，耗时 " + (System.currentTimeMillis() - startedAt) + "ms";
            log.info("[XXL-Job] {}", message);
            XxlJobHelper.handleSuccess(message);
        } catch (Exception exception) {
            log.error("[XXL-Job] 经营概览缓存预热失败", exception);
            XxlJobHelper.handleFail("经营概览缓存预热失败：" + exception.getMessage());
        }
    }
}
