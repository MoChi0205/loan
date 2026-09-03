package com.loan.rule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.common.util.BatchQueryUtils;
import com.loan.exception.BusinessException;
import com.loan.rule.entity.Rule;
import com.loan.rule.entity.RuleCategory;
import com.loan.rule.entity.RuleTemplate;
import com.loan.rule.entity.RuleTemplateField;
import com.loan.rule.entity.RuleTemplateVersion;
import com.loan.rule.mapper.RuleCategoryMapper;
import com.loan.rule.mapper.RuleMapper;
import com.loan.rule.mapper.RuleTemplateFieldMapper;
import com.loan.rule.mapper.RuleTemplateMapper;
import com.loan.rule.mapper.RuleTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 规则模板服务：模板 CRUD + 字段编排 + 发布（版本快照）+ 上线/下线 + 导入为规则。
 *
 * <p>规则模板 = 一条可复用规则的骨架（主表 + 字段定义 + 版本快照），
 * 上线后可通过「导入为规则」实例化为 t_rule 真实规则，或作为策略模板步骤的 rule_template_id 引用。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class RuleTemplateService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final RuleTemplateMapper templateMapper;
    private final RuleTemplateFieldMapper fieldMapper;
    private final RuleTemplateVersionMapper versionMapper;
    private final RuleMapper ruleMapper;
    private final RuleCategoryMapper categoryMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分页查询模板。
     */
    public PageResult<RuleTemplate> page(String customerGroup, String keyword, int page, int size) {
        LambdaQueryWrapper<RuleTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(customerGroup)) {
            wrapper.eq(RuleTemplate::getCustomerGroup, customerGroup);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(RuleTemplate::getTemplateCode, keyword)
                    .or().like(RuleTemplate::getTemplateName, keyword));
        }
        wrapper.orderByDesc(RuleTemplate::getId);
        Page<RuleTemplate> result = templateMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /**
     * 按模板业务编码批量查询，去重且按请求顺序返回，未命中的编码不生成空对象。
     */
    public List<RuleTemplate> listByCodes(List<String> templateCodes) {
        List<String> codes = BatchQueryUtils.normalizeCodes(templateCodes);
        Map<String, RuleTemplate> found = templateMapper.selectList(new LambdaQueryWrapper<RuleTemplate>()
                .in(RuleTemplate::getTemplateCode, codes)).stream()
                .collect(Collectors.toMap(RuleTemplate::getTemplateCode, Function.identity(), (left, right) -> left));
        return codes.stream().map(found::get).filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 新建模板（草稿）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(RuleTemplate template, String operator) {
        if (!StringUtils.hasText(template.getTemplateCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板编码必填");
        }
        if (templateMapper.selectCount(new LambdaQueryWrapper<RuleTemplate>()
                .eq(RuleTemplate::getTemplateCode, template.getTemplateCode())) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板编码已存在");
        }
        template.setId(null);
        template.setStatus(STATUS_DISABLED);
        template.setCreatedBy(operator);
        template.setUpdatedBy(operator);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(template);
        return template.getTemplateCode();
    }

    /**
     * 编辑模板。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(RuleTemplate template, String operator) {
        RuleTemplate current = requireByCode(template.getTemplateCode());
        template.setId(current.getId());
        template.setUpdatedBy(operator);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    /**
     * 删除模板（级联字段定义与版本）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String templateCode) {
        Long id = requireByCode(templateCode).getId();
        fieldMapper.delete(new LambdaQueryWrapper<RuleTemplateField>()
                .eq(RuleTemplateField::getTemplateId, id));
        versionMapper.delete(new LambdaQueryWrapper<RuleTemplateVersion>()
                .eq(RuleTemplateVersion::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    /**
     * 发布模板：生成版本快照（version_no 递增）+ 上线。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(String templateCode, String operator) {
        RuleTemplate template = requireByCode(templateCode);
        Long id = template.getId();
        List<RuleTemplateField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<RuleTemplateField>()
                        .eq(RuleTemplateField::getTemplateId, id)
                        .orderByAsc(RuleTemplateField::getSort));

        List<RuleTemplateVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<RuleTemplateVersion>()
                        .eq(RuleTemplateVersion::getTemplateId, id)
                        .orderByDesc(RuleTemplateVersion::getVersionNo));
        int versionNo = versions.isEmpty() ? 0 : versions.get(0).getVersionNo();

        RuleTemplateVersion version = new RuleTemplateVersion();
        version.setTemplateId(id);
        version.setVersionNo(versionNo + 1);
        version.setSnapshotJson(buildSnapshot(fields));
        version.setStatus(STATUS_ACTIVE);
        version.setPublishedAt(LocalDateTime.now());
        version.setPublishedBy(operator);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);

        template.setStatus(STATUS_ACTIVE);
        template.setUpdatedBy(operator);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    /**
     * 下线。
     */
    @Transactional(rollbackFor = Exception.class)
    public void offline(String templateCode, String operator) {
        RuleTemplate template = requireByCode(templateCode);
        template.setStatus(STATUS_DISABLED);
        template.setUpdatedBy(operator);
        template.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    /**
     * 模板详情（模板 + 字段定义 + 版本列表）。
     */
    public Map<String, Object> detail(String templateCode) {
        RuleTemplate template = requireByCode(templateCode);
        Long id = template.getId();
        List<RuleTemplateField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<RuleTemplateField>()
                        .eq(RuleTemplateField::getTemplateId, id)
                        .orderByAsc(RuleTemplateField::getSort));
        List<RuleTemplateVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<RuleTemplateVersion>()
                        .eq(RuleTemplateVersion::getTemplateId, id)
                        .orderByDesc(RuleTemplateVersion::getVersionNo));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("template", template);
        result.put("fields", fields);
        result.put("versions", versions);
        return result;
    }

    /** 新建字段定义 */
    @Transactional(rollbackFor = Exception.class)
    public Long createField(RuleTemplateField field) {
        field.setId(null);
        if (field.getRequired() == null) {
            field.setRequired(1);
        }
        if (field.getSort() == null) {
            field.setSort(0);
        }
        if (field.getFieldType() == null) {
            field.setFieldType("STRING");
        }
        field.setCreatedAt(LocalDateTime.now());
        fieldMapper.insert(field);
        return field.getId();
    }

    /** 更新字段定义 */
    @Transactional(rollbackFor = Exception.class)
    public void updateField(RuleTemplateField field) {
        fieldMapper.updateById(field);
    }

    /** 删除字段定义 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteField(Long fieldId) {
        fieldMapper.deleteById(fieldId);
    }

    /**
     * 导入为规则：按指定字段定义实例化一条 t_rule（草稿）。
     *
     * @param id       模板 ID
     * @param fieldId  字段定义 ID（为 null 时取第一个字段）
     * @param operator 操作人
     * @return 生成的规则编码
     */
    @Transactional(rollbackFor = Exception.class)
    public String importToRule(String templateCode, Long fieldId, String operator) {
        RuleTemplate template = requireByCode(templateCode);
        Long id = template.getId();
        RuleTemplateField field;
        if (fieldId != null) {
            field = fieldMapper.selectById(fieldId);
            if (field == null || !field.getTemplateId().equals(id)) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "字段定义不存在");
            }
        } else {
            field = fieldMapper.selectOne(new LambdaQueryWrapper<RuleTemplateField>()
                    .eq(RuleTemplateField::getTemplateId, id)
                    .orderByAsc(RuleTemplateField::getSort)
                    .last("LIMIT 1"));
        }
        if (field == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板无字段定义，无法导入");
        }

        String ruleCode = template.getTemplateCode() + "_" + field.getFieldCode() + "_" + System.currentTimeMillis();
        Rule rule = new Rule();
        rule.setRuleCode(ruleCode);
        rule.setRuleName(template.getTemplateName() + "·" + field.getFieldName());
        rule.setFieldCode(field.getFieldCode());
        rule.setFieldName(field.getFieldName());
        rule.setOperator(field.getOperator());
        rule.setValueType(field.getFieldType());
        rule.setValueText(field.getDefaultValue());
        rule.setCustomerGroup(template.getCustomerGroup());
        rule.setDescription(template.getDescription());
        rule.setStatus("DRAFT");
        rule.setCreatedBy(operator);
        rule.setUpdatedBy(operator);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        return ruleCode;
    }

    /**
     * 全部分类（模板分类下拉）。
     */
    public List<RuleCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<RuleCategory>()
                .eq(RuleCategory::getStatus, "ACTIVE")
                .orderByAsc(RuleCategory::getId));
    }

    /** 按模板业务编码查询模板，物理主键仅在模板聚合内部使用。 */
    private RuleTemplate requireByCode(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板编码必填");
        }
        RuleTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<RuleTemplate>()
                .eq(RuleTemplate::getTemplateCode, templateCode.trim()));
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模板不存在");
        }
        return template;
    }

    /**
     * 字段定义快照（JSON 字符串，仅业务字段，不含时间）。
     */
    private String buildSnapshot(List<RuleTemplateField> fields) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RuleTemplateField f : fields) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldCode", f.getFieldCode());
            m.put("fieldName", f.getFieldName());
            m.put("fieldType", f.getFieldType());
            m.put("operator", f.getOperator());
            m.put("defaultValue", f.getDefaultValue());
            m.put("required", f.getRequired());
            m.put("sort", f.getSort());
            list.add(m);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
