package com.loan.dict.service;

import com.loan.dict.vo.DictItemVO;
import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.enums.Grade;
import com.loan.engine.enums.StepResult;
import com.loan.engine.enums.TotalResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 枚举字典服务：集中注册系统全部枚举，统一以 code + 中文 label + 语义色下发前端。
 *
 * <p>设计原则（契约）：
 * <ul>
 *   <li>前端不得硬编码枚举值；所有枚举由本服务统一定义，前端经 {@code /api/dict/all} 解析。</li>
 *   <li>前端展示/选择枚举时，展示中文 label，存储/传输使用 code。</li>
 *   <li>colorType 控制展示色：success 绿 / warning 橙 / danger 红 / info 蓝 / primary 主色 / muted 灰。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Service
public class DictService {

    /** 字典类型 key：客群 */
    public static final String TYPE_CUSTOMER_GROUP = "customerGroup";
    /** 字典类型 key：步骤结果（五态） */
    public static final String TYPE_STEP_RESULT = "stepResult";
    /** 字典类型 key：产品匹配总结果 */
    public static final String TYPE_TOTAL_RESULT = "totalResult";
    /** 字典类型 key：匹配档位 */
    public static final String TYPE_GRADE = "grade";
    /** 字典类型 key：产品来源 */
    public static final String TYPE_PRODUCT_SOURCE = "productSource";
    /** 字典类型 key：产品状态（t_bank_product.status） */
    public static final String TYPE_PRODUCT_STATUS = "productStatus";
    /** 字典类型 key：规则状态（t_rule.status） */
    public static final String TYPE_RULE_STATUS = "ruleStatus";
    /** 字典类型 key：通用启停状态 */
    public static final String TYPE_ENABLE_STATUS = "enableStatus";
    /** 字典类型 key：规则运算符 */
    public static final String TYPE_RULE_OPERATOR = "ruleOperator";
    /** 字典类型 key：规则值类型 */
    public static final String TYPE_RULE_VALUE_TYPE = "ruleValueType";

    /**
     * 返回全部枚举字典（type → 条目列表）。
     *
     * @return 枚举字典映射
     */
    public Map<String, List<DictItemVO>> listAll() {
        Map<String, List<DictItemVO>> dict = new LinkedHashMap<>(16);
        dict.put(TYPE_CUSTOMER_GROUP, customerGroup());
        dict.put(TYPE_STEP_RESULT, stepResult());
        dict.put(TYPE_TOTAL_RESULT, totalResult());
        dict.put(TYPE_GRADE, grade());
        dict.put(TYPE_PRODUCT_SOURCE, productSource());
        dict.put(TYPE_PRODUCT_STATUS, productStatus());
        dict.put(TYPE_RULE_STATUS, ruleStatus());
        dict.put(TYPE_ENABLE_STATUS, enableStatus());
        dict.put(TYPE_RULE_OPERATOR, ruleOperator());
        dict.put(TYPE_RULE_VALUE_TYPE, ruleValueType());
        return dict;
    }

    /**
     * 客群枚举。
     *
     * @return 客群字典
     */
    private List<DictItemVO> customerGroup() {
        List<DictItemVO> list = new ArrayList<>(4);
        for (CustomerGroup e : CustomerGroup.values()) {
            list.add(DictItemVO.of(e.getCode(), e.getName(), "primary"));
        }
        return list;
    }

    /**
     * 步骤结果（五态）枚举。
     *
     * @return 步骤结果字典
     */
    private List<DictItemVO> stepResult() {
        List<DictItemVO> list = new ArrayList<>(8);
        list.add(DictItemVO.of(StepResult.PASS.getCode(), StepResult.PASS.getName(), "success"));
        list.add(DictItemVO.of(StepResult.FAIL.getCode(), StepResult.FAIL.getName(), "danger"));
        list.add(DictItemVO.of(StepResult.SKIP.getCode(), StepResult.SKIP.getName(), "muted"));
        list.add(DictItemVO.of(StepResult.SKIP_SEGMENT_MISMATCH.getCode(), StepResult.SKIP_SEGMENT_MISMATCH.getName(), "muted"));
        list.add(DictItemVO.of(StepResult.ERROR.getCode(), StepResult.ERROR.getName(), "danger"));
        return list;
    }

    /**
     * 产品匹配总结果枚举。
     *
     * @return 总结果字典
     */
    private List<DictItemVO> totalResult() {
        List<DictItemVO> list = new ArrayList<>(8);
        list.add(DictItemVO.of(TotalResult.PASS.getCode(), TotalResult.PASS.getName(), "success"));
        list.add(DictItemVO.of(TotalResult.CONDITION.getCode(), TotalResult.CONDITION.getName(), "warning"));
        list.add(DictItemVO.of(TotalResult.REJECT.getCode(), TotalResult.REJECT.getName(), "danger"));
        list.add(DictItemVO.of(TotalResult.SKIP_SEGMENT_MISMATCH.getCode(), TotalResult.SKIP_SEGMENT_MISMATCH.getName(), "muted"));
        list.add(DictItemVO.of(TotalResult.ERROR.getCode(), TotalResult.ERROR.getName(), "danger"));
        return list;
    }

    /**
     * 匹配档位枚举。
     *
     * @return 档位字典
     */
    private List<DictItemVO> grade() {
        List<DictItemVO> list = new ArrayList<>(4);
        list.add(DictItemVO.of(Grade.HIGH.getCode(), Grade.HIGH.getName(), "success"));
        list.add(DictItemVO.of(Grade.MIDDLE.getCode(), Grade.MIDDLE.getName(), "warning"));
        list.add(DictItemVO.of(Grade.LOW.getCode(), Grade.LOW.getName(), "muted"));
        return list;
    }

    /**
     * 产品来源枚举（t_bank_product.source：渠道自建 / 我司录入）。
     *
     * @return 产品来源字典
     */
    private List<DictItemVO> productSource() {
        List<DictItemVO> list = new ArrayList<>(4);
        list.add(DictItemVO.of("OURS", "我司录入", "primary"));
        list.add(DictItemVO.of("CHANNEL_SELF", "渠道自建", "info"));
        return list;
    }

    /**
     * 产品状态枚举（t_bank_product.status）。
     *
     * @return 产品状态字典
     */
    private List<DictItemVO> productStatus() {
        List<DictItemVO> list = new ArrayList<>(8);
        list.add(DictItemVO.of("DRAFT", "草稿", "muted"));
        list.add(DictItemVO.of("PENDING", "待审核", "warning"));
        list.add(DictItemVO.of("APPROVED", "已入全量库", "success"));
        list.add(DictItemVO.of("REJECTED", "已驳回", "danger"));
        list.add(DictItemVO.of("OFFLINE", "已下线", "muted"));
        return list;
    }

    /**
     * 规则状态枚举（t_rule.status：DRAFT/ONLINE/DISABLED）。
     *
     * @return 规则状态字典
     */
    private List<DictItemVO> ruleStatus() {
        List<DictItemVO> list = new ArrayList<>(4);
        list.add(DictItemVO.of("DRAFT", "草稿", "muted"));
        list.add(DictItemVO.of("ONLINE", "已上线", "success"));
        list.add(DictItemVO.of("DISABLED", "已停用", "muted"));
        return list;
    }

    /**
     * 通用启停状态枚举（t_* 表 status：ACTIVE/DISABLED）。
     *
     * @return 启停状态字典
     */
    private List<DictItemVO> enableStatus() {
        List<DictItemVO> list = new ArrayList<>(4);
        list.add(DictItemVO.of("ACTIVE", "启用", "success"));
        list.add(DictItemVO.of("DISABLED", "停用", "muted"));
        return list;
    }

    /**
     * 规则运算符枚举（t_rule.operator）。
     *
     * @return 运算符字典
     */
    private List<DictItemVO> ruleOperator() {
        List<DictItemVO> list = new ArrayList<>(16);
        list.add(DictItemVO.of("==", "等于", "primary"));
        list.add(DictItemVO.of("!=", "不等于", "primary"));
        list.add(DictItemVO.of(">", "大于", "info"));
        list.add(DictItemVO.of(">=", "大于等于", "info"));
        list.add(DictItemVO.of("<", "小于", "info"));
        list.add(DictItemVO.of("<=", "小于等于", "info"));
        list.add(DictItemVO.of("in", "属于", "info"));
        list.add(DictItemVO.of("not_in", "不属于", "info"));
        list.add(DictItemVO.of("contains", "包含", "info"));
        list.add(DictItemVO.of("not_contains", "不包含", "info"));
        list.add(DictItemVO.of("between", "区间", "info"));
        list.add(DictItemVO.of("is_null", "为空", "muted"));
        list.add(DictItemVO.of("not_null", "非空", "muted"));
        return list;
    }

    /**
     * 规则值类型枚举（t_rule.value_type）。
     *
     * @return 值类型字典
     */
    private List<DictItemVO> ruleValueType() {
        List<DictItemVO> list = new ArrayList<>(4);
        list.add(DictItemVO.of("STRING", "字符串", "primary"));
        list.add(DictItemVO.of("NUMBER", "数字", "info"));
        list.add(DictItemVO.of("DATE", "日期", "info"));
        list.add(DictItemVO.of("LIST", "列表", "info"));
        return list;
    }
}
