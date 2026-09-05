package com.loan.partner.job;

import com.loan.partner.entity.PartnerProduct;
import com.loan.partner.service.PartnerProductService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 合作库到期 XXL-Job 任务（每日扫描，建议 cron：0 0 2 * * ?）。
 *
 * <p>逻辑：
 * <ul>
 *   <li>EXPIRING 且已到期 → EXPIRED（自动下架，小程序不再展示）；</li>
 *   <li>ACTIVE 且进入 T-30/T-7 预警窗口 → EXPIRING，并收集预警集合（工作台提醒数据源）。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerProductExpireJob {

    private final PartnerProductService partnerProductService;

    /**
     * 合作库到期扫描（XXL-Job 任务，建议 cron：0 0 2 * * ?）。
     */
    @XxlJob("partnerProductExpireJob")
    public void partnerProductExpireJob() {
        long start = System.currentTimeMillis();
        log.info("[XXL-Job] partnerProductExpireJob 开始");
        try {
            int expired = partnerProductService.expireOverdue();
            int expiring = partnerProductService.markExpiring();
            List<PartnerProduct> warns = partnerProductService.listExpiring();
            long ms = System.currentTimeMillis() - start;
            String msg = "到期下架 " + expired + " 条，转入临期 " + expiring
                    + " 条，当前预警 " + warns.size() + " 条（T-30/T-7），耗时 " + ms + "ms";
            log.info("[XXL-Job] partnerProductExpireJob 完成：{}", msg);
            XxlJobHelper.handleSuccess(msg);
        } catch (Exception e) {
            log.error("[XXL-Job] partnerProductExpireJob 失败", e);
            XxlJobHelper.handleFail("合作库到期扫描失败：" + e.getMessage());
        }
    }
}
