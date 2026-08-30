package com.riskplatform.indicator.adapter.indicator;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.indicator.application.IndicatorReadService;
import com.riskplatform.indicator.application.IndicatorStorageWriter;
import com.riskplatform.indicator.domain.IndicatorReadResult;
import com.riskplatform.indicator.domain.SliceGranularity;
import com.riskplatform.indicator.domain.SliceKey;
import com.riskplatform.indicator.infrastructure.stats.IndicatorRuntimeStatsWriter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 指标存储 REST 适配器（R9.3/R9.4）。
 *
 * <p>对外暴露读取端点，供规则引擎 / Flink / AI 训练服务调用读取指标当前值：
 * <ul>
 *   <li>GET /api/v1/indicators/{refName} 读取指标当前值（Redis 优先，ES 回退，均不可读返回缺失标记）。</li>
 * </ul>
 *
 * <p>读取路由由 {@link IndicatorReadService} 实现：Redis 命中即返回（目标 ≤50ms）；
 * Redis 缺失/不可用回退 ES；两源均不可读时返回默认值并置 {@code missing=true}，
 * 由调用方依据各自的 {@code defaultValueStrategy} 处理并记录缺失（R9.4/R16.3）。
 */
@RestController
@RequestMapping("/api/v1/indicators")
public class IndicatorController {

    /** 指标引用名格式：1–64 位 [A-Za-z0-9_]，与指标定义保持一致（R7.1）。 */
    private static final Pattern REF_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private static final int MIN_WINDOW_DAYS = 1;
    private static final int MAX_WINDOW_DAYS = 365;

    /** AI 直写指标的默认 TTL（秒）：365 天，覆盖最长窗口，确保可被规则读到。 */
    private static final long AI_WRITE_TTL_SECONDS = 365L * 86400L;

    private final IndicatorReadService indicatorReadService;
    private final IndicatorStorageWriter storageWriter;
    private final IndicatorRuntimeStatsWriter runtimeStatsWriter;

    public IndicatorController(IndicatorReadService indicatorReadService,
                               IndicatorStorageWriter storageWriter,
                               IndicatorRuntimeStatsWriter runtimeStatsWriter) {
        this.indicatorReadService = indicatorReadService;
        this.storageWriter = storageWriter;
        this.runtimeStatsWriter = runtimeStatsWriter;
    }

    /**
     * 读取指标当前值。
     *
     * @param refName      指标引用名（路径参数，1–64 位 [A-Za-z0-9_]）
     * @param dimensionKey 维度键（必填，标识具体统计维度实例，如 {@code merchant#M001}）
     * @param windowDays   时间窗口天数（必填，1–365）
     * @param granularity  切片粒度（必填，MINUTE|HOUR|DAY）
     * @param defaultValue 两源均不可读时返回的默认值（可选，默认 0.0），具体取值策略由调用方决定
     * @return 指标读取结果视图
     */
    @GetMapping("/{refName}")
    public IndicatorValueView read(
            @PathVariable("refName") String refName,
            @RequestParam(name = "dimensionKey", required = false) String dimensionKey,
            @RequestParam(name = "windowDays", required = false) Integer windowDays,
            @RequestParam(name = "granularity", required = false) String granularity,
            @RequestParam(name = "defaultValue", required = false, defaultValue = "0.0") double defaultValue) {

        // —— 输入校验：缺失/越界统一返回结构化字段级错误（R14.2）——
        if (refName == null || !REF_NAME_PATTERN.matcher(refName).matches()) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "指标引用名格式非法（须为 1-64 位 [A-Za-z0-9_]）",
                    Map.of("refName", "格式非法"));
        }
        if (dimensionKey == null || dimensionKey.isBlank()) {
            throw BizException.missingField("dimensionKey");
        }
        if (windowDays == null) {
            throw BizException.missingField("windowDays");
        }
        if (windowDays < MIN_WINDOW_DAYS || windowDays > MAX_WINDOW_DAYS) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "时间窗口天数超出范围（1-365）",
                    Map.of("windowDays", "取值范围 1-365"));
        }
        if (granularity == null || granularity.isBlank()) {
            throw BizException.missingField("granularity");
        }
        SliceGranularity sliceGranularity = parseGranularity(granularity);

        long startNanos = System.nanoTime();
        IndicatorReadResult result = indicatorReadService.read(
                refName, dimensionKey, windowDays, sliceGranularity, Instant.now(), () -> defaultValue);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        if (result.missing()) {
            runtimeStatsWriter.recordReadMiss(refName);
        }

        return new IndicatorValueView(
                refName,
                dimensionKey,
                result.value(),
                result.source().name(),
                result.missing(),
                elapsedMs);
    }

    /**
     * 写入（覆盖）单个指标切片值（R13.2，供 AI 训练服务旁路写入欺诈评分/异常分/图谱指标）。
     *
     * <p>AI 产出的是「绝对值」型指标（如 ai_fraud_score=0.93），非增量累计，故直接覆盖写入
     * 对应切片，切片 Key 与读取路径完全一致，写入后即可被规则引擎按 refName 读到。
     *
     * @param refName 指标引用名（路径参数，1–64 位 [A-Za-z0-9_]）
     * @param body    写入请求体：dimensionKey（必填）、value（必填）、sliceTs（可选，缺省取当前）、
     *                granularity（可选，缺省 DAY）
     * @return 写入结果视图
     */
    @PostMapping("/{refName}")
    public IndicatorWriteView write(
            @PathVariable("refName") String refName,
            @RequestBody IndicatorWriteRequest body) {

        if (refName == null || !REF_NAME_PATTERN.matcher(refName).matches()) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "指标引用名格式非法（须为 1-64 位 [A-Za-z0-9_]）",
                    Map.of("refName", "格式非法"));
        }
        if (body == null || body.dimensionKey() == null || body.dimensionKey().isBlank()) {
            throw BizException.missingField("dimensionKey");
        }
        if (body.value() == null) {
            throw BizException.missingField("value");
        }
        SliceGranularity granularity = body.granularity() == null || body.granularity().isBlank()
                ? SliceGranularity.DAY
                : parseGranularity(body.granularity());

        // 切片起点：优先用请求提供的 sliceTs（epoch 秒），否则按当前时刻截断到切片
        long sliceTs = body.sliceTs() != null
                ? granularity.truncateToSlice(Instant.ofEpochSecond(body.sliceTs()))
                : granularity.truncateToSlice(Instant.now());
        String sliceKey = SliceKey.of(refName, body.dimensionKey(), granularity, sliceTs);

        String orderId = body.source() != null && !body.source().isBlank() ? body.source() : "API";
        storageWriter.writeSlice(sliceKey, body.value(), AI_WRITE_TTL_SECONDS, orderId);

        return new IndicatorWriteView(refName, body.dimensionKey(), body.value(), sliceTs, "OK");
    }

    private SliceGranularity parseGranularity(String granularity) {
        try {
            return SliceGranularity.valueOf(granularity.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(CommonErrorCode.INVALID_FIELD,
                    "切片粒度非法（须为 MINUTE|HOUR|DAY）",
                    Map.of("granularity", "取值须为 MINUTE|HOUR|DAY"));
        }
    }

    /**
     * 指标读取结果视图。
     *
     * @param refName      指标引用名
     * @param dimensionKey 维度键
     * @param value        指标值（缺失时为默认值）
     * @param source       数据来源：REDIS|ES|DEFAULT
     * @param missing      是否缺失（两源均不可读，调用方应按 defaultValueStrategy 处理并记录缺失）
     * @param elapsedMs    服务端读取耗时（毫秒，目标 ≤50ms）
     */
    public record IndicatorValueView(
            String refName,
            String dimensionKey,
            double value,
            String source,
            boolean missing,
            long elapsedMs) {
    }

    /**
     * 指标写入请求体（AI 旁路写入，R13.2）。
     *
     * @param dimensionKey 维度键（必填，如商户号）
     * @param value        指标值（必填）
     * @param sliceTs      切片时间戳（可选，epoch 秒；缺省取当前时刻）
     * @param granularity  切片粒度（可选，MINUTE|HOUR|DAY；缺省 DAY）
     * @param source       来源标识（可选，如 "AI"，仅作记录用途）
     */
    public record IndicatorWriteRequest(
            String dimensionKey,
            Double value,
            Long sliceTs,
            String granularity,
            String source) {
    }

    /**
     * 指标写入结果视图。
     *
     * @param refName      指标引用名
     * @param dimensionKey 维度键
     * @param value        写入的指标值
     * @param sliceTs      实际写入的切片起点（epoch 秒）
     * @param status       写入状态（OK）
     */
    public record IndicatorWriteView(
            String refName,
            String dimensionKey,
            double value,
            long sliceTs,
            String status) {
    }
}
