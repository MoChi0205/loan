package com.loan.submission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.submission.entity.ClientBusinessFact;
import com.loan.submission.entity.ClientSubmission;
import com.loan.submission.mapper.ClientBusinessFactMapper;
import com.loan.submission.mapper.ClientSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提交事实 OCR 回灌合并器（#4b 核心）。
 *
 * <p>仅补空不覆盖：用户手填事实权威性高于 OCR，合并条件 = 目标 key 不存在 / 为 null /
 * 为空串 / 数值为 0。永不覆盖非空用户输入。
 *
 * <p>并发安全：查最新提交单时 {@code for update} 行锁，避免同一报告多材料并发上传的读-改-写丢失
 * （红线：回灌必须串行）。
 *
 * <p>绝不新建 submission（避免 {@code uk_client_submit_id} 唯一索引冲突）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class SubmissionFactsMerger {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClientScreeningMapper screeningMapper;
    private final ClientSubmissionMapper submissionMapper;
    private final ClientBusinessFactMapper factMapper;

    /**
     * 将 OCR 提取的事实回灌到报告对应的最新提交单 data_json。
     *
     * @param reportNo   报告编号
     * @param ocrFacts   OCR 提取的事实（key 为 facts key）
     * @param operator   操作人（留痕）
     * @return {applied, mergedCount, version}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> mergeFromOcr(String reportNo, Map<String, Object> ocrFacts, String operator) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("applied", false);
        result.put("mergedCount", 0);
        result.put("version", 1);

        if (!StringUtils.hasText(reportNo) || ocrFacts == null || ocrFacts.isEmpty()) {
            return result;
        }

        // ① reportNo → match_trace_uuid
        ClientScreening screening = screeningMapper.selectOne(
                new LambdaQueryWrapper<ClientScreening>().eq(ClientScreening::getReportNo, reportNo));
        if (screening == null || !StringUtils.hasText(screening.getMatchTraceUuid())) {
            return result;
        }

        // ② 取最新提交单（for update 行锁，防并发读改写丢失）
        ClientSubmission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<ClientSubmission>()
                        .eq(ClientSubmission::getMatchTraceNo, screening.getMatchTraceUuid())
                        .orderByDesc(ClientSubmission::getCreatedAt)
                        .last("limit 1 for update"));
        if (submission == null || !StringUtils.hasText(submission.getDataJson())) {
            return result;
        }

        // ③ data_json 反序列化
        Map<String, Object> data;
        try {
            data = MAPPER.readValue(submission.getDataJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            data = new LinkedHashMap<String, Object>();
        }

        // ④ 仅补空不覆盖
        int merged = mergeOnlyBlank(data, ocrFacts);
        if (merged <= 0) {
            return result;
        }

        // ⑤ version 递增 + 内嵌 _ocrMeta
        int version = nextVersion(data);
        Map<String, Object> ocrMeta = new LinkedHashMap<String, Object>();
        ocrMeta.put("fileKeys", new ArrayList<String>());
        ocrMeta.put("mergedAt", LocalDateTime.now().toString());
        ocrMeta.put("version", version);
        ocrMeta.put("source", "OCR");
        data.put("_ocrMeta", ocrMeta);

        // ⑥ 写回 data_json
        submission.setDataJson(toJson(data));
        submissionMapper.updateById(submission);

        // ⑦ 逐字段留痕 t_client_business_fact
        writeFactTrace(submission, ocrFacts, operator);

        result.put("applied", true);
        result.put("mergedCount", merged);
        result.put("version", version);
        return result;
    }

    /**
     * 仅补空不覆盖：遍历 source，目标 key 缺失 / null / 空串 / 数值 0 才写入。
     *
     * @return 实际补入的字段数
     */
    private int mergeOnlyBlank(Map<String, Object> target, Map<String, Object> source) {
        int count = 0;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object srcVal = entry.getValue();
            if (srcVal == null) {
                continue;
            }
            Object tgtVal = target.get(key);
            if (isBlank(tgtVal)) {
                target.put(key, srcVal);
                count++;
            }
        }
        return count;
    }

    /** 判定目标值是否「空」（null / 空串 / 数值 0），空则允许 OCR 补入 */
    private boolean isBlank(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof String) {
            return ((String) v).trim().isEmpty();
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue() == 0;
        }
        return false;
    }

    /** 下一版本号（基于已有 _ocrMeta.version 递增，首次为 1） */
    private int nextVersion(Map<String, Object> data) {
        Object meta = data.get("_ocrMeta");
        if (meta instanceof Map) {
            Object v = ((Map<?, ?>) meta).get("version");
            if (v instanceof Number) {
                return ((Number) v).intValue() + 1;
            }
        }
        return 1;
    }

    /** 逐字段写 t_client_business_fact（提取层留痕） */
    private void writeFactTrace(ClientSubmission submission, Map<String, Object> ocrFacts, String operator) {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> entry : ocrFacts.entrySet()) {
            ClientBusinessFact fact = new ClientBusinessFact();
            fact.setClientProfileCode(submission.getClientProfileCode());
            fact.setSubmissionNo(submission.getSubmissionNo());
            fact.setFieldCode(entry.getKey());
            fact.setFieldValue(String.valueOf(entry.getValue()));
            fact.setFieldType(entry.getValue() instanceof Number ? "NUMBER" : "STRING");
            fact.setExtractTime(now);
            fact.setCreatedAt(now);
            try {
                factMapper.insert(fact);
            } catch (Exception e) {
                // 留痕失败不影响主流程
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
