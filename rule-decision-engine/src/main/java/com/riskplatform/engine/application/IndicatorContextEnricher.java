package com.riskplatform.engine.application;

import com.riskplatform.engine.domain.indicator.IndicatorReader;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按规则表达式扫描指标引用名，从 indicator-store 读取并注入决策上下文。
 *
 * <p>供规则包节点与决策流规则包节点共用，维度键取 {@code merchantId}。
 */
public final class IndicatorContextEnricher {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final IndicatorReader indicatorReader;
    private final Set<String> knownIndicatorPrefixes;

    public IndicatorContextEnricher(IndicatorReader indicatorReader) {
        this(indicatorReader, Set.of("ai_", "ind_", "txn_", "amt_", "cnt_", "b2b_"));
    }

    IndicatorContextEnricher(IndicatorReader indicatorReader, Set<String> knownIndicatorPrefixes) {
        this.indicatorReader = indicatorReader;
        this.knownIndicatorPrefixes = knownIndicatorPrefixes;
    }

    public void enrichFromExpressions(Collection<String> expressions, Map<String, Object> ctx) {
        if (expressions == null || expressions.isEmpty() || ctx == null) {
            return;
        }
        String dimensionKey = dimensionKey(ctx);
        if (dimensionKey == null) {
            return;
        }
        Set<String> refs = new HashSet<>();
        for (String expression : expressions) {
            if (expression == null || expression.isBlank()) {
                continue;
            }
            Matcher m = IDENTIFIER.matcher(expression);
            while (m.find()) {
                String token = m.group();
                if (isIndicatorRef(token) && !ctx.containsKey(token)) {
                    refs.add(token);
                }
            }
        }
        for (String ref : refs) {
            double value = indicatorReader.read(ref, dimensionKey, 1, "DAY");
            ctx.put(ref, value);
        }
    }

    private boolean isIndicatorRef(String token) {
        for (String prefix : knownIndicatorPrefixes) {
            if (token.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String dimensionKey(Map<String, Object> ctx) {
        Object v = ctx.get("merchantId");
        if (v == null) {
            v = ctx.get("merchant_id");
        }
        return v == null ? null : String.valueOf(v);
    }
}
