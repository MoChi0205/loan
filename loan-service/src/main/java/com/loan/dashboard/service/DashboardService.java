package com.loan.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.approval.entity.AttachmentDownloadApproval;
import com.loan.approval.mapper.AttachmentDownloadApprovalMapper;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.reward.entity.RewardRecord;
import com.loan.reward.mapper.RewardRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作台待办服务：待审核产品 / 待审批下载 / 待审核奖励 / 我的工单 / 我的线索。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductApprovalMapper productApprovalMapper;
    private final AttachmentDownloadApprovalMapper downloadApprovalMapper;
    private final RewardRecordMapper rewardRecordMapper;
    private final ServiceOrderMapper orderMapper;
    private final LeadMapper leadMapper;

    /**
     * 工作台待办统计。
     *
     * @param roleCode 角色编码
     * @param userNo   当前工号
     * @return 待办统计
     */
    public Map<String, Object> todo(String roleCode, String userNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingProductApproval", productApprovalMapper.selectCount(
                new LambdaQueryWrapper<com.loan.approval.entity.ProductApproval>()
                        .eq(com.loan.approval.entity.ProductApproval::getApproveStatus, "PENDING")));
        m.put("pendingDownloadApproval", downloadApprovalMapper.selectCount(
                new LambdaQueryWrapper<AttachmentDownloadApproval>()
                        .eq(AttachmentDownloadApproval::getApproveStatus, "PENDING")
                        .eq(AttachmentDownloadApproval::getVoidFlag, 0)));
        m.put("pendingReward", rewardRecordMapper.selectCount(
                new LambdaQueryWrapper<RewardRecord>()
                        .eq(RewardRecord::getStatus, RewardRecord.STATUS_PENDING_AUDIT)));
        m.put("myOrderCount", orderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getOwnerStaffCode, userNo)
                .notIn(ServiceOrder::getStatus, "CANCEL", "REFUND")));
        m.put("myLeadCount", leadMapper.selectCount(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getOwnerStaffCode, userNo)));
        return m;
    }
}
