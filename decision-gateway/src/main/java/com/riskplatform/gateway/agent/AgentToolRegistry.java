package com.riskplatform.gateway.agent;

import com.riskplatform.gateway.domain.IndicatorReadGateway;
import com.riskplatform.gateway.domain.ListGateway;
import com.riskplatform.gateway.domain.ScreeningGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具注册表：固定链路与自主编排均通过此处调用。
 */
public class AgentToolRegistry {

    private final ListGateway listGateway;
    private final ScreeningGateway screeningGateway;
    private final IndicatorReadGateway indicatorReadGateway;

    public AgentToolRegistry(
            ListGateway listGateway,
            ScreeningGateway screeningGateway,
            IndicatorReadGateway indicatorReadGateway) {
        this.listGateway = listGateway;
        this.screeningGateway = screeningGateway;
        this.indicatorReadGateway = indicatorReadGateway;
    }

    public void runConfiguredTools(AgentRuntimeConfig config, AgentToolContext ctx) {
        if (config.tools == null) {
            return;
        }
        for (AgentRuntimeConfig.ToolConfig tool : config.tools) {
            if (tool == null || !tool.enabled || tool.id == null) {
                continue;
            }
            invokeTool(tool.id, Map.of(), ctx, config);
        }
    }

    public List<Map<String, Object>> buildToolCatalog(AgentRuntimeConfig config) {
        List<Map<String, Object>> catalog = new ArrayList<>();
        if (config.tools == null) {
            return catalog;
        }
        for (AgentRuntimeConfig.ToolConfig tool : config.tools) {
            if (tool == null || !tool.enabled || tool.id == null) {
                continue;
            }
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", tool.id);
            entry.put("description", describeTool(tool.id));
            entry.put("returns", describeToolReturns(tool.id));
            catalog.add(entry);
        }
        return catalog;
    }

    public void invokeTool(
            String toolId,
            Map<String, Object> args,
            AgentToolContext ctx,
            AgentRuntimeConfig config) {
        AgentRuntimeConfig.ToolConfig toolConfig = findToolConfig(config, toolId);
        switch (toolId) {
            case "read_context" -> readContext(ctx, config);
            case "list_check" -> listCheck(ctx);
            case "read_feature" -> readFeature(ctx, resolveFields(args, toolConfig));
            case "read_indicator" -> readIndicator(ctx, toolConfig, args);
            case "screen_name" -> screenName(ctx);
            case "compare_engine" -> compareEngine(ctx);
            case "analyze_amount_spike" -> analyzeAmountSpike(ctx, toolConfig, args);
            case "check_known_risks" -> checkKnownRisks(ctx, config);
            default -> ctx.addTrace(toolId, Map.of("skipped", true, "reason", "未知工具"));
        }
    }

    private static String describeTool(String id) {
        return switch (id) {
            case "read_context" -> "读取事件上下文与重点特征字段摘要";
            case "list_check" -> "精确名单命中（黑/关注/白）";
            case "read_feature" -> "读取指定字段，args.fields 为字段名数组";
            case "read_indicator" -> "读取累计指标，args.refName/windowDays/granularity";
            case "screen_name" -> "名称模糊筛查";
            case "compare_engine" -> "读取规则引擎决策结果";
            case "analyze_amount_spike" ->
                    "分析单笔 amount 与日累计/笔均的倍数关系（返回数值，是否异常由你判断）";
            case "check_known_risks" ->
                    "对照 knownRisks 清单，根据当前 signals 标记 matched/notHit（辅助参考）";
            default -> id;
        };
    }

    private static String describeToolReturns(String id) {
        return switch (id) {
            case "read_context" -> "features, fieldCount";
            case "list_check" -> "blackHit, watchHit, whiteHit";
            case "read_feature" -> "字段名->值";
            case "read_indicator" -> "refName->数值";
            case "screen_name" -> "subjectName, hitKind";
            case "compare_engine" -> "engineDecision";
            case "analyze_amount_spike" ->
                    "currentAmount, dailyAmtBaseline, txnCnt1d, avgTxnAmount, ratioToDaily, ratioToAvgTxn, spike";
            case "check_known_risks" -> "hits[], notHit[]";
            default -> "output";
        };
    }

    private AgentRuntimeConfig.ToolConfig findToolConfig(AgentRuntimeConfig config, String toolId) {
        if (config.tools == null) {
            return new AgentRuntimeConfig.ToolConfig();
        }
        for (AgentRuntimeConfig.ToolConfig t : config.tools) {
            if (t != null && toolId.equals(t.id)) {
                return t;
            }
        }
        return new AgentRuntimeConfig.ToolConfig();
    }

    private void readContext(AgentToolContext ctx, AgentRuntimeConfig config) {
        Map<String, Object> out = new HashMap<>();
        out.put("eventTypeCode", ctx.eventTypeCode());
        List<String> highlights = config.featureFields != null && !config.featureFields.isEmpty()
                ? config.featureFields
                : List.of("merchantId", "amount", "eventTypeCode");
        Map<String, Object> features = new HashMap<>();
        for (String f : highlights) {
            if (ctx.context().containsKey(f)) {
                features.put(f, ctx.context().get(f));
            }
        }
        out.put("features", features);
        out.put("fieldCount", ctx.context().size());
        ctx.signals().put("contextFeatures", features);
        ctx.addTrace("read_context", out);
    }

    private void listCheck(AgentToolContext ctx) {
        var summary = listGateway.checkContext(ctx.context());
        boolean blackHit = summary.blackHit();
        boolean watchHit = summary.watchHit();
        ctx.signals().put("blackHit", blackHit);
        ctx.signals().put("watchHit", watchHit);
        ctx.signals().put("whiteHit", summary.whiteHit());
        ctx.addTrace("list_check", Map.of(
                "blackHit", blackHit,
                "watchHit", watchHit,
                "whiteHit", summary.whiteHit()));
    }

    private void readFeature(AgentToolContext ctx, List<String> fields) {
        Map<String, Object> values = new HashMap<>();
        if (fields != null) {
            for (String f : fields) {
                Object v = ctx.context().get(f);
                values.put(f, v);
                ctx.signals().put(f, v);
            }
        }
        ctx.addTrace("read_feature", values);
    }

    private void readIndicator(
            AgentToolContext ctx,
            AgentRuntimeConfig.ToolConfig toolConfig,
            Map<String, Object> args) {
        List<AgentRuntimeConfig.IndicatorRef> refs = toolConfig.refs;
        if (args != null && args.get("refName") != null) {
            AgentRuntimeConfig.IndicatorRef single = new AgentRuntimeConfig.IndicatorRef();
            single.refName = String.valueOf(args.get("refName"));
            if (args.get("dimensionField") != null) {
                single.dimensionField = String.valueOf(args.get("dimensionField"));
            }
            if (args.get("windowDays") instanceof Number n) {
                single.windowDays = n.intValue();
            }
            if (args.get("granularity") != null) {
                single.granularity = String.valueOf(args.get("granularity"));
            }
            refs = List.of(single);
        }
        if (refs == null || refs.isEmpty()) {
            ctx.addTrace("read_indicator", Map.of("skipped", true));
            return;
        }
        Map<String, Object> readings = new HashMap<>();
        for (AgentRuntimeConfig.IndicatorRef ref : refs) {
            if (ref.refName == null) {
                continue;
            }
            String dimField = ref.dimensionField == null ? "merchantId" : ref.dimensionField;
            Object dimVal = ctx.context().get(dimField);
            String dimensionKey = dimVal == null ? "" : String.valueOf(dimVal);
            double value = indicatorReadGateway.read(
                    ref.refName, dimensionKey, ref.windowDays, ref.granularity);
            readings.put(ref.refName, value);
            ctx.signals().put("indicator:" + ref.refName, value);
            if (ref.threshold != null) {
                ctx.signals().put("indicator_gt:" + ref.refName, value > ref.threshold);
            }
        }
        ctx.addTrace("read_indicator", readings);
    }

    private void analyzeAmountSpike(
            AgentToolContext ctx,
            AgentRuntimeConfig.ToolConfig cfg,
            Map<String, Object> args) {
        String amountField = argString(args, "amountField", cfg.amountField, "amount");
        String dailyRef = argString(args, "dailyAmtRef", cfg.dailyAmtRef, "b2b_daily_amt");
        String countRef = argString(args, "countRef", cfg.countRef, "txn_cnt_1d");
        double spikeRatio = argDouble(args, "spikeRatio", cfg.spikeRatio, 3.0);
        double spikeAbsolute = argDouble(args, "spikeAbsolute", cfg.spikeAbsolute, 100000.0);

        double currentAmount = parseDouble(ctx.context().get(amountField));
        String merchantId = ctx.context().get("merchantId") == null
                ? "" : String.valueOf(ctx.context().get("merchantId"));

        double dailyAmt = indicatorReadGateway.read(dailyRef, merchantId, 1, "DAY");
        double txnCnt = indicatorReadGateway.read(countRef, merchantId, 1, "DAY");
        double avgTxn = txnCnt > 0 ? dailyAmt / txnCnt : dailyAmt;

        double ratioToDaily = dailyAmt > 0 ? currentAmount / dailyAmt : currentAmount;
        double ratioToAvg = avgTxn > 0 ? currentAmount / avgTxn : currentAmount;

        boolean spike = currentAmount >= spikeAbsolute
                || (dailyAmt > 0 && ratioToDaily >= spikeRatio)
                || (avgTxn > 0 && ratioToAvg >= spikeRatio);

        Map<String, Object> out = new HashMap<>();
        out.put("currentAmount", currentAmount);
        out.put("dailyAmtBaseline", dailyAmt);
        out.put("txnCnt1d", txnCnt);
        out.put("avgTxnAmount", avgTxn);
        out.put("ratioToDaily", ratioToDaily);
        out.put("ratioToAvgTxn", ratioToAvg);
        out.put("spike", spike);
        out.put("spikeRatioThreshold", spikeRatio);
        out.put("spikeAbsoluteThreshold", spikeAbsolute);

        ctx.signals().put("amountSpike", spike);
        ctx.signals().put("currentAmount", currentAmount);
        ctx.signals().put("dailyAmtBaseline", dailyAmt);
        ctx.signals().put("amountSpikeRatio", ratioToAvg);
        ctx.addTrace("analyze_amount_spike", out);
    }

    private void screenName(AgentToolContext ctx) {
        String name = extractSubjectName(ctx.context());
        ScreeningGateway.HitKind kind = screeningGateway.screenName(name);
        ctx.signals().put("screenHit", kind.name());
        ctx.addTrace("screen_name", Map.of("subjectName", name == null ? "" : name, "hitKind", kind.name()));
    }

    private void compareEngine(AgentToolContext ctx) {
        ctx.signals().put("engineDecision", ctx.engineDecision());
        ctx.addTrace("compare_engine", Map.of("engineDecision", ctx.engineDecision()));
    }

    private void checkKnownRisks(AgentToolContext ctx, AgentRuntimeConfig config) {
        List<Map<String, Object>> hits = new ArrayList<>();
        List<Map<String, Object>> notHit = new ArrayList<>();
        if (config.knownRisks == null) {
            ctx.addTrace("check_known_risks", Map.of("hits", hits, "notHit", notHit));
            return;
        }
        for (AgentRuntimeConfig.KnownRisk risk : config.knownRisks) {
            if (risk == null || risk.id == null) {
                continue;
            }
            boolean matched = matchesKnownRisk(risk, ctx.signals(), ctx);
            Map<String, Object> item = Map.of(
                    "id", risk.id,
                    "name", risk.name == null ? risk.id : risk.name,
                    "matched", matched);
            if (matched) {
                hits.add(item);
                ctx.signals().put("knownRisk:" + risk.id, true);
            } else {
                notHit.add(item);
            }
        }
        ctx.signals().put("knownRiskHitCount", hits.size());
        ctx.addTrace("check_known_risks", Map.of("hits", hits, "notHit", notHit));
    }

    private static boolean matchesKnownRisk(
            AgentRuntimeConfig.KnownRisk risk,
            Map<String, Object> signals,
            AgentToolContext ctx) {
        if (risk.signalKeys == null || risk.signalKeys.isEmpty()) {
            return false;
        }
        for (String key : risk.signalKeys) {
            if ("engineDecision".equals(key)) {
                if ("REJECT".equalsIgnoreCase(ctx.engineDecision())) {
                    return true;
                }
                continue;
            }
            Object v = signals.get(key);
            if (Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> resolveFields(Map<String, Object> args, AgentRuntimeConfig.ToolConfig toolConfig) {
        if (args != null && args.get("fields") instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return toolConfig.fields;
    }

    private static String argString(
            Map<String, Object> args, String key, String fromCfg, String defaultVal) {
        if (args != null && args.get(key) != null) {
            return String.valueOf(args.get(key));
        }
        if (fromCfg != null && !fromCfg.isBlank()) {
            return fromCfg;
        }
        return defaultVal;
    }

    private static double argDouble(Map<String, Object> args, String key, double fromCfg, double defaultVal) {
        if (args != null && args.get(key) instanceof Number n) {
            return n.doubleValue();
        }
        if (fromCfg > 0) {
            return fromCfg;
        }
        return defaultVal;
    }

    private static double parseDouble(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractSubjectName(Map<String, Object> context) {
        for (String key : new String[]{"subjectName", "payerName", "counterpartyName", "name"}) {
            Object v = context.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }
}
