package com.loan.importing.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 产品、线索导入模板生成，避免维护静态二进制文件。 */
@Service
public class ExcelTemplateService {
    public byte[] template(String type) {
        String[] headers = "PRODUCT".equalsIgnoreCase(type)
                ? new String[]{"产品名称*", "银行名称*", "客群*", "额度下限", "额度上限", "利率下限", "利率上限", "期限下限(月)", "期限上限(月)"}
                : new String[]{"客群*", "联系人*", "手机号*", "企业名称", "统一社会信用代码", "身份证号", "备注"};
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("导入模板");
            Row row = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) { row.createCell(i).setCellValue(headers[i]); sheet.setColumnWidth(i, 20 * 256); }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成导入模板失败", e);
        }
    }
}
