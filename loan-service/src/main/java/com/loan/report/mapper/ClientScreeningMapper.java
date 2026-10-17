package com.loan.report.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.report.entity.ClientScreening;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.stream.Collectors;
/** 渠道客户范围与报告查询共享同一 Wrapper 权限来源。 */
@Mapper
public interface ClientScreeningMapper extends BaseMapper<ClientScreening> {
    default List<ClientProfile> channelClients(String channelNo) {
        return SqlHelper.execute(ClientProfile.class, mapper -> ((ClientProfileMapper) mapper).channelOwnedClients(channelNo));
    }
    default Page<ClientScreening> selectChannelOwnedPage(Page<ClientScreening> page, String channelNo,
            String status, String grade, String keyword, String phoneHash, String orderDir) {
        List<ClientProfile> clients = channelClients(channelNo);
        if (clients.isEmpty()) { page.setTotal(0); page.setRecords(Collections.emptyList()); return page; }
        List<String> codes = clients.stream().map(ClientProfile::getClientCode).collect(Collectors.toList());
        LambdaQueryWrapper<ClientScreening> q = new LambdaQueryWrapper<ClientScreening>()
            .in(ClientScreening::getClientProfileCode, codes)
            .eq(StringUtils.hasText(status), ClientScreening::getStatus, status)
            .eq(StringUtils.hasText(grade), ClientScreening::getGrade, grade);
        if (StringUtils.hasText(keyword)) {
            List<String> named = clients.stream().filter(c -> (c.getContactName() != null && c.getContactName().contains(keyword))
                || (c.getEnterpriseName() != null && c.getEnterpriseName().contains(keyword))
                || (phoneHash != null && phoneHash.equals(c.getPhoneHash()))).map(ClientProfile::getClientCode).collect(Collectors.toList());
            String date = keyword.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-");
            q.and(w -> {
                w.like(ClientScreening::getReportNo, keyword);
                if (!named.isEmpty()) w.or().in(ClientScreening::getClientProfileCode, named);
                try {
                    java.time.LocalDate day = java.time.LocalDate.parse(date.matches("[0-9]{8}") ?
                        date.substring(0,4) + "-" + date.substring(4,6) + "-" + date.substring(6) : date);
                    w.or().ge(ClientScreening::getCreatedAt, day.atStartOfDay()).lt(ClientScreening::getCreatedAt, day.plusDays(1).atStartOfDay());
                } catch (java.time.format.DateTimeParseException ignored) { }
            });
        }
        q.orderBy(true, "asc".equalsIgnoreCase(orderDir), ClientScreening::getCreatedAt);
        return selectPage(page, q);
    }
    default List<ClientScreening> selectChannelOwnedByReportNos(String channelNo, Collection<String> reportNos) {
        List<String> codes = channelClients(channelNo).stream().map(ClientProfile::getClientCode).collect(Collectors.toList());
        if (codes.isEmpty() || reportNos == null || reportNos.isEmpty()) return Collections.emptyList();
        return selectList(new LambdaQueryWrapper<ClientScreening>().in(ClientScreening::getClientProfileCode, codes)
            .in(ClientScreening::getReportNo, reportNos));
    }
    default int countChannelOwnedReport(String channelNo, String reportNo) {
        List<String> codes = channelClients(channelNo).stream().map(ClientProfile::getClientCode).collect(Collectors.toList());
        return codes.isEmpty() ? 0 : selectCount(new LambdaQueryWrapper<ClientScreening>()
            .in(ClientScreening::getClientProfileCode, codes).eq(ClientScreening::getReportNo, reportNo)).intValue();
    }
}
