package com.loan.common.util;

import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import org.springframework.util.StringUtils;

/**
 * 字段长度守卫：编码/短文本字段超长时显式报错，<b>禁止静默截断</b>。
 *
 * <p>背景：MySQL 非严格模式或手写 substring 会把超长值悄悄截断入库，造成
 * 「保存成功但数据被切掉一截」的脏数据；严格模式则直接抛
 * {@code Data too long for column 'xxx'}，用户看到的是原始 SQL 错误，无法定位字段。
 * 因此长度校验必须前置到 Service 层，给出「哪个字段超了、上限多少」的明确提示。
 *
 * <p>约定：
 * <ul>
 *   <li>用户可输入的编码字段，<b>前端输入框必须 maxlength + show-word-limit + 文案提示上限</b>（原生 maxlength 不拦截 JS 赋值，前端表单规则还需 max 校验器兜底）；</li>
 *   <li>后端 Service 保存入口必须调用本工具显式校验，上限与 {@code db/loan-db-schema.sql} 列宽一致；</li>
 *   <li>服务端自动生成的编码（{@link BizIdGenerator}）按目标列宽选 {@code generate(prefix, totalLength)} 定长生成；</li>
 *   <li>任何情况下不得对用户输入做 substring / abbreviate 式静默截断。</li>
 * </ul>
 *
 * @author loan-platform
 */
public final class FieldLengthGuard {

    private FieldLengthGuard() {
    }

    /**
     * 校验字段长度（为空跳过，由必填校验负责）。
     *
     * @param fieldName 字段中文名（如「部门编码」），用于错误提示
     * @param value     字段值
     * @param max       最大字符数（与数据库列宽一致）
     */
    public static void requireMaxLength(String fieldName, String value, int max) {
        if (StringUtils.hasText(value) && value.length() > max) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    fieldName + "长度不能超过 " + max + " 个字符（当前 " + value.length() + "）");
        }
    }

    /**
     * 校验字段必填 + 长度上限（编码字段常用：必填且不超长）。
     *
     * @param fieldName 字段中文名
     * @param value     字段值
     * @param max       最大字符数
     */
    public static void requireText(String fieldName, String value, int max) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "必填");
        }
        requireMaxLength(fieldName, value, max);
    }
}
