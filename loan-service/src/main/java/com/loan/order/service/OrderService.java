package com.loan.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.common.util.PageOrder;
import com.loan.common.service.BusinessNameService;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.HashUtils;
import com.loan.order.dto.OrderCreateReq;
import com.loan.order.dto.OrderStatusReq;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.reward.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 服务工单服务：建单（谁建单归谁）/ 分页 / 详情 / 状态流转。
 *
 * <p>状态机：NEW 新建 → IN_SERVICE 服务中 → DEAL 已成交 / CANCEL 已取消；
 * DEAL → REFUND 已退款（REFUND 联动奖励冲正，奖励结算在奖励模块处理）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ServiceOrderMapper orderMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final BankProductMapper bankProductMapper;
    private final RewardService rewardService;
    private final BusinessNameService businessNameService;

    /** 合法状态集合 */
    private static final List<String> VALID_STATUS = Arrays.asList(
            ServiceOrder.STATUS_NEW, ServiceOrder.STATUS_IN_SERVICE, ServiceOrder.STATUS_DEAL,
            ServiceOrder.STATUS_CANCEL, ServiceOrder.STATUS_REFUND);

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<ServiceOrder, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", ServiceOrder::getCreatedAt);
        ORDER_FIELDS.put("updatedAt", ServiceOrder::getUpdatedAt);
        ORDER_FIELDS.put("dealTime", ServiceOrder::getDealTime);
        ORDER_FIELDS.put("dealAmount", ServiceOrder::getDealAmount);
    }

    /**
     * 新建工单（谁建单归谁；来源 MANUAL / OFFLINE_SUPPLEMENT）。
     *
     * @param req          请求
     * @param operatorCode 操作人工号（归属顾问）
     * @param operatorName 操作人姓名
     * @return 工单号（业务唯一ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(OrderCreateReq req, String operatorCode, String operatorName) {
        if (!StringUtils.hasText(req.getClientCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户必选");
        }
        if (!StringUtils.hasText(req.getCustomerGroup())
                || !Arrays.asList("ENTERPRISE", "PERSONAL").contains(req.getCustomerGroup())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客群必填且只能为 ENTERPRISE/PERSONAL");
        }
        ClientProfile client = clientProfileMapper.selectOne(
                new LambdaQueryWrapper<ClientProfile>().eq(ClientProfile::getClientCode, req.getClientCode()));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户不存在");
        }
        if (StringUtils.hasText(req.getBankProductCode())) {
            BankProduct product = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                    .eq(BankProduct::getProductCode, req.getBankProductCode()));
            if (product == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "关联产品不存在");
            }
        }

        ServiceOrder order = new ServiceOrder();
        order.setOrderNo(BizIdGenerator.generate("order"));
        order.setClientProfileCode(client.getClientCode());
        order.setCustomerGroup(req.getCustomerGroup());
        order.setBankProductCode(req.getBankProductCode());
        order.setOwnerStaffCode(operatorCode);
        order.setCustomerRemark(req.getCustomerRemark());
        order.setInternalRemark(req.getInternalRemark());
        order.setPayType(req.getPayType());
        order.setStatus(ServiceOrder.STATUS_NEW);
        order.setSource(StringUtils.hasText(req.getSource()) ? req.getSource() : ServiceOrder.SOURCE_MANUAL);
        order.setSourceOrderNo(req.getSourceOrderNo());
        order.setRewardSettledFlag(0);
        order.setCreatedBy(operatorName);
        order.setUpdatedBy(operatorName);
        orderMapper.insert(order);
        return order.getOrderNo();
    }

    /**
     * 工单分页（角色决定可见范围：老板/主管看全部，顾问只看自己的）。
     *
     * @param status     状态（可选）
     * @param keyword    关键字：工单号 / 客户编码（可选）
     * @param mineOnly   仅我的工单
     * @param roleCode   当前角色
     * @param userNo     当前工号
     * @param page       页码
     * @param size       每页大小
     * @return 工单分页（含客户名 / 产品名）
     */
    public PageResult<Map<String, Object>> page(String status, String keyword, boolean mineOnly,
                                                String roleCode, String userNo, int page, int size,
                                                String orderBy, String orderDir) {
        LambdaQueryWrapper<ServiceOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ServiceOrder::getStatus, status);
        }
        if (mineOnly || !isManager(roleCode)) {
            wrapper.eq(ServiceOrder::getOwnerStaffCode, userNo);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 工单号模糊；客户姓名 / 企业名 / 手机号（SHA-256 哈希精确）命中则按客户编码追加匹配。
            // 不再按客户内部编码(客户ID)模糊，统一以「姓名/手机号/企业名」关键字查询。
            List<String> nameCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                            .like(ClientProfile::getContactName, kw)
                            .or().like(ClientProfile::getEnterpriseName, kw))
                    .stream().map(ClientProfile::getClientCode).filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            List<String> phoneCodes = new ArrayList<>();
            if (kw.matches("\\d{11}")) {
                String hash = HashUtils.sha256Hex(kw);
                phoneCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                                .eq(ClientProfile::getPhoneHash, hash))
                        .stream().map(ClientProfile::getClientCode).filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            }
            java.util.Set<String> matchedCodes = new java.util.LinkedHashSet<>();
            matchedCodes.addAll(nameCodes);
            matchedCodes.addAll(phoneCodes);
            wrapper.and(w -> {
                w.like(ServiceOrder::getOrderNo, kw);
                if (!matchedCodes.isEmpty()) {
                    w.or().in(ServiceOrder::getClientProfileCode, matchedCodes);
                }
            });
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, ServiceOrder::getCreatedAt);
        Page<ServiceOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), toViews(result.getRecords()));
    }

    /**
     * 工单详情（按业务唯一ID）。
     *
     * @param orderNo 工单号
     * @return 详情视图（含客户名 / 产品名）
     */
    public Map<String, Object> detail(String orderNo) {
        ServiceOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<ServiceOrder>().eq(ServiceOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工单不存在");
        }
        Map<String, Object> view = toViews(java.util.Collections.singletonList(order)).get(0);
        // 补充内部字段
        view.put("internalRemark", order.getInternalRemark());
        view.put("rewardSettledFlag", order.getRewardSettledFlag());
        view.put("createdBy", order.getCreatedBy());
        view.put("createdAt", order.getCreatedAt());
        return view;
    }

    /**
     * 工单状态流转。
     *
     * @param orderNo  工单号
     * @param req      目标状态（+ 成交金额/时间）
     * @param operator 操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String orderNo, OrderStatusReq req, String operator) {
        String target = req == null ? null : req.getStatus();
        if (!StringUtils.hasText(target) || !VALID_STATUS.contains(target)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标状态非法");
        }
        ServiceOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<ServiceOrder>().eq(ServiceOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工单不存在");
        }
        String current = order.getStatus();
        if (target.equals(current)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "工单已是该状态");
        }
        switch (current) {
            case ServiceOrder.STATUS_NEW:
                if (!target.equals(ServiceOrder.STATUS_IN_SERVICE)
                        && !target.equals(ServiceOrder.STATUS_CANCEL)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "NEW 仅可流转 IN_SERVICE / CANCEL");
                }
                break;
            case ServiceOrder.STATUS_IN_SERVICE:
                if (!target.equals(ServiceOrder.STATUS_DEAL)
                        && !target.equals(ServiceOrder.STATUS_CANCEL)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "IN_SERVICE 仅可流转 DEAL / CANCEL");
                }
                break;
            case ServiceOrder.STATUS_DEAL:
                if (!target.equals(ServiceOrder.STATUS_REFUND)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "DEAL 仅可流转 REFUND");
                }
                break;
            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不可流转");
        }

        order.setStatus(target);
        if (ServiceOrder.STATUS_DEAL.equals(target)) {
            if (req.getDealAmount() == null || req.getDealAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "成交金额必填且大于 0");
            }
            order.setDealAmount(req.getDealAmount());
            order.setDealTime(req.getDealTime() == null ? LocalDateTime.now() : req.getDealTime());
            order.setRewardSettledFlag(0); // 待奖励模块结算
        }
        order.setUpdatedBy(operator);
        orderMapper.updateById(order);
        // 成交后自动结算推荐奖励（幂等；无推荐关系/无生效规则则不生成）
        if (ServiceOrder.STATUS_DEAL.equals(target)) {
            rewardService.settleForOrder(orderNo, operator, req.getRewardAmount());
        }
    }

    /**
     * 是否管理角色（老板 / 主管）。
     */
    private boolean isManager(String roleCode) {
        // 管理角色：老板 / 部门经理 / 运营 / 超管（阶段3 B4：补齐 OPERATOR/SUPER_ADMIN）
        return "BOSS".equalsIgnoreCase(roleCode)
                || "DEPT_MANAGER".equalsIgnoreCase(roleCode)
                || "OPERATOR".equalsIgnoreCase(roleCode)
                || "SUPER_ADMIN".equalsIgnoreCase(roleCode)
                || "SUPER".equalsIgnoreCase(roleCode);
    }

    /**
     * 实体 → 视图（含客户名 / 产品名）。
     */
    private List<Map<String, Object>> toViews(List<ServiceOrder> orders) {
        if (orders.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 批量查客户名 / 产品名
        List<String> clientCodes = orders.stream().map(ServiceOrder::getClientProfileCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> clientNameMap = businessNameService.clientNames(clientCodes);

        List<String> productCodes = orders.stream().map(ServiceOrder::getBankProductCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> productNameMap = businessNameService.productNames(productCodes);

        List<String> staffCodes = orders.stream().map(ServiceOrder::getOwnerStaffCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> staffNameMap = businessNameService.staffNames(staffCodes);

        return orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("clientProfileCode", o.getClientProfileCode());
            m.put("clientName", clientNameMap.get(o.getClientProfileCode()));
            m.put("customerGroup", o.getCustomerGroup());
            m.put("bankProductCode", o.getBankProductCode());
            m.put("bankProductName", productNameMap.get(o.getBankProductCode()));
            m.put("ownerStaffCode", o.getOwnerStaffCode());
            m.put("ownerStaffName", staffNameMap.get(o.getOwnerStaffCode()));
            m.put("dealAmount", o.getDealAmount());
            m.put("dealTime", o.getDealTime());
            m.put("customerRemark", o.getCustomerRemark());
            m.put("payType", o.getPayType());
            m.put("status", o.getStatus());
            m.put("source", o.getSource());
            m.put("sourceOrderNo", o.getSourceOrderNo());
            m.put("updatedAt", o.getUpdatedAt());
            return m;
        }).collect(Collectors.toList());
    }
}
