package com.loan.plan.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.loan.plan.entity.ChannelUserList;
import com.loan.plan.mapper.ChannelUserListMapper;
import com.loan.plan.model.ChannelUserListUpdateRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChannelUserListService} 单元测试。
 *
 * @author loan-platform
 */
class ChannelUserListServiceTest {

    /** 离线单测不启动 Spring，显式注册 Lambda 字段元数据。 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                ChannelUserList.class);
    }

    /** 批量新增应在内存去重并只调用一次批量写库。 */
    @Test
    void addUsesOneBatchInsertAndGeneratesFixedLengthCode() {
        ChannelUserListMapper mapper = mock(ChannelUserListMapper.class);
        when(mapper.insertIgnoreBatch(any())).thenReturn(1);
        ChannelUserListService service = new ChannelUserListService(mapper);

        int added = service.add("CH001", "PERSONAL", "LOCAL_BLACK",
                Arrays.asList("13800138000", "13800138000"), "测试员");

        ArgumentCaptor<List<ChannelUserList>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertIgnoreBatch(captor.capture());
        verify(mapper, never()).selectCount(any());
        assertEquals(1, added);
        assertEquals(1, captor.getValue().size());
        assertTrue(captor.getValue().get(0).getListCode().matches("culist[0-9a-z]{10}"));
    }

    /** 空批量删除不访问数据库。 */
    @Test
    void emptyBatchDeleteDoesNotAccessDatabase() {
        ChannelUserListMapper mapper = mock(ChannelUserListMapper.class);
        ChannelUserListService service = new ChannelUserListService(mapper);
        assertEquals(0, service.batchDelete(Collections.emptyList()));
        verify(mapper, never()).delete(any());
    }

    /** 修改必须以业务编码作为唯一定位条件。 */
    @Test
    void updateUsesBusinessCodeInsteadOfPhysicalId() {
        ChannelUserListMapper mapper = mock(ChannelUserListMapper.class);
        ChannelUserList current = new ChannelUserList();
        current.setListCode("culist0000000001");
        current.setCustomerGroup("PERSONAL");
        when(mapper.selectOne(any())).thenReturn(current);
        when(mapper.update(any(), any())).thenReturn(1);
        ChannelUserListService service = new ChannelUserListService(mapper);
        ChannelUserListUpdateRequest request = new ChannelUserListUpdateRequest();
        request.setChannelCode("CH002");
        request.setCustomerGroup("PERSONAL");
        request.setListType("LOCAL_WHITE");
        request.setListKey("13900139000");

        service.update("culist0000000001", request);

        verify(mapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }
}
