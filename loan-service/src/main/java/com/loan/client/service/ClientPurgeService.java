package com.loan.client.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.client.entity.ClientLifecycleEvent;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.client.mapper.ClientProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户数据清理：mark（禁用标记）→ 分批 purge（物理删除），参考 tse
 * {@code ResourcePoolDataPurgeService} 的「标记 + 定时分批清理」模式。
 *
 * <p><b>mark：</b>复用 {@code t_client_profile.status = DISABLED} 作为「待清理」标记——
 * 业务侧禁用客户后（合规下线 / 数据纠错），由本服务定时分批物理清除，避免一次性大事务。
 *
 * <p><b>purge：</b>cursor 分页扫描 DISABLED 客户，删除前向 {@code t_client_lifecycle_event}
 * 追加一条 {@code DELETE} 审计事件（历史事件表保留，作为「客户已被清除」的留痕），
 * 随后物理删除主表记录。关联业务表（订单 / 审批 / 敏感授权 / 画像快照等）不在此级联，
 * 保留审计与合规价值，后续如需清理再逐域评估。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPurgeService {

    /** 待清理标记（复用业务侧禁用状态，不新增 DDL） */
    private static final String PURGE_STATUS = "DISABLED";

    /** 单批清理上限（防大事务与长事务锁表） */
    private static final int MAX_BATCH = 500;

    private final ClientProfileMapper clientProfileMapper;
    private final ClientLifecycleEventMapper lifecycleEventMapper;

    /**
     * 分批物理删除已禁用客户。
     *
     * @param batchSize 单批条数（1~500，越界回退默认）
     * @return 本次清除条数
     */
    public int purgeDisabledClients(int batchSize) {
        int size = batchSize > 0 && batchSize <= MAX_BATCH ? batchSize : MAX_BATCH;
        int purged = 0;
        long cursor = 0L;
        while (true) {
            List<ClientProfile> batch = clientProfileMapper.selectList(
                    new LambdaQueryWrapper<ClientProfile>()
                            .eq(ClientProfile::getStatus, PURGE_STATUS)
                            .gt(ClientProfile::getId, cursor)
                            .orderByAsc(ClientProfile::getId).last("LIMIT " + size));
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (ClientProfile client : batch) {
                if (client.getId() != null) {
                    cursor = client.getId();
                }
                auditDelete(client);
                clientProfileMapper.deleteById(client.getId());
                purged++;
            }
        }
        if (purged > 0) {
            log.info("[ClientPurge] 分批物理删除禁用客户 {} 条", purged);
        }
        return purged;
    }

    /** 审计留痕：追加写 DELETE 生命周期事件，记录客户被物理清除的事实。 */
    private void auditDelete(ClientProfile client) {
        try {
            ClientLifecycleEvent evt = new ClientLifecycleEvent();
            evt.setClientCode(client.getClientCode());
            evt.setEventType("DELETE");
            evt.setStaffCode(client.getOwnerStaffCode());
            evt.setOperatorStaffCode("system");
            evt.setReasonCode("disabled_client_purge");
            evt.setEventAt(LocalDateTime.now());
            lifecycleEventMapper.insert(evt);
        } catch (Exception e) {
            log.warn("[ClientPurge] 审计事件写入失败（不阻断删除）: clientCode={}, err={}",
                    client.getClientCode(), e.getMessage());
        }
    }
}
