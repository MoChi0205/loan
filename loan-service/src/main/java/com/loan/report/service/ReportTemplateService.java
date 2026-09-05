package com.loan.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.report.entity.ReportTemplate;
import com.loan.report.mapper.ReportTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报告模板管理服务。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ReportTemplateService {

    private final ReportTemplateMapper templateMapper;

    /**
     * 模板分页。
     */
    public PageResult<Map<String, Object>> page(String keyword, int page, int size) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ReportTemplate::getTemplateCode, kw)
                    .or().like(ReportTemplate::getTemplateName, kw));
        }
        wrapper.orderByDesc(ReportTemplate::getCreatedAt);
        Page<ReportTemplate> result = templateMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("templateCode", t.getTemplateCode());
            m.put("versionNo", t.getVersionNo());
            m.put("templateName", t.getTemplateName());
            m.put("disclaimerText", t.getDisclaimerText());
            m.put("status", t.getStatus());
            m.put("publishedAt", t.getPublishedAt());
            m.put("publishedBy", t.getPublishedBy());
            m.put("updatedAt", t.getUpdatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 新增 / 编辑模板（templateCode + versionNo 唯一）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(ReportTemplate req, String operator) {
        if (!StringUtils.hasText(req.getTemplateCode())
                || req.getVersionNo() == null
                || !StringUtils.hasText(req.getTemplateName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板编码/版本号/名称必填");
        }
        ReportTemplate exist = templateMapper.selectOne(new LambdaQueryWrapper<ReportTemplate>()
                .eq(ReportTemplate::getTemplateCode, req.getTemplateCode())
                .eq(ReportTemplate::getVersionNo, req.getVersionNo()));
        if (exist == null) {
            req.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "ACTIVE");
            req.setCreatedBy(operator);
            req.setUpdatedBy(operator);
            templateMapper.insert(req);
        } else {
            exist.setTemplateName(req.getTemplateName());
            exist.setGradeRuleJson(req.getGradeRuleJson());
            exist.setDisclaimerText(req.getDisclaimerText());
            exist.setAdviceRulesJson(req.getAdviceRulesJson());
            exist.setWecomGuideConfig(req.getWecomGuideConfig());
            exist.setWatermarkConfig(req.getWatermarkConfig());
            exist.setUpdatedBy(operator);
            templateMapper.updateById(exist);
        }
    }

    /**
     * 发布 / 停用模板。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggle(String templateCode, Integer versionNo, boolean active, String operator) {
        ReportTemplate exist = templateMapper.selectOne(new LambdaQueryWrapper<ReportTemplate>()
                .eq(ReportTemplate::getTemplateCode, templateCode)
                .eq(ReportTemplate::getVersionNo, versionNo));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "模板不存在");
        }
        exist.setStatus(active ? "ACTIVE" : "DISABLED");
        if (active) {
            exist.setPublishedAt(LocalDateTime.now());
            exist.setPublishedBy(operator);
        }
        exist.setUpdatedBy(operator);
        templateMapper.updateById(exist);
    }
}
