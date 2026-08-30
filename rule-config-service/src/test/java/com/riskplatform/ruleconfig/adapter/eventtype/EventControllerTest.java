package com.riskplatform.ruleconfig.adapter.eventtype;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.ValidationException;
import com.riskplatform.common.web.GlobalExceptionHandler;
import com.riskplatform.ruleconfig.application.eventtype.EventTypeAppService;
import com.riskplatform.ruleconfig.application.scenario.ScenarioAppService;
import com.riskplatform.ruleconfig.domain.eventtype.EventEngineStatusQuery;
import com.riskplatform.ruleconfig.domain.eventtype.EventKind;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
import com.riskplatform.ruleconfig.domain.scenario.Scenario;
import com.riskplatform.ruleconfig.domain.scenario.ScenarioStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事件（参数管理）REST 适配器 Web 层测试（standalone MockMvc，无需数据库）。
 *
 * <p>覆盖 risk-console-redesign R2 新增端点：场景→事件树、按场景列表、创建/编辑/删除、
 * 批量导入逐条校验、引擎可执行状态。
 */
class EventControllerTest {

    private EventTypeAppService appService;
    private ScenarioAppService scenarioAppService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appService = Mockito.mock(EventTypeAppService.class);
        scenarioAppService = Mockito.mock(ScenarioAppService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EventController(appService, scenarioAppService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static EventType eventWithId(long id, String code, long scenarioId) {
        EventType e = EventType.create(code, code + "名", scenarioId,
                EnumSet.of(EventPurpose.COMPUTE, EventPurpose.DECISION), EventKind.FACT);
        e.assignId(id);
        return e;
    }

    @Test
    void scenarioTree_groupsEventsUnderScenario() throws Exception {
        Scenario scenario = Scenario.rehydrate(7L, "SCN", "场景甲", ScenarioStatus.ENABLED, List.of());
        when(scenarioAppService.list()).thenReturn(List.of(scenario));
        when(appService.list()).thenReturn(List.of(eventWithId(1L, "EVT1", 7L),
                eventWithId(2L, "EVT2", 99L)));

        mockMvc.perform(get("/api/v1/scenarios/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].events.length()").value(1))
                .andExpect(jsonPath("$[0].events[0].code").value("EVT1"));
    }

    @Test
    void listEvents_byScenario_returnsScopedEvents() throws Exception {
        when(appService.listByScenario(7L)).thenReturn(List.of(eventWithId(1L, "EVT1", 7L)));
        mockMvc.perform(get("/api/v1/events").param("scenarioId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].purposes.length()").value(2));
    }

    @Test
    void createEvent_returnsView() throws Exception {
        when(appService.create(any(), any(), any(), any(), any()))
                .thenReturn(eventWithId(10L, "EVT_NEW", 7L));
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EVT_NEW\",\"name\":\"新事件\",\"scenarioId\":7,"
                                + "\"purposes\":[\"COMPUTE\",\"DECISION\"],\"eventKind\":\"FACT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EVT_NEW"))
                .andExpect(jsonPath("$.eventKind").value("FACT"));
    }

    @Test
    void createEvent_missingRequiredField_returns400WithFieldName() throws Exception {
        when(appService.create(any(), any(), any(), any(), any()))
                .thenThrow(ValidationException.builder().field("scenarioId", "必填").build());
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"EVT_NEW\",\"name\":\"新事件\","
                                + "\"purposes\":[\"COMPUTE\"],\"eventKind\":\"FACT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.scenarioId").exists());
    }

    @Test
    void createEvent_duplicateCode_returns409() throws Exception {
        when(appService.create(any(), any(), any(), any(), any()))
                .thenThrow(BizException.duplicate("事件 code 已存在: DUP"));
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DUP\",\"name\":\"重复\",\"scenarioId\":7,"
                                + "\"purposes\":[\"COMPUTE\"],\"eventKind\":\"FACT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS.DUPLICATE"));
    }

    @Test
    void updateEvent_returnsView() throws Exception {
        when(appService.edit(eq(10L), any(), any(), any(), any()))
                .thenReturn(eventWithId(10L, "EVT1", 7L));
        mockMvc.perform(put("/api/v1/events/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\",\"scenarioId\":7,"
                                + "\"purposes\":[\"DECISION\"],\"eventKind\":\"DIMENSION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void deleteEvent_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/events/10"))
                .andExpect(status().isOk());
    }

    @Test
    void importEvents_perRecordValidation_returnsCountsAndReasons() throws Exception {
        EventTypeAppService.ImportResult result = new EventTypeAppService.ImportResult(
                List.of(eventWithId(1L, "OK1", 7L)),
                List.of(new EventTypeAppService.ImportFailure(1, "BAD", "purposes: 至少选择一个事件用途")));
        when(appService.importEvents(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/events/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"code\":\"OK1\",\"name\":\"好\",\"scenarioId\":7,"
                                + "\"purposes\":[\"COMPUTE\"],\"eventKind\":\"FACT\"},"
                                + "{\"code\":\"BAD\",\"name\":\"坏\",\"scenarioId\":7,"
                                + "\"purposes\":[],\"eventKind\":\"FACT\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failures[0].reason").value("purposes: 至少选择一个事件用途"));
    }

    @Test
    void engineStatus_returnsStatus() throws Exception {
        when(appService.engineStatus(10L)).thenReturn(EventEngineStatusQuery.Status.EXECUTABLE);
        mockMvc.perform(get("/api/v1/events/10/engine-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(10))
                .andExpect(jsonPath("$.engineStatus").value("EXECUTABLE"));
    }
}
