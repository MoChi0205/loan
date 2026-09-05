package com.loan.client.job;

import com.loan.client.service.ClientAllocationService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 客户回收 XXL-Job 任务（对齐 LeadRecycleJobHandler / tse CrmDailyScheduledJobHandler）。
 *
 * <p>由 XXL-JOB 调度中心触发（appname=loan-platform-executor）：
 * <ul>
 *   <li>{@code clientRecycleJob}：超期未跟进客户回收进公海（按 {@code t_client_recycle_config}，默认 30 天，冷却 7 天）。</li>
 *   <li>{@code clientRecycleWarnJob}：距回收剩余预警天数内向归属人发站内预警通知。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientRecycleJobHandler {

    private final ClientAllocationService clientAllocationService;

    /**
     * 客户超期回收（XXL-Job 任务，建议 cron：0 0 2 * * ?）。
     */
    @XxlJob("clientRecycleJob")
    public void clientRecycleJob() {
        long start = System.currentTimeMillis();
        log.info("[XXL-Job] clientRecycleJob 开始");
        try {
            int recycled = clientAllocationService.recycleOverdue();
            long ms = System.currentTimeMillis() - start;
            String msg = "回收 " + recycled + " 条，耗时 " + ms + "ms";
            log.info("[XXL-Job] clientRecycleJob 完成：{}", msg);
            XxlJobHelper.handleSuccess(msg);
        } catch (Exception e) {
            log.error("[XXL-Job] clientRecycleJob 失败", e);
            XxlJobHelper.handleFail("回收失败：" + e.getMessage());
        }
    }

    /**
     * 客户回收预警（XXL-Job 任务，建议 cron：0 0 9 * * ?）。
     */
    @XxlJob("clientRecycleWarnJob")
    public void clientRecycleWarnJob() {
        long start = System.currentTimeMillis();
        log.info("[XXL-Job] clientRecycleWarnJob 开始");
        try {
            int warned = clientAllocationService.warnRecycle();
            long ms = System.currentTimeMillis() - start;
            String msg = "发送预警 " + warned + " 条，耗时 " + ms + "ms";
            log.info("[XXL-Job] clientRecycleWarnJob 完成：{}", msg);
            XxlJobHelper.handleSuccess(msg);
        } catch (Exception e) {
            log.error("[XXL-Job] clientRecycleWarnJob 失败", e);
            XxlJobHelper.handleFail("预警失败：" + e.getMessage());
        }
    }
}
