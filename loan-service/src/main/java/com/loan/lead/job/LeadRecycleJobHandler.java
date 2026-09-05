package com.loan.lead.job;

import com.loan.lead.service.LeadService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 线索回收 XXL-Job 任务（对齐 tse CrmDailyScheduledJobHandler）。
 *
 * <p>由 XXL-JOB 调度中心触发（appname=loan-platform-executor）：
 * <ul>
 *   <li>{@code leadRecycleJob}：超期线索回收进公海（超 30 天未跟进，冷却 7 天）。</li>
 *   <li>{@code leadRecycleWarnJob}：3 天内到期线索向归属人发站内预警通知。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadRecycleJobHandler {

    private final LeadService leadService;

    /**
     * 线索超期回收（XXL-Job 任务，建议 cron：0 0 2 * * ?）。
     */
    @XxlJob("leadRecycleJob")
    public void leadRecycleJob() {
        long start = System.currentTimeMillis();
        log.info("[XXL-Job] leadRecycleJob 开始");
        try {
            int recycled = leadService.recycleOverdue();
            long ms = System.currentTimeMillis() - start;
            String msg = "回收 " + recycled + " 条，耗时 " + ms + "ms";
            log.info("[XXL-Job] leadRecycleJob 完成：{}", msg);
            XxlJobHelper.handleSuccess(msg);
        } catch (Exception e) {
            log.error("[XXL-Job] leadRecycleJob 失败", e);
            XxlJobHelper.handleFail("回收失败：" + e.getMessage());
        }
    }

    /**
     * 线索回收预警（XXL-Job 任务，建议 cron：0 0 9 * * ?）。
     */
    @XxlJob("leadRecycleWarnJob")
    public void leadRecycleWarnJob() {
        long start = System.currentTimeMillis();
        log.info("[XXL-Job] leadRecycleWarnJob 开始");
        try {
            int warned = leadService.warnRecycle();
            long ms = System.currentTimeMillis() - start;
            String msg = "发送预警 " + warned + " 条，耗时 " + ms + "ms";
            log.info("[XXL-Job] leadRecycleWarnJob 完成：{}", msg);
            XxlJobHelper.handleSuccess(msg);
        } catch (Exception e) {
            log.error("[XXL-Job] leadRecycleWarnJob 失败", e);
            XxlJobHelper.handleFail("预警失败：" + e.getMessage());
        }
    }
}
