package com.loan.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.infrastructure.oss.OssStorageService;
import com.loan.report.entity.ReportTemplate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报告产物生成器：在初筛事务完成后生成客户/员工两套不可变快照，并分别留存 HTML 与 PDF。
 * 模板配置在生成时复制进快照，后续修改模板不会改变历史报告。
 */
@Service
@RequiredArgsConstructor
public class ReportArtifactService {

    private static final String DISCLAIMER = "本报告仅用于经营分析，不构成贷款审批、额度或通过承诺。";

    private final ObjectMapper objectMapper;
    private final OssStorageService ossStorageService;

    public ArtifactKeys generate(String reportNo, String clientCode, ReportTemplate template,
                                 String grade, int bankCount, int productCount,
                                 int passCount, int conditionCount, int rejectCount,
                                 String adviceJson) {
        String templateCode = template == null ? null : template.getTemplateCode();
        Integer version = template == null ? null : template.getVersionNo();
        String generatedAt = LocalDateTime.now().toString();
        // 客户快照只保留风险分析文案；模板编码、版本、规则 JSON 和内部命中统计均不进入客户产物。
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("reportNo", reportNo);
        customer.put("generatedAt", generatedAt);
        customer.put("audience", "CUSTOMER");
        customer.put("风险等级", gradeLabel(grade));
        customer.put("经营建议", adviceJson);
        customer.put("免责声明", text(template == null ? null : template.getDisclaimerText(), DISCLAIMER));
        customer.put("数据来源声明", "本报告仅使用客户主动填写及授权上传材料；未调用外部个人信息查询接口。");
        customer.put("summary", "仅展示风险分析与经营建议，不展示银行、产品、准入规则或内部匹配结果。");

        Map<String, Object> employee = new LinkedHashMap<>();
        employee.put("reportNo", reportNo);
        employee.put("clientCode", clientCode);
        employee.put("generatedAt", generatedAt);
        employee.put("templateCode", templateCode);
        employee.put("templateVersion", version);
        employee.put("grade", grade);
        employee.put("advice", adviceJson);
        employee.put("disclaimer", text(template == null ? null : template.getDisclaimerText(), DISCLAIMER));
        employee.put("watermark", template == null ? null : template.getWatermarkConfig());
        employee.put("gradeRule", template == null ? null : template.getGradeRuleJson());
        employee.put("adviceRules", template == null ? null : template.getAdviceRulesJson());
        employee.put("audience", "STAFF");
        Map<String, Object> internal = new LinkedHashMap<>();
        internal.put("bankCount", bankCount);
        internal.put("productCount", productCount);
        internal.put("passCount", passCount);
        internal.put("conditionCount", conditionCount);
        internal.put("rejectCount", rejectCount);
        employee.put("internalMatchSummary", internal);

        String prefix = "reports/" + reportNo + "/";
        String customerKey = prefix + "customer-snapshot.json";
        String staffKey = prefix + "staff-snapshot.json";
        String customerPdf = prefix + "customer-report.pdf";
        String staffPdf = prefix + "staff-report.pdf";
        upload(customerKey, json(customer));
        upload(staffKey, json(employee));
        upload(prefix + "customer-report.html", html("客户经营分析报告", customer));
        upload(prefix + "staff-report.html", html("员工内部经营咨询报告", employee));
        upload(customerPdf, pdf("Customer report " + reportNo + "\nRisk analysis only"));
        upload(staffPdf, pdf("Staff report " + reportNo + "\nInternal analysis"));
        return new ArtifactKeys(customerKey, staffKey, customerPdf, staffPdf);
    }

    private void upload(String key, byte[] bytes) {
        ossStorageService.upload(key, new ByteArrayInputStream(bytes), bytes.length);
    }

    private byte[] json(Map<String, Object> value) {
        try { return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value); }
        catch (Exception e) { throw new IllegalStateException("报告快照序列化失败", e); }
    }

    private byte[] html(String title, Map<String, Object> value) {
        StringBuilder body = new StringBuilder("<!doctype html><html lang=\"zh-CN\"><meta charset=\"UTF-8\"><title>")
                .append(escape(title)).append("</title><body><h1>").append(escape(title)).append("</h1><pre>");
        try { body.append(escape(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))); }
        catch (Exception e) { body.append("报告内容生成失败"); }
        return (body.append("</pre></body></html>")).toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 生成最小合法 PDF；中文正文保留在 HTML/JSON 快照中，PDF 用于归档和下载标识。 */
    private byte[] pdf(String text) {
        String stream = "BT /F1 10 Tf 50 780 Td (" + text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\n", ") Tj 0 -16 Td (") + ") Tj ET";
        String[] objects = {"1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n", "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n", "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n", "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n", "5 0 obj << /Length " + stream.length() + " >> stream\n" + stream + "\nendstream endobj\n"};
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) { offsets[i + 1] = pdf.length(); pdf.append(objects[i]); }
        int xref = pdf.length(); pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        pdf.append("trailer << /Size ").append(offsets.length).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String text(String value, String fallback) { return StringUtils.hasText(value) ? value : fallback; }
    private String gradeLabel(String value) {
        if ("HIGH".equalsIgnoreCase(value)) return "高风险画像";
        if ("MIDDLE".equalsIgnoreCase(value)) return "中风险画像";
        if ("LOW".equalsIgnoreCase(value)) return "低风险画像";
        return "待分析";
    }
    private String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    @Data
    public static class ArtifactKeys {
        private final String customerSnapshotKey;
        private final String staffSnapshotKey;
        private final String customerPdfKey;
        private final String staffPdfKey;
        public ArtifactKeys(String customerSnapshotKey, String staffSnapshotKey, String customerPdfKey, String staffPdfKey) {
            this.customerSnapshotKey = customerSnapshotKey; this.staffSnapshotKey = staffSnapshotKey;
            this.customerPdfKey = customerPdfKey; this.staffPdfKey = staffPdfKey;
        }
    }
}
