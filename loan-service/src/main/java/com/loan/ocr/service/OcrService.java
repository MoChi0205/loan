package com.loan.ocr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.ocr.entity.ExtractFieldDef;
import com.loan.ocr.entity.OcrRecord;
import com.loan.ocr.mapper.ExtractFieldDefMapper;
import com.loan.ocr.mapper.OcrRecordMapper;
import com.loan.ocr.model.OcrResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OCR 提取服务：字段定义查询 + 识别记录落库 + 识别（#4b 回灌链路）。
 *
 * <p>识别链路（可插拔）：
 * <ol>
 *   <li>{@link OcrExtractor#extract} 取原始字段；</li>
 *   <li>{@link #mapToFacts} 按 {@code t_extract_field_def.extract_rule_json} 映射为 facts；
 *       无映射规则时容忍返回空 facts（不阻塞本轮，仅在结果标注需补规则种子）；</li>
 *   <li>写 {@code t_ocr_record} 留痕；</li>
 *   <li>返回 {@link OcrResult} 供 {@code MiniMaterialService} 回灌诊断。</li>
 * </ol>
 *
 * <p>OCR 引擎仅借鉴 tse OcrStrategyFactory「按 provider 选实现」范式，不引入 tse 业务动作。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class OcrService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExtractFieldDefMapper extractFieldDefMapper;
    private final OcrRecordMapper ocrRecordMapper;
    private final OcrExtractor extractor;

    @Value("${loan.upload.base-dir:./uploads}")
    private String baseDir;

    /**
     * 查询提取字段定义（按客群）。
     *
     * @param customerGroup 客群（可选）
     * @return 字段定义列表
     */
    public List<ExtractFieldDef> listFieldDefs(String customerGroup) {
        LambdaQueryWrapper<ExtractFieldDef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExtractFieldDef::getStatus, "ACTIVE");
        if (StringUtils.hasText(customerGroup)) {
            wrapper.and(w -> w.eq(ExtractFieldDef::getCustomerGroup, customerGroup)
                    .or().eq(ExtractFieldDef::getCustomerGroup, "COMMON"));
        }
        wrapper.orderByAsc(ExtractFieldDef::getId);
        return extractFieldDefMapper.selectList(wrapper);
    }

    /**
     * 保存识别记录（阶段一模拟：记录原文件 key + 空提取结果，人工可后续修正）。
     *
     * @param record 识别记录
     */
    public void saveRecord(OcrRecord record) {
        record.setOcrType(record.getOcrType() == null ? extractor.providerName() : record.getOcrType());
        record.setCreatedAt(java.time.LocalDateTime.now());
        ocrRecordMapper.insert(record);
    }

    /**
     * 识别：提取原始字段 → 映射为 facts → 落 t_ocr_record。
     *
     * @param fileKey       文件 key（落盘后的本地文件名前缀）
     * @param bizType       资料类型（ID_CARD / BUSINESS_LICENSE / FINANCIAL_STATEMENT / CONTRACT / DUE_DILIGENCE / OTHER）
     * @param customerGroup 客群（可选）
     * @return 识别结果（facts / 平均置信度 / 记录主键 / 提取字段明细）
     */
    public OcrResult recognize(String fileKey, String bizType, String customerGroup) {
        String filePath = resolveFilePath(fileKey);
        Map<String, Object> raw = extractor.extract(filePath, bizType);

        List<ExtractFieldDef> defs = listFieldDefs(customerGroup);
        Map<String, Object> facts = mapToFacts(raw, defs);
        boolean rulesMissing = defs.isEmpty();

        // 落 t_ocr_record（best-effort 留痕，失败仅告警不阻断）
        Long recordId = null;
        try {
            OcrRecord record = new OcrRecord();
            record.setBizScene(mapBizScene(bizType));
            record.setBizCode(null);
            record.setFileKey(fileKey);
            record.setOcrType(extractor.providerName());
            record.setExtractJson(toJson(facts));
            record.setConfidenceAvg(null);
            record.setOperatorType("CUSTOMER");
            record.setCreatedBy("system");
            record.setCreatedAt(java.time.LocalDateTime.now());
            ocrRecordMapper.insert(record);
            recordId = record.getId();
        } catch (Exception e) {
            // 识别记录落库失败不影响主流程
        }

        OcrResult result = new OcrResult();
        result.setFacts(facts);
        result.setConfidenceAvg(null);
        result.setOcrRecordId(recordId);
        result.setExtractedFields(toExtractedFields(facts));
        result.setRulesMissing(rulesMissing);
        return result;
    }

    /**
     * 按提取字段定义，将 OCR 原始字段映射为诊断事实 facts。
     *
     * <p>映射规则 JSON 约定（{@code extract_rule_json}）：
     * <pre>
     * { "sourceKeys": ["纳税总额","年度纳税"], "transform": "YUAN", "targetFactKey": "annualTaxAmount" }
     * </pre>
     * 无规则定义时返回空 facts（容忍，不阻塞）。
     *
     * @param raw  原始字段
     * @param defs 字段定义列表
     * @return 映射后的 facts
     */
    public Map<String, Object> mapToFacts(Map<String, Object> raw, List<ExtractFieldDef> defs) {
        Map<String, Object> facts = new LinkedHashMap<String, Object>();
        if (raw == null || raw.isEmpty() || defs == null || defs.isEmpty()) {
            return facts;
        }
        for (ExtractFieldDef def : defs) {
            String ruleJson = def.getExtractRuleJson();
            if (!StringUtils.hasText(ruleJson)) {
                continue;
            }
            Map<String, Object> rule;
            try {
                rule = MAPPER.readValue(ruleJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            } catch (Exception e) {
                continue;
            }
            Object targetObj = rule.get("targetFactKey");
            Object sourceKeysObj = rule.get("sourceKeys");
            if (!(targetObj instanceof String) || !(sourceKeysObj instanceof List)) {
                continue;
            }
            String target = (String) targetObj;
            List<?> sourceKeys = (List<?>) sourceKeysObj;
            String transform = rule.get("transform") == null ? null : String.valueOf(rule.get("transform"));
            // 取第一个命中的 source key
            Object val = null;
            for (Object sk : sourceKeys) {
                if (sk == null) {
                    continue;
                }
                if (raw.containsKey(String.valueOf(sk))) {
                    val = raw.get(String.valueOf(sk));
                    break;
                }
            }
            if (val == null) {
                continue;
            }
            facts.put(target, transform(val, transform));
        }
        return facts;
    }

    /* ==================== 私有方法 ==================== */

    /** 解析 fileKey → 本地文件绝对路径（按前缀匹配，与上传落盘命名一致） */
    private String resolveFilePath(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            return null;
        }
        try {
            Path dir = Paths.get(baseDir);
            if (!Files.exists(dir)) {
                return null;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                Path found = stream
                        .filter(p -> p.getFileName().toString().startsWith(fileKey))
                        .findFirst()
                        .orElse(null);
                return found == null ? null : found.toAbsolutePath().toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 资料类型 → 业务场景（对齐 t_ocr_record.biz_scene 三值） */
    private String mapBizScene(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return "CLIENT_SUBMIT";
        }
        switch (bizType) {
            case "ID_CARD":
            case "BUSINESS_LICENSE":
                return "CLIENT_AUTH";
            case "FINANCIAL_STATEMENT":
            case "CONTRACT":
            case "DUE_DILIGENCE":
            case "OTHER":
            default:
                return "CLIENT_SUBMIT";
        }
    }

    /** 数值 / 字符串转换（YUAN 尝试解析为数值） */
    private Object transform(Object val, String transform) {
        if (val == null) {
            return null;
        }
        if ("YUAN".equals(transform)) {
            Double d = parseDouble(val);
            return d == null ? val : d;
        }
        return val;
    }

    private Double parseDouble(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** facts → 提取字段明细（响应用） */
    private List<Map<String, Object>> toExtractedFields(Map<String, Object> facts) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        if (facts == null || facts.isEmpty()) {
            return list;
        }
        for (Map.Entry<String, Object> e : facts.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("fieldCode", e.getKey());
            m.put("value", e.getValue());
            m.put("confidence", null);
            list.add(m);
        }
        return list;
    }
}
