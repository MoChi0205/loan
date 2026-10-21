package com.loan.sensitive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.context.LoanUser;
import com.loan.config.entity.ConfigItem;
import com.loan.config.mapper.ConfigItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
 * 敏感数据查看授权服务（受限角色默认脱敏 → 申请 → 审批 → 可配置日限额（初始 30） → 留痕；豁免角色直看明文）。
 *
 * <p>规则：
 * <ul>
 *   <li>豁免角色（BOSS / SUPER_ADMIN / SUPER）：直接查看明文，不受日限额约束。</li>
 *   <li>顾问、运营、部门经理：额度内首次解锁直接查看；顾问/运营超限由本部门经理审批，
 *       部门经理超限由老板或超级管理员审批。</li>
 *   <li>客户手机号按“员工 + 客户 + 日期”统计首次解锁；同日重复查看不扣次，跨日重新计数。</li>
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
    @Autowired private ConfigItemMapper configItemMapper;
    /** 当前环境每日首次客户解锁上限，管理员配置持久化到 t_config。 */
    public static final String LIMIT_KEY = "daily-limit";

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
        ConfigItem item = configItemMapper == null ? null : configItemMapper.selectOne(
                new LambdaQueryWrapper<ConfigItem>().eq(ConfigItem::getConfigGroup, "SENSITIVE_VIEW")
                .eq(ConfigItem::getConfigKey, LIMIT_KEY).eq(ConfigItem::getEnabled, 1));
        if (item == null) return DEFAULT_DAILY_LIMIT;
        try {
            int value = Integer.parseInt(item.getConfigValue());
            if (value >= 1 && value <= 100000) return value;
        } catch (NumberFormatException ignored) { }
        throw new BusinessException(ResultCode.RULE_CONFIG_ERROR, "手机号查看额度配置无效，请联系管理员");
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
        return getQuota(userNo, null);
    }

    public SensitiveQuotaVO getQuota(String userNo, String roleCode) {
        if (isExemptRole(roleCode)) {
            SensitiveQuotaVO unlimited = new SensitiveQuotaVO();
            unlimited.setLimit(-1); unlimited.setUsed(0); unlimited.setRemaining(-1); unlimited.setUnlimited(true);
            return unlimited;
        }
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

    /** 线索手机号查看统一走客户额度/审批链路，先校验范围，禁止历史授权绕过。 */
    @Transactional(rollbackFor = Exception.class)
    public SensitiveApplyViewResp applyView(String leadNo, LoanUser user) {
        requireInternalStaff(user);
        if (!StringUtils.hasText(leadNo)) throw new BusinessException(ResultCode.PARAM_ERROR, "线索不能为空");
        // 在任何一致性读之前串行化额度所属员工，避免旧快照造成并发超限。
        lockQuotaOwner(user.getUserNo());
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo));
        if (lead == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        ClientProfile visible = new ClientProfile();
        visible.setOwnerStaffCode(lead.getOwnerStaffCode());
        scopeService.requireClientVisibleToStaff(user, visible);
        if ("CHANNEL".equalsIgnoreCase(lead.getSource())
                && Arrays.asList("PENDING_APPROVAL", "REJECTED").contains(lead.getFollowStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道线索尚未通过审核");
        }
        String clientCode = lead.getClientProfileCode();
        if (!StringUtils.hasText(clientCode)) {
            // 历史未绑定的公司公海线索按业务自然键复用或建档，不建立虚假员工归属。
            ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                    .eq(ClientProfile::getPhoneHash, lead.getPhoneHash())
                    .eq(ClientProfile::getCustomerGroup, lead.getLeadType()).last("LIMIT 1"));
            if (client == null) {
                client = new ClientProfile();
                client.setClientCode(BizIdGenerator.generate("client"));
                client.setContactName(lead.getContactName()); client.setPhone(lead.getPhone());
                client.setPhonePlain(lead.getPhone()); client.setPhoneHash(lead.getPhoneHash());
                client.setCustomerGroup(lead.getLeadType()); client.setOwnerStaffCode(lead.getOwnerStaffCode());
                client.setLeadNo(lead.getLeadNo()); client.setSource("LEAD");
                client.setCustomerTag("NEW"); client.setStatus("ACTIVE");
                if (!StringUtils.hasText(lead.getOwnerStaffCode())) client.setSeaLevel("ENTERPRISE");
                client.setCreatedAt(LocalDateTime.now()); client.setCreatedBy(user.getUserNo());
                try { clientProfileMapper.insert(client); }
                catch (DuplicateKeyException duplicate) {
                    client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                            .eq(ClientProfile::getPhoneHash, lead.getPhoneHash())
                            .eq(ClientProfile::getCustomerGroup, lead.getLeadType()).last("FOR UPDATE"));
                    if (client == null) throw duplicate;
                }
            }
            clientCode = client.getClientCode();
            leadMapper.bindClientProfile(lead.getLeadNo(), clientCode, user.getUserNo());
        }
        SensitiveApplyViewResp response = applyClientView(clientCode, user);
        response.setLeadNo(lead.getLeadNo());
        return response;
    }

    private void requireInternalStaff(LoanUser user) {
        if (user == null || !LoanUser.TYPE_STAFF.equals(user.getUserType())
                || !StringUtils.hasText(user.getUserNo())
                || !Arrays.asList("ADVISER", "DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER").contains(user.getRoleCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅公司内部员工可申请查看手机号");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveDailyLimit(int limit, LoanUser user) {
        com.loan.infrastructure.security.AdminRoleGuard.requireSystemConfigAdmin(user);
        if (limit < 1 || limit > 100000) throw new BusinessException(ResultCode.PARAM_ERROR, "每日额度应在1至100000之间");
        ConfigItem item = configItemMapper.selectOne(new LambdaQueryWrapper<ConfigItem>()
                .eq(ConfigItem::getConfigGroup, "SENSITIVE_VIEW").eq(ConfigItem::getConfigKey, LIMIT_KEY));
        if (item == null) {
            item = new ConfigItem(); item.setConfigGroup("SENSITIVE_VIEW"); item.setConfigKey(LIMIT_KEY);
            item.setConfigValue(String.valueOf(limit)); item.setEnabled(1); item.setIsSecret(0);
            item.setDescription("员工每日首次客户手机号解锁额度"); item.setCreatedBy(user.getUserNo());
            configItemMapper.insert(item);
        } else {
            item.setConfigValue(String.valueOf(limit)); item.setEnabled(1); item.setUpdatedBy(user.getUserNo());
            configItemMapper.updateById(item);
        }
    }

    /**
     * 申请查看客户档案手机号原值。列表接口永远只返回脱敏值；原值仅在本次受控响应中返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensitiveApplyViewResp applyClientView(String clientCode, com.loan.context.LoanUser user) {
        requireInternalStaff(user);
        String userNo = user == null ? null : user.getUserNo();
        String roleCode = user == null ? null : user.getRoleCode();
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户不能为空");
        }
        if (!StringUtils.hasText(userNo)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        // MySQL 默认 REPEATABLE READ 下，必须在任何客户/日志一致性读之前取得员工行锁。
        // 若先读客户再等待锁，后续额度查询可能仍沿用等待前建立的旧快照。
        lockQuotaOwner(userNo);
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode.trim()).last("limit 1"));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        scopeService.requireClientVisibleToStaff(user, client);
        String plain = clientProfileMapper.selectPhonePlain(client.getClientCode());
        if (!StringUtils.hasText(plain)) {
            // ClientProfile.phone 由 AesTypeHandler 读取时解密。
            plain = client.getPhone();
            if (StringUtils.hasText(plain) && plain.matches("1[3-9]\\d{9}")) {
                clientProfileMapper.backfillPhonePlain(client.getClientCode(), plain);
            }
        }
        if (!StringUtils.hasText(plain) || !plain.matches("1[3-9]\\d{9}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户手机号未完善，请先更新联系方式");
        }
        SensitiveApplyViewResp resp = new SensitiveApplyViewResp();
        resp.setClientCode(client.getClientCode());
        resp.setPhoneMasked(DesensitizeUtils.phone(plain));
        if (isExemptRole(roleCode)) {
            // 老板/超管不受额度限制，但每个员工-客户-日期仍保留一次审计留痕。
            if (!hasClientViewedToday(userNo, clientCode)) insertClientViewLog(userNo, clientCode);
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo, roleCode);
            return resp;
        }
        // 同一客户在“当天”已解锁才不重复计数；历史永久授权不能跨天绕过当日额度。
        if (hasClientViewedToday(userNo, clientCode)) {
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo, roleCode);
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
                resp.setPhonePlain(plain); resp.setRevealed(true); applyQuota(resp, userNo, roleCode); return resp;
            }
            SensitiveViewApproval approval = ensureOverLimitApproval(clientCode, user);
            resp.setApprovalNo(approval.getApprovalNo());
            resp.setApprovalStatus(approval.getApproveStatus());
            resp.setMessage("已超过今日查看额度，申请已提交审批；审批通过后再次点击查看");
            applyQuota(resp, userNo, roleCode);
            return resp;
        }
        insertClientGrant(userNo, clientCode);
        insertClientViewLog(userNo, clientCode);
        resp.setPhonePlain(plain);
        resp.setRevealed(true);
        applyQuota(resp, userNo, roleCode);
        return resp;
    }

    /** 在当前事务内锁定额度所属员工，确保“计数 → 判断 → 留痕”原子串行。 */
    private void lockQuotaOwner(String userNo) {
        Long staffId = staffMapper.lockByStaffCode(userNo.trim());
        if (staffId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前员工账号不存在或已失效");
        }
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
            req.setTitle("敏感手机号超额查看审批"); req.setContent("本部门员工已超过今日查看额度，请处理手机号查看申请"); req.setRelatedId(approval.getApprovalNo());
            try { notificationService.sendOnce(req); } catch (Exception e) { log.warn("发送手机号超额审批通知失败", e); }
        }
    }

    public List<SensitiveViewApproval> pendingApprovals(com.loan.context.LoanUser user) {
        return pendingApprovals(user, null);
    }

    /**
     * 手机号查看审批列表（D83-九批：支持状态筛选，审批后数据不再从列表"消失"）。
     *
     * @param status PENDING / APPROVED / REJECTED；空=全部
     */
    public List<SensitiveViewApproval> pendingApprovals(com.loan.context.LoanUser user, String status) {
        if (user == null || !StringUtils.hasText(user.getRoleCode())) return java.util.Collections.emptyList();
        String role = user.getRoleCode().toUpperCase();
        LambdaQueryWrapper<SensitiveViewApproval> q = new LambdaQueryWrapper<SensitiveViewApproval>();
        if (StringUtils.hasText(status)) {
            q.eq(SensitiveViewApproval::getApproveStatus, status.trim().toUpperCase());
        }
        q.orderByDesc(SensitiveViewApproval::getCreatedAt);
        if ("DEPT_MANAGER".equals(role)) q.eq(SensitiveViewApproval::getApprovalStage, "MANAGER_REVIEW").eq(SensitiveViewApproval::getApplicantDeptCode, user.getDeptCode());
        else if (!Arrays.asList("BOSS", "SUPER_ADMIN", "SUPER").contains(role)) return java.util.Collections.emptyList();
        List<SensitiveViewApproval> rows = approvalMapper.selectList(q);
        if (rows.isEmpty()) return rows;
        java.util.Map<String, String> staffNames = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .select(Staff::getStaffCode, Staff::getStaffName).in(Staff::getStaffCode,
                        rows.stream().map(SensitiveViewApproval::getApplicantStaffCode).collect(java.util.stream.Collectors.toSet())))
                .stream().collect(java.util.stream.Collectors.toMap(Staff::getStaffCode,
                        s -> StringUtils.hasText(s.getStaffName()) ? s.getStaffName() : "姓名待补充", (a,b) -> a));
        java.util.Map<String, String> clientNames = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                .select(ClientProfile::getClientCode, ClientProfile::getContactName, ClientProfile::getEnterpriseName)
                .in(ClientProfile::getClientCode, rows.stream().map(SensitiveViewApproval::getClientCode).collect(java.util.stream.Collectors.toSet())))
                .stream().collect(java.util.stream.Collectors.toMap(ClientProfile::getClientCode,
                        c -> StringUtils.hasText(c.getEnterpriseName()) ? c.getEnterpriseName()
                                : (StringUtils.hasText(c.getContactName()) ? c.getContactName() : "客户名称待补充"), (a,b) -> a));
        rows.forEach(row -> { row.setApplicantName(staffNames.get(row.getApplicantStaffCode())); row.setClientName(clientNames.get(row.getClientCode())); });
        return rows;
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
        int changed = approvalMapper.update(null, new LambdaUpdateWrapper<SensitiveViewApproval>()
                .eq(SensitiveViewApproval::getApprovalNo, approvalNo).eq(SensitiveViewApproval::getApproveStatus, "PENDING")
                .set(SensitiveViewApproval::getApproveStatus, approve ? "APPROVED" : "REJECTED")
                .set(SensitiveViewApproval::getApproveOpinion, opinion)
                .set(SensitiveViewApproval::getApproverStaffCode, user.getUserNo())
                .set(SensitiveViewApproval::getApprovedAt, LocalDateTime.now()));
        if (changed != 1) throw new BusinessException(ResultCode.PARAM_ERROR, "该审批已被处理，请刷新");
        NotificationReq req = new NotificationReq(); req.setUserNo(a.getApplicantStaffCode()); req.setType(Notification.TYPE_SYSTEM_NOTICE); req.setTitle("敏感手机号查看审批结果"); req.setContent(approve ? "审批已通过，请重新申请查看手机号" : "审批已驳回：" + opinion); req.setRelatedId(a.getApprovalNo()); notificationService.send(req);
    }

    private long countTodayClientViews(String userNo, LocalDate date) {
        Long count = logMapper.countTodayClientViews(userNo.trim(), date);
        return count == null ? 0L : count;
    }

    private boolean hasClientViewedToday(String userNo, String clientCode) {
        if (!StringUtils.hasText(userNo) || !StringUtils.hasText(clientCode)) return false;
        Long count = logMapper.countTodayClientView(userNo.trim(), LocalDate.now(), clientCode.trim());
        return count != null && count > 0L;
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
        try { logMapper.insert(logPo); } catch (DuplicateKeyException ignored) { }
    }

    /** 填充额度字段。 */
    private void applyQuota(SensitiveApplyViewResp resp, String userNo) {
        applyQuota(resp, userNo, null);
    }

    private void applyQuota(SensitiveApplyViewResp resp, String userNo, String roleCode) {
        SensitiveQuotaVO quota = getQuota(userNo, roleCode);
        resp.setLimit(quota.getLimit());
        resp.setUsed(quota.getUsed());
        resp.setRemaining(quota.getRemaining());
    }

}
