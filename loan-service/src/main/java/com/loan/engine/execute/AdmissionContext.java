package com.loan.engine.execute;

import com.loan.engine.enums.CustomerGroup;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 准入执行上下文（参考 mds AdmissionContext）。
 *
 * <p>承载一次匹配所需的客户标识、客群与经营事实（field_code → value）。
 * 事实数据源为 {@code t_client_business_fact}（提取层），调试中心影子执行时由模拟客户参数直接构造。
 *
 * @author loan-platform
 */
@Getter
public class AdmissionContext {

    /** 客户档案 ID（影子执行可为 null） */
    private final Long clientProfileId;

    /** 提交单 ID（影子执行可为 null） */
    private final Long submissionId;

    /** 客群（身份选择锁定） */
    private final CustomerGroup customerGroup;

    /** 渠道编码（合作渠道，按渠道加载执行策略/计划） */
    private final String channelCode;

    /** 链路 UUID（审计对账用） */
    private final String traceUuid;

    /** 客户经营事实：field_code → value（数值为字符串，求值器按类型转换） */
    private final Map<String, Object> fieldValues;

    /**
     * 构造执行上下文。
     *
     * @param clientProfileId 客户档案 ID
     * @param submissionId    提交单 ID
     * @param customerGroup   客群
     * @param channelCode     渠道编码
     * @param traceUuid       链路 UUID
     * @param fieldValues     客户事实（可为 null）
     */
    @Builder
    public AdmissionContext(Long clientProfileId, Long submissionId, CustomerGroup customerGroup,
                            String channelCode, String traceUuid, Map<String, Object> fieldValues) {
        this.clientProfileId = clientProfileId;
        this.submissionId = submissionId;
        this.customerGroup = customerGroup;
        this.channelCode = channelCode;
        this.traceUuid = traceUuid;
        this.fieldValues = fieldValues == null ? new HashMap<String, Object>() : fieldValues;
    }

    /**
     * 按字段编码取事实值（未命中返回 null）。
     *
     * @param fieldCode 字段编码
     * @return 字段值，未命中为 null
     */
    public Object getFact(String fieldCode) {
        return fieldValues.get(fieldCode);
    }

    /**
     * 批量处理时的上下文唯一键（影子执行用 traceUuid，正式匹配用 clientProfileId）。
     *
     * @return 上下文唯一键
     */
    public String contextKey() {
        return clientProfileId != null ? String.valueOf(clientProfileId) : traceUuid;
    }
}
