package com.riskplatform.screening.application;

import com.riskplatform.screening.domain.ScreeningListEntry;
import com.riskplatform.screening.domain.ScreeningMatcher;
import com.riskplatform.screening.domain.ScreeningResult;
import com.riskplatform.screening.domain.ScreeningThreshold;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 筛查应用服务（R11.1–R11.6）：在可配置时限内执行名称筛查，映射 HIT/MISS/TIMEOUT/FAILED。
 */
public class ScreeningService {

    public static final int MIN_TIMEOUT_MS = 1;
    public static final int MAX_TIMEOUT_MS = 5000;
    public static final int DEFAULT_TIMEOUT_MS = 500;

    private final ScreeningMatcher matcher;
    private final ExecutorService executor;

    public ScreeningService(ScreeningMatcher matcher) {
        this(matcher, Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "screening");
            t.setDaemon(true);
            return t;
        }));
    }

    public ScreeningService(ScreeningMatcher matcher, ExecutorService executor) {
        this.matcher = matcher;
        this.executor = executor;
    }

    /**
     * 在时限内执行筛查。
     *
     * @param subjectName   被筛查主体名称
     * @param entriesLoader 名单条目加载过程（可能因数据源不可用抛异常 R11.6）
     * @param threshold     相似度阈值
     * @param timeoutMs     时限（1..5000ms）
     */
    public ScreeningOutcomeResult screen(String subjectName,
                                         Callable<List<ScreeningListEntry>> entriesLoader,
                                         ScreeningThreshold threshold,
                                         int timeoutMs) {
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("筛查时限须在 [1,5000]ms，实际为 " + timeoutMs);
        }
        Future<ScreeningResult> future = executor.submit(() -> {
            List<ScreeningListEntry> entries = entriesLoader.call();
            return matcher.screen(subjectName, entries, threshold.value());
        });
        try {
            ScreeningResult r = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return ScreeningOutcomeResult.fromResult(r);
        } catch (TimeoutException te) {
            future.cancel(true);
            return ScreeningOutcomeResult.timeout("筛查超过时限 " + timeoutMs + "ms"); // R11.5
        } catch (Exception e) {
            future.cancel(true);
            return ScreeningOutcomeResult.failed("筛查失败: " + e.getMessage()); // R11.6
        }
    }
}
