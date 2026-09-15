package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.approval.entity.ClientAllocationApproval;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.entity.ClientLifecycleEvent;
import com.loan.client.mapper.ClientLifecycleEventMapper;
import com.loan.client.service.ClientAllocationService;
import com.loan.common.ResultCode;
import com.loan.common.service.BusinessNameService;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.lead.entity.LeadAllocationRecord;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序端客户查重与归属流转（C2 归属流转 + C10 自动查重）。
 *
 * <p><b>C10 自动查重：</b>员工替客户匹配时，输入企业名称 / 手机号 / 统一社会信用代码，
 * 系统自动检索已有客户，命中则走归属流转，未命中则录入新客户，
 * 无需用户手动选择「录入新客户 / 申请分配老客户」。
 *
 * <p><b>C2 归属流转：</b>
 * <ul>
 *   <li>情形 A（未命中）：只创建未分配客户，顾问随后提交认领审批；禁止“录入即归属”。</li>
 *   <li>情形 B（已归属本人）：幂等通过。</li>
 *   <li>情形 B（已归属他人或无归属）：提交分配/转移审批，审批通过后才获得匹配权限。</li>
 * </ul>
 *
 * <p><b>敏感字段：</b>手机号与统一社会信用代码在库内以 SHA-256 摘要存储
 * （phone_hash / credit_code_hash），精确匹配前对入参做同样摘要，不以明文比对。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniClientService {

    /** 历史分配动作常量（兼容存量流水读取，新流程不再录入即归属）。 */
    public static final String ACTION_AUTO_CLAIM = "AUTO_CLAIM";
    /** 分配动作：申请分配（无归宿，需审批） */
    public static final String ACTION_CLAIM_APPLY = "CLAIM_APPLY";
    /** 分配动作：审批通过后归属流转 */
    public static final String ACTION_CLAIM_APPROVED = "CLAIM_APPROVED";

    /** 分配审批状态 */
    public static final String ALLOC_PENDING = "PENDING";
    public static final String ALLOC_APPROVED = "APPROVED";
    public static final String ALLOC_REJECTED = "REJECTED";

    /** 客户档案状态（t_client_profile.status，schema：ACTIVE/DISABLED） */
    public static final String CLIENT_STATUS_ACTIVE = "ACTIVE";
    public static final String CLIENT_STATUS_DISABLED = "DISABLED";

    /** 员工在职状态（t_staff.status） */
    public static final String STAFF_STATUS_ACTIVE = "ACTIVE";

    /** 公海层级（t_client_profile.sea_level） */
    public static final String SEA_ENTERPRISE = "ENTERPRISE";
    public static final String SEA_TEAM = "TEAM";

    private final ClientProfileMapper clientProfileMapper;
    private final ClientLifecycleEventMapper lifecycleEventMapper;
    private final LeadAllocationRecordMapper allocationRecordMapper;
    private final StaffMapper staffMapper;
    private final ClientAllocationService clientAllocationService;
    private final BusinessNameService businessNameService;

    /**
     * 客户查重（C10 综合关键词，对齐 Web 端 ClientService）：
     * 联系人姓名 / 企业名称模糊；手机号 / 身份证 / 统一社会信用代码按摘要精确匹配，任一命中即返回。
     *
     * <p>原实现按关键词格式分支（18 位字母数字 → 信用代码、6+ 位数字 → 手机号、其他 → 企业名），
     * 不支持联系人姓名 / 身份证查重，且容易因格式判断遗漏命中；改为 OR 组合一次性覆盖。
     *
     * @param keyword 关键词（调用方保证已 trim 且长度 ≥2）
     * @return 命中客户（含 hasOwner 供前端分流），未命中返回 null
     */
    public Map<String, Object> search(String keyword) {
        String kw = keyword.trim();
        LambdaQueryWrapper<ClientProfile> wrapper = new LambdaQueryWrapper<>();
        // 综合关键词 OR 匹配：联系人/企业名模糊 + 手机号/身份证/信用代码精确（SHA-256 摘要比对）
        String sha = sha256(kw);
        wrapper.and(w -> w.like(ClientProfile::getContactName, kw)
                .or().like(ClientProfile::getEnterpriseName, kw)
                .or().eq(ClientProfile::getPhoneHash, sha)
                // 身份证：个人档案表 t_personal_profile（client_profile_code 关联 + id_card_hash 摘要）
                .or().exists("SELECT 1 FROM t_personal_profile pp WHERE pp.client_profile_code = t_client_profile.client_code AND pp.id_card_hash = {0}", sha)
                .or().eq(ClientProfile::getCreditCodeHash, sha));
        List<ClientProfile> list = clientProfileMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return null;
        }
        ClientProfile c = list.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientCode", c.getClientCode());
        m.put("entName", c.getEnterpriseName());
        m.put("contactName", c.getContactName());
        m.put("contactPhone", maskPhone(c.getPhone()));
        m.put("ownerStaffCode", c.getOwnerStaffCode());
        m.put("ownerStaffName", staffName(c.getOwnerStaffCode()));
        m.put("hasOwner", StringUtils.hasText(c.getOwnerStaffCode()));
        return m;
    }

    /**
     * 录入新客户（D39）：只建档为未分配客户，不因录入人身份自动建立服务归属。
     *
     * @param payload   客户信息（entName 必填）
     * @param user      当前员工
     * @return 新建/已存在客户编码与当前归属状态
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Map<String, Object> payload, LoanUser user) {
        String entName = payload.get("entName") == null ? null : String.valueOf(payload.get("entName")).trim();
        if (!StringUtils.hasText(entName)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "企业名称不能为空");
        }
        String creditCode = payload.get("creditCode") == null ? null : String.valueOf(payload.get("creditCode")).trim();
        String phone = payload.get("contactPhone") == null ? null : String.valueOf(payload.get("contactPhone")).trim();
        if (StringUtils.hasText(creditCode)
                && !creditCode.toUpperCase().matches("[0-9A-HJ-NPQRTUWXY]{18}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "统一社会信用代码格式不正确");
        }
        if (StringUtils.hasText(phone) && !phone.matches("1\\d{10}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号格式不正确");
        }

        // 幂等保护：信用代码或手机号已存在则直接返回，不重复建档
        if (StringUtils.hasText(creditCode)) {
            ClientProfile exist = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                    .eq(ClientProfile::getCreditCodeHash, sha256(creditCode)));
            if (exist != null) {
                return result(exist.getClientCode(), exist.getOwnerStaffCode(), "EXISTED");
            }
        }
        if (StringUtils.hasText(phone)) {
            ClientProfile exist = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                    .eq(ClientProfile::getPhoneHash, sha256(phone)));
            if (exist != null) {
                return result(exist.getClientCode(), exist.getOwnerStaffCode(), "EXISTED");
            }
        }

        String staffCode = user == null ? null : user.getUserNo();
        ClientProfile client = new ClientProfile();
        client.setClientCode(BizIdGenerator.generate("client"));
        client.setEnterpriseName(entName);
        client.setContactName(payload.get("contactName") == null ? null : String.valueOf(payload.get("contactName")).trim());
        client.setCustomerGroup(strValue(payload.get("customerGroup"), "ENTERPRISE"));
        // 状态枚举对齐 schema（ACTIVE/DISABLED）与其他建档入口（AuthService/MiniAuthService）；
        // 历史误写为 "NORMAL"，无任何查询按该值过滤，故此处归位为 ACTIVE。
        client.setStatus(CLIENT_STATUS_ACTIVE);
        client.setSource("MINI_STAFF_CREATE");
        // 员工自行新增的客户直接归属本人；只有主动释放后才进入公司公海。
        client.setOwnerStaffCode(staffCode);
        client.setSeaLevel(null);
        client.setCreatedBy(staffCode);
        if (StringUtils.hasText(phone)) {
            client.setPhoneHash(sha256(phone));
        }
        if (StringUtils.hasText(creditCode)) {
            client.setCreditCodeHash(sha256(creditCode));
        }
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        clientProfileMapper.insert(client);

        ClientLifecycleEvent event = new ClientLifecycleEvent();
        event.setClientCode(client.getClientCode());
        event.setEventType(staffCode == null ? "ENTER_COMPANY_SEA" : "OWNER_ASSIGNED");
        event.setStaffCode(staffCode);
        event.setSeaLevel(staffCode == null ? SEA_ENTERPRISE : null);
        event.setEpisodeNo(1);
        event.setEventAt(client.getCreatedAt());
        event.setOperatorStaffCode(staffCode);
        event.setReasonCode(staffCode == null ? "SYSTEM_CREATE" : "SELF_CREATE");
        lifecycleEventMapper.insert(event);

        return result(client.getClientCode(), staffCode, "CREATED_ASSIGNED");
    }

    /**
     * 申请分配已有客户（C2 情形 B）。
     *
     * <p>已归属本人 → 幂等返回 AUTO_CLAIMED；
     * 已归属他人或无归宿 → 记录申请并返回 PENDING_APPROVAL，等待上级或运营审批。
     *
     * @param clientCode 客户编码
     * @param user       当前员工
     * @return { result: AUTO_CLAIMED | PENDING_APPROVAL, approvalNo? }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> claim(String clientCode, LoanUser user) {
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        String staffCode = user == null ? null : user.getUserNo();
        if (staffCode != null && staffCode.equals(client.getOwnerStaffCode())) {
            // 已是自己的客户，直接放行
            return claimResult("AUTO_CLAIMED", null);
        }

        Map<String, Object> apply = clientAllocationService.applyTransfer(clientCode, staffCode, user);
        if (apply.get("approvalNo") == null && client.getOwnerStaffCode() != null
                && client.getOwnerStaffCode().equals(staffCode)) {
            return claimResult("AUTO_CLAIMED", null);
        }
        return claimResult("PENDING_APPROVAL", String.valueOf(apply.get("approvalNo")));
    }

    /**
     * 查询分配申请审批状态（供前端轮询"无归宿待审批"分支）。
     *
     * @param clientCode 客户编码
     * @param user       当前员工
     * @return { status: PENDING | APPROVED | REJECTED, approvalNo?, rejectReason? }
     */
    public Map<String, Object> claimStatus(String clientCode, LoanUser user) {
        return clientAllocationService.status(clientCode, user == null ? null : user.getUserNo());
    }

    public Map<String, Object> release(String clientCode, LoanUser user) {
        return clientAllocationService.selfRelease(clientCode, user);
    }

    /* ==================== B3：无归宿分配审批（运营/超管） ==================== */

    /**
     * 分配待审列表（运营/超管）。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 待审单分页（含客户企业名/联系人/手机掩码/申请人姓名）
     */
    public com.loan.api.dto.PageResult<Map<String, Object>> pendingAllocations(int page, int size) {
        return clientAllocationService.pendingPage(page, size);
    }

    /**
     * 通过分配审批：客户归属流转给申请人。
     *
     * @param approvalNo 审批单号
     * @param user       审批人（运营/超管，Controller 已校验角色）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approveAllocation(String approvalNo, LoanUser user) {
        return clientAllocationService.approve(approvalNo, user);
    }

    /**
     * 驳回分配审批。
     *
     * @param approvalNo 审批单号
     * @param opinion    驳回意见（必填）
     * @param user       审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rejectAllocation(String approvalNo, String opinion, LoanUser user) {
        return clientAllocationService.reject(approvalNo, opinion, user);
    }

    /* ==================== 我的客户 / 客户公海（小程序员工侧） ==================== */

    /**
     * 「我的客户」列表：仅本人归属客户（用户 2026-09-10 确认「统一只显示本人归属」）。
     *
     * <p><b>数据范围：</b>一律按 {@code owner_staff_code = 当前登录工号} 收敛，
     * 顾问 / 部门经理 / 运营 / 老板 / 超级管理员口径一致，不因角色放大。
     *
     * <p><b>状态口径：</b>排除 {@code DISABLED}。历史遗留的 {@code NORMAL} 行
     * （见 {@link #create} 修复说明）与 {@code ACTIVE} 行一并可见，避免存量数据凭空消失。
     *
     * <p><b>合规：</b>对外只返回企业名 / 联系人 / 掩码手机号 / 归属人与状态，
     * 不含任何业务单号展示字段（D68）。
     *
     * @param keyword 企业名模糊关键词，可为空
     * @param page    页码（调用方已归一化）
     * @param size    每页大小（调用方已归一化）
     * @param user    当前登录员工
     * @return 本人归属客户分页摘要
     */
    public PageResult<Map<String, Object>> myClients(String keyword, int page, int size, LoanUser user) {
        LambdaQueryWrapper<ClientProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClientProfile::getOwnerStaffCode, user.getUserNo());
        wrapper.ne(ClientProfile::getStatus, CLIENT_STATUS_DISABLED);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ClientProfile::getEnterpriseName, keyword.trim());
        }
        wrapper.orderByDesc(ClientProfile::getUpdatedAt);
        Page<ClientProfile> result = clientProfileMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), clientSummaries(result.getRecords()));
    }

    /**
     * 客户公海列表（15-客户公海团队客户与分配回收规则 §11/§12）。
     *
     * <p><b>范围：</b>{@code ENTERPRISE} 公司公海全员可见、全员可认领；
     * {@code TEAM} 团队公海仅本部门可见、仅本部门成员可认领。
     *
     * <p><b>边界：</b>只做查询与展示，认领复用 {@code POST /api/mini/client/{clientCode}/claim}
     * 的既有原子落归属逻辑（并发仅一人成功）；冷却期拦截由认领链路负责。
     *
     * @param keyword  企业名模糊关键词，可为空
     * @param seaLevel 公海层级：ENTERPRISE / TEAM，非法或空时按 ENTERPRISE
     * @param page     页码
     * @param size     每页大小
     * @param user     当前登录员工
     * @return 公海客户分页摘要（归属人字段为空，前端展示为「公海客户」）
     */
    public PageResult<Map<String, Object>> seaClients(String keyword, String seaLevel, int page, int size,
                                                      LoanUser user) {
        // 安全边界不能只依赖 Controller 或网关：渠道账号即使通过内部调用/错误路由
        // 进入服务层，也绝不能读取公司或团队公海。
        if (user == null || LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "渠道合作方无公海数据权限");
        }
        String level = SEA_TEAM.equalsIgnoreCase(String.valueOf(seaLevel)) ? SEA_TEAM : SEA_ENTERPRISE;
        LambdaQueryWrapper<ClientProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClientProfile::getSeaLevel, level);
        if (SEA_TEAM.equals(level)) {
            // 团队公海按部门隔离：未绑定部门的账号看不到任何团队公海客户（fail-closed）
            if (!StringUtils.hasText(user.getDeptCode())) {
                return PageResult.build(page, size, 0L, Collections.emptyList());
            }
            wrapper.eq(ClientProfile::getSeaDeptCode, user.getDeptCode());
        }
        wrapper.ne(ClientProfile::getStatus, CLIENT_STATUS_DISABLED);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ClientProfile::getEnterpriseName, keyword.trim());
        }
        wrapper.orderByDesc(ClientProfile::getUpdatedAt);
        Page<ClientProfile> result = clientProfileMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), clientSummaries(result.getRecords()));
    }

    /**
     * 「团队客户」列表（15-客户公海团队客户与分配回收规则 §10）。
     *
     * <p><b>范围：</b>本部门在职成员（**排除本人**）名下客户，与「我的客户」独立展示。
     * 仅部门经理使用；部门编码为空返回空集（fail-closed）。
     *
     * <p><b>用途：</b>为部门经理提供「回收」操作的目标列表（§32/§34：可回收本团队顾问客户，
     * 且不得回收其他团队客户）。回收动作由 {@link #recycle} 承担。
     *
     * @param keyword 企业名模糊关键词，可为空
     * @param page    页码
     * @param size    每页大小
     * @param user    当前登录部门经理
     * @return 本部门成员名下客户分页摘要
     */
    public PageResult<Map<String, Object>> teamClients(String keyword, int page, int size, LoanUser user) {
        List<String> memberCodes = staffCodesOfDept(user.getDeptCode(), user.getUserNo());
        if (memberCodes.isEmpty()) {
            return PageResult.build(page, size, 0L, Collections.emptyList());
        }
        LambdaQueryWrapper<ClientProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ClientProfile::getOwnerStaffCode, memberCodes);
        wrapper.ne(ClientProfile::getStatus, CLIENT_STATUS_DISABLED);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ClientProfile::getEnterpriseName, keyword.trim());
        }
        wrapper.orderByDesc(ClientProfile::getUpdatedAt);
        Page<ClientProfile> result = clientProfileMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), clientSummaries(result.getRecords()));
    }

    /**
     * 管理者回收客户进公海（15-规则 §32/§33/§34）。
     *
     * <p><b>归属落点：</b>部门经理回收本团队客户 → <b>团队公海</b>（带本部门编码）；
     * 老板 / 运营 / 超级管理员 → <b>公司公海</b>。均覆盖冷却期，不删除客户档案。
     *
     * <p><b>范围校验：</b>部门经理仅可回收本团队顾问的客户，跨团队由
     * {@code ClientAllocationService#manualRecycle} 抛 FORBIDDEN。
     *
     * @param clientCode 客户编码
     * @param user       操作人（角色由 Controller 的 requireApprover 守卫）
     * @return { clientCode, recycled=true, fromOwnerStaffCode }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recycle(String clientCode, LoanUser user) {
        return clientAllocationService.manualRecycle(clientCode, user);
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 客户档案 → 列表摘要（字段口径与渠道侧 ChannelDataScopeService#clientSummaries 对齐）。
     *
     * @param clients 客户档案
     * @return 摘要行；归属人为空表示公海未分配
     */
    private List<Map<String, Object>> clientSummaries(List<ClientProfile> clients) {
        if (clients == null || clients.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ownerCodes = clients.stream().map(ClientProfile::getOwnerStaffCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> ownerNames = ownerCodes.isEmpty()
                ? Collections.emptyMap() : businessNameService.staffNames(ownerCodes);
        return clients.stream().map(client -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clientCode", client.getClientCode());
            row.put("enterpriseName", client.getEnterpriseName());
            row.put("contactName", client.getContactName());
            row.put("contactPhone", DesensitizeUtils.phone(decrypt(client.getPhone())));
            row.put("ownerStaffName", ownerNames.get(client.getOwnerStaffCode()));
            row.put("assigned", StringUtils.hasText(client.getOwnerStaffCode()));
            row.put("seaLevel", client.getSeaLevel());
            row.put("status", client.getStatus());
            row.put("lastFollowedAt", client.getLastFollowedAt());
            return row;
        }).collect(Collectors.toList());
    }

    /** 客户手机号密文解密（与渠道侧保持同一实现）。 */
    private String decrypt(String value) {
        return StringUtils.hasText(value) ? com.loan.infrastructure.security.AesUtils.decrypt(value) : null;
    }

    /**
     * 本部门在职员工工号（排除本人）。
     *
     * @param deptCode        部门编码；为空返回空列表（fail-closed，不放大到全司）
     * @param excludeStaffNo  需排除的工号（本人）
     * @return 工号列表（去重保序）
     */
    private List<String> staffCodesOfDept(String deptCode, String excludeStaffNo) {
        if (!StringUtils.hasText(deptCode)) {
            return Collections.emptyList();
        }
        List<Staff> members = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getDeptCode, deptCode)
                .eq(Staff::getStatus, STAFF_STATUS_ACTIVE));
        return members.stream()
                .map(Staff::getStaffCode)
                .filter(StringUtils::hasText)
                .filter(code -> !code.equals(excludeStaffNo))
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, Object> result(String clientCode, String ownerStaffCode, String action) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientCode", clientCode);
        m.put("ownerStaffCode", ownerStaffCode);
        m.put("action", action);
        return m;
    }

    private Map<String, Object> claimResult(String result, String approvalNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", result);
        m.put("approvalNo", approvalNo);
        return m;
    }

    /** 记录归属流转流水（t_lead_allocation_record） */
    private void recordAllocation(String clientCode, String fromStaff, String toStaff,
                                  String actionType, String remark) {
        LeadAllocationRecord record = new LeadAllocationRecord();
        record.setLeadNo(clientCode);
        record.setActionType(actionType);
        record.setFromStaffCode(fromStaff);
        record.setToStaffCode(toStaff);
        record.setOperator(toStaff);
        record.setRemark(remark);
        record.setCreatedAt(LocalDateTime.now());
        allocationRecordMapper.insert(record);
    }

    private String strValue(Object v, String def) {
        return v == null || !StringUtils.hasText(String.valueOf(v)) ? def : String.valueOf(v).trim();
    }

    /** 手机号掩码：138****0001 */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 员工工号 → 姓名（接 t_staff；查不到返回工号兜底）。
     */
    private String staffName(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            return null;
        }
        List<Staff> list = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode).last("limit 1"));
        if (list.isEmpty() || !StringUtils.hasText(list.get(0).getStaffName())) {
            return staffCode;
        }
        return list.get(0).getStaffName();
    }

    /** SHA-256 摘要（与 AuthService / SmsService 保持一致的实现） */
    private String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "摘要计算失败");
        }
    }
}
