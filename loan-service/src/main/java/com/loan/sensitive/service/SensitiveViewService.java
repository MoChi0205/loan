package com.loan.sensitive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import com.loan.sensitive.dto.SensitiveApplyViewResp;
import com.loan.sensitive.dto.SensitiveQuotaVO;
import com.loan.sensitive.entity.SensitiveViewGrant;
import com.loan.sensitive.entity.SensitiveViewLog;
import com.loan.sensitive.mapper.SensitiveViewGrantMapper;
import com.loan.sensitive.mapper.SensitiveViewLogMapper;
import com.loan.sensitive.mapper.SensitiveViewApprovalMapper;
import com.loan.sensitive.entity.SensitiveViewApproval;
import com.loan.common.util.BizIdGenerator;
import com.loan.serviceops.service.ServiceOperationScopeService;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感数据查看授权服务（受限角色默认脱敏 → 申请 → 授权 → 日限额 30 → 留痕；豁免角色直看明文）。
 *
 * <p>规则：
 * <ul>
 *   <li>豁免角色（BOSS / SUPER_ADMIN / SUPER）：直接查看明文，不受日限额约束。</li>
 *   <li>顾问、运营、部门经理：额度内首次解锁直接查看；顾问/运营超限由本部门经理审批，
 *       部门经理超限由老板或超级管理员审批。</li>
 *   <li>授权持久化：同一线索已授权后再次查看不再消耗当日额度。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveViewService {

    /** 受限角色每日查看敏感数据上限（需求：日限额 30）。 */
    public static final int DEFAULT_DAILY_LIMIT = 30;

    /** 豁免角色（老板/超级管理员）：直接查看明文。 */
    private static final Set<String> EXEMPT_ROLES =
            new HashSet<>(Arrays.asList("BOSS", "SUPER_ADMIN", "SUPER"));

    private final SensitiveViewGrantMapper grantMapper;
    private final SensitiveViewLogMapper logMapper;
    private final SensitiveViewApprovalMapper approvalMapper;
    private final LeadMapper leadMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final StaffMapper staffMapper;
    private final NotificationService notificationService;
    private final ServiceOperationScopeService scopeService;

    /**
     * 是否豁免角色（老板/主管）。
     *
     * @param roleCode 角色编码
     * @return true 豁免
     */
    public boolean isExemptRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        return EXEMPT_ROLES.contains(roleCode.trim().toUpperCase());
    }

    /**
     * 当日查看上限。
     *
     * @return 上限
     */
    public int getDailyLimit() {
        return DEFAULT_DAILY_LIMIT;
    }

    /**
     * 统计某员工当日查看次数。
     *
     * @param userNo 工号
     * @param date   日期
     * @return 当日次数
     */
    public long countTodayViews(String userNo, LocalDate date) {
        if (!StringUtils.hasText(userNo)) {
            return 0L;
        }
        Long count = logMapper.countToday(userNo.trim(), date == null ? LocalDate.now() : date);
        return count == null ? 0L : count;
    }

    /**
     * 查询当前用户额度（上限 / 已用 / 剩余）。
     *
     * @param userNo 工号
     * @return 额度 VO
     */
    public SensitiveQuotaVO getQuota(String userNo) {
        int limit = getDailyLimit();
        // 客户手机号额度按“首次客户解锁次数”独立统计，不混入旧线索手机号查看次数。
        long used = StringUtils.hasText(userNo)
                ? countTodayClientViews(userNo, LocalDate.now()) : 0L;
        SensitiveQuotaVO vo = new SensitiveQuotaVO();
        vo.setLimit(limit);
        vo.setUsed((int) Math.min(used, Integer.MAX_VALUE));
        vo.setRemaining((int) Math.max(0L, limit - used));
        return vo;
    }

    /**
     * 是否已授权查看某线索。
     *
     * @param userNo 工号
     * @param leadNo 线索业务 ID
     * @return true 已授权
     */
    public boolean hasGrant(String userNo, String leadNo) {
        if (!StringUtils.hasText(userNo) || !StringUtils.hasText(leadNo)) {
            return false;
        }
        Long count = grantMapper.selectCount(new LambdaQueryWrapper<SensitiveViewGrant>()
                .eq(SensitiveViewGrant::getUserNo, userNo.trim())
                .eq(SensitiveViewGrant::getLeadNo, leadNo.trim())
                .last("LIMIT 1"));
        return count != null && count > 0;
    }

    /** 是否已授权查看某客户档案手机号。 */
    public boolean hasClientGrant(String userNo, String clientCode) {
        if (!StringUtils.hasText(userNo) || !StringUtils.hasText(clientCode)) {
            return false;
        }
        Long count = grantMapper.selectCount(new LambdaQueryWrapper<SensitiveViewGrant>()
                .eq(SensitiveViewGrant::getUserNo, userNo.trim())
                .eq(SensitiveViewGrant::getClientCode, clientCode.trim())
                .last("LIMIT 1"));
        return count != null && count > 0;
    }

    /**
     * 申请查看线索敏感手机号。
     *
     * <p>豁免角色直看明文；受限角色首次申请走授权 + 留痕 + 日限额校验；已授权直接看明文不再消耗额度。
     *
     * @param leadNo   线索业务 ID
     * @param userNo   当前工号
     * @param roleCode 当前角色
     * @return 申请查看响应
     */
    @Transactional(rollbackFor = Exception.class)
    public SensitiveApplyViewResp applyView(String leadNo, String userNo, String roleCode) {
        if (!StringUtils.hasText(leadNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索编号必填");
        }
        if (!StringUtils.hasText(userNo)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo.trim()));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        String plain = AesUtils.decrypt(lead.getPhone());
        String masked = DesensitizeUtils.phone(plain);

        SensitiveApplyViewResp resp = new SensitiveApplyViewResp();
        resp.setLeadNo(leadNo.trim());
        resp.setPhoneMasked(masked);

        // 豁免角色：直接看明文，不走授权/限额
        if (isExemptRole(roleCode)) {
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo);
            return resp;
        }

        // 已授权：直接看明文，不再消耗当日额度（授权持久化）
        if (hasGrant(userNo, leadNo)) {
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo);
            return resp;
        }

        // 受限角色：校验当日额度
        long used = countTodayViews(userNo, LocalDate.now());
        if (used >= getDailyLimit()) {
            notifyOverLimit(userNo);
            throw new BusinessException(ResultCode.SENSITIVE_QUOTA_EXCEEDED,
                    String.format("今日查看次数已用完（已用 %d / 上限 %d）", used, getDailyLimit()));
        }
        insertGrant(userNo, leadNo);
        insertViewLog(userNo, leadNo);
        resp.setPhonePlain(plain);
        resp.setRevealed(true);
        long usedAfter = countTodayViews(userNo, LocalDate.now());
        if (usedAfter >= getDailyLimit()) {
            notifyOverLimit(userNo);
        }
        applyQuota(resp, userNo);
        return resp;
    }

    /**
     * 申请查看客户档案手机号原值。列表接口永远只返回脱敏值；原值仅在本次受控响应中返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensitiveApplyViewResp applyClientView(String clientCode, com.loan.context.LoanUser user) {
        String userNo = user == null ? null : user.getUserNo();
        String roleCode = user == null ? null : user.getRoleCode();
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户不能为空");
        }
        if (!StringUtils.hasText(userNo)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode.trim()).last("limit 1"));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        scopeService.requireClientVisibleToStaff(user, client);
        String plain = clientProfileMapper.selectPhonePlain(client.getClientCode());
        if (!StringUtils.hasText(plain)) {
            plain = AesUtils.decrypt(client.getPhone());
            if (StringUtils.hasText(plain)) {
                clientProfileMapper.backfillPhonePlain(client.getClientCode(), plain);
            }
        }
        SensitiveApplyViewResp resp = new SensitiveApplyViewResp();
        resp.setClientCode(client.getClientCode());
        resp.setPhoneMasked(DesensitizeUtils.phone(plain));
        if (isExemptRole(roleCode)) {
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo);
            return resp;
        }
        // 已授权客户再次查看不重复消耗当天额度，但仍返回当前额度。
        if (hasClientGrant(userNo, clientCode)) {
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo);
            return resp;
        }
        // 客户手机号额度只统计“当天首次解锁的不同客户”，不得混入旧线索查看日志。
        long used = countTodayClientViews(userNo, LocalDate.now());
        if (used >= getDailyLimit()) {
            SensitiveViewApproval approved = approvalMapper.selectOne(new LambdaQueryWrapper<SensitiveViewApproval>()
                    .eq(SensitiveViewApproval::getClientCode, clientCode.trim())
                    .eq(SensitiveViewApproval::getApplicantStaffCode, userNo)
                    .eq(SensitiveViewApproval::getViewDate, LocalDate.now())
                    .eq(SensitiveViewApproval::getApproveStatus, "APPROVED")
                    .last("LIMIT 1"));
            if (approved != null) {
                insertClientGrant(userNo, clientCode); insertClientViewLog(userNo, clientCode);
                resp.setPhonePlain(plain); resp.setRevealed(true); applyQuota(resp, userNo); return resp;
            }
            SensitiveViewApproval approval = ensureOverLimitApproval(clientCode, user);
            resp.setApprovalNo(approval.getApprovalNo());
            resp.setApprovalStatus(approval.getApproveStatus());
            resp.setMessage("已超过今日查看额度，申请已提交审批；审批通过后再次点击查看");
            applyQuota(resp, userNo);
            return resp;
        }
        insertClientGrant(userNo, clientCode);
        insertClientViewLog(userNo, clientCode);
        resp.setPhonePlain(plain);
        resp.setRevealed(true);
        applyQuota(resp, userNo);
        return resp;
    }

    private SensitiveViewApproval ensureOverLimitApproval(String clientCode, com.loan.context.LoanUser user) {
        String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        String stage = "DEPT_MANAGER".equals(role) ? "BOSS_REVIEW" : "MANAGER_REVIEW";
        SensitiveViewApproval existing = approvalMapper.selectOne(new LambdaQueryWrapper<SensitiveViewApproval>()
                .eq(SensitiveViewApproval::getClientCode, clientCode.trim())
                .eq(SensitiveViewApproval::getApplicantStaffCode, user.getUserNo())
                .eq(SensitiveViewApproval::getViewDate, LocalDate.now())
                .eq(SensitiveViewApproval::getApproveStatus, "PENDING")
                .last("LIMIT 1"));
        if (existing != null) return existing;
        SensitiveViewApproval approval = new SensitiveViewApproval();
        approval.setApprovalNo(BizIdGenerator.generate("svapr")); approval.setClientCode(clientCode.trim());
        approval.setApplicantStaffCode(user.getUserNo()); approval.setApplicantRoleCode(role);
        approval.setApplicantDeptCode(user.getDeptCode()); approval.setViewDate(LocalDate.now());
        approval.setApprovalStage(stage); approval.setApproveStatus("PENDING"); approval.setCreatedAt(LocalDateTime.now());
        approvalMapper.insert(approval); notifyApprovalRequired(approval); return approval;
    }

    private void notifyApprovalRequired(SensitiveViewApproval approval) {
        LambdaQueryWrapper<Staff> q = new LambdaQueryWrapper<Staff>().eq(Staff::getStatus, "ACTIVE");
        if ("BOSS_REVIEW".equals(approval.getApprovalStage())) q.in(Staff::getRoleCode, "BOSS", "SUPER_ADMIN", "SUPER");
        else q.eq(Staff::getRoleCode, "DEPT_MANAGER").eq(Staff::getDeptCode, approval.getApplicantDeptCode());
        for (Staff target : staffMapper.selectList(q)) {
            NotificationReq req = new NotificationReq(); req.setUserNo(target.getStaffCode()); req.setType(Notification.TYPE_SYSTEM_NOTICE);
            req.setTitle("敏感手机号超额查看审批"); req.setContent("员工 " + approval.getApplicantStaffCode() + " 已超过今日查看额度，请处理审批申请"); req.setRelatedId(approval.getApprovalNo());
            try { notificationService.sendOnce(req); } catch (Exception e) { log.warn("发送手机号超额审批通知失败", e); }
        }
    }

    public List<SensitiveViewApproval> pendingApprovals(com.loan.context.LoanUser user) {
        if (user == null || !StringUtils.hasText(user.getRoleCode())) return java.util.Collections.emptyList();
        String role = user.getRoleCode().toUpperCase();
        LambdaQueryWrapper<SensitiveViewApproval> q = new LambdaQueryWrapper<SensitiveViewApproval>().eq(SensitiveViewApproval::getApproveStatus, "PENDING").orderByDesc(SensitiveViewApproval::getCreatedAt);
        if ("DEPT_MANAGER".equals(role)) q.eq(SensitiveViewApproval::getApprovalStage, "MANAGER_REVIEW").eq(SensitiveViewApproval::getApplicantDeptCode, user.getDeptCode());
        else if (Arrays.asList("BOSS", "SUPER_ADMIN", "SUPER").contains(role)) q.eq(SensitiveViewApproval::getApprovalStage, "BOSS_REVIEW");
        else return java.util.Collections.emptyList();
        return approvalMapper.selectList(q);
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditApproval(String approvalNo, boolean approve, String opinion, com.loan.context.LoanUser user) {
        SensitiveViewApproval a = approvalMapper.selectOne(new LambdaQueryWrapper<SensitiveViewApproval>().eq(SensitiveViewApproval::getApprovalNo, approvalNo).last("LIMIT 1"));
        if (a == null || !"PENDING".equals(a.getApproveStatus())) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "审批单不存在或已处理");
        String role = user == null || user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
        boolean boss = Arrays.asList("BOSS", "SUPER_ADMIN", "SUPER").contains(role);
        boolean manager = "DEPT_MANAGER".equals(role) && "MANAGER_REVIEW".equals(a.getApprovalStage()) && user.getDeptCode() != null && user.getDeptCode().equals(a.getApplicantDeptCode());
        if (!(boss || manager) || user.getUserNo().equals(a.getApplicantStaffCode())) throw new BusinessException(ResultCode.FORBIDDEN, "无权审批该申请");
        if (!approve && !StringUtils.hasText(opinion)) throw new BusinessException(ResultCode.PARAM_ERROR, "驳回意见必填");
        a.setApproveStatus(approve ? "APPROVED" : "REJECTED"); a.setApproveOpinion(opinion); a.setApproverStaffCode(user.getUserNo()); a.setApprovedAt(LocalDateTime.now()); approvalMapper.updateById(a);
        NotificationReq req = new NotificationReq(); req.setUserNo(a.getApplicantStaffCode()); req.setType(Notification.TYPE_SYSTEM_NOTICE); req.setTitle("敏感手机号查看审批结果"); req.setContent(approve ? "审批已通过，请重新申请查看手机号" : "审批已驳回：" + opinion); req.setRelatedId(a.getApprovalNo()); notificationService.send(req);
    }

    private long countTodayClientViews(String userNo, LocalDate date) {
        Long count = logMapper.countTodayClientViews(userNo.trim(), date);
        return count == null ? 0L : count;
    }

    private void insertClientGrant(String userNo, String clientCode) {
        SensitiveViewGrant grant = new SensitiveViewGrant();
        grant.setUserNo(userNo.trim());
        grant.setClientCode(clientCode.trim());
        grant.setCreatedAt(LocalDateTime.now());
        try { grantMapper.insert(grant); } catch (DuplicateKeyException ignored) { }
    }

    private void insertClientViewLog(String userNo, String clientCode) {
        SensitiveViewLog logPo = new SensitiveViewLog();
        logPo.setUserNo(userNo.trim());
        logPo.setClientCode(clientCode.trim());
        logPo.setViewDate(LocalDate.now());
        logPo.setCreatedAt(LocalDateTime.now());
        logMapper.insert(logPo);
    }

    /** 填充额度字段。 */
    private void applyQuota(SensitiveApplyViewResp resp, String userNo) {
        SensitiveQuotaVO quota = getQuota(userNo);
        resp.setLimit(quota.getLimit());
        resp.setUsed(quota.getUsed());
        resp.setRemaining(quota.getRemaining());
    }

    /** 写入授权记录（并发重复视为已授权）。 */
    private void insertGrant(String userNo, String leadNo) {
        SensitiveViewGrant grant = new SensitiveViewGrant();
        grant.setUserNo(userNo.trim());
        grant.setLeadNo(leadNo.trim());
        grant.setCreatedAt(LocalDateTime.now());
        try {
            grantMapper.insert(grant);
        } catch (DuplicateKeyException ignored) {
            // 并发重复申请：UK 冲突，视为已授权
        }
    }

    /** 写入查看留痕（日限额统计依据）。 */
    private void insertViewLog(String userNo, String leadNo) {
        SensitiveViewLog logPo = new SensitiveViewLog();
        logPo.setUserNo(userNo.trim());
        logPo.setLeadNo(leadNo.trim());
        logPo.setViewDate(LocalDate.now());
        logPo.setCreatedAt(LocalDateTime.now());
        logMapper.insert(logPo);
    }

    /** 达上限通知老板/主管。 */
    private void notifyOverLimit(String applicantUserNo) {
        Staff applicant = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, applicantUserNo.trim()));
        String name = applicant != null && StringUtils.hasText(applicant.getStaffName())
                ? applicant.getStaffName() : applicantUserNo;
        String content = String.format("%s 今日查看客户敏感数据已达上限 %d 次，请留意！！！", name, getDailyLimit());
        List<Staff> managers = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .in(Staff::getRoleCode, "BOSS", "DEPT_MANAGER")
                .eq(Staff::getStatus, "ACTIVE"));
        if (managers == null) {
            return;
        }
        Set<String> notified = new HashSet<>();
        for (Staff s : managers) {
            if (s != null && StringUtils.hasText(s.getStaffCode()) && notified.add(s.getStaffCode())) {
                NotificationReq req = new NotificationReq();
                req.setUserNo(s.getStaffCode());
                req.setType(Notification.TYPE_SYSTEM_NOTICE);
                req.setTitle("敏感数据查看超额预警");
                req.setContent(content);
                req.setRelatedId(applicantUserNo + ":" + LocalDate.now());
                try {
                    notificationService.sendOnce(req);
                } catch (Exception e) {
                    log.warn("发送敏感查看超额通知失败: {}", s.getStaffCode(), e);
                }
            }
        }
    }
}
