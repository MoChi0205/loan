package com.loan.client.job;

import com.loan.client.service.ClientPurgeService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 禁用客户分批物理清理 XXL-Job 任务。
 *
 * <p>由 XXL-JOB 调度中心触发（appname=loan-platform-executor），
 * 建议 cron：{@code 0 0 4 * * ?}（每日凌晨，避开业务高峰）。调用
 * {@link ClientPurgeService#purgeDisabledClients(int)} 分批物理删除已禁用客户，
 * 删除前追加审计事件，避免一次性大事务锁表。
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientPurgeJob {

    private final ClientPurgeService clientPurgeService;

    /**
     * 禁用客户分批物理清理（XXL-Job 任务，建议 cron：0 0 4 * * ?）。
     */
    @XxlJob("clientPurgeJob")
    public void clientPurgeJob() {
        long start = System.currentTimeMillis();
        log.info("[XXL-Job] clientPurgeJob 开始");
        try {
            int purged = clientPurgeService.purgeDisabledClients(500);
            long ms = System.currentTimeMillis() - start;
            String msg = "分批清除禁用客户 " + purged + " 条，耗时 " + ms + "ms";
            log.info("[XXL-Job] clientPurgeJob 完成：{}", msg);
            XxlJobHelper.handleSuccess(msg);
        } catch (Exception e) {
            log.error("[XXL-Job] clientPurgeJob 失败", e);
            XxlJobHelper.handleFail("客户数据清理失败：" + e.getMessage());
        }
    }
}
