package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.approval.entity.ClientAllocationApproval;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.service.ClientAllocationService;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.lead.entity.LeadAllocationRecord;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final ClientProfileMapper clientProfileMapper;
    private final LeadAllocationRecordMapper allocationRecordMapper;
    private final StaffMapper staffMapper;
    private final ClientAllocationService clientAllocationService;

    /**
     * 客户查重（C10）：按企业名称（模糊）/ 手机号（精确）/ 统一社会信用代码（精确）任一命中。
     *
     * @param keyword 关键词（调用方保证已 trim 且长度 ≥2）
     * @return 命中客户（含 hasOwner 供前端分流），未命中返回 null
     */
    public Map<String, Object> search(String keyword) {
        String kw = keyword.trim();
        LambdaQueryWrapper<ClientProfile> wrapper = new LambdaQueryWrapper<>();
        // 18 位字母数字 → 按统一社会信用代码精确匹配（摘要）
        if (kw.matches("[0-9A-Za-z]{18}")) {
            wrapper.eq(ClientProfile::getCreditCodeHash, sha256(kw));
        } else if (kw.matches("\\d{6,}")) {
            // 6 位以上纯数字 → 按手机号精确匹配（摘要）
            wrapper.eq(ClientProfile::getPhoneHash, sha256(kw));
        } else {
            // 其余按企业名称模糊匹配
            wrapper.like(ClientProfile::getEnterpriseName, kw);
        }
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
        client.setStatus("NORMAL");
        client.setSource("MINI_STAFF_CREATE");
        // 引荐人、录入人与服务顾问是三类关系。新档案先进入未分配客户池，
        // 顾问认领走审批；管理角色通过管理端选择目标归属人后直接分配。
        client.setOwnerStaffCode(null);
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

        return result(client.getClientCode(), null, "CREATED_UNASSIGNED");
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

    /* ==================== 私有方法 ==================== */

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
