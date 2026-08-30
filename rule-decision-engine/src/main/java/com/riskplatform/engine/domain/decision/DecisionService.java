package com.riskplatform.engine.domain.decision;

import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 决策服务（R6.5/R6.7）：在配置时限内完成聚合并返回，超时则按处置策略产出。
 */
public class DecisionService {

    private final DecisionAggregator aggregator;
    private final ExecutorService executor;

    public DecisionService(DecisionAggregator aggregator) {
        this(aggregator, Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "decision-agg");
            t.setDaemon(true);
            return t;
        }));
    }

    public DecisionService(DecisionAggregator aggregator, ExecutorService executor) {
        this.aggregator = aggregator;
        this.executor = executor;
    }

    /**
     * 直接聚合（无时限），用于不需要超时控制的场景与属性测试。
     */
    public Decision aggregate(List<HitDecision> hits) {
        return aggregator.aggregate(hits);
    }

    /**
     * 在时限内对"命中决策的产出过程"求最终决策。
     *
     * @param hitsSupplier 命中决策的产出过程（可能涉及规则执行耗时）
     * @param config       决策时限与超时处置
     * @return 最终决策（含是否超时与耗时）
     */
    public FinalDecision decideWithDeadline(Callable<List<HitDecision>> hitsSupplier, DecisionConfig config) {
        long start = System.nanoTime();
        Future<List<HitDecision>> future = executor.submit(hitsSupplier);
        try {
            List<HitDecision> hits = future.get(config.timeoutMs(), TimeUnit.MILLISECONDS);
            Decision decision = aggregator.aggregate(hits);
            return FinalDecision.normal(decision, elapsedMs(start));
        } catch (TimeoutException te) {
            future.cancel(true);
            return FinalDecision.timeout(config.timeoutDisposition(),
                    "决策聚合超过时限 " + config.timeoutMs() + "ms", elapsedMs(start));
        } catch (Exception e) {
            future.cancel(true);
            // 计算异常亦按超时处置兜底，记录原因
            return FinalDecision.timeout(config.timeoutDisposition(),
                    "决策聚合异常: " + e.getMessage(), elapsedMs(start));
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
