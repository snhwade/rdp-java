package com.riskplatform.bff.adapter;

import com.riskplatform.bff.application.BffAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 参数管理（事件 / 事件字段 / 验证策略）BFF 透传聚合（risk-console-redesign R2/R4/R5）。
 *
 * <p>为风控控制台「事件管理」「事件字段」「验证策略」页提供页面级接口，转发至规则配置服务
 * {@code /api/v1/**} 并透传 JWT 与字段级错误（R14.1/R14.2）。命名中性：本控制器标识与路径
 * 不含任何产品厂商专有名词。
 *
 * <p>覆盖资源前缀：
 * <ul>
 *   <li>{@code /events}、{@code /events/**}：事件 CRUD、批量导入、引擎状态、事件字段子资源；</li>
 *   <li>{@code /verify-strategies}、{@code /verify-strategies/**}：验证策略 CRUD 与关联关系。</li>
 * </ul>
 *
 * <p>说明：场景树 {@code /scenarios/tree} 与字段库 {@code /fields} 分别由既有透传控制器覆盖，
 * 本控制器前缀与之互不冲突。事件批量导入请求体为 JSON 数组，故 POST 体用 {@link Object} 承载。
 */
@RestController
@RequestMapping("/bff/api/v1")
public class ParamConsoleBffController {

    private final BffAggregationService aggregation;

    public ParamConsoleBffController(BffAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    private static String auth(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    /** 取 /bff/api/v1 之后的下游路径并附带原始查询串（如 ?scenarioId=）。 */
    private static String downstreamPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf("/bff/api/v1");
        String tail = idx >= 0 ? uri.substring(idx + "/bff/api/v1".length()) : uri;
        String query = request.getQueryString();
        return "/api/v1" + tail + (query != null && !query.isBlank() ? "?" + query : "");
    }

    // —— GET：事件列表/详情/引擎状态/事件字段列表、验证策略列表/详情/关联关系 ——
    @GetMapping({
            "/events", "/events/**",
            "/verify-strategies", "/verify-strategies/**",
            "/agent-strategies", "/agent-strategies/**"
    })
    public Object paramGet(HttpServletRequest request) {
        return aggregation.ruleConfigGet(downstreamPath(request), auth(request));
    }

    /**
     * POST：事件创建、批量导入（数组体）、事件字段添加，验证策略创建。
     *
     * <p>请求体可能为对象（创建）或数组（导入），统一以 {@link Object} 透传。
     */
    @PostMapping({
            "/events", "/events/**",
            "/verify-strategies", "/verify-strategies/**",
            "/agent-strategies", "/agent-strategies/**"
    })
    public Object paramPost(@RequestBody(required = false) Object body, HttpServletRequest request) {
        return aggregation.ruleConfigPost(downstreamPath(request), body, auth(request));
    }

    // —— PUT：事件编辑、事件字段衍生标记，验证策略编辑 ——
    @PutMapping({
            "/events/**",
            "/verify-strategies/**",
            "/agent-strategies/**"
    })
    public Object paramPut(@RequestBody(required = false) Map<String, Object> body,
                           HttpServletRequest request) {
        return aggregation.ruleConfigPut(downstreamPath(request), body, auth(request));
    }

    // —— DELETE：事件删除、事件字段移除 ——
    @DeleteMapping({
            "/events/**"
    })
    public Object paramDelete(HttpServletRequest request) {
        return aggregation.ruleConfigDelete(downstreamPath(request), auth(request));
    }
}
