package com.riskplatform.gateway.adapter.order;

import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.gateway.application.OrderQueryService;
import com.riskplatform.gateway.domain.RiskOrderView;
import com.riskplatform.gateway.infrastructure.order.InMemoryOrderQueryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单查询 REST 适配器 Web 层测试（R10.4/R10.5）。
 *
 * <p>使用 MockMvc standaloneSetup（无需数据库），覆盖：
 * <ul>
 *   <li>正常分页返回订单及最终决策；</li>
 *   <li>无匹配返回空列表（total=0）；</li>
 *   <li>未提供任何过滤条件返回 400；</li>
 *   <li>起始时间晚于结束时间返回 400；</li>
 *   <li>每页大小超过 200 返回 400。</li>
 * </ul>
 */
class OrderControllerTest {

    private InMemoryOrderQueryStore store;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = new InMemoryOrderQueryStore();
        OrderQueryService service = new OrderQueryService(store);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void query_byMerchant_returnsPagedOrders() throws Exception {
        store.put(new RiskOrderView("e1", "e1", "B2B_RECV", "M001", 1000L, "PASS"));
        store.put(new RiskOrderView("e2", "e2", "B2B_RECV", "M001", 2000L, "REJECT"));
        store.put(new RiskOrderView("e3", "e3", "B2B_RECV", "M002", 3000L, "PASS"));

        mockMvc.perform(get("/api/v1/orders").param("merchantId", "M001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                // 事件时间倒序，最新（e2, 2000）在前
                .andExpect(jsonPath("$.data[0].eventId").value("e2"))
                .andExpect(jsonPath("$.data[0].finalDecision").value("REJECT"));
    }

    @Test
    void query_noMatch_returnsEmptyList() throws Exception {
        store.put(new RiskOrderView("e1", "e1", "B2B_RECV", "M001", 1000L, "PASS"));

        mockMvc.perform(get("/api/v1/orders").param("merchantId", "NOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void query_byTimeRange_filtersByEventTime() throws Exception {
        store.put(new RiskOrderView("e1", "e1", "T", "M1", 1000L, "PASS"));
        store.put(new RiskOrderView("e2", "e2", "T", "M1", 5000L, "PASS"));
        store.put(new RiskOrderView("e3", "e3", "T", "M1", 9000L, "PASS"));

        mockMvc.perform(get("/api/v1/orders")
                        .param("startTimeMs", "2000")
                        .param("endTimeMs", "8000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].eventId").value("e2"));
    }

    @Test
    void query_noFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.filter").exists());
    }

    @Test
    void query_invertedTimeRange_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("startTimeMs", "9000")
                        .param("endTimeMs", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.timeRange").exists());
    }

    @Test
    void query_pageSizeOverMax_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .param("merchantId", "M001")
                        .param("pageSize", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.pageSize").exists());
    }
}
