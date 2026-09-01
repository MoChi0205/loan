package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.order.service.OrderService;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序端服务单 / 工单：客户查自己的单；企业员工按四维筛选查全量（C7）。
 *
 * <p><b>C7 四维筛选：</b>客户姓名 / 手机号 / 工单状态 / 时间区间，
 * 客户仅允许状态与时间（无权检索他人工单，后端强制忽略其余参数）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniOrderService {

    private final ServiceOrderMapper orderMapper;
    private final OrderService orderService;
    private final ClientProfileMapper clientProfileMapper;
    private final StaffMapper staffMapper;

    /* ==================== C7：四维筛选 ==================== */

    /**
     * 客户服务单（C7）：仅状态 + 时间区间，强制以登录态 clientCode 过滤。
     *
     * @param clientCode 客户编码（来自登录态）
     * @param status     工单状态（空表示不限）
     * @param dateRange  日期区间：today / 7d / 30d / all
     * @param page       页码
     * @param size       每页大小
     * @return 工单分页
     */
    public PageResult<Map<String, Object>> myOrdersByFilter(String clientCode, String status,
                                                            String dateRange, int page, int size) {
        LambdaQueryWrapper<ServiceOrder> wrapper = new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getClientProfileCode, clientCode);
        if (StringUtils.hasText(status)) {
            wrapper.eq(ServiceOrder::getStatus, status);
        }
        LocalDateTime from = resolveDateFrom(dateRange);
        if (from != null) {
            wrapper.ge(ServiceOrder::getCreatedAt, from);
        }
        wrapper.orderByDesc(ServiceOrder::getCreatedAt);
        Page<ServiceOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), toOrderRows(result.getRecords()));
    }

    /**
     * 企业员工工单（C7 四维）：客户姓名 / 手机号 / 状态 / 时间区间。
     *
     * <p>采用两步查询：先在 t_client_profile 上按客户维度条件筛出 clientCode 集合，
     * 再以 {@code IN} 条件查 t_service_order，避免跨表联表索引失效。
     * 手机号在库内为 SHA-256 摘要（phone_hash），精确匹配前对入参做同样摘要。
     *
     * @param status     工单状态
     * @param clientName 客户姓名（模糊）
     * @param phone      手机号（精确，摘要比对）
     * @param dateRange  日期区间
     * @param page       页码
     * @param size       每页大小
     * @return 工单分页
     */
    public PageResult<Map<String, Object>> allOrders(String status, String clientName, String phone,
                                                     String dateRange, int page, int size) {
        // 第一步：按客户维度条件筛 clientCode
        List<String> clientCodes = null;
        if (StringUtils.hasText(clientName) || StringUtils.hasText(phone)) {
            LambdaQueryWrapper<ClientProfile> cw = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(clientName)) {
                cw.like(ClientProfile::getContactName, clientName.trim());
            }
            if (StringUtils.hasText(phone)) {
                cw.eq(ClientProfile::getPhoneHash, sha256(phone.trim()));
            }
            List<ClientProfile> clients = clientProfileMapper.selectList(cw);
            clientCodes = clients.stream().map(ClientProfile::getClientCode).collect(Collectors.toList());
            if (clientCodes.isEmpty()) {
                return PageResult.build(page, size, 0L, new ArrayList<Map<String, Object>>());
            }
        }

        // 第二步：查工单（客户集合 + 状态 + 日期区间）
        LambdaQueryWrapper<ServiceOrder> wrapper = new LambdaQueryWrapper<>();
        if (clientCodes != null) {
            wrapper.in(ServiceOrder::getClientProfileCode, clientCodes);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ServiceOrder::getStatus, status);
        }
        LocalDateTime from = resolveDateFrom(dateRange);
        if (from != null) {
            wrapper.ge(ServiceOrder::getCreatedAt, from);
        }
        wrapper.orderByDesc(ServiceOrder::getCreatedAt);
        Page<ServiceOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);

        // 第三步：补客户维度展示字段
        Map<String, ClientProfile> profileMap = loadProfiles(result.getRecords());
        List<String> staffCodes = result.getRecords().stream()
                .map(ServiceOrder::getOwnerStaffCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> staffNameMap = loadStaffNames(staffCodes);
        List<Map<String, Object>> rows = result.getRecords().stream().map(o -> {
            Map<String, Object> m = toOrderRow(o);
            ClientProfile p = profileMap.get(o.getClientProfileCode());
            if (p != null) {
                m.put("clientName", p.getContactName());
                m.put("phone", maskPhone(p.getPhone()));
            }
            if (StringUtils.hasText(o.getOwnerStaffCode())) {
                m.put("ownerStaffName", staffNameMap.getOrDefault(o.getOwnerStaffCode(), o.getOwnerStaffCode()));
            }
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), rows);
    }

    /* ==================== 私有工具 ==================== */

    private List<Map<String, Object>> toOrderRows(List<ServiceOrder> list) {
        return list.stream().map(this::toOrderRow).collect(Collectors.toList());
    }

    private Map<String, Object> toOrderRow(ServiceOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("customerGroup", o.getCustomerGroup());
        m.put("bankProductCode", o.getBankProductCode());
        m.put("serviceType", resolveServiceType(o));
        m.put("status", o.getStatus());
        m.put("dealAmount", o.getDealAmount());
        m.put("dealTime", o.getDealTime());
        m.put("payType", o.getPayType());
        m.put("ownerStaffCode", o.getOwnerStaffCode());
        m.put("createdAt", o.getCreatedAt());
        return m;
    }

    /**
     * 服务类型（前端列表标题用）：按关联产品与来源推导，缺省为「服务单」。
     *
     * <p>TODO 接入 t_bank_product 按 bankProductCode 取真实产品分类
     * （融资 / 资料下载 / 额度确认 / 投诉建议）。
     */
    private String resolveServiceType(ServiceOrder o) {
        String source = o.getSource();
        if (StringUtils.hasText(source)) {
            if ("DOWNLOAD".equals(source)) return "资料下载";
            if ("COMPLAINT".equals(source)) return "投诉建议";
        }
        return "融资";
    }

    private Map<String, ClientProfile> loadProfiles(List<ServiceOrder> records) {
        List<String> codes = records.stream()
                .map(ServiceOrder::getClientProfileCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (codes.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<ClientProfile> profiles = clientProfileMapper.selectList(
                new LambdaQueryWrapper<ClientProfile>().in(ClientProfile::getClientCode, codes));
        Map<String, ClientProfile> map = new LinkedHashMap<>();
        for (ClientProfile p : profiles) {
            map.put(p.getClientCode(), p);
        }
        return map;
    }

    /** 员工工号集合 → 姓名 Map（批量，防 N+1；查不到保留工号） */
    private Map<String, String> loadStaffNames(List<String> staffCodes) {
        Map<String, String> map = new LinkedHashMap<>();
        if (staffCodes == null || staffCodes.isEmpty()) {
            return map;
        }
        List<Staff> staffs = staffMapper.selectList(
                new LambdaQueryWrapper<Staff>().in(Staff::getStaffCode, staffCodes));
        for (Staff s : staffs) {
            map.put(s.getStaffCode(), StringUtils.hasText(s.getStaffName()) ? s.getStaffName() : s.getStaffCode());
        }
        for (String code : staffCodes) {
            map.putIfAbsent(code, code);
        }
        return map;
    }

    /** 日期区间 → 起始时间（null 表示不限） */
    private LocalDateTime resolveDateFrom(String dateRange) {
        if (!StringUtils.hasText(dateRange) || "all".equals(dateRange)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (dateRange) {
            case "today": return now.toLocalDate().atStartOfDay();
            case "7d":    return now.minusDays(7);
            case "30d":   return now.minusDays(30);
            default:      return null;
        }
    }

    /** 手机号掩码：138****0001 */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** SHA-256 摘要（与 AuthService / SmsService 保持一致） */
    private String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "摘要计算失败");
        }
    }

    /**
     * 我的服务单列表（客户视角）。
     *
     * @param clientCode 客户编码
     * @param status     状态（可选）
     * @param page       页码
     * @param size       每页大小
     * @return 工单分页
     */
    public PageResult<Map<String, Object>> myOrders(String clientCode, String status, int page, int size) {
        LambdaQueryWrapper<ServiceOrder> wrapper = new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getClientProfileCode, clientCode)
                .orderByDesc(ServiceOrder::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(ServiceOrder::getStatus, status);
        }
        Page<ServiceOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("customerGroup", o.getCustomerGroup());
            m.put("status", o.getStatus());
            m.put("dealAmount", o.getDealAmount());
            m.put("dealTime", o.getDealTime());
            m.put("payType", o.getPayType());
            m.put("createdAt", o.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 服务单详情（C7 角色二分）。
     *
     * <p>归属校验语义：客户 CUSTOMER 必传 clientCode 且必须等于工单归属（clientProfileCode）；
     * 企业员工 STAFF 传 null / 空串表示「全量可看」，跳过归属校验（同 allOrders）。
     *
     * @param orderNo    工单号
     * @param clientCode 客户编码（客户视角必传；员工视角传 null / 空串跳过校验）
     * @return 工单详情
     */
    public Map<String, Object> myOrderDetail(String orderNo, String clientCode) {
        ServiceOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "工单不存在");
        }
        // 归属校验：仅客户视角（clientCode 非空）执行；员工全量视角（null/空）跳过
        if (StringUtils.hasText(clientCode) && !clientCode.equals(order.getClientProfileCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该工单");
        }
        return orderService.detail(orderNo);
    }
}
