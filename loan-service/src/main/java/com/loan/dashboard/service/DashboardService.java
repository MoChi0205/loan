package com.loan.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.approval.entity.AttachmentDownloadApproval;
import com.loan.approval.entity.ClientAllocationApproval;
import com.loan.approval.mapper.AttachmentDownloadApprovalMapper;
import com.loan.approval.mapper.ClientAllocationApprovalMapper;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.reward.entity.RewardRecord;
import com.loan.reward.mapper.RewardRecordMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
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
    private final ClientProfileMapper clientProfileMapper;
    private final StaffMapper staffMapper;

    /**
     * 客户档案禁用态。
     *
     * <p>统计口径统一为「排除 DISABLED」而非「等于 ACTIVE」：小程序建档曾误写 {@code NORMAL}
     * （已修复），存量行需与「我的客户」列表保持一致，否则统计数与列表数对不上。
     */
    private static final String CLIENT_STATUS_DISABLED = "DISABLED";
    /** 团队公海层级 */
    private static final String SEA_LEVEL_TEAM = "TEAM";
    /** 员工在职状态 */
    private static final String STAFF_STATUS_ACTIVE = "ACTIVE";
    /** 审批待审状态 */
    private static final String APPROVAL_PENDING = "PENDING";

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

    /**
     * 首页统计行：按角色返回差异化指标（数据范围与角色边界见 01-角色权限模型.md）。
     *
     * <p>角色 → 指标映射（对齐原型 redesign-all-roles-v1 的首页统计行）：
     * <ul>
     *   <li>ADVISER：我的客户 / 待跟进（本人名下）</li>
     *   <li>DEPT_MANAGER：团队成员 / 待分配（本部门范围）</li>
     *   <li>BOSS：全司客户 / 在途审批（待我审批）</li>
     *   <li>OPERATOR：我的客户 / 待我审批</li>
     *   <li>SUPER_ADMIN/SUPER：全司客户 / 系统异常（暂无监控数据源，固定 0）</li>
     *   <li>CHANNEL：我录线索 / 已转化（仅本人录入，D50 数据隔离）</li>
     *   <li>CUSTOMER：无统计行</li>
     * </ul>
     *
     * @param userType 用户类型（CUSTOMER / CHANNEL / STAFF）
     * @param roleCode 员工角色编码（仅 STAFF 有：ADVISER/DEPT_MANAGER/BOSS/OPERATOR/SUPER_ADMIN/SUPER）
     * @param userNo   当前工号（员工）或客户编码
     * @param deptCode 当前员工部门编码（部门范围统计用，可为空）
     * @return 指标键值，仅包含该角色允许的指标（避免越权暴露其它范围数据）
     */
    public Map<String, Object> homeStats(String userType, String roleCode, String userNo, String deptCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        String type = userType == null ? "" : userType.toUpperCase();
        String role = roleCode == null ? "" : roleCode.toUpperCase();

        if ("CHANNEL".equals(type)) {
            m.put("leadCount", countLeadByRecorder(userNo));
            m.put("convertedCount", countConvertedLeadByRecorder(userNo));
            return m;
        }
        if (!"STAFF".equals(type)) {
            // 客户无统计行
            return m;
        }
        if ("ADVISER".equals(role)) {
            m.put("clientCount", countClientByOwner(userNo));
            m.put("orderCount", countActiveOrderByOwner(userNo));
        } else if ("DEPT_MANAGER".equals(role)) {
            m.put("teamMemberCount", countTeamMember(deptCode, userNo));
            m.put("unassignedCount", countTeamSeaClient(deptCode));
        } else if ("OPERATOR".equals(role)) {
            m.put("clientCount", countClientByOwner(userNo));
            m.put("pendingApprovalCount", countPendingApproval(userNo));
        } else if ("BOSS".equals(role)) {
            m.put("orgClientCount", countActiveClient());
            m.put("pendingApprovalCount", countPendingApproval(userNo));
        } else if ("SUPER_ADMIN".equals(role) || "SUPER".equals(role)) {
            m.put("orgClientCount", countActiveClient());
            m.put("systemErrorCount", 0L);
        }
        return m;
    }

    /**
     * 本人归属的有效客户数（我的客户）。
     *
     * @param userNo 工号
     * @return 客户数
     */
    private Long countClientByOwner(String userNo) {
        return clientProfileMapper.selectCount(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getOwnerStaffCode, userNo)
                .ne(ClientProfile::getStatus, CLIENT_STATUS_DISABLED));
    }

    /**
     * 全司有效客户数（全司客户）。
     *
     * @return 客户数
     */
    private Long countActiveClient() {
        return clientProfileMapper.selectCount(new LambdaQueryWrapper<ClientProfile>()
                .ne(ClientProfile::getStatus, CLIENT_STATUS_DISABLED));
    }

    /**
     * 本人负责的未结束服务单数（待跟进）。
     *
     * @param userNo 工号
     * @return 服务单数
     */
    private Long countActiveOrderByOwner(String userNo) {
        return orderMapper.selectCount(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getOwnerStaffCode, userNo)
                .notIn(ServiceOrder::getStatus, "CANCEL", "REFUND"));
    }

    /**
     * 本部门在职成员数（团队成员，排除本人）。
     *
     * @param deptCode 部门编码
     * @param userNo   当前工号
     * @return 成员数
     */
    private Long countTeamMember(String deptCode, String userNo) {
        if (deptCode == null || deptCode.isEmpty()) {
            return 0L;
        }
        return staffMapper.selectCount(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getDeptCode, deptCode)
                .eq(Staff::getStatus, STAFF_STATUS_ACTIVE)
                .ne(Staff::getStaffCode, userNo));
    }

    /**
     * 本部门 TEAM 公海待分配客户数（待分配）。
     *
     * @param deptCode 部门编码
     * @return 待分配客户数
     */
    private Long countTeamSeaClient(String deptCode) {
        if (deptCode == null || deptCode.isEmpty()) {
            return 0L;
        }
        return clientProfileMapper.selectCount(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getSeaLevel, SEA_LEVEL_TEAM)
                .eq(ClientProfile::getSeaDeptCode, deptCode));
    }

    /**
     * 我录入的线索数（我录线索）。
     *
     * @param userNo 工号
     * @return 线索数
     */
    private Long countLeadByRecorder(String userNo) {
        return leadMapper.selectCount(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getRecorderStaffCode, userNo));
    }

    /**
     * 我录入且已转正的线索数（已转化，clientProfileCode 非空即已转正）。
     *
     * @param userNo 工号
     * @return 已转化线索数
     */
    private Long countConvertedLeadByRecorder(String userNo) {
        return leadMapper.selectCount(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getRecorderStaffCode, userNo)
                .isNotNull(Lead::getClientProfileCode));
    }

    /**
     * 待我审批合计（产品 + 附件下载 + 客户分配，审批人视角）。
     *
     * @param userNo 工号
     * @return 待我审批总数
     */
    private Long countPendingApproval(String userNo) {
        return productApprovalMapper.selectCount(
                new LambdaQueryWrapper<com.loan.approval.entity.ProductApproval>()
                        .eq(com.loan.approval.entity.ProductApproval::getApproveStatus, APPROVAL_PENDING)
                        .eq(com.loan.approval.entity.ProductApproval::getApproverStaffCode, userNo))
                + downloadApprovalMapper.selectCount(new LambdaQueryWrapper<AttachmentDownloadApproval>()
                        .eq(AttachmentDownloadApproval::getApproveStatus, APPROVAL_PENDING)
                        .eq(AttachmentDownloadApproval::getVoidFlag, 0)
                        .eq(AttachmentDownloadApproval::getApproverStaffCode, userNo))
                + allocationApprovalMapper.selectCount(new LambdaQueryWrapper<ClientAllocationApproval>()
                        .eq(ClientAllocationApproval::getApproveStatus, APPROVAL_PENDING)
                        .eq(ClientAllocationApproval::getApproverStaffCode, userNo));
    }
}
