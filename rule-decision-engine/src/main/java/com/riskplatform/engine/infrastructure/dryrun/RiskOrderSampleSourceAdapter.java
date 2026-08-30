package com.riskplatform.engine.infrastructure.dryrun;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskplatform.engine.domain.dryrun.DryRunSample;
import com.riskplatform.engine.domain.dryrun.DryRunSampleSource;
import com.riskplatform.engine.domain.dryrun.DryRunSampleSourcePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史订单样本来源适配器（R5.1/R5.2/R14.3）。
 *
 * <p>从 MySQL {@code risk_order} 读取历史订单作为试运行影子样本（参考 decision-gateway 的
 * 订单查询：按事件时间过滤、倒序、分页）。解析订单上下文为「影子上下文」Map 注入评估。
 *
 * <h3>样本来源约定</h3>
 * <ul>
 *   <li>{@link DryRunSampleSource#ORDER}：直接读 risk_order；</li>
 *   <li>{@link DryRunSampleSource#EVENT}：当前阶段事件样本以订单样本承载（订单即一次决策事件的
 *       落库形态，eventId/eventTypeCode/context 一致），故复用同一读取路径。后续引入独立事件
 *       样本存储后，可在此按 source 分流到独立实现（预留扩展）。</li>
 * </ul>
 *
 * <h3>上下文解析</h3>
 * risk_order.context 由 decision-gateway 序列化落库（当前为 {@code Map.toString()} 形态，
 * 经字段加密透明存取）。本适配器优先按 JSON 解析；失败时回退解析 {@code {k=v, ...}} 形态；
 * 仍失败则以空上下文承载（该样本评估多半不命中，但不影响任务整体，符合影子模式只读取样语义）。
 *
 * <p>只读 risk_order，绝不修改；不触发任何在线决策。
 */
@Component
public class RiskOrderSampleSourceAdapter implements DryRunSampleSourcePort {

    private static final Logger log = LoggerFactory.getLogger(RiskOrderSampleSourceAdapter.class);

    /** 样本数量安全上限（limit&lt;=0 不限时的兜底，避免一次拉取过多历史样本压垮内存）。 */
    private static final int MAX_SAMPLE_LIMIT = 10_000;

    private final RiskOrderSampleMapper mapper;
    private final ObjectMapper objectMapper;

    public RiskOrderSampleSourceAdapter(RiskOrderSampleMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DryRunSample> fetch(DryRunSampleSource source, LocalDateTime from,
                                    LocalDateTime to, int limit) {
        // EVENT 当前以订单样本承载（见类注释）；后续独立事件存储就绪后在此分流。
        LambdaQueryWrapper<RiskOrderSamplePO> wrapper = new LambdaQueryWrapper<>();
        if (from != null) {
            wrapper.ge(RiskOrderSamplePO::getEventTime, from);
        }
        if (to != null) {
            wrapper.le(RiskOrderSamplePO::getEventTime, to);
        }
        wrapper.orderByDesc(RiskOrderSamplePO::getEventTime);

        int effectiveLimit = (limit <= 0) ? MAX_SAMPLE_LIMIT : Math.min(limit, MAX_SAMPLE_LIMIT);
        Page<RiskOrderSamplePO> page = Page.of(1, effectiveLimit);
        IPage<RiskOrderSamplePO> result = mapper.selectPage(page, wrapper);

        List<DryRunSample> samples = new ArrayList<>(result.getRecords().size());
        for (RiskOrderSamplePO po : result.getRecords()) {
            samples.add(new DryRunSample(po.getEventId(), po.getEventTypeCode(),
                    parseContext(po.getContext(), po.getMerchantId())));
        }
        return samples;
    }

    /**
     * 解析订单上下文为影子上下文 Map：优先 JSON，回退 toString 形态，最终兜底空上下文。
     * 始终补充 merchantId（便于评分/指标维度，与在线链路维度键一致）。
     */
    private Map<String, Object> parseContext(String raw, String merchantId) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (raw != null && !raw.isBlank()) {
            Map<String, Object> parsed = tryParseJson(raw);
            if (parsed == null) {
                parsed = tryParseToString(raw);
            }
            if (parsed != null) {
                ctx.putAll(parsed);
            } else {
                log.debug("订单上下文无法解析，按空上下文承载: snippetLen={}", raw.length());
            }
        }
        if (merchantId != null && !ctx.containsKey("merchantId")) {
            ctx.put("merchantId", merchantId);
        }
        return ctx;
    }

    private Map<String, Object> tryParseJson(String raw) {
        String s = raw.trim();
        if (!s.startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 {@code java.util.Map#toString()} 形态：{@code {k1=v1, k2=v2}}。
     * 仅做浅层键值切分，值统一作为字符串（数值型规则在 Aviator/评分计算侧会按需转换）。
     * 含嵌套结构时该形态不可靠，解析失败返回 null（回退空上下文）。
     */
    private Map<String, Object> tryParseToString(String raw) {
        String s = raw.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            return null;
        }
        String body = s.substring(1, s.length() - 1).trim();
        if (body.isEmpty()) {
            return Map.of();
        }
        // 嵌套对象/数组无法可靠切分，放弃（回退空上下文）
        if (body.indexOf('{') >= 0 || body.indexOf('[') >= 0) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String pair : body.split(",\\s*")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return map.isEmpty() ? null : map;
    }
}
