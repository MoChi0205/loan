package com.loan.lead.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.allocation.service.ClaimQuotaService;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.util.PageOrder;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.lead.entity.Lead;
import com.loan.lead.entity.LeadAllocationRecord;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.lead.mapper.LeadMapper;
import com.loan.lead.entity.LeadRecycleConfig;
import com.loan.lead.mapper.LeadRecycleConfigMapper;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import com.loan.org.entity.Department;
import com.loan.org.mapper.DepartmentMapper;
import com.loan.sensitive.service.SensitiveViewService;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.Arrays;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 线索服务：新增（谁录入归谁，渠道终审通过/VIP 进公海）/ 分页（我的/公海）/ 认领 / 指派。
 *
 * <p>回收规则：按 t_lead_recycle_config 逐状态配置回收天数（阶段一默认 NEW 30 天），
 * 超过 lastFollowedAt 无跟进自动回收进公海（见 {@link #recycleOverdue()}，供定时任务）。
 * 回收前 3 天向归属人发站内预警通知（参考 tse CustomerAutoRecycleNotifyService）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int MAX_BATCH_SIZE = 100;

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<Lead, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", Lead::getCreatedAt);
        ORDER_FIELDS.put("updatedAt", Lead::getUpdatedAt);
        ORDER_FIELDS.put("lastFollowedAt", Lead::getLastFollowedAt);
    }

    private final LeadMapper leadMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final LeadAllocationRecordMapper allocationRecordMapper;
    private final NotificationService notificationService;
    private final SensitiveViewService sensitiveViewService;
    private final ClaimQuotaService claimQuotaService;
    private final LeadRecycleConfigMapper leadRecycleConfigMapper;
    private final com.loan.staff.mapper.StaffMapper staffMapper;
    private final DepartmentMapper departmentMapper;

    private static final List<String> FOLLOW_STATUSES = Arrays.asList(
            "NEW", "INTENTION", "POTENTIAL", "VISITED", "NO_ANSWER", "NO_NEED");

    /**
     * 新增线索（公司员工谁录入归谁；渠道/客户录入不建立员工归属）。
     *
     * @param lead          线索（contactName/phone/leadType 必填）
     * @param recorderCode  录入人工号（业务编码）
     * @param recorderName  录入人姓名
     * @return 线索编号（业务唯一ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(Lead lead, String recorderCode, String recorderName) {
        if (!StringUtils.hasText(lead.getContactName()) || !StringUtils.hasText(lead.getPhone())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "联系人与手机号必填");
        }
        lead.setLeadNo(genLeadNo());
        lead.setPhoneHash(sha256(lead.getPhone()));
        lead.setRecorderStaffCode(recorderCode);
        lead.setFollowStatus(lead.getFollowStatus() == null ? "NEW" : lead.getFollowStatus());
        // 员工录入一律直接归属本人。渠道/客户不是公司员工，不建立服务归属。
        boolean toPool = !StringUtils.hasText(recorderCode)
                || "CHANNEL".equalsIgnoreCase(lead.getSource())
                || "VIP".equalsIgnoreCase(lead.getSource())
                || StringUtils.hasText(lead.getSeaLevel()); // 导入/录入进池：已指定公海层级则进公海
        lead.setOwnerStaffCode(toPool ? null : recorderCode);
        lead.setCreatedBy(recorderName);
        // 线索标签属于业务数据：主表暂不扩列，统一写入 ext_json，保证渠道录入/员工录入均可追溯。
        if (StringUtils.hasText(lead.getCustomerTag())) {
            try {
                Map<String, Object> ext = StringUtils.hasText(lead.getExtJson())
                        ? JSON.readValue(lead.getExtJson(), Map.class) : new LinkedHashMap<>();
                ext.put("customerTag", normalizeCustomerTag(lead.getCustomerTag()));
                lead.setExtJson(JSON.writeValueAsString(ext));
            } catch (Exception ignored) {
                Map<String, Object> ext = new LinkedHashMap<>();
                ext.put("customerTag", normalizeCustomerTag(lead.getCustomerTag()));
                lead.setExtJson(toJson(ext));
            }
        }
        leadMapper.insert(lead);

        // 公司员工录入的线索同时进入“我的客户”。渠道/VIP 仍保持原审批、公海链路不变。
        if (!toPool) {
            ensureClientProfile(lead, recorderCode, recorderName);
        }

        allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "MANUAL", null, lead.getOwnerStaffCode(),
                recorderName, "录入线索"));
        return lead.getLeadNo();
    }

    /**
     * 线索 → 客户建档：按手机号+客群查找客户档案，不存在则新建（归属 owner），
     * 已存在且未分配则认领归属；并回填 lead.client_profile_code、同步 pool_code。
     */
    private void ensureClientProfile(Lead lead, String ownerCode, String ownerName) {
        if (!StringUtils.hasText(lead.getPhoneHash())) {
            return;
        }
        ClientProfile profile = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getPhoneHash, lead.getPhoneHash())
                .eq(ClientProfile::getCustomerGroup, lead.getLeadType())
                .last("limit 1"));
        if (profile == null) {
            profile = new ClientProfile();
            profile.setClientCode(BizIdGenerator.generate("client"));
            profile.setCustomerGroup(lead.getLeadType());
            profile.setContactName(lead.getContactName());
            profile.setPhone(lead.getPhone());
            profile.setPhonePlain(lead.getPhone());
            profile.setPhoneHash(lead.getPhoneHash());
            profile.setOwnerStaffCode(ownerCode);
            profile.setSource("LEAD");
            profile.setLeadNo(lead.getLeadNo());
            profile.setPoolCode(lead.getPoolCode());
            profile.setCustomerTag(normalizeCustomerTag(lead.getCustomerTag()));
            profile.setStatus("ACTIVE");
            profile.setInvitedFlag(0);
            profile.setWecomAdded(0);
            profile.setCreatedBy(ownerName);
            clientProfileMapper.insert(profile);
        } else if (!StringUtils.hasText(profile.getOwnerStaffCode())) {
            clientProfileMapper.assignOwnerIfUnassigned(profile.getClientCode(), ownerCode,
                    ownerName, LocalDateTime.now());
            // 已有客户档案但未绑定池时，补池标记（认领的导入线索归入对应资源池）。
            if (!StringUtils.hasText(profile.getPoolCode()) && StringUtils.hasText(lead.getPoolCode())) {
                clientProfileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ClientProfile>()
                        .eq(ClientProfile::getClientCode, profile.getClientCode())
                        .set(ClientProfile::getPoolCode, lead.getPoolCode()));
            }
        }
        lead.setClientProfileCode(profile.getClientCode());
        leadMapper.bindClientProfile(lead.getLeadNo(), profile.getClientCode(), ownerName);
    }

    private String normalizeCustomerTag(String tag) {
        if (!StringUtils.hasText(tag)) return "NEW";
        String value = tag.trim().toUpperCase();
        return java.util.Arrays.asList("NEW", "INTENTION", "POTENTIAL", "NO_ANSWER", "NO_NEED").contains(value) ? value : "NEW";
    }

    private String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    /**
     * 分页查询线索（我的线索 / 公海）。
     *
     * <p>手机号出参：豁免角色（老板/主管）直接返回明文；受限角色（顾问）列表页统一脱敏，
     * 明文需经敏感查看授权流程（apply-view）单独获取。
     *
     * @param selfStaffCode  本人工号；非空 = 当前归属我的线索，null = 线索公海
     * @param leadType       客群（可选）
     * @param followStatus   跟进状态（可选）
     * @param keyword        关键字（可选）
     * @param page           页码
     * @param size           每页大小
     * @param roleCode       当前角色（决定列表页手机号是否脱敏）
     * @param userNo         当前工号
     * @return 线索分页
     */
    public PageResult<Lead> page(String selfStaffCode, String leadType, String followStatus,
                                 String keyword, int page, int size, String roleCode, String userNo,
                                 String poolCode, String seaScope, String seaDeptCode,
                                 String orderBy, String orderDir,
                                 LocalDate createdAtStart, LocalDate createdAtEnd) {
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<>();
        // 按资源池隔离：导入/认领只作用于指定池的线索。
        if (StringUtils.hasText(poolCode)) {
            wrapper.eq(Lead::getPoolCode, poolCode.trim());
        }
        if (selfStaffCode != null) {
            wrapper.eq(Lead::getOwnerStaffCode, selfStaffCode);
        } else {
            wrapper.isNull(Lead::getOwnerStaffCode);
            if ("TEAM".equalsIgnoreCase(seaScope)) {
                // 团队线索公海：仅本部门、团队池线索（部门经理查看/认领）。
                wrapper.eq(Lead::getSeaLevel, "TEAM");
                if (StringUtils.hasText(seaDeptCode)) {
                    wrapper.eq(Lead::getSeaDeptCode, seaDeptCode.trim());
                }
            } else {
                // 公司线索公海：管理角色看全部公海（含团队池）；普通员工只看非团队池。
                boolean fullRole = roleCode != null && java.util.Arrays.asList("BOSS", "SUPER_ADMIN", "SUPER", "OPERATOR")
                        .contains(roleCode.toUpperCase());
                if (!fullRole) {
                    wrapper.and(w -> w.isNull(Lead::getSeaLevel).or().ne(Lead::getSeaLevel, "TEAM"));
                }
                // 渠道线索只有终审转 NEW 后才进入公海；待审/驳回仅渠道本人可见。
                wrapper.and(w -> w.ne(Lead::getSource, "CHANNEL").or().eq(Lead::getFollowStatus, "NEW"));
            }
        }
        if (StringUtils.hasText(leadType)) {
            wrapper.eq(Lead::getLeadType, leadType);
        }
        if (StringUtils.hasText(followStatus)) {
            wrapper.eq(Lead::getFollowStatus, followStatus);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 联系人 / 线索编号模糊；11 位手机号追加 phoneHash（SHA-256）精确匹配（phone 为密文不可 LIKE）
            if (kw.matches("\\d{11}")) {
                String hash = sha256(kw);
                wrapper.and(w -> w.like(Lead::getContactName, kw).or().like(Lead::getLeadNo, kw)
                        .or().eq(Lead::getPhoneHash, hash));
            } else {
                wrapper.and(w -> w.like(Lead::getContactName, kw).or().like(Lead::getLeadNo, kw));
            }
        }
        if (createdAtStart != null) {
            wrapper.ge(Lead::getCreatedAt, createdAtStart.atStartOfDay());
        }
        if (createdAtEnd != null) {
            wrapper.lt(Lead::getCreatedAt, createdAtEnd.plusDays(1).atStartOfDay());
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, Lead::getCreatedAt);
        Page<Lead> result = leadMapper.selectPage(new Page<>(page, size), wrapper);
        // 列表出参一律脱敏；明文仅允许通过敏感查看服务的授权、限额和审计链路读取。
        result.getRecords().forEach(lead -> {
            String plain = lead.getPhone();
            lead.setPhone(DesensitizeUtils.phone(plain));
            lead.setCustomerTag(customerTagOf(lead));
        });
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /** 渠道本人录入线索分页；录入与归属两个维度不可混用。 */
    public PageResult<Lead> pageByRecorder(String recorderCode, String leadType, String followStatus,
                                           String keyword, int page, int size, String orderBy, String orderDir,
                                           LocalDate createdAtStart, LocalDate createdAtEnd) {
        if (!StringUtils.hasText(recorderCode)) {
            return PageResult.build(page, size, 0L, java.util.Collections.emptyList());
        }
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Lead::getSource, "CHANNEL")
                .and(w -> w.eq(Lead::getRecorderStaffCode, recorderCode)
                        .or().apply("JSON_UNQUOTE(JSON_EXTRACT(ext_json, '$.recorderChannelNo')) = {0}", recorderCode));
        applyLeadFilters(wrapper, leadType, followStatus, keyword);
        if (createdAtStart != null) {
            wrapper.ge(Lead::getCreatedAt, createdAtStart.atStartOfDay());
        }
        if (createdAtEnd != null) {
            wrapper.lt(Lead::getCreatedAt, createdAtEnd.plusDays(1).atStartOfDay());
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, Lead::getCreatedAt);
        Page<Lead> result = leadMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(lead -> {
            String plain = lead.getPhone();
            lead.setPhone(DesensitizeUtils.phone(plain));
            lead.setCustomerTag(customerTagOf(lead));
            lead.setRecorderStaffCode(null);
            lead.setOwnerStaffCode(null);
        });
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    private String customerTagOf(Lead lead) {
        if (lead == null) return "NEW";
        String status = lead.getFollowStatus();
        if ("INTENTION".equals(status) || "POTENTIAL".equals(status)
                || "VISITED".equals(status) || "NO_ANSWER".equals(status) || "NO_NEED".equals(status)) {
            return status;
        }
        if (StringUtils.hasText(lead.getCustomerTag())) return lead.getCustomerTag();
        if (StringUtils.hasText(lead.getExtJson())) {
            try {
                Object value = JSON.readValue(lead.getExtJson(), Map.class).get("customerTag");
                if (value != null && StringUtils.hasText(String.valueOf(value))) return String.valueOf(value);
            } catch (Exception ignored) { /* 历史脏 JSON 回退新用户 */ }
        }
        return "NEW";
    }

    /** 公司终审人查看渠道新增线索待审列表。 */
    public PageResult<Map<String, Object>> channelPendingPage(String keyword, String leadType,
                                                              java.time.LocalDate startDate, java.time.LocalDate endDate,
                                                              int page, int size) {
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<Lead>()
                .eq(Lead::getSource, "CHANNEL")
                .eq(Lead::getFollowStatus, "PENDING_APPROVAL");
        applyLeadFilters(wrapper, leadType, null, keyword);
        if (startDate != null) wrapper.ge(Lead::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) wrapper.lt(Lead::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        wrapper.orderByAsc(Lead::getCreatedAt);
        Page<Lead> result = leadMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(lead -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("leadNo", lead.getLeadNo());
            row.put("contactName", lead.getContactName());
            row.put("phone", DesensitizeUtils.phone(lead.getPhone()));
            row.put("leadType", lead.getLeadType());
            row.put("createdBy", lead.getCreatedBy());
            row.put("channelName", lead.getCreatedBy());
            row.put("followStatus", lead.getFollowStatus());
            row.put("createdAt", lead.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 渠道新增线索单级终审。相同结论重复请求幂等通过，相反结论或非待审状态拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditChannelLead(String leadNo, boolean approve, String opinion, String operator) {
        if (!StringUtils.hasText(leadNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索编号不能为空");
        }
        if (!approve && !StringUtils.hasText(opinion)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回意见必填");
        }
        Lead current = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo.trim()));
        if (current == null || !"CHANNEL".equals(current.getSource())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "渠道线索不存在");
        }
        String target = approve ? "NEW" : "REJECTED";
        if (target.equals(current.getFollowStatus())) {
            return;
        }
        if (!"PENDING_APPROVAL".equals(current.getFollowStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该线索已完成审批，不可变更结论");
        }
        int updated = leadMapper.auditChannelLead(leadNo.trim(), target, operator);
        if (updated == 0) {
            Lead latest = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo.trim()));
            if (latest != null && target.equals(latest.getFollowStatus())) {
                return;
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "审批状态已变化，请刷新后重试");
        }
        allocationRecordMapper.insert(buildRecord(leadNo.trim(), approve ? "APPROVE" : "REJECT",
                null, null, operator, approve ? "渠道线索审批通过" : "渠道线索驳回：" + opinion.trim()));
    }

    private void applyLeadFilters(LambdaQueryWrapper<Lead> wrapper, String leadType,
                                  String followStatus, String keyword) {
        if (StringUtils.hasText(leadType)) {
            wrapper.eq(Lead::getLeadType, leadType);
        }
        if (StringUtils.hasText(followStatus)) {
            wrapper.eq(Lead::getFollowStatus, followStatus);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            if (kw.matches("\\d{11}")) {
                wrapper.and(w -> w.like(Lead::getContactName, kw).or().like(Lead::getLeadNo, kw)
                        .or().eq(Lead::getPhoneHash, sha256(kw)));
            } else {
                wrapper.and(w -> w.like(Lead::getContactName, kw).or().like(Lead::getLeadNo, kw));
            }
        }
    }

    /**
     * 公海认领（冷却期内不可认领）。
     *
     * @param leadNo     线索编号（业务唯一ID）
     * @param staffCode  认领人工号（业务编码）
     * @param staffName  认领人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(String leadNo, String staffCode, String staffName) {
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        if (lead.getOwnerStaffCode() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索已被认领");
        }
        // 冷却期内原归属人不可认领
        if (lead.getAssignBlockedUntil() != null
                && lead.getAssignBlockedUntil().isAfter(LocalDateTime.now())
                && lead.getRecorderStaffCode() != null
                && lead.getRecorderStaffCode().equals(staffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回收冷却期内不可认领");
        }
        // 数据保护期：进入公海后 N 天内所有人不可认领（认领设置 LEAD 行 protect_days，0 = 不启用）
        int protectDays = claimQuotaService.protectDaysOf(ClaimQuotaService.SCOPE_LEAD, lead.getPoolCode());
        if (protectDays > 0) {
            LeadAllocationRecord lastEnter = allocationRecordMapper.selectOne(
                    new LambdaQueryWrapper<LeadAllocationRecord>()
                            .eq(LeadAllocationRecord::getLeadNo, leadNo)
                            .eq(LeadAllocationRecord::getActionType, "RECYCLE")
                            .orderByDesc(LeadAllocationRecord::getCreatedAt).last("LIMIT 1"));
            if (lastEnter != null && lastEnter.getCreatedAt() != null
                    && lastEnter.getCreatedAt().plusDays(protectDays).isAfter(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "该线索处于数据保护期内（进入公海 " + protectDays + " 天内不可认领），到期后再试");
            }
        }
        // 每日认领上限（参考 tse daily_assign_limit_per_user）：先预占额度，失败回滚
        claimQuotaService.consume(ClaimQuotaService.SCOPE_LEAD, lead.getPoolCode(), staffCode, 1);
        int updated;
        try {
            updated = leadMapper.claimIfUnowned(leadNo, staffCode, staffName);
        } catch (RuntimeException e) {
            claimQuotaService.refund(ClaimQuotaService.SCOPE_LEAD, staffCode, 1);
            throw e;
        }
        if (updated == 0) {
            claimQuotaService.refund(ClaimQuotaService.SCOPE_LEAD, staffCode, 1);
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索已被其他顾问认领，请刷新后查看");
        }
        // 认领即转化：线索公海认领后自动建档为客户（归属认领人），进入「我的客户」。
        ensureClientProfile(lead, staffCode, staffName);
        allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "CLAIM", null, staffCode, staffName, "公海认领"));
    }

    /** 本人主动释放自己的线索到公司公海，释放后 7 天内本人不可重新认领。 */
    @Transactional(rollbackFor = Exception.class)
    public void release(String leadNo, String staffCode, String staffName) {
        if (!StringUtils.hasText(leadNo) || !StringUtils.hasText(staffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索编号与当前员工不能为空");
        }
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo.trim()));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        if (!staffCode.equals(lead.getOwnerStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能释放归属自己的线索");
        }
        // 手动释放统一进「公司公海」，与客户侧手动回收、以及自动回收（recycleOverdue）口径一致。
        // 历史问题：此前按释放人部门写入 TEAM 团队池，而「公司公海」查询对普通角色排除 TEAM、
        // 且团队公海页签仅部门经理可见 → 顾问释放后在任何页签都查不到自己的线索。
        int updated = leadMapper.releaseOwned(leadNo.trim(), staffCode, staffName, LocalDateTime.now().plusDays(7),
                "COMPANY", null);
        if (updated == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索归属已变化，请刷新后重试");
        }
        allocationRecordMapper.insert(buildRecord(leadNo.trim(), "RELEASE", staffCode, null,
                staffName, "本人主动释放到公司公海"));
    }

    /**
     * 查询本人线索档案。出参只包含页面所需中文可解释字段，不暴露物理主键或人员业务编码。
     */
    public Map<String, Object> detail(String leadNo, String staffCode) {
        Lead lead = requireOwnedLead(leadNo, staffCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contactName", lead.getContactName());
        result.put("phone", DesensitizeUtils.phone(lead.getPhone()));
        result.put("leadType", lead.getLeadType());
        result.put("source", lead.getSource());
        result.put("followStatus", lead.getFollowStatus());
        result.put("createdBy", lead.getCreatedBy());
        result.put("createdAt", lead.getCreatedAt());
        result.put("lastFollowedAt", lead.getLastFollowedAt());
        result.put("hasClientProfile", StringUtils.hasText(lead.getClientProfileCode()));
        result.put("history", allocationRecordMapper.selectList(
                new LambdaQueryWrapper<LeadAllocationRecord>()
                        .eq(LeadAllocationRecord::getLeadNo, lead.getLeadNo())
                        .orderByDesc(LeadAllocationRecord::getCreatedAt)).stream()
                .map(this::safeHistoryRow).collect(Collectors.toList()));
        return result;
    }

    /**
     * 本人填写线索跟进；同步已关联客户的最后跟进时间，保持线索与客户回收依据一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> follow(String leadNo, String staffCode, String staffName,
                                      String followStatus, String content) {
        Lead lead = requireOwnedLead(leadNo, staffCode);
        String status = StringUtils.hasText(followStatus) ? followStatus.trim().toUpperCase() : "NEW";
        if (!FOLLOW_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "跟进结果不合法");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写跟进内容");
        }
        String remark = content.trim();
        if (remark.length() > 240) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "跟进内容最多 240 个字");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = StringUtils.hasText(staffName) ? staffName : staffCode;
        if (leadMapper.followOwned(lead.getLeadNo(), staffCode, status, now, operator) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "线索归属已变化，请刷新后重试");
        }
        allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "FOLLOW_UP",
                staffCode, staffCode, operator, remark));

        if (StringUtils.hasText(lead.getClientProfileCode())) {
            clientProfileMapper.update(null, new LambdaUpdateWrapper<ClientProfile>()
                    .eq(ClientProfile::getClientCode, lead.getClientProfileCode())
                    .eq(ClientProfile::getOwnerStaffCode, staffCode)
                    .set(ClientProfile::getLastFollowedAt, now)
                    .set(ClientProfile::getCustomerTag, status)
                    .set(ClientProfile::getUpdatedBy, operator)
                    .set(ClientProfile::getUpdatedAt, now));
            allocationRecordMapper.insert(buildRecord(lead.getClientProfileCode(), "FOLLOW_UP",
                    staffCode, staffCode, operator, remark));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("followStatus", status);
        result.put("followedAt", now);
        return result;
    }

    private Lead requireOwnedLead(String leadNo, String staffCode) {
        if (!StringUtils.hasText(leadNo) || !StringUtils.hasText(staffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索与当前员工不能为空");
        }
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>()
                .eq(Lead::getLeadNo, leadNo.trim()).last("limit 1"));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        if (!staffCode.equals(lead.getOwnerStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看或跟进归属自己的线索");
        }
        return lead;
    }

    private Map<String, Object> safeHistoryRow(LeadAllocationRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actionType", record.getActionType());
        row.put("operator", record.getOperator());
        row.put("remark", record.getRemark());
        row.put("createdAt", record.getCreatedAt());
        return row;
    }

    /**
     * 手动指派（主管/老板）。
     *
     * @param leadNo      线索编号（业务唯一ID）
     * @param toStaffCode 目标员工工号（业务编码）
     * @param operator    操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void assign(String leadNo, String toStaffCode, String operator) {
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        String from = lead.getOwnerStaffCode();
        lead.setOwnerStaffCode(toStaffCode);
        lead.setUpdatedBy(operator);
        leadMapper.updateById(lead);
        allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "MANUAL", from, toStaffCode, operator, "手动指派"));
    }

    /**
     * 批量认领（公海 → 我的线索）。整体事务：任一条失败则全部回滚。
     *
     * @param leadNos   线索编号列表
     * @param staffCode 认领人工号（业务编码）
     * @param staffName 认领人姓名
     * @return 成功认领条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchClaim(List<String> leadNos, String staffCode, String staffName) {
        List<String> distinct = normalizeBatch(leadNos);
        Map<String, Lead> leads = loadLeadMap(distinct);
        // 每日认领上限：整批预占，事务回滚时整批退还
        claimQuotaService.consume(ClaimQuotaService.SCOPE_LEAD, staffCode, distinct.size());
        try {
            for (String leadNo : distinct) {
                Lead lead = requireBatchLead(leads, leadNo);
                validateClaim(lead, staffCode);
                if (leadMapper.claimIfUnowned(leadNo, staffCode, staffName) == 0) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "线索已被其他顾问认领，请刷新后查看");
                }
                ensureClientProfile(lead, staffCode, staffName);
                allocationRecordMapper.insert(buildRecord(leadNo, "CLAIM", null, staffCode, staffName, "批量公海认领"));
            }
        } catch (RuntimeException e) {
            claimQuotaService.refund(ClaimQuotaService.SCOPE_LEAD, staffCode, distinct.size());
            throw e;
        }
        return distinct.size();
    }

    /**
     * 批量指派。整体事务：任一条失败则全部回滚。
     *
     * @param leadNos     线索编号列表
     * @param toStaffCode 目标员工工号（业务编码）
     * @param operator    操作人姓名
     * @return 成功指派条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchAssign(List<String> leadNos, String toStaffCode, String operator) {
        List<String> distinct = normalizeBatch(leadNos);
        if (!StringUtils.hasText(toStaffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择目标员工");
        }
        Map<String, Lead> leads = loadLeadMap(distinct);
        for (String leadNo : distinct) {
            Lead lead = requireBatchLead(leads, leadNo);
            String from = lead.getOwnerStaffCode();
            allocationRecordMapper.insert(buildRecord(leadNo, "MANUAL", from, toStaffCode, operator, "批量手动指派"));
        }
        int updated = leadMapper.assignByLeadNos(distinct, toStaffCode, operator);
        if (updated != distinct.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "部分线索状态已变化，请刷新后重试");
        }
        return updated;
    }

    /**
     * 批量删除线索（物理删除，审计留痕 DELETE）。
     * <p>t_lead 无逻辑删除字段，物理删除前会写 t_lead_allocation_record（DELETE 类型），
     * 且删除动作本身即为不可逆操作，前端需二次确认。
     *
     * @param leadNos  线索编号列表
     * @param operator 操作人姓名
     * @return 成功删除条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<String> leadNos, String operator) {
        List<String> distinct = normalizeBatch(leadNos);
        Map<String, Lead> leads = loadLeadMap(distinct);
        List<String> found = new ArrayList<>();
        for (String leadNo : distinct) {
            Lead lead = leads.get(leadNo);
            if (lead == null) continue;
            allocationRecordMapper.insert(buildRecord(leadNo, "DELETE",
                    lead.getOwnerStaffCode(), null, operator, "批量删除线索"));
            found.add(leadNo);
        }
        return found.isEmpty() ? 0 : leadMapper.deleteByLeadNos(found);
    }

    /** 批量入参去空、去重并限制单次规模，防止超长 IN 与接口滥用。 */
    private List<String> normalizeBatch(List<String> leadNos) {
        if (leadNos == null || leadNos.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要操作的线索");
        }
        LinkedHashSet<String> distinct = leadNos.stream().filter(StringUtils::hasText)
                .map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择有效线索");
        }
        if (distinct.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多操作 " + MAX_BATCH_SIZE + " 条线索");
        }
        return new ArrayList<>(distinct);
    }

    /** 一次批量查询并以业务编码映射。 */
    private Map<String, Lead> loadLeadMap(List<String> leadNos) {
        return leadMapper.selectList(new LambdaQueryWrapper<Lead>().in(Lead::getLeadNo, leadNos)).stream()
                .collect(Collectors.toMap(Lead::getLeadNo, lead -> lead, (left, right) -> left, LinkedHashMap::new));
    }

    private Lead requireBatchLead(Map<String, Lead> leads, String leadNo) {
        Lead lead = leads.get(leadNo);
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在：" + leadNo);
        }
        return lead;
    }

    private void validateClaim(Lead lead, String staffCode) {
        if (lead.getOwnerStaffCode() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索已被认领");
        }
        if (lead.getAssignBlockedUntil() != null && lead.getAssignBlockedUntil().isAfter(LocalDateTime.now())
                && staffCode != null && staffCode.equals(lead.getRecorderStaffCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回收冷却期内不可认领");
        }
    }

    /**
     * 回收过期线索进公海（供 XXL-Job 任务 leadRecycleJob 调用）。
     * 阶段一默认：NEW 状态 30 天未跟进即回收，回收后冷却 7 天。
     *
     * @return 回收数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int recycleOverdue() {
        List<LeadAllocationRecord> records = new ArrayList<>();
        // 循环外一次性加载回收配置到 Map（消除循环内逐条 selectOne 的 N+1；followStatus 唯一）
        Map<String, LeadRecycleConfig> configMap = leadRecycleConfigMapper.selectList(null).stream()
                .collect(Collectors.toMap(LeadRecycleConfig::getFollowStatus, c -> c, (a, b) -> a));
        int recycled = 0;
        // 分块回收：cursor 分页，避免一次性全量 selectList 造成大事务 / 长锁
        long cursor = 0L;
        while (true) {
            List<Lead> overdue = leadMapper.selectList(new LambdaQueryWrapper<Lead>()
                    .isNotNull(Lead::getOwnerStaffCode)
                    .gt(Lead::getId, cursor)
                    .orderByAsc(Lead::getId).last("LIMIT 500"));
            if (overdue == null || overdue.isEmpty()) break;
            for (Lead lead : overdue) {
                if (lead.getId() != null) cursor = lead.getId();
                LeadRecycleConfig cfg = configMap.get(lead.getFollowStatus());
                if (cfg == null || cfg.getRecycleEnabled() == null || cfg.getRecycleEnabled() != 1
                        || cfg.getRecycleDays() == null || cfg.getRecycleDays() <= 0
                        || "NO_NEED".equalsIgnoreCase(lead.getFollowStatus())) continue;
                LocalDateTime active = lead.getLastFollowedAt() != null ? lead.getLastFollowedAt() : lead.getCreatedAt();
                if (active != null && active.isAfter(LocalDateTime.now().minusDays(cfg.getRecycleDays()))) continue;
                String from = lead.getOwnerStaffCode();
                String ownerNo = lead.getOwnerStaffCode();
                // 回收后冷却 7 天、操作人 system（owner_staff_code 维持不变，与 updateById 原行为一致）
                records.add(buildRecord(lead.getLeadNo(), "RECYCLE", from, null, "system", "超期未跟进回收进公海"));
                leadMapper.update(null, new LambdaUpdateWrapper<Lead>()
                        .eq(Lead::getLeadNo, lead.getLeadNo()).eq(Lead::getOwnerStaffCode, from)
                        .set(Lead::getOwnerStaffCode, (String) null)
                        .set(Lead::getSeaLevel, "COMPANY").set(Lead::getSeaDeptCode, null)
                        .set(Lead::getAssignBlockedUntil, LocalDateTime.now().plusDays(cfg.getCooldownDays() == null ? 7 : cfg.getCooldownDays()))
                        .set(Lead::getUpdatedBy, "system"));
                recycled++;
                // 站内通知：线索已回收进公海（非 DB 操作，保持逐条、顺序与位置不变）
                if (ownerNo != null) {
                    notificationService.send(buildNotice(ownerNo, Notification.TYPE_LEAD_RECYCLE_WARN,
                            "线索已回收进公海",
                            "线索【" + lead.getLeadNo() + "】因超期未跟进已回收进公海，冷却期内不可认领。",
                            lead.getLeadNo()));
                }
            }
        }
        // 流转记录批量落库（一次多值 INSERT）
        if (!records.isEmpty()) {
            allocationRecordMapper.insertBatch(records);
        }
        return recycled;
    }

    /**
     * 回收预警：扫描 3 天内到期的线索，向归属人发站内预警通知（去重）。
     *
     * @return 预警发送数
     */
    @Transactional(rollbackFor = Exception.class)
    public int warnRecycle() {
        // 循环外一次性加载回收配置到 Map（消除循环内逐条 selectOne 的 N+1）
        Map<String, LeadRecycleConfig> configMap = leadRecycleConfigMapper.selectList(null).stream()
                .collect(Collectors.toMap(LeadRecycleConfig::getFollowStatus, c -> c, (a, b) -> a));
        int sent = 0;
        // 分块扫描：cursor 分页，避免一次性全量 selectList
        long cursor = 0L;
        while (true) {
            List<Lead> warns = leadMapper.selectList(new LambdaQueryWrapper<Lead>()
                    .isNotNull(Lead::getOwnerStaffCode)
                    .gt(Lead::getId, cursor)
                    .orderByAsc(Lead::getId).last("LIMIT 500"));
            if (warns == null || warns.isEmpty()) break;
            // 批量加载归属人 + 部门，消除循环内逐条 selectOne 的 N+1，供经理预警
            java.util.Set<String> ownerCodes = warns.stream().map(Lead::getOwnerStaffCode)
                    .filter(StringUtils::hasText).collect(Collectors.toSet());
            Map<String, com.loan.staff.entity.Staff> staffMap = ownerCodes.isEmpty() ? Collections.emptyMap()
                    : staffMapper.selectList(new LambdaQueryWrapper<com.loan.staff.entity.Staff>()
                            .in(com.loan.staff.entity.Staff::getStaffCode, ownerCodes)).stream()
                            .collect(Collectors.toMap(com.loan.staff.entity.Staff::getStaffCode, s -> s, (a, b) -> a));
            java.util.Set<String> deptCodes = staffMap.values().stream()
                    .map(com.loan.staff.entity.Staff::getDeptCode).filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            Map<String, Department> deptMap = deptCodes.isEmpty() ? Collections.emptyMap()
                    : departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                            .in(Department::getDeptCode, deptCodes)).stream()
                            .collect(Collectors.toMap(Department::getDeptCode, d -> d, (a, b) -> a));
            for (Lead lead : warns) {
                if (lead.getId() != null) cursor = lead.getId();
                String ownerNo = lead.getOwnerStaffCode();
                if (ownerNo == null) {
                    continue;
                }
                LeadRecycleConfig cfg = configMap.get(lead.getFollowStatus());
                if (cfg == null || cfg.getRecycleEnabled() == null || cfg.getRecycleEnabled() != 1
                        || cfg.getRecycleDays() == null || cfg.getRecycleDays() <= 0) continue;
                int warnDays = cfg.getWarnDays() == null ? 3 : cfg.getWarnDays();
                LocalDateTime active = lead.getLastFollowedAt() != null ? lead.getLastFollowedAt() : lead.getCreatedAt();
                if (active != null) {
                    LocalDateTime due = active.plusDays(cfg.getRecycleDays());
                    if (due.isBefore(LocalDateTime.now()) || due.isAfter(LocalDateTime.now().plusDays(warnDays))) continue;
                }
                // 去重：同类型 + 同关联线索只发一次
                if (notificationService.existsByTypeAndRelatedId(Notification.TYPE_LEAD_RECYCLE_WARN, lead.getLeadNo())) {
                    continue;
                }
                notificationService.send(buildNotice(ownerNo, Notification.TYPE_LEAD_RECYCLE_WARN,
                        "线索即将回收预警",
                        "线索【" + lead.getLeadNo() + "】将在 " + warnDays + " 天内因超期未跟进被回收进公海，请及时跟进。",
                        lead.getLeadNo()));
                sent++;
                // 经理预警：通知归属人部门负责人（团队维度，独立去重，幂等防重复）
                com.loan.staff.entity.Staff ownerStaff = staffMap.get(ownerNo);
                Department dept = ownerStaff == null ? null : deptMap.get(ownerStaff.getDeptCode());
                String managerNo = dept == null ? null : dept.getLeaderStaffCode();
                if (StringUtils.hasText(managerNo) && !managerNo.equals(ownerNo)) {
                    notificationService.sendOnce(buildNotice(managerNo, Notification.TYPE_LEAD_RECYCLE_MANAGER_WARN,
                            "团队线索即将回收预警",
                            "您的团队线索【" + lead.getLeadNo() + "】将在 " + warnDays
                                    + " 天内因超期未跟进被回收进公海，请关注。",
                            lead.getLeadNo()));
                }
            }
        }
        return sent;
    }

    /**
     * 构建流转记录（线索/归属人均为业务编码）。
     */
    private LeadAllocationRecord buildRecord(String leadNo, String actionType, String fromCode, String toCode,
                                             String operator, String remark) {
        LeadAllocationRecord r = new LeadAllocationRecord();
        r.setLeadNo(leadNo);
        r.setActionType(actionType);
        r.setFromStaffCode(fromCode);
        r.setToStaffCode(toCode);
        r.setOperator(operator);
        r.setRemark(remark);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    /**
     * 构建通知请求。
     */
    private NotificationReq buildNotice(String userNo, String type, String title, String content, String relatedId) {
        NotificationReq req = new NotificationReq();
        req.setUserNo(userNo);
        req.setType(type);
        req.setTitle(title);
        req.setContent(content);
        req.setRelatedId(relatedId);
        return req;
    }

    /** 生成线索业务 ID（业务前缀 lead + 32 位随机，全表唯一）。 */
    private String genLeadNo() {
        return BizIdGenerator.generate("lead");
    }

    /** 手机号 SHA-256 哈希。 */
    private String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }
}
