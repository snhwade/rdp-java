package com.riskplatform.ruleconfig.adapter.eventtype;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.ruleconfig.application.eventtype.EventTypeAppService;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事件类型 REST 适配器 Web 层测试（standalone MockMvc，无需数据库）。
 */
class EventTypeControllerTest {

    private EventTypeAppService appService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appService = Mockito.mock(EventTypeAppService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EventTypeController(appService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_returnsView() throws Exception {
        when(appService.create(any(), any())).thenReturn(EventType.create("B2B_RECV", "B2B 收款"));
        mockMvc.perform(post("/api/v1/event-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"B2B_RECV\",\"name\":\"B2B 收款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("B2B_RECV"))
                .andExpect(jsonPath("$.status").value("ENABLED"));
    }

    @Test
    void create_duplicate_returns409WithErrorBody() throws Exception {
        when(appService.create(any(), any())).thenThrow(BizException.duplicate("code 已存在"));
        mockMvc.perform(post("/api/v1/event-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DUP\",\"name\":\"重复\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS.DUPLICATE"));
    }

    @Test
    void create_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/event-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returnsArray() throws Exception {
        when(appService.list()).thenReturn(List.of(EventType.create("A", "甲"), EventType.create("B", "乙")));
        mockMvc.perform(get("/api/v1/event-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
