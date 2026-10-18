package com.loan.report.job;

import com.loan.report.service.IndustryBenchmarkService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 行业基准均值缓存刷新 XXL-Job 任务。
 *
 * <p>行业基准均值以 {@code t_industry_benchmark} 为真源，应用启动时经
 * {@link IndustryBenchmarkService#init()} 预热到内存缓存；本任务定期重新加载
 * ACTIVE 行，使运营修改基准后无需重启即可生效。
 *
 * <p>由 XXL-JOB 调度中心触发（appname=loan-platform-executor），
 * 建议 cron：{@code 0 30 3 * * ?}（每日凌晨）。
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndustryBenchmarkRefreshJob {

    private final IndustryBenchmarkService industryBenchmarkService;

    /**
     * 行业基准均值刷新（XXL-Job 任务，建议 cron：0 30 3 * * ?）。
     */
    @XxlJob("industryBenchmarkRefreshJob")
    public void industryBenchmarkRefreshJob() {
        long start = System.currentTimeMillis();
        log.info("[XXL-Job] industryBenchmarkRefreshJob 开始");
        try {
            int loaded = industryBenchmarkService.refresh();
            long ms = System.currentTimeMillis() - start;
            String msg = "重新加载行业基准 ACTIVE 行 " + loaded + " 条，耗时 " + ms + "ms";
            log.info("[XXL-Job] industryBenchmarkRefreshJob 完成：{}", msg);
            XxlJobHelper.handleSuccess(msg);
        } catch (Exception e) {
            log.error("[XXL-Job] industryBenchmarkRefreshJob 失败", e);
            XxlJobHelper.handleFail("行业基准刷新失败：" + e.getMessage());
        }
    }
}
