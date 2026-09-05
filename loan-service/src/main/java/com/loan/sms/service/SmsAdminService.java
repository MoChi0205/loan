package com.loan.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.common.util.PageOrder;
import com.loan.exception.BusinessException;
import com.loan.sms.entity.SmsRecord;
import com.loan.sms.entity.SmsTemplate;
import com.loan.sms.mapper.SmsRecordMapper;
import com.loan.sms.mapper.SmsTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 短信中心管理服务：模板管理 / 发送记录 / 手动发送（模拟通道）。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsAdminService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<SmsTemplate, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", SmsTemplate::getCreatedAt);
        ORDER_FIELDS.put("updatedAt", SmsTemplate::getUpdatedAt);
    }

    /** 发送记录允许排序字段（白名单） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<SmsRecord, ?>> RECORD_ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        RECORD_ORDER_FIELDS.put("createdAt", SmsRecord::getCreatedAt);
        RECORD_ORDER_FIELDS.put("sendTime", SmsRecord::getSendTime);
    }

    private final SmsTemplateMapper smsTemplateMapper;
    private final SmsRecordMapper smsRecordMapper;

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 模板分页。
     */
    public PageResult<Map<String, Object>> templatePage(String keyword, int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<SmsTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SmsTemplate::getTemplateCode, kw)
                    .or().like(SmsTemplate::getTemplateName, kw));
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, SmsTemplate::getCreatedAt);
        Page<SmsTemplate> result = smsTemplateMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("templateCode", t.getTemplateCode());
            m.put("templateName", t.getTemplateName());
            m.put("content", t.getContent());
            m.put("signName", t.getSignName());
            m.put("smsType", t.getSmsType());
            m.put("providerTemplateId", t.getProviderTemplateId());
            m.put("freqStrategy", t.getFreqStrategy());
            m.put("unsubscribeRequired", t.getUnsubscribeRequired());
            m.put("enabled", t.getEnabled());
            m.put("updatedAt", t.getUpdatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 模板全部（下拉用）。
     */
    public List<Map<String, Object>> templateList() {
        return smsTemplateMapper.selectList(new LambdaQueryWrapper<SmsTemplate>()
                        .orderByDesc(SmsTemplate::getCreatedAt)).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("templateCode", t.getTemplateCode());
            m.put("templateName", t.getTemplateName());
            m.put("content", t.getContent());
            m.put("smsType", t.getSmsType());
            m.put("enabled", t.getEnabled());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 新增 / 编辑模板（templateCode 存在则编辑）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveTemplate(SmsTemplate req, String operator) {
        if (!StringUtils.hasText(req.getTemplateCode())
                || !StringUtils.hasText(req.getTemplateName())
                || !StringUtils.hasText(req.getContent())
                || !StringUtils.hasText(req.getSignName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板编码/名称/内容/签名必填");
        }
        SmsTemplate exist = smsTemplateMapper.selectOne(new LambdaQueryWrapper<SmsTemplate>()
                .eq(SmsTemplate::getTemplateCode, req.getTemplateCode()));
        if (exist == null) {
            req.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
            req.setUnsubscribeRequired(req.getUnsubscribeRequired() == null ? 0 : req.getUnsubscribeRequired());
            req.setCreatedBy(operator);
            req.setUpdatedBy(operator);
            smsTemplateMapper.insert(req);
        } else {
            exist.setTemplateName(req.getTemplateName());
            exist.setContent(req.getContent());
            exist.setSignName(req.getSignName());
            exist.setSmsType(req.getSmsType());
            exist.setProviderTemplateId(req.getProviderTemplateId());
            exist.setFreqStrategy(req.getFreqStrategy());
            exist.setUpdatedBy(operator);
            smsTemplateMapper.updateById(exist);
        }
    }

    /**
     * 启停模板。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleTemplate(String templateCode, boolean enabled, String operator) {
        SmsTemplate exist = smsTemplateMapper.selectOne(new LambdaQueryWrapper<SmsTemplate>()
                .eq(SmsTemplate::getTemplateCode, templateCode));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模板不存在");
        }
        exist.setEnabled(enabled ? 1 : 0);
        exist.setUpdatedBy(operator);
        smsTemplateMapper.updateById(exist);
    }

    // ============================================================
    // 发送记录 / 手动发送
    // ============================================================

    /**
     * 发送记录分页（手机号按 hash 查询）。
     */
    public PageResult<Map<String, Object>> recordPage(String smsType, String status, String phone, int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<SmsRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(smsType)) {
            wrapper.eq(SmsRecord::getSmsType, smsType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SmsRecord::getStatus, status);
        }
        if (StringUtils.hasText(phone)) {
            wrapper.eq(SmsRecord::getPhoneHash, sha256(phone.trim()));
        }
        PageOrder.apply(wrapper, orderBy, orderDir, RECORD_ORDER_FIELDS, SmsRecord::getCreatedAt);
        Page<SmsRecord> result = smsRecordMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("phone", com.loan.infrastructure.security.AesUtils.decrypt(r.getPhone()));
            m.put("smsType", r.getSmsType());
            m.put("templateCode", r.getTemplateCode());
            m.put("content", r.getContent());
            m.put("channelCode", r.getChannelCode());
            m.put("status", r.getStatus());
            m.put("retryCount", r.getRetryCount());
            m.put("operator", r.getOperator());
            m.put("sendTime", r.getSendTime());
            m.put("createdAt", r.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 手动发送短信（按模板编码，模拟通道落记录）。
     *
     * @param phone        手机号
     * @param templateCode 模板编码
     * @param content      内容（可选；缺省用模板内容）
     * @param operator     操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void sendManual(String phone, String templateCode, String content, String operator) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(templateCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手机号与模板必填");
        }
        SmsTemplate template = smsTemplateMapper.selectOne(new LambdaQueryWrapper<SmsTemplate>()
                .eq(SmsTemplate::getTemplateCode, templateCode));
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模板不存在");
        }
        SmsRecord record = new SmsRecord();
        record.setPhone(phone);
        record.setPhoneHash(sha256(phone));
        record.setSmsType(template.getSmsType());
        record.setTemplateCode(templateCode);
        record.setContent(StringUtils.hasText(content) ? content : template.getContent());
        record.setChannelCode("MOCK");
        record.setStatus("SUCCESS");
        record.setRetryCount(0);
        record.setOperator(operator);
        record.setSendTime(LocalDateTime.now());
        record.setCreatedBy(operator);
        smsRecordMapper.insert(record);
        log.info("短信已发送（模拟通道） phone={} template={}", phone, templateCode);
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
