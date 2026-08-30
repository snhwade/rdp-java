package com.riskplatform.engine.domain.decisionflow.node;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 子决策流递归防护（扩展阶段 10.3，R8.6）。
 *
 * <p>子决策流节点会递归调用决策流引擎执行被引用的子流程。跨流程引用可能形成环
 * （A 引 B、B 引 A），或层层嵌套过深，运行期需要防护以避免无限递归 / 栈溢出。
 *
 * <p><b>双重防护</b>：
 * <ol>
 *   <li><b>递归深度上限</b> {@code maxDepth}：每进入一层子流程深度 +1，超过上限即拒绝下钻（降级）；</li>
 *   <li><b>已访问子流程 id 集合</b> {@code visitedFlowIds}：进入某子流程前把其 id 记入集合，
 *       若该 id 已在集合中说明出现环路引用，拒绝再次进入（降级）。</li>
 * </ol>
 *
 * <p>本对象<b>不可变</b>：{@link #enter(long)} 返回携带「深度 +1、id 已并入」的新实例，
 * 沿递归链向下传递，天然按调用栈隔离各分支的访问集合（同一父流程的不同分支互不污染）。
 *
 * <p>关于配置面静态环检测：单个决策流内部的环路由 config 侧
 * {@code DecisionFlow.validateStructure()} 在保存期 DFS 检测拒绝；但<strong>跨流程</strong>的
 * 子流程引用环（A↔B 分处不同流程）跨越聚合边界，保存期需联表加载全部被引流程方能判定，成本高且易漏，
 * 故本设计以运行期 {@code SubFlowGuard} 为<strong>主</strong>防线：A→B→A 场景下，进入 B 时
 * 记 {@code visited={B}}，B 引 A 时记 {@code visited={B,A}}，A 再引 B 即命中 visited 而被拒绝，
 * 即便顶层流程自身 id 未知亦能终止环路。
 */
public final class SubFlowGuard {

    /** 默认子流程递归深度上限。超过即拒绝继续下钻（降级）。 */
    public static final int DEFAULT_MAX_DEPTH = 10;

    private final int maxDepth;
    private final int depth;
    private final Set<Long> visitedFlowIds;

    /** 顶层入口用：深度 0、空访问集合、默认深度上限。 */
    public SubFlowGuard() {
        this(DEFAULT_MAX_DEPTH, 0, new LinkedHashSet<>());
    }

    /** 顶层入口用：指定深度上限。 */
    public SubFlowGuard(int maxDepth) {
        this(maxDepth <= 0 ? DEFAULT_MAX_DEPTH : maxDepth, 0, new LinkedHashSet<>());
    }

    private SubFlowGuard(int maxDepth, int depth, Set<Long> visitedFlowIds) {
        this.maxDepth = maxDepth;
        this.depth = depth;
        this.visitedFlowIds = visitedFlowIds;
    }

    /**
     * 是否允许进入 {@code flowId} 子流程（深度未越界且未形成环）。
     *
     * @param flowId 待进入的子流程 id
     * @return true 可进入；false 越界或环路，应拒绝（降级）
     */
    public boolean canEnter(long flowId) {
        return depth < maxDepth && !visitedFlowIds.contains(flowId);
    }

    /**
     * 下钻进入 {@code flowId} 子流程，返回携带「深度 +1、flowId 并入访问集合」的新防护对象。
     * 调用前应先用 {@link #canEnter(long)} 判定可进入。
     */
    public SubFlowGuard enter(long flowId) {
        Set<Long> next = new LinkedHashSet<>(visitedFlowIds);
        next.add(flowId);
        return new SubFlowGuard(maxDepth, depth + 1, next);
    }

    public int depth() {
        return depth;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public Set<Long> visitedFlowIds() {
        return visitedFlowIds;
    }
}
