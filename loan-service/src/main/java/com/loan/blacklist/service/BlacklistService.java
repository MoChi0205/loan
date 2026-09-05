package com.loan.blacklist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.blacklist.entity.Blacklist;
import com.loan.blacklist.mapper.BlacklistMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.PageOrder;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 风控黑名单服务：分页 / 新增（提交即全局生效，留痕到人）/ 解禁（仅老板）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class BlacklistService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<Blacklist, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", Blacklist::getCreatedAt);
        ORDER_FIELDS.put("updatedAt", Blacklist::getUpdatedAt);
    }

    private final BlacklistMapper blacklistMapper;

    private static final List<String> DIMENSIONS = Arrays.asList("PHONE", "ID_CARD", "CREDIT_CODE", "LEGAL_PERSON");
    private static final List<String> REASON_TYPES = Arrays.asList("FRAUD", "DISHONEST", "SENSITIVE", "OTHER");

    /**
     * 黑名单分页。
     *
     * @param dimension 维度（可选）
     * @param status    状态（可选）
     * @param keyword   手机号/信用代码关键字（精确，走 value_hash）
     * @param page      页码
     * @param size      每页大小
     * @return 黑名单分页
     */
    public PageResult<Map<String, Object>> page(String dimension, String status, String keyword, int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<Blacklist> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dimension)) {
            wrapper.eq(Blacklist::getDimension, dimension);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Blacklist::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.eq(Blacklist::getValueHash, sha256(keyword.trim()));
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, Blacklist::getCreatedAt);
        Page<Blacklist> result = blacklistMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("dimension", b.getDimension());
            m.put("value", AesUtils.decrypt(b.getValue()));
            m.put("reasonType", b.getReasonType());
            m.put("reasonRemark", b.getReasonRemark());
            m.put("status", b.getStatus());
            m.put("releaseStaffCode", b.getReleaseStaffCode());
            m.put("releaseTime", b.getReleaseTime());
            m.put("createdBy", b.getCreatedBy());
            m.put("createdAt", b.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 新增黑名单（提交即全局生效，留痕到人）。
     *
     * @param dimension   维度
     * @param value       命中值（明文手机号/证件/信用代码/法人）
     * @param reasonType  原因分类
     * @param reasonRemark 原因说明
     * @param operator    操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void add(String dimension, String value, String reasonType, String reasonRemark, String operator) {
        if (!DIMENSIONS.contains(dimension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "维度非法");
        }
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "命中值必填");
        }
        if (!REASON_TYPES.contains(reasonType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "原因分类非法");
        }
        String hash = sha256(value.trim());
        Long exists = blacklistMapper.selectCount(new LambdaQueryWrapper<Blacklist>()
                .eq(Blacklist::getDimension, dimension)
                .eq(Blacklist::getValueHash, hash));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该黑名单记录已存在");
        }
        Blacklist b = new Blacklist();
        b.setDimension(dimension);
        b.setValue(AesUtils.encrypt(value.trim()));
        b.setValueHash(hash);
        b.setReasonType(reasonType);
        b.setReasonRemark(reasonRemark);
        b.setStatus("EFFECTIVE");
        b.setCreatedBy(operator);
        b.setUpdatedBy(operator);
        blacklistMapper.insert(b);
    }

    /**
     * 解禁（删除 = 解禁，仅老板可操作）。
     *
     * @param id       黑名单主键
     * @param operator 解禁人
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id, String operator) {
        Blacklist b = blacklistMapper.selectById(id);
        if (b == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "黑名单记录不存在");
        }
        if ("RELEASED".equals(b.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该记录已解禁");
        }
        b.setStatus("RELEASED");
        b.setReleaseStaffCode(operator);
        b.setReleaseTime(LocalDateTime.now());
        b.setUpdatedBy(operator);
        blacklistMapper.updateById(b);
    }

    /** SHA-256 哈希。 */
    private String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }
}
