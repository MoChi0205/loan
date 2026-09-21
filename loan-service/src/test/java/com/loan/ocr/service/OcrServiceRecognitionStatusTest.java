package com.loan.ocr.service;

import com.loan.ocr.entity.ExtractFieldDef;
import com.loan.ocr.mapper.ExtractFieldDefMapper;
import com.loan.ocr.mapper.OcrRecordMapper;
import com.loan.ocr.model.OcrResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcrServiceRecognitionStatusTest {

    private ExtractFieldDefMapper fieldDefMapper;
    private OcrRecordMapper recordMapper;

    @BeforeEach
    void setUp() {
        fieldDefMapper = mock(ExtractFieldDefMapper.class);
        recordMapper = mock(OcrRecordMapper.class);
    }

    @Test
    void mockProviderIsReportedAsNotEnabled() {
        OcrExtractor extractor = extractor("mock", false, Collections.<String, Object>emptyMap());
        when(fieldDefMapper.selectList(any())).thenReturn(Collections.<ExtractFieldDef>emptyList());

        OcrResult result = new OcrService(fieldDefMapper, recordMapper, extractor)
                .recognize("missing-file", "BANK_STATEMENT", "ENTERPRISE");

        assertEquals("mock", result.getProvider());
        assertFalse(result.isAiRecognitionEnabled());
        assertEquals("NOT_ENABLED", result.getRecognitionStatus());
    }

    @Test
    void configuredProviderReportsExtractedOnlyWhenMappedFactsExist() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("年纳税额", 120000);
        OcrExtractor extractor = extractor("vlm", true, raw);
        ExtractFieldDef def = new ExtractFieldDef();
        def.setCustomerGroup("ENTERPRISE");
        def.setStatus("ACTIVE");
        def.setExtractRuleJson("{\"sourceKeys\":[\"年纳税额\"],\"targetFactKey\":\"annualTaxAmount\"}");
        when(fieldDefMapper.selectList(any())).thenReturn(Collections.singletonList(def));

        OcrResult result = new OcrService(fieldDefMapper, recordMapper, extractor)
                .recognize("missing-file", "TAX_RECORD", "ENTERPRISE");

        assertTrue(result.isAiRecognitionEnabled());
        assertEquals("EXTRACTED", result.getRecognitionStatus());
        assertEquals(120000, result.getFacts().get("annualTaxAmount"));
    }

    private OcrExtractor extractor(final String provider, final boolean enabled,
                                   final Map<String, Object> facts) {
        return new OcrExtractor() {
            @Override
            public Map<String, Object> extract(String filePath, String bizType) {
                return facts;
            }

            @Override
            public String providerName() {
                return provider;
            }

            @Override
            public boolean isAiRecognitionEnabled() {
                return enabled;
            }
        };
    }
}
