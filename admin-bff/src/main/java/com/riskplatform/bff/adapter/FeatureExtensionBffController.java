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
 * 平台增强相关后端端点的 BFF 透传聚合（R13.3）。
 *
 * <p>为前端「规则包 / 结构化规则 / 策略 / 试运行 / 场景 / 机构 / 字典 / 枚举库 / 决策流扩展」
 * 配置与监控页提供页面级接口。统一透传 JWT（Authorization 头）与字段级错误（下游结构化错误体
 * 由 {@link BffExceptionHandler} 映射回前端表单项）。
 *
 * <p>路由按目标下游服务区分：
 * <ul>
 *   <li><b>rule-config-service（8082）</b>：场景、机构、字典（风险类型/等级/决策标签）、枚举库、
 *       规则包、结构化规则 rules-v2、策略 strategies、score-bands 策略绑定；</li>
 *   <li><b>rule-decision-engine（8083）</b>：试运行 dry-run（发起/查询）、决策流执行链路
 *       decision-flows/evaluate-trace。</li>
 * </ul>
 *
 * <p>决策流的 CRUD 与版本对比（{@code /decision-flows}、{@code /decision-flows/{id}/versions}、
 * {@code /versions/compare}）已由 {@link RuleConfigProxyController} 透传至 rule-config，故本控制器
 * 仅补充 engine 侧的 {@code /decision-flows/evaluate-trace}，前缀互不冲突。
 *
 * <p>采用「原始 URI + 查询串」通用透传：路径参数、状态切换等 query 参数（如
 * {@code ?enabled=true}、{@code ?targetId=}）随原始查询串原样转发，无需逐一显式声明。
 */
@RestController
@RequestMapping("/bff/api/v1")
public class FeatureExtensionBffController {

    private final BffAggregationService aggregation;

    public FeatureExtensionBffController(BffAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    private static String auth(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    /** 取 /bff/api/v1 之后的下游路径并附带原始查询串。 */
    private static String downstreamPath(HttpServletRequest request) {
        String uri = request.getRequestURI();           // /bff/api/v1/scenarios/1/status
        int idx = uri.indexOf("/bff/api/v1");
        String tail = idx >= 0 ? uri.substring(idx + "/bff/api/v1".length()) : uri;
        String query = request.getQueryString();
        return "/api/v1" + tail + (query != null && !query.isBlank() ? "?" + query : "");
    }

    // ===================== rule-config-service（8082）通用透传 =====================

    /**
     * rule-config 资源 GET 透传：场景/机构/字典/枚举库/规则包/结构化规则/策略/score-bands。
     * 覆盖列表、详情及各子资源（如 orgs/{id}/subtree、orgs/{id}/applicable、scenarios/{id}/events、
     * enum-libs/{id}/values、enum-libs/{id}/values/export、rules-v2/{id}/strategies 等）。
     */
    @GetMapping({
            "/scenarios", "/scenarios/**",
            "/orgs", "/orgs/**",
            "/risk-types", "/risk-types/**",
            "/risk-levels", "/risk-levels/**",
            "/decision-tags", "/decision-tags/**",
            "/enum-libs", "/enum-libs/**",
            "/rule-packages", "/rule-packages/**",
            "/rules-v2", "/rules-v2/**",
            "/strategies", "/strategies/**",
            "/score-bands/**",
            "/rating-models", "/rating-models/**"
    })
    public Object configGet(HttpServletRequest request) {
        return aggregation.ruleConfigGet(downstreamPath(request), auth(request));
    }

    /**
     * rule-config 资源 POST 透传：创建及子资源动作（如 scenarios/{id}/events、
     * rule-packages/{id}/score-bands、rule-packages/{id}/rules、rules-v2/compile-preview、
     * rules-v2/{id}/strategies、score-bands/{id}/strategies、enum-libs/{id}/values、
     * enum-libs/{id}/values/import）。
     */
    @PostMapping({
            "/scenarios", "/scenarios/**",
            "/orgs", "/orgs/**",
            "/risk-types", "/risk-types/**",
            "/risk-levels", "/risk-levels/**",
            "/decision-tags", "/decision-tags/**",
            "/enum-libs", "/enum-libs/**",
            "/rule-packages", "/rule-packages/**",
            "/rules-v2", "/rules-v2/**",
            "/strategies", "/strategies/**",
            "/score-bands/**",
            "/rating-models", "/rating-models/**"
    })
    public Object configPost(@RequestBody(required = false) Map<String, Object> body,
                             HttpServletRequest request) {
        return aggregation.ruleConfigPost(downstreamPath(request), body, auth(request));
    }

    /**
     * rule-config 资源 PUT 透传：更新及状态切换（如 {id}、{id}/status?enabled=、
     * enum-libs/{id}/values/{valueId}）。状态切换等 query 参数随原始查询串转发。
     */
    @PutMapping({
            "/scenarios/**",
            "/orgs/**",
            "/risk-types/**",
            "/risk-levels/**",
            "/decision-tags/**",
            "/enum-libs/**",
            "/rule-packages/**",
            "/rules-v2/**",
            "/strategies/**",
            "/rating-models/**"
    })
    public Object configPut(@RequestBody(required = false) Map<String, Object> body,
                            HttpServletRequest request) {
        return aggregation.ruleConfigPut(downstreamPath(request), body, auth(request));
    }

    /**
     * rule-config 资源 DELETE 透传：字典项、枚举库/枚举值删除（删除前引用校验由下游处理）。
     */
    @DeleteMapping({
            "/risk-types/**",
            "/risk-levels/**",
            "/decision-tags/**",
            "/enum-libs/**"
    })
    public Object configDelete(HttpServletRequest request) {
        return aggregation.ruleConfigDelete(downstreamPath(request), auth(request));
    }

    // ===================== rule-decision-engine（8083）透传 =====================

    /** 发起试运行（影子模式，异步），路由至引擎。 */
    @PostMapping("/dry-run")
    public Object startDryRun(@RequestBody(required = false) Map<String, Object> body,
                              HttpServletRequest request) {
        return aggregation.enginePost("/api/v1/dry-run", body, auth(request));
    }

    /** 查询试运行任务与报告，路由至引擎。 */
    @GetMapping("/dry-run/{id}")
    public Object getDryRun(HttpServletRequest request) {
        return aggregation.engineGet(downstreamPath(request), auth(request));
    }

    /** 决策流执行链路（9 类节点 + 网关/分流/子流程），路由至引擎。 */
    @PostMapping("/decision-flows/evaluate-trace")
    public Object evaluateTrace(@RequestBody(required = false) Map<String, Object> body,
                                HttpServletRequest request) {
        return aggregation.enginePost("/api/v1/decision-flows/evaluate-trace", body, auth(request));
    }
}
