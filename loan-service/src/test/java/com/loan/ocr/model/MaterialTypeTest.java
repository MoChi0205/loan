package com.loan.ocr.model;

import com.loan.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialTypeTest {

    @Test
    void acceptsAllFrontendMaterialCategories() {
        assertEquals("TAX_RECORD", MaterialType.normalize("tax_record"));
        assertEquals("BANK_STATEMENT", MaterialType.normalize("BANK_STATEMENT"));
        assertEquals("CREDIT_REPORT", MaterialType.normalize("CREDIT_REPORT"));
        assertEquals("SOCIAL_SECURITY", MaterialType.normalize("SOCIAL_SECURITY"));
        assertEquals("OTHER", MaterialType.normalize(null));
        assertTrue(MaterialType.allowedValues().contains("INVOICE_RECORD"));
    }

    @Test
    void rejectsUnknownMaterialCategory() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> MaterialType.normalize("UNKNOWN_FILE"));
        assertEquals("不支持的材料类别: UNKNOWN_FILE", error.getMessage());
    }
}
