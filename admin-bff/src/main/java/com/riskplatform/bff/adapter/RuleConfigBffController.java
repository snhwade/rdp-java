package com.riskplatform.bff.adapter;

import com.riskplatform.bff.application.BffAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 规则配置类页面 BFF 聚合接口（R14.1）。
 *
 * <p>为前端「事件类型」配置页与指标读取提供页面级接口，转发至规则配置/指标存储服务并透传 JWT。
 * 指标定义 CRUD 等其余资源由 {@link RuleConfigProxyController} 通用透传。
 */
@RestController
@RequestMapping("/bff/api/v1")
public class RuleConfigBffController {

    private final BffAggregationService aggregation;

    public RuleConfigBffController(BffAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    private static String auth(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    // —— 事件类型 ——
    @GetMapping("/event-types")
    public Object listEventTypes(HttpServletRequest request) {
        return aggregation.ruleConfigGet("/api/v1/event-types", auth(request));
    }

    @PostMapping("/event-types")
    public Object createEventType(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return aggregation.ruleConfigPost("/api/v1/event-types", body, auth(request));
    }

    @PutMapping("/event-types/{id}/status")
    public Object updateEventTypeStatus(@PathVariable("id") String id,
                                        @RequestBody Map<String, Object> body,
                                        HttpServletRequest request) {
        return aggregation.ruleConfigPut("/api/v1/event-types/" + id + "/status", body, auth(request));
    }

    // —— 指标读取（指标存储服务）——
    @GetMapping("/indicators/{refName}")
    public Object readIndicator(@PathVariable("refName") String refName,
                                @RequestParam Map<String, String> params,
                                HttpServletRequest request) {
        return aggregation.indicatorGet("/api/v1/indicators/" + refName + buildQuery(params), auth(request));
    }

    /** 将查询参数拼接为已编码查询串（简单透传，键值为前端可信表单值）。 */
    static String buildQuery(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }
}
