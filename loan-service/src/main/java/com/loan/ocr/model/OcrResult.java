package com.loan.ocr.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * OCR 识别结果（统一返回模型）。
 *
 * <p>由 {@code OcrService.recognize} 组装，供 {@code MiniMaterialService.ingest} 使用：
 * <ul>
 *   <li>{@code facts}：映射到诊断事实字段（annualTaxAmount / annualInvoiceAmount / foundYears / industry / entName / creditCode）；</li>
 *   <li>{@code extractedFields}：提取字段明细（响应用：fieldCode / fieldName / value / confidence）；</li>
 *   <li>{@code ocrRecordId}：落库的 t_ocr_record 主键；</li>
 *   <li>{@code rulesMissing}：是否缺失映射规则（true 时调用方应在报告标注「需补映射规则种子」）。</li>
 * </ul>
 *
 * @author loan-platform
 */
public class OcrResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 映射到 facts 的字段 */
    private Map<String, Object> facts;

    /** 平均置信度（Mock 为 null） */
    private BigDecimal confidenceAvg;

    /** 落库的 t_ocr_record 主键 */
    private Long ocrRecordId;

    /** 提取字段明细（响应用） */
    private List<Map<String, Object>> extractedFields;

    /** 是否缺失映射规则 */
    private boolean rulesMissing;

    public Map<String, Object> getFacts() {
        return facts;
    }

    public void setFacts(Map<String, Object> facts) {
        this.facts = facts;
    }

    public BigDecimal getConfidenceAvg() {
        return confidenceAvg;
    }

    public void setConfidenceAvg(BigDecimal confidenceAvg) {
        this.confidenceAvg = confidenceAvg;
    }

    public Long getOcrRecordId() {
        return ocrRecordId;
    }

    public void setOcrRecordId(Long ocrRecordId) {
        this.ocrRecordId = ocrRecordId;
    }

    public List<Map<String, Object>> getExtractedFields() {
        return extractedFields;
    }

    public void setExtractedFields(List<Map<String, Object>> extractedFields) {
        this.extractedFields = extractedFields;
    }

    public boolean isRulesMissing() {
        return rulesMissing;
    }

    public void setRulesMissing(boolean rulesMissing) {
        this.rulesMissing = rulesMissing;
    }
}
