package com.loan.submission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.exception.BusinessException;
import com.loan.submission.entity.ClientBusinessFact;
import com.loan.submission.entity.ClientSubmission;
import com.loan.submission.mapper.ClientBusinessFactMapper;
import com.loan.submission.mapper.ClientSubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户资料提交服务：幂等落 t_client_submission（client_submit_id 防重）+ 拆 facts 落 t_client_business_fact。
 *
 * <p>facts 的 key 即 {@code t_rule.field_code}，与匹配引擎 {@code AdmissionContext.fieldValues} 天然对齐；
 * 匹配完成后由 {@code markMatched} 回填 match_trace_no 并置状态 MATCHED。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final ClientSubmissionMapper submissionMapper;
    private final ClientBusinessFactMapper factMapper;
    private final ObjectMapper objectMapper;

    /**
     * 幂等提交：同 client_submit_id 返回既有提交单（不重复落库）。
     *
     * @param clientCode     客户编码（业务ID）
     * @param customerGroup  客群
     * @param facts          经营事实（key = field_code）
     * @param clientSubmitId 客户端幂等键（可为空，空则按新单处理）
     * @param operator       操作人
     * @return 提交单实体（含 submissionNo / id）
     */
    @Transactional(rollbackFor = Exception.class)
    public ClientSubmission submit(String clientCode, String customerGroup, Map<String, Object> facts,
                                   String clientSubmitId, String operator) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户必填");
        }
        if (StringUtils.hasText(clientSubmitId)) {
            ClientSubmission exist = submissionMapper.selectOne(new LambdaQueryWrapper<ClientSubmission>()
                    .eq(ClientSubmission::getClientSubmitId, clientSubmitId).last("limit 1"));
            if (exist != null) {
                return exist;
            }
        }
        ClientSubmission submission = new ClientSubmission();
        submission.setSubmissionNo(BizIdGenerator.generate("submit"));
        submission.setClientProfileCode(clientCode);
        submission.setCustomerGroup(StringUtils.hasText(customerGroup) ? customerGroup : "ENTERPRISE");
        submission.setDataJson(toJson(facts));
        submission.setClientSubmitId(clientSubmitId);
        submission.setStatus("SUBMITTED");
        submission.setCreatedBy(StringUtils.hasText(operator) ? operator : "system");
        submission.setCreatedAt(LocalDateTime.now());
        try {
            submissionMapper.insert(submission);
        } catch (DuplicateKeyException e) {
            // 并发同幂等键：uk_client_submit_id 兜底，取已存在单
            log.info("幂等键冲突，复用既有提交单: clientSubmitId={}", clientSubmitId);
            ClientSubmission exist = submissionMapper.selectOne(new LambdaQueryWrapper<ClientSubmission>()
                    .eq(ClientSubmission::getClientSubmitId, clientSubmitId).last("limit 1"));
            if (exist != null) {
                return exist;
            }
            throw e;
        }
        saveFacts(submission, facts);
        return submission;
    }

    /**
     * 匹配完成回填：match_trace_no + 状态 MATCHED。
     *
     * @param submissionNo 提交单号（业务ID）
     * @param matchTraceNo 匹配审计链路 UUID（业务编码）
     */
    @Transactional(rollbackFor = Exception.class)
    public void markMatched(String submissionNo, String matchTraceNo) {
        if (!StringUtils.hasText(submissionNo) || !StringUtils.hasText(matchTraceNo)) {
            return;
        }
        ClientSubmission submission = submissionMapper.selectOne(new LambdaQueryWrapper<ClientSubmission>()
                .eq(ClientSubmission::getSubmissionNo, submissionNo).last("limit 1"));
        if (submission == null) {
            return;
        }
        submission.setMatchTraceNo(matchTraceNo);
        submission.setStatus("MATCHED");
        submissionMapper.updateById(submission);
    }

    /**
     * 拆 facts 落 t_client_business_fact。
     *
     * @param submission 提交单
     * @param facts      经营事实
     */
    private void saveFacts(ClientSubmission submission, Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> e : facts.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty()) {
                continue;
            }
            ClientBusinessFact fact = new ClientBusinessFact();
            fact.setClientProfileCode(submission.getClientProfileCode());
            fact.setSubmissionNo(submission.getSubmissionNo());
            fact.setFieldCode(e.getKey());
            fact.setFieldValue(e.getValue() == null ? null : String.valueOf(e.getValue()));
            fact.setFieldType(inferType(e.getValue()));
            fact.setExtractTime(now);
            fact.setCreatedAt(now);
            factMapper.insert(fact);
        }
    }

    /**
     * 值类型推断（STRING/NUMBER/BOOL/DATE）。
     *
     * @param value 值
     * @return 字段类型
     */
    private String inferType(Object value) {
        if (value instanceof Number) {
            return "NUMBER";
        }
        if (value instanceof Boolean) {
            return "BOOL";
        }
        if (value instanceof java.time.LocalDate || value instanceof java.util.Date) {
            return "DATE";
        }
        return "STRING";
    }

    /**
     * facts 序列化为 JSON（空 map 也输出合法 JSON）。
     *
     * @param facts 经营事实
     * @return JSON 字符串
     */
    private String toJson(Map<String, Object> facts) {
        try {
            return objectMapper.writeValueAsString(facts == null ? new LinkedHashMap<>() : facts);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "提交资料序列化失败");
        }
    }
}
