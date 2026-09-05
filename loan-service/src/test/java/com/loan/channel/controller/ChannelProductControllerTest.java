package com.loan.channel.controller;

import com.loan.context.LoanUser;
import com.loan.mini.service.MiniProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 渠道 Web 产品工作区契约测试。 */
class ChannelProductControllerTest {

    private MiniProductService productService;
    private MockMvc mvc;
    private LoanUser channel;

    @BeforeEach
    void setUp() {
        productService = mock(MiniProductService.class);
        ChannelProductController controller = new ChannelProductController(productService);
        channel = new LoanUser();
        channel.setUserId(2L);
        channel.setUserNo("channel-user-no");
        channel.setUserType(LoanUser.TYPE_CHANNEL);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new com.loan.test.CurrentUserArgumentResolver())
                .build();
    }

    @Test
    void listsOnlyCurrentChannelProducts() throws Exception {
        when(productService.myProducts(2L)).thenReturn(Collections.emptyList());
        try {
            com.loan.context.UserContext.setUser(channel);
            mvc.perform(get("/api/channel/product/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray());
            verify(productService).myProducts(2L);
        } finally {
            com.loan.context.UserContext.clear();
        }
    }
}
