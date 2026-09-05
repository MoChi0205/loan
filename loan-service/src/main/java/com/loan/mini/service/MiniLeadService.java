package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.infrastructure.security.HashUtils;
import com.loan.lead.entity.Lead;
import com.loan.lead.entity.LeadEntExt;
import com.loan.lead.mapper.LeadEntExtMapper;
import com.loan.lead.mapper.LeadMapper;
import com.loan.lead.service.LeadService;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小程序端线索录入：渠道/客户/员工提交融资需求 → 线索（CHANNEL 终审通过后、VIP 直接进入公海）。
 *
 * <p>敏感字段（phone/creditCode）AES 加密 + SHA-256 哈希落库；查重走哈希比对（不以明文）。
 * 渠道录入不归属渠道（owner_staff_id = NULL），待公司终审通过后才进入公海，由员工后续认领。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniLeadService {

    private final LeadService leadService;
    private final LeadMapper leadMapper;
    private final LeadEntExtMapper leadEntExtMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 提交融资需求（渠道/客户/员工通用；来源按用户类型 + 角色派生）。
     *
     * @param body 提交内容（contactName/phone/leadType/remark/entName/creditCode/industry/
     *             foundYears/annualTaxAmount/annualInvoiceAmount）
     * @param user 当前登录用户
     * @return {leadNo, duplicated}；重复录入时 duplicated=true、leadNo=null
     */
    public Map<String, Object> submit(Map<String, String> body, LoanUser user) {
        if (body == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "提交内容为空");
        }
        String contactName = body.get("contactName");
        String phone = body.get("phone");
        if (!StringUtils.hasText(contactName) || !StringUtils.hasText(phone)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "联系人与手机号必填");
        }

        Lead lead = new Lead();
        lead.setContactName(contactName);
        lead.setPhone(phone);
        String leadType = body.get("leadType");
        lead.setLeadType(StringUtils.hasText(leadType) ? leadType.trim().toUpperCase() : "ENTERPRISE");
        // 来源按用户类型 + 角色派生（修复旧版写死 MINI 不在字典的缺陷，设计 §2.3a）
        lead.setSource(deriveSource(user));
        // ext_json 记录录入人业务编码（奖励归因预留）+ 备注
        Map<String, Object> ext = new LinkedHashMap<>();
        String remark = body.get("remark");
        ext.put("remark", remark == null ? "" : remark);
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            ext.put("recorderChannelNo", user.getUserNo());
        } else {
            ext.put("recorderNo", user.getUserNo());
        }
        lead.setExtJson(toJson(ext));
        lead.setFollowStatus(LoanUser.TYPE_CHANNEL.equals(user.getUserType()) ? "PENDING_APPROVAL" : "NEW");

        // 唯一索引冲突（uk_phone_hash_type）兜底：返回 duplicated=true，不泄露归属人（沙箱隔离）
        try {
            String leadNo = leadService.create(lead, recorderCodeOf(user), user.getName());
            saveEntExt(lead.getId(), body, user.getName());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("leadNo", leadNo);
            result.put("duplicated", false);
            return result;
        } catch (DuplicateKeyException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("leadNo", null);
            result.put("duplicated", true);
            return result;
        }
    }

    /**
     * 我录入的线索（仅当前用户本人，沙箱脱敏）。
     *
     * @param page 页码
     * @param size 每页大小
     * @param user 当前登录用户
     * @return 分页（leadNo/contactName脱敏/entName/phone掩码/followStatus/createdAt）
     */
    public PageResult<Map<String, Object>> myLeads(int page, int size, LoanUser user) {
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<>();
        if (LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            // 渠道账号必须按稳定业务编号隔离；createdBy 姓名可能重名，只保留为历史数据兼容。
            wrapper.eq(Lead::getSource, "CHANNEL")
                    .and(w -> w.eq(Lead::getRecorderStaffCode, user.getUserNo())
                            .or().apply("JSON_UNQUOTE(JSON_EXTRACT(ext_json, '$.recorderChannelNo')) = {0}",
                                    user.getUserNo()));
        } else if (StringUtils.hasText(user.getUserNo())) {
            wrapper.eq(Lead::getRecorderStaffCode, user.getUserNo());
        } else {
            wrapper.eq(Lead::getCreatedBy, user.getName());
        }
        wrapper.orderByDesc(Lead::getCreatedAt);
        Page<Lead> p = leadMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, String> enterpriseNames = enterpriseNamesOf(p.getRecords());

        List<Map<String, Object>> records = new ArrayList<>(p.getRecords().size());
        for (Lead l : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("leadNo", l.getLeadNo());
            m.put("contactName", DesensitizeUtils.name(l.getContactName()));
            m.put("entName", enterpriseNames.get(l.getId()));
            // phone 密文需手动解密（AesTypeHandler 仅写入生效）后掩码
            String plain = AesUtils.decrypt(l.getPhone());
            m.put("phone", plain == null ? "" : DesensitizeUtils.phone(plain));
            m.put("followStatus", l.getFollowStatus());
            m.put("clientCode", l.getClientProfileCode());
            m.put("createdAt", l.getCreatedAt() == null ? null : l.getCreatedAt().toLocalDate().format(DATE_FMT));
            records.add(m);
        }
        return PageResult.build(page, size, p.getTotal(), records);
    }

    /**
     * 来源派生（设计 §2.3a 表）：
     * CHANNEL→CHANNEL / CUSTOMER→VIP / STAFF(BOSS)→BOSS / STAFF(其他)→ADVISER。
     */
    private String deriveSource(LoanUser user) {
        String userType = user.getUserType();
        if (LoanUser.TYPE_CHANNEL.equals(userType)) {
            return "CHANNEL";
        }
        if (LoanUser.TYPE_CUSTOMER.equals(userType)) {
            return "VIP";
        }
        if (LoanUser.TYPE_STAFF.equals(userType)) {
            String role = user.getRoleCode() == null ? "" : user.getRoleCode().toUpperCase();
            if ("BOSS".equals(role)) {
                return "BOSS";
            }
            return "ADVISER";
        }
        return "VIP";
    }

    /** 录入主体业务编号。归属是否进公海由 LeadService 根据来源独立判定。 */
    private String recorderCodeOf(LoanUser user) {
        return user == null ? null : user.getUserNo();
    }

    /**
     * 写企业扩展表（entName/creditCode/industry 等；creditCode AES + 哈希）。
     * 无任何企业字段时跳过（个人客群只读主表）。
     */
    private void saveEntExt(Long leadId, Map<String, String> body, String recorderName) {
        if (leadId == null) {
            return;
        }
        String entName = body.get("entName");
        String creditCode = body.get("creditCode");
        String industry = body.get("industry");
        String foundYears = body.get("foundYears");
        String annualTaxAmount = body.get("annualTaxAmount");
        String annualInvoiceAmount = body.get("annualInvoiceAmount");
        boolean hasEntField = StringUtils.hasText(entName) || StringUtils.hasText(creditCode)
                || StringUtils.hasText(industry) || StringUtils.hasText(foundYears)
                || StringUtils.hasText(annualTaxAmount) || StringUtils.hasText(annualInvoiceAmount);
        if (!hasEntField) {
            return;
        }
        LeadEntExt ext = new LeadEntExt();
        ext.setLeadId(leadId);
        ext.setCompanyName(entName);
        if (StringUtils.hasText(creditCode)) {
            ext.setCreditCode(creditCode.trim());
            ext.setCreditCodeHash(HashUtils.sha256Hex(creditCode.trim()));
        }
        ext.setIndustry(industry);
        if (StringUtils.hasText(foundYears)) {
            try {
                ext.setFoundYears(Integer.valueOf(foundYears.trim()));
            } catch (NumberFormatException ignore) {
                // 非法数字忽略，不阻断主流程
            }
        }
        ext.setAnnualTaxAmount(toDecimal(annualTaxAmount));
        ext.setAnnualInvoiceAmount(toDecimal(annualInvoiceAmount));
        ext.setCreatedBy(recorderName);
        ext.setUpdatedBy(recorderName);
        leadEntExtMapper.insert(ext);
    }

    /** 当前页一次批量装配企业名称，避免“我的线索”逐行查询形成 N+1。 */
    private Map<Long, String> enterpriseNamesOf(List<Lead> leads) {
        if (leads == null || leads.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> leadIds = leads.stream().map(Lead::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (leadIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LeadEntExt> extensions = leadEntExtMapper.selectList(
                new LambdaQueryWrapper<LeadEntExt>().in(LeadEntExt::getLeadId, leadIds));
        Map<Long, String> names = new HashMap<>();
        for (LeadEntExt extension : extensions) {
            if (extension.getLeadId() != null) {
                names.put(extension.getLeadId(), extension.getCompanyName());
            }
        }
        return names;
    }

    /**
     * 安全转 BigDecimal（空/非法返回 null）。
     */
    private BigDecimal toDecimal(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 对象序列化为 JSON 字符串（异常兜底返回 {}）。
     */
    private String toJson(Object o) {
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
