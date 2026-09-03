package com.loan.client.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.approval.entity.ClientAllocationApproval;
import com.loan.approval.mapper.ClientAllocationApprovalMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.entity.ClientRecycleConfig;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.mapper.ClientRecycleConfigMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.common.service.BusinessNameService;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.lead.entity.LeadAllocationRecord;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户归属服务：未分配客户池、顾问认领申请、管理者指定顾问及审批落归属。
 *
 * <p>未分配客户池直接以 {@code t_client_profile.owner_staff_code IS NULL} 为真源，
 * 微信首次登录创建档案后无需伪造手机号或重复创建线索。分享引荐关系不参与客户归属。</p>
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ClientAllocationService {

    /** 分配审批状态。 */
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    /**
     * 可直接落归属的目标角色（D39）：顾问 + 团队管理者。
     *
     * <p>业务规则变更：管理者/老板「指定归属人」改为<b>直接落归属、无需审批</b>，
     * 且目标不再局限于顾问，团队管理者本人也可作为客户归属人。</p>
     */
    private static final List<String> ASSIGNABLE_ROLES = Arrays.asList("ADVISER", "DEPT_MANAGER");

    private final ClientProfileMapper clientProfileMapper;
    private final ClientAllocationApprovalMapper approvalMapper;
    private final StaffMapper staffMapper;
    private final LeadAllocationRecordMapper allocationRecordMapper;
    private final ClientRecycleConfigMapper clientRecycleConfigMapper;
    private final NotificationService notificationService;
    private final BusinessNameService businessNameService;

    /**
     * 分页查询未分配客户池。
     *
     * @param keyword 客户姓名或企业名称（可选）
     * @param page 页码
     * @param size 每页数量
     * @return 未分配客户分页，人员信息一次批量补齐
     */
    public PageResult<Map<String, Object>> pageUnassigned(String keyword, int page, int size) {
        LambdaQueryWrapper<ClientProfile> wrapper = new LambdaQueryWrapper<ClientProfile>()
                .isNull(ClientProfile::getOwnerStaffCode);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(ClientProfile::getContactName, value)
                    .or().like(ClientProfile::getEnterpriseName, value));
        }
        wrapper.orderByAsc(ClientProfile::getCreatedAt);
        Page<ClientProfile> result = clientProfileMapper.selectPage(new Page<>(page, size), wrapper);

        List<String> clientCodes = result.getRecords().stream()
                .map(ClientProfile::getClientCode).collect(Collectors.toList());
        Map<String, ClientAllocationApproval> pendingMap = pendingByClientCodes(clientCodes);
        Set<String> pendingStaffCodes = pendingMap.values().stream()
                .map(ClientAllocationApproval::getApplicantStaffCode)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, String> pendingStaffNames = businessNameService.staffNames(pendingStaffCodes);
        List<Map<String, Object>> records = new ArrayList<>(result.getRecords().size());
        for (ClientProfile client : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clientCode", client.getClientCode());
            row.put("customerName", client.getContactName());
            row.put("enterpriseName", client.getEnterpriseName());
            row.put("customerGroup", client.getCustomerGroup());
            row.put("phone", DesensitizeUtils.phone(AesUtils.decrypt(client.getPhone())));
            row.put("registeredAt", client.getCreatedAt());
            ClientAllocationApproval pending = pendingMap.get(client.getClientCode());
            row.put("allocationPending", pending != null);
            row.put("applicantName", pending == null ? null
                    : pendingStaffNames.get(pending.getApplicantStaffCode()));
            records.add(row);
        }
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 创建未分配客户分配申请。相同客户存在待审单时幂等返回原审批单。
     *
     * @param clientCode 客户业务编码
     * @param targetStaffCode 目标顾问工号
     * @param operator 当前操作人
     * @param source 申请来源（ADVISER_CLAIM / MANAGER_ASSIGN）
     * @return 申请结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(String clientCode, String targetStaffCode,
                                     LoanUser operator, String source) {
        ClientProfile client = requireClient(clientCode);
        if (StringUtils.hasText(client.getOwnerStaffCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该客户已有服务顾问");
        }
        Staff target = requireActiveAdviser(targetStaffCode);
        ClientAllocationApproval existing = pending(clientCode);
        if (existing != null) {
            if (!targetStaffCode.equals(existing.getApplicantStaffCode())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该客户已有待审批的顾问分配申请");
            }
            return applyResult(existing, target.getStaffName(), true);
        }

        ClientAllocationApproval approval = new ClientAllocationApproval();
        approval.setApprovalNo(BizIdGenerator.generate("alloc"));
        approval.setClientCode(clientCode);
        approval.setApplicantStaffCode(targetStaffCode);
        approval.setApproveStatus(PENDING);
        approval.setPendingKey(clientCode);
        approval.setApplySource(source);
        approval.setApplyOperatorCode(operator == null ? null : operator.getUserNo());
        approval.setCreatedAt(LocalDateTime.now());
        try {
            approvalMapper.insert(approval);
        } catch (DuplicateKeyException e) {
            ClientAllocationApproval concurrent = pending(clientCode);
            if (concurrent != null && targetStaffCode.equals(concurrent.getApplicantStaffCode())) {
                return applyResult(concurrent, target.getStaffName(), true);
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "该客户已有待审批的顾问分配申请");
        }
        record(clientCode, null, targetStaffCode, "CLAIM_APPLY",
                operator == null ? "system" : operator.getName(),
                "MANAGER_ASSIGN".equals(source) ? "管理者指定顾问，等待审批" : "顾问从未分配客户池申请认领");
        return applyResult(approval, target.getStaffName(), false);
    }

    /**
     * 顾问替客匹配申请：已归属他人的客户必须走审批；记录原归属以防审批期间被并发改写。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyTransfer(String clientCode, String targetStaffCode,
                                              LoanUser operator) {
        ClientProfile client = requireClient(clientCode);
        if (!StringUtils.hasText(client.getOwnerStaffCode())) {
            return apply(clientCode, targetStaffCode, operator, "ADVISER_CLAIM");
        }
        if (client.getOwnerStaffCode().equals(targetStaffCode)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", APPROVED);
            result.put("adviserName", null);
            result.put("reused", true);
            result.put("direct", true);
            return result;
        }
        Staff target = requireActiveAdviser(targetStaffCode);
        ClientAllocationApproval existing = pending(clientCode);
        if (existing != null) {
            if (!targetStaffCode.equals(existing.getApplicantStaffCode())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该客户已有待审批的顾问分配申请");
            }
            return applyResult(existing, target.getStaffName(), true);
        }
        ClientAllocationApproval approval = new ClientAllocationApproval();
        approval.setApprovalNo(BizIdGenerator.generate("alloc"));
        approval.setClientCode(clientCode);
        approval.setApplicantStaffCode(targetStaffCode);
        approval.setFromOwnerStaffCode(client.getOwnerStaffCode());
        approval.setApproveStatus(PENDING);
        approval.setPendingKey(clientCode);
        approval.setApplySource("ADVISER_TRANSFER");
        approval.setApplyOperatorCode(operator == null ? null : operator.getUserNo());
        approval.setCreatedAt(LocalDateTime.now());
        try {
            approvalMapper.insert(approval);
        } catch (DuplicateKeyException e) {
            ClientAllocationApproval concurrent = pending(clientCode);
            if (concurrent != null && targetStaffCode.equals(concurrent.getApplicantStaffCode())) {
                return applyResult(concurrent, target.getStaffName(), true);
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "该客户已有待审批的顾问分配申请");
        }
        record(clientCode, client.getOwnerStaffCode(), targetStaffCode, "TRANSFER_APPLY",
                operator == null ? "system" : operator.getName(), "已有归属客户转移申请，等待审批");
        return applyResult(approval, target.getStaffName(), false);
    }

    /**
     * 管理者/老板直接指定归属人，<b>立即落归属、不生成审批单</b>（D39 业务变更）。
     *
     * <p>目标可为顾问或团队管理者（{@link #ASSIGNABLE_ROLES}）。使用乐观条件更新避免并发覆盖。</p>
     *
     * @param clientCode 客户编码
     * @param targetStaffCode 目标归属人工号（顾问 / 团队管理者）
     * @param operator 操作人
     * @return { clientCode, ownerStaffName, status=APPROVED, direct=true }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> directAssign(String clientCode, String targetStaffCode, LoanUser operator) {
        ClientProfile client = requireClient(clientCode);
        Staff target = requireActiveAssignee(targetStaffCode);
        String expectedOwner = client.getOwnerStaffCode();
        int changed = clientProfileMapper.assignOwnerIfUnchanged(clientCode, targetStaffCode, expectedOwner,
                operator == null ? "system" : operator.getName(), LocalDateTime.now());
        if (changed != 1) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "客户归属已发生变化，请刷新后重试");
        }
        // 管理者直接分配优先级高于顾问转移申请：关闭同客户尚未处理的待审单，避免后续审批再次覆盖本次决定。
        closePendingApplications(clientCode, operator);
        // 归属落定后刷新跟进基准并清除回收冷却，回收倒计时从本次归属起算
        touchAssignment(clientCode);
        record(clientCode, expectedOwner, targetStaffCode, "MANAGER_ASSIGN",
                operator == null ? "system" : operator.getName(), "管理者直接指定归属人（无需审批）");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientCode", clientCode);
        result.put("adviserName", target.getStaffName());
        result.put("ownerStaffName", target.getStaffName());
        result.put("ownerStaffRole", target.getRoleCode());
        result.put("status", APPROVED);
        result.put("direct", true);
        result.put("needApproval", false);
        return result;
    }

    /** 查询指定客户最近一次分配状态。 */
    public Map<String, Object> status(String clientCode, String staffCode) {
        ClientProfile client = requireClient(clientCode);
        ClientAllocationApproval approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<ClientAllocationApproval>()
                        .eq(ClientAllocationApproval::getClientCode, clientCode)
                        .orderByDesc(ClientAllocationApproval::getCreatedAt).last("limit 1"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientCode", clientCode);
        if (approval != null) {
            result.put("status", approval.getApproveStatus());
            result.put("approvalNo", approval.getApprovalNo());
            result.put("rejectReason", approval.getApproveOpinion());
        } else {
            result.put("status", staffCode != null && staffCode.equals(client.getOwnerStaffCode())
                    ? APPROVED : PENDING);
        }
        return result;
    }

    /**
     * 分配待审分页，客户与申请顾问均批量查询，避免行级 N+1。
     *
     * <p>团队管理者（DEPT_MANAGER）仅可见<b>本人团队</b>（申请人部门 == 本人部门）的待审单；
     * 跨团队待审单由其上级（BOSS 等）审批。其余审批角色可见全部。</p>
     */
    public PageResult<Map<String, Object>> pendingPage(int page, int size) {
        List<ClientAllocationApproval> all = approvalMapper.selectList(
                new LambdaQueryWrapper<ClientAllocationApproval>()
                        .eq(ClientAllocationApproval::getApproveStatus, PENDING)
                        .orderByAsc(ClientAllocationApproval::getCreatedAt));
        // 团队管理者按申请人部门过滤为本团队
        LoanUser operator = UserContext.getUser();
        if (isDeptManager(operator)) {
            Set<String> applicantCodes = all.stream()
                    .map(ClientAllocationApproval::getApplicantStaffCode)
                    .filter(StringUtils::hasText).collect(Collectors.toSet());
            Map<String, String> codeToDept = applicantCodes.isEmpty() ? Collections.emptyMap()
                    : staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                    .in(Staff::getStaffCode, applicantCodes)).stream()
                    .collect(Collectors.toMap(Staff::getStaffCode, Staff::getDeptCode, (a, b) -> a));
            String myDept = operator.getDeptCode();
            all = all.stream()
                    .filter(a -> myDept != null
                            && myDept.equalsIgnoreCase(codeToDept.get(a.getApplicantStaffCode())))
                    .collect(Collectors.toList());
        }
        Set<String> clientCodes = all.stream().map(ClientAllocationApproval::getClientCode)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Set<String> staffCodes = all.stream().map(ClientAllocationApproval::getApplicantStaffCode)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, ClientProfile> clients = clientCodes.isEmpty() ? Collections.emptyMap()
                : clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                .in(ClientProfile::getClientCode, clientCodes)).stream()
                .collect(Collectors.toMap(ClientProfile::getClientCode, Function.identity(), (a, b) -> a));
        Map<String, String> names = businessNameService.staffNames(staffCodes);

        int total = all.size();
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);
        List<ClientAllocationApproval> slice = fromIndex >= total ? Collections.emptyList()
                : all.subList(fromIndex, toIndex);

        List<Map<String, Object>> rows = new ArrayList<>(slice.size());
        for (ClientAllocationApproval approval : slice) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("approvalNo", approval.getApprovalNo());
            row.put("clientCode", approval.getClientCode());
            row.put("applicantName", names.get(approval.getApplicantStaffCode()));
            row.put("applySource", approval.getApplySource());
            row.put("createdAt", approval.getCreatedAt());
            ClientProfile client = clients.get(approval.getClientCode());
            if (client != null) {
                row.put("entName", client.getEnterpriseName());
                row.put("contactName", client.getContactName());
                row.put("contactPhone", DesensitizeUtils.phone(AesUtils.decrypt(client.getPhone())));
            }
            rows.add(row);
        }
        return PageResult.build(page, size, total, rows);
    }

    /** 审批通过，使用状态条件更新保证并发下仅成功一次。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approve(String approvalNo, LoanUser user) {
        ClientAllocationApproval approval = requirePending(approvalNo);
        // 团队管理者仅可审批本人团队（申请人部门 == 本人部门）的客户，跨团队需 BOSS 审批
        assertDeptManagerScope(user, approval.getApplicantStaffCode());
        int changed = approvalMapper.update(null, new LambdaUpdateWrapper<ClientAllocationApproval>()
                .eq(ClientAllocationApproval::getApprovalNo, approvalNo)
                .eq(ClientAllocationApproval::getApproveStatus, PENDING)
                .set(ClientAllocationApproval::getApproveStatus, APPROVED)
                .set(ClientAllocationApproval::getPendingKey, null)
                .set(ClientAllocationApproval::getApproverStaffCode, user == null ? null : user.getUserNo())
                .set(ClientAllocationApproval::getApprovedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "该审批单已处理");
        }
        requireClient(approval.getClientCode());
        int assigned = StringUtils.hasText(approval.getFromOwnerStaffCode())
                ? clientProfileMapper.transferOwnerIfUnchanged(approval.getClientCode(), approval.getApplicantStaffCode(),
                approval.getFromOwnerStaffCode(), user == null ? "system" : user.getName(), LocalDateTime.now())
                : clientProfileMapper.assignOwnerIfUnassigned(approval.getClientCode(), approval.getApplicantStaffCode(),
                user == null ? "system" : user.getName(), LocalDateTime.now());
        if (assigned != 1) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "客户已由其他流程完成分配，请刷新后重试");
        }
        // 审批通过后刷新跟进基准并清除回收冷却
        touchAssignment(approval.getClientCode());
        record(approval.getClientCode(), approval.getFromOwnerStaffCode(), approval.getApplicantStaffCode(),
                "CLAIM_APPROVED", user == null ? "system" : user.getName(), "客户分配审批通过");
        return auditResult(approvalNo, APPROVED, approval.getClientCode());
    }

    /** 审批驳回，清除 pendingKey 后允许重新申请。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reject(String approvalNo, String opinion, LoanUser user) {
        if (!StringUtils.hasText(opinion)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回意见不能为空");
        }
        ClientAllocationApproval approval = requirePending(approvalNo);
        // 团队管理者仅可驳回本人团队（申请人部门 == 本人部门）的客户，跨团队需 BOSS 审批
        assertDeptManagerScope(user, approval.getApplicantStaffCode());
        int changed = approvalMapper.update(null, new LambdaUpdateWrapper<ClientAllocationApproval>()
                .eq(ClientAllocationApproval::getApprovalNo, approvalNo)
                .eq(ClientAllocationApproval::getApproveStatus, PENDING)
                .set(ClientAllocationApproval::getApproveStatus, REJECTED)
                .set(ClientAllocationApproval::getPendingKey, null)
                .set(ClientAllocationApproval::getApproveOpinion, opinion.trim())
                .set(ClientAllocationApproval::getApproverStaffCode, user == null ? null : user.getUserNo())
                .set(ClientAllocationApproval::getApprovedAt, LocalDateTime.now()));
        if (changed != 1) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "该审批单已处理");
        }
        return auditResult(approvalNo, REJECTED, null);
    }

    private Map<String, ClientAllocationApproval> pendingByClientCodes(List<String> clientCodes) {
        if (clientCodes == null || clientCodes.isEmpty()) return Collections.emptyMap();
        return approvalMapper.selectList(new LambdaQueryWrapper<ClientAllocationApproval>()
                .in(ClientAllocationApproval::getClientCode, clientCodes)
                .eq(ClientAllocationApproval::getApproveStatus, PENDING)).stream()
                .collect(Collectors.toMap(ClientAllocationApproval::getClientCode,
                        Function.identity(), (a, b) -> a));
    }

    private ClientProfile requireClient(String clientCode) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户编码必填");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode).last("limit 1"));
        if (client == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        return client;
    }

    private Staff requireActiveAdviser(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择目标顾问");
        }
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode).last("limit 1"));
        if (staff == null || !"ACTIVE".equalsIgnoreCase(staff.getStatus())
                || !"ADVISER".equalsIgnoreCase(staff.getRoleCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标顾问不存在或不可用");
        }
        return staff;
    }

    /**
     * 校验目标归属人可用（D39）：在职且角色为顾问或团队管理者。
     *
     * <p>与 {@link #requireActiveAdviser} 的差异：直接分配场景目标可扩展到
     * 团队管理者本人，因此单独放宽角色校验，其余保持一致。</p>
     */
    private Staff requireActiveAssignee(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择目标归属人");
        }
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode).last("limit 1"));
        if (staff == null || !"ACTIVE".equalsIgnoreCase(staff.getStatus())
                || !ASSIGNABLE_ROLES.contains(String.valueOf(staff.getRoleCode()).toUpperCase())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标归属人不存在或不可用（仅顾问/团队管理者）");
        }
        return staff;
    }

    private ClientAllocationApproval pending(String clientCode) {
        return approvalMapper.selectOne(new LambdaQueryWrapper<ClientAllocationApproval>()
                .eq(ClientAllocationApproval::getPendingKey, clientCode).last("limit 1"));
    }

    /** 当前用户是否为团队管理者（仅企业员工中的部门经理）。 */
    private boolean isDeptManager(LoanUser user) {
        return user != null && LoanUser.TYPE_STAFF.equals(user.getUserType())
                && "DEPT_MANAGER".equalsIgnoreCase(user.getRoleCode());
    }

    /**
     * 团队管理者审批范围断言：仅允许审批申请人（目标归属人）与本人同部门的待审单。
     *
     * <p>跨团队待审单必须由 BOSS 等上级审批，调用方会收到明确提示。</p>
     *
     * @param user 审批人
     * @param applicantStaffCode 申请人（目标归属人）工号
     */
    private void assertDeptManagerScope(LoanUser user, String applicantStaffCode) {
        if (!isDeptManager(user)) {
            return;
        }
        String applicantDept = deptCodeOf(applicantStaffCode);
        if (user.getDeptCode() == null || !user.getDeptCode().equalsIgnoreCase(applicantDept)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "非本团队客户，需由 BOSS 审批");
        }
    }

    /** 查询员工所属部门编码（找不到返回 null）。 */
    private String deptCodeOf(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            return null;
        }
        Staff staff = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode).last("limit 1"));
        return staff == null ? null : staff.getDeptCode();
    }

    /**
     * 判断当前员工是否已获得该客户的归属审批（已审批通过且申请人为该员工）。
     *
     * <p>用于「顾问替客匹配对他人归属客户必须审批」的放行判定：已通过归属审批的员工
     * 视为该客户的合法归属人，可直接发起匹配；否则需先走分配审批。</p>
     *
     * @param clientCode 客户编码
     * @param operatorStaffCode 操作人员工工号
     * @return true 表示已通过归属审批
     */
    public boolean hasApprovedOwnership(String clientCode, String operatorStaffCode) {
        if (!StringUtils.hasText(clientCode) || !StringUtils.hasText(operatorStaffCode)) {
            return false;
        }
        // 通过审批后 owner_staff_code 已同步落为申请人；只认当前实时归属，不能被历史审批单放行。
        return clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode)
                .eq(ClientProfile::getOwnerStaffCode, operatorStaffCode)
                .last("limit 1")) != null;
    }

    /**
     * 解析并校验小程序客户操作上下文。客户端客户编码只表示操作目标，
     * 最终权限以登录身份和数据库中的实时归属为准，供匹配和材料上传复用。
     */
    public String requireOperationClientCode(LoanUser user, String requestedClientCode) {
        if (user == null || !StringUtils.hasText(user.getUserNo())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方不可操作客户资料");
        }
        if (LoanUser.TYPE_CUSTOMER.equals(user.getUserType())) {
            return user.getUserNo();
        }
        if (!LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前身份不可操作客户资料");
        }
        if (!StringUtils.hasText(requestedClientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请先选择目标客户");
        }
        if (!hasApprovedOwnership(requestedClientCode, user.getUserNo())) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "当前客户尚未归属本人，请先完成分配或转移审批");
        }
        return requestedClientCode;
    }

    /** 关闭同客户仍处于 PENDING 的申请，保证管理员直接分配与审批流不会互相覆盖。 */
    private void closePendingApplications(String clientCode, LoanUser operator) {
        approvalMapper.update(null, new LambdaUpdateWrapper<ClientAllocationApproval>()
                .eq(ClientAllocationApproval::getClientCode, clientCode)
                .eq(ClientAllocationApproval::getApproveStatus, PENDING)
                .set(ClientAllocationApproval::getApproveStatus, REJECTED)
                .set(ClientAllocationApproval::getPendingKey, null)
                .set(ClientAllocationApproval::getApproveOpinion, "管理员已直接分配，原待审申请自动关闭")
                .set(ClientAllocationApproval::getApproverStaffCode, operator == null ? null : operator.getUserNo())
                .set(ClientAllocationApproval::getApprovedAt, LocalDateTime.now()));
    }

    /* ==================== 客户自动回收 + 预警（参考 tse 资源池 / LeadService） ==================== */

    /**
     * 读取客户回收配置（全局单行）。无配置行时返回 null，由调用方按默认值兜底。
     */
    private ClientRecycleConfig recycleConfig() {
        List<ClientRecycleConfig> list = clientRecycleConfigMapper.selectList(
                new LambdaQueryWrapper<ClientRecycleConfig>().last("limit 1"));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /**
     * 超期未跟进客户自动回收进公海（XXL-Job 定时任务调用）。
     *
     * @return 回收数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int recycleOverdue() {
        ClientRecycleConfig cfg = recycleConfig();
        if (cfg == null || !cfg.isRecycleEnabled() || cfg.getRecycleDays() == null || cfg.getRecycleDays() <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(cfg.getRecycleDays());
        List<ClientProfile> overdue = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                .isNotNull(ClientProfile::getOwnerStaffCode)
                .and(w -> w.isNull(ClientProfile::getLastFollowedAt)
                        .or().lt(ClientProfile::getLastFollowedAt, threshold)));
        int n = 0;
        for (ClientProfile client : overdue) {
            recycleClient(client, "因超期未跟进已回收进公海", "system");
            n++;
        }
        return n;
    }

    /**
     * 回收预警：扫描距回收剩余预警天数内的客户，向归属人发站内预警（去重）。
     *
     * @return 预警发送数
     */
    @Transactional(rollbackFor = Exception.class)
    public int warnRecycle() {
        ClientRecycleConfig cfg = recycleConfig();
        if (cfg == null || !cfg.isRecycleEnabled() || cfg.getRecycleDays() == null || cfg.getRecycleDays() <= 0) {
            return 0;
        }
        int warnDays = cfg.getWarnDays() != null && cfg.getWarnDays() > 0 ? cfg.getWarnDays() : 3;
        LocalDateTime warnStart = LocalDateTime.now().minusDays(cfg.getRecycleDays());
        LocalDateTime warnEnd = LocalDateTime.now().minusDays(Math.max(0, cfg.getRecycleDays() - warnDays));
        List<ClientProfile> warns = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                .isNotNull(ClientProfile::getOwnerStaffCode)
                .and(w -> w.isNull(ClientProfile::getLastFollowedAt)
                        .or().between(ClientProfile::getLastFollowedAt, warnStart, warnEnd)));
        int sent = 0;
        for (ClientProfile client : warns) {
            String ownerNo = client.getOwnerStaffCode();
            if (ownerNo == null) {
                continue;
            }
            if (notificationService.existsByTypeAndRelatedId(
                    Notification.TYPE_CLIENT_RECYCLE_WARN, client.getClientCode())) {
                continue;
            }
            String name = client.getEnterpriseName() != null ? client.getEnterpriseName() : client.getContactName();
            notificationService.send(buildClientNotice(ownerNo, "客户即将回收预警",
                    "客户【" + (name == null ? client.getClientCode() : name) + "】将在 " + warnDays
                            + " 天内因超期未跟进被回收进公海，请及时跟进。",
                    client.getClientCode()));
            sent++;
        }
        return sent;
    }

    /**
     * 管理端手动回收客户进公海（管理者操作，覆盖冷却）。
     *
     * @param clientCode 客户编码
     * @param operator   操作人
     * @return { clientCode, recycled=true, fromOwnerStaffCode }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> manualRecycle(String clientCode, LoanUser operator) {
        ClientProfile client = requireClient(clientCode);
        if (!StringUtils.hasText(client.getOwnerStaffCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该客户无归属顾问，无需回收");
        }
        String from = client.getOwnerStaffCode();
        recycleClient(client, "已被管理员手动回收进公海", operator == null ? "system" : operator.getName());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientCode", clientCode);
        result.put("recycled", true);
        result.put("fromOwnerStaffCode", from);
        return result;
    }

    /**
     * 执行单个客户回收：清空归属 + 置冷却 + 记流转 + 站内通知。
     *
     * <p>仅更新非加密字段，规避 {@code @TableField(typeHandler=AesTypeHandler)} 在
     * {@code updateById} 下对 phone / creditCode 二次加密的隐患。</p>
     */
    private void recycleClient(ClientProfile client, String reason, String operatorName) {
        String from = client.getOwnerStaffCode();
        ClientRecycleConfig cfg = recycleConfig();
        int cooldown = (cfg != null && cfg.getCooldownDays() != null && cfg.getCooldownDays() > 0)
                ? cfg.getCooldownDays() : 7;
        clientProfileMapper.update(null, new LambdaUpdateWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, client.getClientCode())
                .set(ClientProfile::getOwnerStaffCode, (String) null)
                .set(ClientProfile::getAssignBlockedUntil, LocalDateTime.now().plusDays(cooldown))
                .set(ClientProfile::getUpdatedBy, operatorName));
        record(client.getClientCode(), from, null, "CLIENT_RECYCLE", operatorName, reason);
        if (from != null) {
            String name = client.getEnterpriseName() != null ? client.getEnterpriseName() : client.getContactName();
            String title = "客户已回收进公海";
            String content = "客户【" + (name == null ? client.getClientCode() : name) + "】" + reason
                    + "，冷却期内不可认领。";
            notificationService.send(buildClientNotice(from, title, content, client.getClientCode()));
        }
    }

    /** 归属落定后刷新最后跟进时间并清除回收冷却（避免旧冷却阻碍新归属）。 */
    private void touchAssignment(String clientCode) {
        clientProfileMapper.update(null, new LambdaUpdateWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode)
                .set(ClientProfile::getLastFollowedAt, LocalDateTime.now())
                .set(ClientProfile::getAssignBlockedUntil, (LocalDateTime) null));
    }

    /** 构建客户回收类站内通知请求。 */
    private NotificationReq buildClientNotice(String userNo, String title, String content, String relatedId) {
        NotificationReq req = new NotificationReq();
        req.setUserNo(userNo);
        req.setType(Notification.TYPE_CLIENT_RECYCLE_WARN);
        req.setTitle(title);
        req.setContent(content);
        req.setRelatedId(relatedId);
        return req;
    }

    private ClientAllocationApproval requirePending(String approvalNo) {
        ClientAllocationApproval approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<ClientAllocationApproval>()
                        .eq(ClientAllocationApproval::getApprovalNo, approvalNo).last("limit 1"));
        if (approval == null) throw new BusinessException(ResultCode.DATA_NOT_FOUND, "审批单不存在");
        if (!PENDING.equals(approval.getApproveStatus())) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "该审批单已处理");
        }
        return approval;
    }

    private Map<String, Object> applyResult(ClientAllocationApproval approval, String adviserName, boolean reused) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalNo", approval.getApprovalNo());
        result.put("status", approval.getApproveStatus());
        result.put("adviserName", adviserName);
        result.put("reused", reused);
        return result;
    }

    private Map<String, Object> auditResult(String approvalNo, String status, String clientCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalNo", approvalNo);
        result.put("status", status);
        if (clientCode != null) result.put("clientCode", clientCode);
        return result;
    }

    private void record(String clientCode, String fromStaff, String toStaff,
                        String actionType, String operator, String remark) {
        LeadAllocationRecord record = new LeadAllocationRecord();
        record.setLeadNo(clientCode);
        record.setActionType(actionType);
        record.setFromStaffCode(fromStaff);
        record.setToStaffCode(toStaff);
        record.setOperator(StringUtils.hasText(operator) ? operator : "system");
        record.setRemark(remark);
        record.setCreatedAt(LocalDateTime.now());
        allocationRecordMapper.insert(record);
    }
}
