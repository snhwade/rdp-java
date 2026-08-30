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
 * 规则配置服务增量功能透传聚合（R14.1）。
 *
 * <p>S1–S12 新增的决策工具/治理资源众多（决策表/评分卡/决策流/决策树/决策矩阵/名单/
 * 审批/资产版本/字段库/衍生字段/用户/登录等），逐一写显式聚合方法成本高。本控制器对这些
 * 固定资源前缀做通用透传：将请求原样转发至规则配置服务 {@code /api/v1/**}，
 * 透传 JWT、查询串与字段级错误（R14.2/R17.1）。
 *
 * <p>前端统一通过 {@code /bff/api/v1/<资源>} 访问（与 api/tools.ts 一致）；事件类型/规则等
 * 显式聚合接口在 RuleConfigBffController，前缀不同不冲突。
 */
@RestController
@RequestMapping("/bff/api/v1")
public class RuleConfigProxyController {

    private final BffAggregationService aggregation;

    public RuleConfigProxyController(BffAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    private static String auth(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    /** 取 /bff/api/v1 之后的下游路径并附带原始查询串。 */
    private static String downstreamPath(HttpServletRequest request) {
        String uri = request.getRequestURI();          // /bff/api/v1/decision-tables
        int idx = uri.indexOf("/bff/api/v1");
        String tail = idx >= 0 ? uri.substring(idx + "/bff/api/v1".length()) : uri;
        String query = request.getQueryString();
        return "/api/v1" + tail + (query != null && !query.isBlank() ? "?" + query : "");
    }

    @GetMapping({
            "/decision-tables/**", "/scorecards/**", "/decision-flows/**",
            "/decision-trees/**", "/decision-matrices/**", "/approvals/**", "/asset-versions/**",
            "/fields/**", "/derived-fields/**", "/users/**",
            "/indicator-definitions", "/indicator-definitions/**",
            "/indicator-groups", "/indicator-groups/**",
            "/logical-indicators", "/logical-indicators/**",
            "/decision-tables", "/scorecards", "/decision-flows",
            "/decision-trees", "/decision-matrices", "/approvals", "/asset-versions",
            "/fields", "/derived-fields", "/users"
    })
    public Object proxyGet(HttpServletRequest request) {
        return aggregation.ruleConfigGet(downstreamPath(request), auth(request));
    }

    @PostMapping({
            "/decision-tables", "/scorecards", "/decision-flows", "/decision-flows/**",
            "/decision-trees", "/decision-matrices", "/fields", "/fields/**", "/derived-fields",
            "/users", "/auth/login", "/indicator-definitions", "/indicator-definitions/**",
            "/indicator-groups", "/indicator-groups/**",
            "/logical-indicators", "/logical-indicators/**"
    })
    public Object proxyPost(@RequestBody(required = false) Map<String, Object> body,
                            HttpServletRequest request) {
        return aggregation.ruleConfigPost(downstreamPath(request), body, auth(request));
    }

    @PutMapping({
            "/approvals/**", "/decision-tables/**", "/scorecards/**",
            "/decision-flows/**", "/decision-trees/**", "/decision-matrices/**",
            "/asset-versions/**", "/fields/**", "/users/**",
            "/indicator-definitions/**", "/indicator-groups/**",
            "/logical-indicators/**"
    })
    public Object proxyPut(@RequestBody(required = false) Map<String, Object> body,
                           HttpServletRequest request) {
        return aggregation.ruleConfigPut(downstreamPath(request), body, auth(request));
    }

    @DeleteMapping({
            "/decision-tables/**", "/scorecards/**", "/decision-flows/**",
            "/decision-trees/**", "/decision-matrices/**", "/fields/**", "/derived-fields/**",
            "/users/**", "/indicator-definitions/**", "/indicator-groups/**",
            "/logical-indicators/**"
    })
    public Object proxyDelete(HttpServletRequest request) {
        return aggregation.ruleConfigDelete(downstreamPath(request), auth(request));
    }
}
