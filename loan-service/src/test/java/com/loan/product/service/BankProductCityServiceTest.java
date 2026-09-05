package com.loan.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.loan.product.entity.BankProductCity;
import com.loan.product.mapper.BankProductCityMapper;
import com.loan.product.model.ProductCityUpdateRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BankProductCityService} 单元测试。
 *
 * @author loan-platform
 */
class BankProductCityServiceTest {

    /** 离线单测不启动 Spring，显式注册 Lambda 字段元数据。 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                BankProductCity.class);
    }

    /** 批量绑定应在内存去重并只调用一次批量写库。 */
    @Test
    void bindUsesOneBatchInsertAndGeneratesFixedLengthCode() {
        BankProductCityMapper mapper = mock(BankProductCityMapper.class);
        when(mapper.insertIgnoreBatch(any())).thenReturn(1);
        BankProductCityService service = new BankProductCityService(mapper);
        BankProductCityService.CityItem first = city("湖北省", "武汉市");
        BankProductCityService.CityItem duplicate = city("湖北省", "武汉市");

        int added = service.bind("PRD001", Arrays.asList(first, duplicate), "测试员");

        ArgumentCaptor<List<BankProductCity>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertIgnoreBatch(captor.capture());
        verify(mapper, never()).selectCount(any());
        assertEquals(1, added);
        assertEquals(1, captor.getValue().size());
        assertTrue(captor.getValue().get(0).getProductCityCode().matches("pcity[0-9a-z]{11}"));
    }

    /** 修改必须以关系业务编码作为唯一定位条件。 */
    @Test
    void updateUsesBusinessCodeInsteadOfPhysicalId() {
        BankProductCityMapper mapper = mock(BankProductCityMapper.class);
        BankProductCity current = new BankProductCity();
        current.setProductCityCode("pcity00000000001");
        when(mapper.selectOne(any())).thenReturn(current);
        when(mapper.update(any(), any())).thenReturn(1);
        BankProductCityService service = new BankProductCityService(mapper);
        ProductCityUpdateRequest request = new ProductCityUpdateRequest();
        request.setProductCode("PRD002");
        request.setProvince("广东省");
        request.setCity("深圳市");

        service.update("pcity00000000001", request);

        verify(mapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    private BankProductCityService.CityItem city(String province, String city) {
        BankProductCityService.CityItem item = new BankProductCityService.CityItem();
        item.setProvince(province);
        item.setCity(city);
        return item;
    }
}
