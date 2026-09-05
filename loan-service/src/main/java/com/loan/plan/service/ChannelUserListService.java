package com.loan.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.exception.BusinessException;
import com.loan.plan.entity.ChannelUserList;
import com.loan.plan.mapper.ChannelUserListMapper;
import com.loan.plan.model.ChannelUserListQuery;
import com.loan.plan.model.ChannelUserListUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 渠道本地白/黑名单服务：统一支持单条、批量与组合分页查询。
 *
 * <p>名单键语义：PERSONAL=手机号 MD5(32hex)；ENTERPRISE=统一社会信用代码(18位，原样存)。
 * 新增时后端统一归一化并通过批量 {@code INSERT IGNORE} 保证并发幂等。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ChannelUserListService {

    private static final String GROUP_PERSONAL = "PERSONAL";
    private static final int MAX_BATCH_SIZE = 100;
    private static final int BIZ_CODE_LENGTH = 16;

    private final ChannelUserListMapper listMapper;

    /** 组合条件分页查询名单。 */
    public PageResult<ChannelUserList> page(ChannelUserListQuery query) {
        ChannelUserListQuery safeQuery = query == null ? new ChannelUserListQuery() : query;
        int page = safeQuery.getPage() <= 0 ? 1 : safeQuery.getPage();
        int size = safeQuery.getSize() <= 0 ? 10 : Math.min(safeQuery.getSize(), 100);
        LambdaQueryWrapper<ChannelUserList> wrapper = buildQuery(safeQuery);
        wrapper.orderByDesc(ChannelUserList::getCreatedAt).orderByDesc(ChannelUserList::getId);
        Page<ChannelUserList> result = listMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /** 按业务编码查询单条名单。 */
    public ChannelUserList detail(String listCode) {
        requireText(listCode, "名单业务编码不能为空");
        ChannelUserList item = listMapper.selectOne(new LambdaQueryWrapper<ChannelUserList>()
                .eq(ChannelUserList::getListCode, listCode.trim()));
        if (item == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "名单记录不存在");
        }
        return item;
    }

    /** 按业务编码批量查询，结果保持请求顺序，未命中项不返回。 */
    public List<ChannelUserList> batchQuery(List<String> listCodes) {
        List<String> codes = normalizeCodes(listCodes, "名单业务编码");
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, ChannelUserList> itemMap = listMapper.selectList(new LambdaQueryWrapper<ChannelUserList>()
                        .in(ChannelUserList::getListCode, codes)).stream()
                .collect(Collectors.toMap(ChannelUserList::getListCode, item -> item, (left, right) -> left));
        return codes.stream().map(itemMap::get).filter(item -> item != null).collect(Collectors.toList());
    }

    /** 批量新增名单（后端归一化名单键，单次批量写库）。 */
    @Transactional(rollbackFor = Exception.class)
    public int add(String channelCode, String customerGroup, String listType, List<String> rawKeys, String operator) {
        requireText(channelCode, "渠道编码必填");
        requireText(customerGroup, "客群必填");
        requireText(listType, "名单类型必填");
        if (rawKeys == null || rawKeys.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "名单键不能为空");
        }
        if (rawKeys.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多处理 " + MAX_BATCH_SIZE + " 条名单");
        }

        Set<String> normalizedKeys = new LinkedHashSet<>();
        for (String raw : rawKeys) {
            if (StringUtils.hasText(raw)) {
                normalizedKeys.add(normalizeKey(raw.trim(), customerGroup));
            }
        }
        if (normalizedKeys.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        List<ChannelUserList> toInsert = new ArrayList<>();
        for (String key : normalizedKeys) {
            ChannelUserList item = new ChannelUserList();
            item.setListCode(BizIdGenerator.generate("culist", BIZ_CODE_LENGTH));
            item.setChannelCode(channelCode.trim());
            item.setCustomerGroup(customerGroup.trim());
            item.setListType(listType.trim());
            item.setListKey(key);
            item.setCreatedBy(operator);
            item.setCreatedAt(now);
            toInsert.add(item);
        }
        return listMapper.insertIgnoreBatch(toInsert);
    }

    /**
     * 按业务编码修改名单。
     *
     * <p>仅用 {@code listCode} 定位记录，不接收或依赖数据库物理主键；业务编码自身不可修改。
     * 支持局部修改，未传名单键时保留原值，避免把个人名单的 MD5 展示值二次哈希。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(String listCode, ChannelUserListUpdateRequest request) {
        requireText(listCode, "名单业务编码不能为空");
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "修改内容不能为空");
        }

        ChannelUserList current = detail(listCode);
        boolean hasChannelCode = StringUtils.hasText(request.getChannelCode());
        boolean hasCustomerGroup = StringUtils.hasText(request.getCustomerGroup());
        boolean hasListType = StringUtils.hasText(request.getListType());
        boolean hasListKey = StringUtils.hasText(request.getListKey());
        if (!hasChannelCode && !hasCustomerGroup && !hasListType && !hasListKey) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "至少提供一个需要修改的字段");
        }

        String targetGroup = hasCustomerGroup ? request.getCustomerGroup().trim() : current.getCustomerGroup();
        if (hasCustomerGroup && !targetGroup.equals(current.getCustomerGroup()) && !hasListKey) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "变更客群时必须同时提供原始名单键");
        }
        LambdaUpdateWrapper<ChannelUserList> wrapper = new LambdaUpdateWrapper<ChannelUserList>()
                .eq(ChannelUserList::getListCode, listCode.trim());
        if (hasChannelCode) {
            wrapper.set(ChannelUserList::getChannelCode, request.getChannelCode().trim());
        }
        if (hasCustomerGroup) {
            wrapper.set(ChannelUserList::getCustomerGroup, targetGroup);
        }
        if (hasListType) {
            wrapper.set(ChannelUserList::getListType, request.getListType().trim());
        }
        if (hasListKey) {
            wrapper.set(ChannelUserList::getListKey, normalizeKey(request.getListKey().trim(), targetGroup));
        }
        try {
            listMapper.update(null, wrapper);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "相同渠道、客群、类型和名单键的记录已存在");
        }
    }

    /** 按业务编码删除单条。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String listCode) {
        requireText(listCode, "名单业务编码不能为空");
        int deleted = listMapper.delete(new LambdaQueryWrapper<ChannelUserList>()
                .eq(ChannelUserList::getListCode, listCode.trim()));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "名单记录不存在");
        }
    }

    /** 按业务编码批量删除。 */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<String> listCodes) {
        List<String> codes = normalizeCodes(listCodes, "名单业务编码");
        if (codes.isEmpty()) {
            return 0;
        }
        return listMapper.delete(new LambdaQueryWrapper<ChannelUserList>()
                .in(ChannelUserList::getListCode, codes));
    }

    /** 构建统一组合查询条件。 */
    private LambdaQueryWrapper<ChannelUserList> buildQuery(ChannelUserListQuery query) {
        LambdaQueryWrapper<ChannelUserList> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getChannelCode())) {
            wrapper.eq(ChannelUserList::getChannelCode, query.getChannelCode().trim());
        }
        if (StringUtils.hasText(query.getCustomerGroup())) {
            wrapper.eq(ChannelUserList::getCustomerGroup, query.getCustomerGroup().trim());
        }
        if (StringUtils.hasText(query.getListType())) {
            wrapper.eq(ChannelUserList::getListType, query.getListType().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            if (keyword.matches("\\d{11}")) {
                wrapper.in(ChannelUserList::getListKey, keyword, md5(keyword));
            } else {
                wrapper.like(ChannelUserList::getListKey, keyword);
            }
        }
        return wrapper;
    }

    /** 名单键归一化：个人=手机号 MD5；企业=统一社会信用代码。 */
    private String normalizeKey(String raw, String customerGroup) {
        if (GROUP_PERSONAL.equals(customerGroup)) {
            if (!raw.matches("\\d{11}")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "手机号须为 11 位数字");
            }
            return md5(raw);
        }
        String code = raw.toUpperCase();
        if (code.length() != 18) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "统一社会信用代码须为 18 位：" + raw);
        }
        return code;
    }

    /** 归一化并限制批量业务编码。 */
    private List<String> normalizeCodes(List<String> codes, String fieldName) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        if (codes.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多处理 " + MAX_BATCH_SIZE + " 条" + fieldName);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String code : codes) {
            if (StringUtils.hasText(code)) {
                result.add(code.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "名单键计算失败");
        }
    }
}
