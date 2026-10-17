package com.loan.common;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.loan.plan.entity.ChannelUserList;
import com.loan.plan.mapper.ChannelUserListMapper;
import com.loan.product.entity.BankProductCity;
import com.loan.product.mapper.BankProductCityMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 批量 Wrapper 只预查一次；唯一键冲突必须区分并发重复与业务编码碰撞。 */
class BatchNaturalKeyMapperTest {
    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), ChannelUserList.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), BankProductCity.class);
    }

    @Test void channelBatchQueriesOnceAndSkipsExistingAndInputDuplicates() {
        ChannelUserListMapper mapper = mock(ChannelUserListMapper.class, CALLS_REAL_METHODS);
        ChannelUserList old = channel("old"), fresh = channel("new");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(old));
        when(mapper.insert(fresh)).thenReturn(1);
        assertEquals(1, mapper.insertIgnoreBatch(Arrays.asList(old, fresh, channel("new"))));
        verify(mapper, times(1)).selectList(any());
        verify(mapper, never()).selectCount(any());
        verify(mapper, times(1)).insert(any());
    }

    @Test void cityBatchQueriesOnceAndSkipsDuplicates() {
        BankProductCityMapper mapper = mock(BankProductCityMapper.class, CALLS_REAL_METHODS);
        BankProductCity old = city("old"), fresh = city("new");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(old));
        when(mapper.insert(fresh)).thenReturn(1);
        assertEquals(1, mapper.insertIgnoreBatch(Arrays.asList(old, fresh, city("new"))));
        verify(mapper, times(1)).selectList(any());
        verify(mapper, never()).selectCount(any());
        verify(mapper, times(1)).insert(any());
    }

    @Test void cityConcurrentNaturalKeyDuplicateIsIgnored() {
        BankProductCityMapper mapper = mock(BankProductCityMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectOne(any())).thenReturn(city("new"));
        clearInvocations(mapper);
        assertEquals(0, mapper.insertIgnoreBatch(Collections.singletonList(city("new"))));
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> query =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().endsWith("FOR UPDATE"));
    }

    @Test void channelBusinessCodeCollisionIsNotSilentlyIgnored() {
        ChannelUserListMapper mapper = mock(ChannelUserListMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectOne(any())).thenReturn(null);
        assertThrows(DuplicateKeyException.class,
                () -> mapper.insertIgnoreBatch(Collections.singletonList(channel("new"))));
    }

    private static ChannelUserList channel(String key) {
        ChannelUserList row = new ChannelUserList(); row.setChannelCode("channel");
        row.setCustomerGroup("PERSONAL"); row.setListType("LOCAL_BLACK"); row.setListKey(key); return row;
    }
    private static BankProductCity city(String name) {
        BankProductCity row = new BankProductCity(); row.setProductCode("product");
        row.setProvince("province"); row.setCity(name); return row;
    }
}
