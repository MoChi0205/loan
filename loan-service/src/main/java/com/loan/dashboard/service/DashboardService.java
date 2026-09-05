package com.loan.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.approval.entity.AttachmentDownloadApproval;
import com.loan.approval.entity.ClientAllocationApproval;
import com.loan.approval.mapper.AttachmentDownloadApprovalMapper;
import com.loan.approval.mapper.ClientAllocationApprovalMapper;
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
 * 工作台待办服务：待我审批产品 / 待我审批下载 / 待审核奖励 / 我的工单 / 我的线索 /
 * 我的分配申请 / 我的下载申请。
 *
 * <p>"待我审批 X"按 approverStaffCode=userNo 过滤（仅审批人视角）；
 * "我的 X 申请"按 applicantStaffCode=userNo 过滤（申请人视角）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductApprovalMapper productApprovalMapper;
    private final AttachmentDownloadApprovalMapper downloadApprovalMapper;
    private final ClientAllocationApprovalMapper allocationApprovalMapper;
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
        // 待我审批 X（仅当前用户作为审批人的工单）
        m.put("pendingProductApproval", productApprovalMapper.selectCount(
                new LambdaQueryWrapper<com.loan.approval.entity.ProductApproval>()
                        .eq(com.loan.approval.entity.ProductApproval::getApproveStatus, "PENDING")
                        .eq(com.loan.approval.entity.ProductApproval::getApproverStaffCode, userNo)));
        m.put("pendingDownloadApproval", downloadApprovalMapper.selectCount(
                new LambdaQueryWrapper<AttachmentDownloadApproval>()
                        .eq(AttachmentDownloadApproval::getApproveStatus, "PENDING")
                        .eq(AttachmentDownloadApproval::getVoidFlag, 0)
                        .eq(AttachmentDownloadApproval::getApproverStaffCode, userNo)));
        m.put("pendingAllocationApproval", allocationApprovalMapper.selectCount(
                new LambdaQueryWrapper<ClientAllocationApproval>()
                        .eq(ClientAllocationApproval::getApproveStatus, "PENDING")
                        .eq(ClientAllocationApproval::getApproverStaffCode, userNo)));
        m.put("pendingReward", rewardRecordMapper.selectCount(
                new LambdaQueryWrapper<RewardRecord>()
                        .eq(RewardRecord::getStatus, RewardRecord.STATUS_PENDING_AUDIT)));
        // 我的 X 申请（申请人视角：ADVISER/DM 提交后等待审批）
        m.put("myDownloadApply", downloadApprovalMapper.selectCount(
                new LambdaQueryWrapper<AttachmentDownloadApproval>()
                        .eq(AttachmentDownloadApproval::getApproveStatus, "PENDING")
                        .eq(AttachmentDownloadApproval::getApplicantStaffCode, userNo)));
        m.put("myAllocationApply", allocationApprovalMapper.selectCount(
                new LambdaQueryWrapper<ClientAllocationApproval>()
                        .eq(ClientAllocationApproval::getApproveStatus, "PENDING")
                        .eq(ClientAllocationApproval::getApplicantStaffCode, userNo)));
        // 我的 X（owner 视角）
        m.put("myOrderCount", orderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getOwnerStaffCode, userNo)
                .notIn(ServiceOrder::getStatus, "CANCEL", "REFUND")));
        m.put("myLeadCount", leadMapper.selectCount(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getOwnerStaffCode, userNo)));
        return m;
    }
}
