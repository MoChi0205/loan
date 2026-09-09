package com.loan.importing.controller;

import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.importing.service.ExcelTemplateService;
import com.loan.importing.service.ImportRoleGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import com.loan.common.Result;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import com.loan.product.service.ProductService;
import com.loan.product.dto.ProductSaveReq;
import com.loan.mini.service.MiniLeadService;
import com.loan.common.util.BizIdGenerator;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import java.io.ByteArrayOutputStream;
import org.springframework.web.bind.annotation.RestController;
import com.loan.importing.entity.BatchImportTask;
import com.loan.importing.mapper.BatchImportTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;

/** 产品/客户线索批量导入公共入口。 */
@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
public class BatchImportController {
    private final ImportRoleGuard roleGuard;
    private final ExcelTemplateService templateService;
    private final ProductService productService;
    private final MiniLeadService miniLeadService;
    private final BatchImportTaskMapper taskMapper;

    @PostMapping("/task")
    public Result<Map<String,Object>> createTask(@RequestParam String type, @RequestParam("file") MultipartFile file, @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        if (file == null || file.isEmpty()) throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.PARAM_ERROR,"请选择导入文件");
        String normalized = "PRODUCT".equalsIgnoreCase(type) ? "PRODUCT" : "LEAD";
        @SuppressWarnings("unchecked") List<Map<String,String>> rows=(List<Map<String,String>>)templateService.preview(normalized,file).get("rows");
        BatchImportTask task=new BatchImportTask(); task.setTaskNo(BizIdGenerator.generate("imptask")); task.setImportType(normalized); task.setStatus("PENDING"); task.setTotalCount(rows.size()); task.setSuccessCount(0); task.setFailedCount(0); task.setProgressPercent(0); task.setApplicantStaffCode(user.getUserNo()); task.setCreatedAt(LocalDateTime.now()); task.setUpdatedAt(LocalDateTime.now()); taskMapper.insert(task);
        Map<String,Object> out=new LinkedHashMap<>(); out.put("taskNo",task.getTaskNo()); out.put("status",task.getStatus()); out.put("total",task.getTotalCount()); return Result.ok(out);
    }

    @GetMapping("/task")
    public Result<Map<String,Object>> task(@RequestParam String taskNo, @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user); BatchImportTask t=taskMapper.selectOne(new LambdaQueryWrapper<BatchImportTask>().eq(BatchImportTask::getTaskNo,taskNo));
        if(t==null) throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.DATA_NOT_FOUND,"导入任务不存在");
        Map<String,Object> out=new LinkedHashMap<>(); out.put("taskNo",t.getTaskNo()); out.put("status",t.getStatus()); out.put("total",t.getTotalCount()); out.put("success",t.getSuccessCount()); out.put("failed",t.getFailedCount()); out.put("progress",t.getProgressPercent()); out.put("error",t.getErrorMessage()); return Result.ok(out);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam String type, @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        String normalized = "PRODUCT".equalsIgnoreCase(type) ? "PRODUCT" : "LEAD";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + normalized.toLowerCase() + "-import-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateService.template(normalized));
    }

    /** 上传预览：仅解析与校验，不写库；正式导入任务后续复用同一解析结果。 */
    @PostMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam String type,
                                               @RequestParam("file") MultipartFile file,
                                               @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        if (file == null || file.isEmpty()) {
            throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "请选择导入文件");
        }
        if (file.getSize() > 20 * 1024 * 1024L) {
            throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "文件不能超过 20MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "仅支持 .xlsx 文件");
        }
        String normalized = "PRODUCT".equalsIgnoreCase(type) ? "PRODUCT" : "LEAD";
        return Result.ok(templateService.preview(normalized, file));
    }

    /** 正式导入：逐行事务处理，返回成功/失败明细；单行失败不影响其他行。 */
    @PostMapping("/execute")
    public Result<Map<String, Object>> execute(@RequestParam String type,
                                               @RequestParam("file") MultipartFile file,
                                               @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        String normalized = "PRODUCT".equalsIgnoreCase(type) ? "PRODUCT" : "LEAD";
        @SuppressWarnings("unchecked") List<Map<String, String>> rows =
                (List<Map<String, String>>) templateService.preview(normalized, file).get("rows");
        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;
        Set<String> naturalKeys = new HashSet<>();
        Set<String> leadKeys = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, String> row = rows.get(i);
                if ("PRODUCT".equals(normalized)) {
                    ProductSaveReq req = new ProductSaveReq();
                    req.setProductCode(BizIdGenerator.generate("prod"));
                    req.setProductName(row.get("产品名称*")); req.setBankName(row.get("银行名称*"));
                    req.setCustomerGroup(row.get("客群*")); req.setStatus("DRAFT"); req.setSource("OURS");
                    String naturalKey = String.valueOf(req.getBankName()).trim().toUpperCase() + "|"
                            + String.valueOf(req.getProductName()).trim().toUpperCase() + "|"
                            + String.valueOf(req.getCustomerGroup()).trim().toUpperCase();
                    if (!naturalKeys.add(naturalKey)) throw new IllegalArgumentException("文件内产品重复");
                    productService.create(req, user.getName());
                } else {
                    Map<String, String> lead = new LinkedHashMap<>();
                    lead.put("contactName", row.get("联系人*")); lead.put("phone", row.get("手机号*"));
                    lead.put("leadType", row.get("客群*")); lead.put("entName", row.get("企业名称"));
                    lead.put("creditCode", row.get("统一社会信用代码")); lead.put("idCardNo", row.get("身份证号"));
                    lead.put("remark", row.get("备注"));
                    String identity = String.valueOf(lead.get("phone")).trim();
                    if (identity.isEmpty() || "null".equals(identity)) identity = String.valueOf(lead.get("idCardNo")).trim();
                    if (identity.isEmpty() || "null".equals(identity)) identity = String.valueOf(lead.get("creditCode")).trim();
                    if (!identity.isEmpty() && !leadKeys.add(identity)) throw new IllegalArgumentException("文件内线索重复");
                    miniLeadService.submit(lead, user);
                }
                success++;
            } catch (RuntimeException ex) {
                Map<String, Object> failure = new LinkedHashMap<>(); failure.put("row", i + 2);
                failure.put("message", ex.getMessage() == null ? "导入失败" : ex.getMessage()); failures.add(failure);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("total", rows.size());
        result.put("success", success); result.put("failed", failures.size()); result.put("failures", failures);
        return Result.ok(result);
    }

    /** 导入失败明细导出为 Excel，供管理员修正后重试。 */
    @PostMapping("/result-export")
    public ResponseEntity<byte[]> resultExport(@RequestBody Map<String, Object> body, @CurrentUser LoanUser user) {
        roleGuard.requireImportAdmin(user);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row header = workbook.createSheet("导入结果").createRow(0);
            header.createCell(0).setCellValue("行号"); header.createCell(1).setCellValue("失败原因");
            Object failures = body == null ? null : body.get("failures");
            if (failures instanceof List) {
                int rowNo = 1;
                for (Object value : (List<?>) failures) {
                    if (!(value instanceof Map)) continue;
                    Map<?, ?> item = (Map<?, ?>) value;
                    Row row = workbook.getSheetAt(0).createRow(rowNo++);
                    row.createCell(0).setCellValue(String.valueOf(item.get("row")));
                    row.createCell(1).setCellValue(String.valueOf(item.get("message")));
                }
            }
            workbook.write(out);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-result.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (Exception e) {
            throw new com.loan.exception.BusinessException(com.loan.common.ResultCode.PARAM_ERROR, "结果导出失败");
        }
    }
}
