package com.loan.ocr.model;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 材料类别统一白名单。
 *
 * <p>小程序、H5、Web 与服务端必须使用同一组编码；数据库字段为 varchar，不能代替业务校验。</p>
 */
public final class MaterialType {

    public static final String ID_CARD = "ID_CARD";
    public static final String BUSINESS_LICENSE = "BUSINESS_LICENSE";
    public static final String TAX_RECORD = "TAX_RECORD";
    public static final String INVOICE_RECORD = "INVOICE_RECORD";
    public static final String BANK_STATEMENT = "BANK_STATEMENT";
    public static final String FINANCIAL_STATEMENT = "FINANCIAL_STATEMENT";
    public static final String CONTRACT = "CONTRACT";
    public static final String DUE_DILIGENCE = "DUE_DILIGENCE";
    public static final String CREDIT_REPORT = "CREDIT_REPORT";
    public static final String INCOME_PROOF = "INCOME_PROOF";
    public static final String ASSET_PROOF = "ASSET_PROOF";
    public static final String SOCIAL_SECURITY = "SOCIAL_SECURITY";
    public static final String OTHER = "OTHER";

    private static final Set<String> ALLOWED = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            ID_CARD, BUSINESS_LICENSE, TAX_RECORD, INVOICE_RECORD, BANK_STATEMENT,
            FINANCIAL_STATEMENT, CONTRACT, DUE_DILIGENCE, CREDIT_REPORT,
            INCOME_PROOF, ASSET_PROOF, SOCIAL_SECURITY, OTHER)));

    private MaterialType() {
    }

    /** 空值按补充材料处理；非空值统一大写后执行白名单校验。 */
    public static String normalize(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : OTHER;
        if (!ALLOWED.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的材料类别: " + normalized);
        }
        return normalized;
    }

    public static Set<String> allowedValues() {
        return ALLOWED;
    }
}
