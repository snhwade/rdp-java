package com.riskplatform.engine.domain.decisionflow.node;

import com.riskplatform.engine.domain.decisionflow.DecisionFlowDef;
import com.riskplatform.engine.domain.rule.HitDecision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策流执行上下文（扩展阶段，R9.1/R9.2）。
 *
 * <p>贯穿整条决策流执行：承载可变求值环境 {@code env}（原始上下文字段 + 指标值 + 节点产出的
 * 赋值字段，如 lastDecision/lastScore/lastLevel）、累计命中决策 {@code hits}、以及只读的决策流
 * 定义 {@code def}（供决策工具节点取用内联的决策表/评分卡定义）。
 *
 * <p>本类为可变对象，由 {@link com.riskplatform.engine.domain.decisionflow.DecisionFlowEngine}
 * 在单次执行内创建并在各 {@link NodeHandler} 间传递；非线程安全（单次决策同线程使用）。
 *
 * <p>赋值字段登记（{@link #putAssignment}）是节点间数据传递的统一入口，后续节点的边条件或左变量
 * 可通过 env 读取前序节点产出（R9.1）。
 *
 * <h3>赋值字段命名规范（11.1，与 rule-config-service 的 {@code ConditionCompiler} 取值约定对齐）</h3>
 * <p>各节点产出登记的「原始 key」分两类，{@link #putAssignment} 在写入 env 时会<b>同时</b>登记一个
 * {@code assign_} 前缀的别名，使得两套引用方式都能取到同一份产出：
 * <ul>
 *   <li><b>原始 key</b>：供决策流「边条件」（条件网关/串行边的 Aviator 表达式）直接按名引用，
 *       例如 {@code lastScore}、{@code lastLevel}、{@code lastDecision}、{@code model_<ref>}、
 *       {@code rulePackage_<id>_decision} 等；</li>
 *   <li><b>{@code assign_<原始key>} 别名</b>：供「结构化规则」条件的左变量（来源 ASSIGNMENT）引用——
 *       {@code ConditionCompiler} 把来源为 ASSIGNMENT、ref=X 的左变量编译为上下文键 {@code assign_X}，
 *       故登记 {@code lastScore} 会同时可由 {@code assign_lastScore} 引用，登记
 *       {@code rulePackage_5_decision} 会同时可由 {@code assign_rulePackage_5_decision} 引用。</li>
 * </ul>
 * <p>注意：MODEL 来源左变量在编译器侧约定为 {@code model_<ref>}，与 {@code ModelNodeHandler} 登记的
 * 原始 key 已天然一致，无需经 assign_ 别名（别名仍会附带登记，二者皆可用）。已是 {@code assign_} 前缀的
 * key 不再重复加前缀。
 *
 * <p>前序节点未执行/未产出某赋值字段时，env 中不存在对应键；后续边条件用 Aviator 的 nullable 模式
 * 求值（缺键作 null，见 {@code DecisionFlowEngine#evalCondition}），结构化规则求值同样以 nullable 模式
 * 并由执行器 try/catch 容错，故缺失赋值字段按空值处理、不会中断流程（R9.2）。
 */
public final class FlowContext {

    /** 结构化规则 ASSIGNMENT 来源左变量在上下文中的 key 前缀（与 ConditionCompiler 约定一致，R9.1）。 */
    private static final String ASSIGNMENT_ALIAS_PREFIX = "assign_";

    private final DecisionFlowDef def;
    private final Map<String, Object> env;
    private final List<HitDecision> hits = new ArrayList<>();

    /**
     * 子决策流递归防护（R8.6）。贯穿整条决策流执行：子决策流节点据此判定能否下钻
     * （深度未越界且未形成环），并在进入子流程时 {@code enter()} 派生携带「深度+1、id 并入」的新防护
     * 沿递归链向下传递。顶层执行默认使用全新 {@link SubFlowGuard}。
     */
    private final SubFlowGuard subFlowGuard;

    public FlowContext(DecisionFlowDef def, Map<String, Object> context) {
        this(def, context, new SubFlowGuard());
    }

    /** 携带指定递归防护的构造器（子决策流递归执行时由引擎传入 R8.6）。 */
    public FlowContext(DecisionFlowDef def, Map<String, Object> context, SubFlowGuard subFlowGuard) {
        this.def = def;
        this.env = new HashMap<>(context == null ? Map.of() : context);
        this.subFlowGuard = subFlowGuard == null ? new SubFlowGuard() : subFlowGuard;
    }

    public DecisionFlowDef def() {
        return def;
    }

    /** 子决策流递归防护（R8.6）。 */
    public SubFlowGuard subFlowGuard() {
        return subFlowGuard;
    }

    /** 可变求值环境（节点读写）。 */
    public Map<String, Object> env() {
        return env;
    }

    /** 累计命中决策（节点追加，END 处聚合）。 */
    public List<HitDecision> hits() {
        return hits;
    }

    public void addHit(HitDecision hit) {
        if (hit != null) {
            hits.add(hit);
        }
    }

    /**
     * 登记一个赋值字段到求值环境，供后续节点引用（R9.1）。
     *
     * <p>同时写入两份键，以兼容两套引用约定（见类注释「赋值字段命名规范」）：
     * <ol>
     *   <li><b>原始 key</b>：供决策流边条件（Aviator 表达式）直接按名引用；</li>
     *   <li><b>{@code assign_<key>} 别名</b>：供结构化规则 ASSIGNMENT 来源左变量引用
     *       （{@code ConditionCompiler} 将其编译为 {@code assign_<ref>}）。</li>
     * </ol>
     * 若 key 已是 {@code assign_} 前缀则不重复加前缀；value 为 null 也会登记（使「显式产出空值」
     * 与「从未产出」可区分，但对 nullable 求值二者等价，均按空值处理 R9.2）。
     */
    public void putAssignment(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        env.put(key, value);
        if (!key.startsWith(ASSIGNMENT_ALIAS_PREFIX)) {
            // 同步登记 assign_ 别名，使结构化规则条件（ConditionCompiler: ASSIGNMENT -> assign_<ref>）可引用
            env.put(ASSIGNMENT_ALIAS_PREFIX + key, value);
        }
    }
}
