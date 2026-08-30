package com.riskplatform.ruleconfig.domain.rulev2.condition;

import com.riskplatform.common.error.ValidationException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 条件树 -> Aviator 表达式编译器（R2.5/R2.7/R14.2，对齐 design.md Property 1）。
 *
 * <p>把 {@link ConditionNode} 条件树深度优先编译为一段可被现有 Aviator 引擎
 * （rule-decision-engine 的 {@code RuleExpressionEvaluator} / {@code DecisionFlowEvaluator}
 * 使用的 {@code AviatorEvaluator.newInstance()}）直接求值的布尔表达式字符串。
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li><b>无状态：</b>本类全部为静态方法、不持有任何可变状态，线程安全。编译产物（表达式
 *       字符串）由调用方（RuleV2AppService，任务 4.4）缓存到 {@code rule_v2.compiled_expr}
 *       并配 {@code expr_version} 版本号；引擎按 {@code expr_version} 命中缓存避免重复编译
 *       （R14.2）。本类不做缓存。</li>
 *   <li><b>优先级：</b>深度优先遍历，AND-&gt;{@code &&}、OR-&gt;{@code ||}、NOT-&gt;{@code !}，
 *       每个分支整体加括号，保证与条件树语义一致（Property 1）。</li>
 *   <li><b>未声明校验：</b>{@link #compile(ConditionNode, Set, Set)} 接受调用方注入的
 *       「已声明字段名 / 已声明指标名」集合，遍历收集未声明的 FIELD/INDICATOR 引用标识名，
 *       存在则抛出字段级 {@link ValidationException}（R2.7）。</li>
 * </ul>
 *
 * <h2>运算符映射表</h2>
 * <pre>
 *   GT          -&gt; left &gt; right
 *   GTE         -&gt; left &gt;= right
 *   LT          -&gt; left &lt; right
 *   LTE         -&gt; left &lt;= right
 *   EQ          -&gt; left == right
 *   NEQ         -&gt; left != right
 *   CONTAINS    -&gt; STRING:     string.contains(left, right)
 *                  COLLECTION:  include(left, right)
 *   STARTS_WITH -&gt; string.startsWith(left, right)
 *   IN          -&gt; include(rightSet, left)
 *   NOT_IN      -&gt; !include(rightSet, left)
 * </pre>
 * 其中 {@code string.contains}、{@code string.startsWith}、{@code include}、{@code seq.list}
 * 均为 Aviator 内置函数（见 risk-rule-engine 既有用法 {@code include(seq.list(...), x)}）。
 *
 * <h2>左变量取值约定（按 {@link VariableSource}）</h2>
 * <p>引擎执行时以 {@code Map<String,Object>} 作为上下文，表达式中的标识名即上下文 key：
 * <ul>
 *   <li>{@code FIELD}      -&gt; 直接使用字段名 {@code ref}（业务事件字段已平铺注入上下文）。</li>
 *   <li>{@code INDICATOR}  -&gt; 直接使用指标注入名 {@code ref}（指标读取后以同名 key 注入上下文）。</li>
 *   <li>{@code MODEL}      -&gt; 使用 {@code model_<ref>}（模型节点输出以该 key 注入赋值上下文）。</li>
 *   <li>{@code ASSIGNMENT} -&gt; 使用 {@code assign_<ref>}（决策流前序节点产出的赋值字段以该 key 注入）。</li>
 * </ul>
 *
 * <h2>右值生成（按 {@link RightOperandKind}）</h2>
 * <ul>
 *   <li>{@code CONST}      -&gt; 字面量：字符串加单引号并转义，数值/布尔直出；若为集合则生成 {@code seq.list(...)}。</li>
 *   <li>{@code VARIABLE}   -&gt; 同左变量取值约定解析为上下文标识名。</li>
 *   <li>{@code ENUM_LIB}   -&gt; 使用 {@code enumlib_<code>}（枚举库的值集合由引擎在执行前以该 key 注入上下文，
 *       本阶段不在编译期内联枚举值，避免枚举库变更后需重新编译；注入来源为 enum_lib/enum_value 配置）。</li>
 *   <li>{@code IMPORT_SET} -&gt; 直接内联为 {@code seq.list('a','b',...)}（批量导入值在编译期已确定）。</li>
 * </ul>
 */
public final class ConditionCompiler {

    /** MODEL 来源左变量在上下文中的 key 前缀。 */
    private static final String MODEL_KEY_PREFIX = "model_";
    /** ASSIGNMENT 来源左变量在上下文中的 key 前缀。 */
    private static final String ASSIGNMENT_KEY_PREFIX = "assign_";
    /** ENUM_LIB 右值在上下文中的集合 key 前缀。 */
    private static final String ENUM_LIB_KEY_PREFIX = "enumlib_";

    private ConditionCompiler() {
    }

    /**
     * 仅编译，不做未声明校验（适用于已确保变量声明的内部调用或预览场景）。
     *
     * @param root 条件树根节点
     * @return 可被 Aviator 求值的布尔表达式字符串
     * @throws ValidationException 条件树结构非法时抛出
     */
    public static String compile(ConditionNode root) {
        return compile(root, null, null);
    }

    /**
     * 编译条件树为 Aviator 表达式，并按注入的已声明集合做未声明字段/指标编译期校验（R2.7）。
     *
     * @param root              条件树根节点（不能为空）
     * @param declaredFields    已声明字段名集合；为 {@code null} 时跳过 FIELD 校验
     * @param declaredIndicators 已声明指标名集合；为 {@code null} 时跳过 INDICATOR 校验
     * @return 可被 Aviator 求值的布尔表达式字符串
     * @throws ValidationException 条件树为空 / 结构非法 / 存在未声明字段或指标时抛出（字段级错误，含标识名）
     */
    public static String compile(ConditionNode root, Set<String> declaredFields, Set<String> declaredIndicators) {
        if (root == null) {
            throw ValidationException.builder().field("condition", "条件树不能为空").build();
        }
        // 复用领域模型自带的结构与运算符适配校验（R2.2/R2.4）
        root.validate();

        // 未声明字段/指标校验（R2.7）：收集后统一抛出，字段名即未声明的引用标识名
        if (declaredFields != null || declaredIndicators != null) {
            ValidationException.Builder errors = ValidationException.builder();
            collectUndeclared(root, declaredFields, declaredIndicators, errors);
            errors.throwIfAny();
        }

        StringBuilder sb = new StringBuilder();
        compileNode(root, sb);
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 编译：深度优先遍历
    // ---------------------------------------------------------------------

    private static void compileNode(ConditionNode node, StringBuilder sb) {
        switch (node.op()) {
            case LEAF -> compileLeaf(node, sb);
            case NOT -> {
                sb.append("!");
                sb.append("(");
                compileNode(node.children().get(0), sb);
                sb.append(")");
            }
            case AND -> compileBranch(node.children(), "&&", sb);
            case OR -> compileBranch(node.children(), "||", sb);
            default -> throw ValidationException.builder()
                    .field("condition.op", "不支持的节点类型: " + node.op()).build();
        }
    }

    private static void compileBranch(List<ConditionNode> children, String connector, StringBuilder sb) {
        sb.append("(");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                sb.append(" ").append(connector).append(" ");
            }
            sb.append("(");
            compileNode(children.get(i), sb);
            sb.append(")");
        }
        sb.append(")");
    }

    private static void compileLeaf(ConditionNode node, StringBuilder sb) {
        String left = resolveVariable(node.left());
        Operator op = node.operator();
        RightOperand right = node.right();
        switch (op) {
            case GT -> appendBinary(sb, left, ">", renderScalarRight(right));
            case GTE -> appendBinary(sb, left, ">=", renderScalarRight(right));
            case LT -> appendBinary(sb, left, "<", renderScalarRight(right));
            case LTE -> appendBinary(sb, left, "<=", renderScalarRight(right));
            case EQ -> appendBinary(sb, left, "==", renderScalarRight(right));
            case NEQ -> appendBinary(sb, left, "!=", renderScalarRight(right));
            case STARTS_WITH ->
                    sb.append("string.startsWith(").append(left).append(", ").append(renderScalarRight(right)).append(")");
            case CONTAINS -> {
                // 字符串子串用 string.contains；集合包含用 include
                if (node.left().dataType() == DataType.COLLECTION) {
                    sb.append("include(").append(left).append(", ").append(renderScalarRight(right)).append(")");
                } else {
                    sb.append("string.contains(").append(left).append(", ").append(renderScalarRight(right)).append(")");
                }
            }
            case IN -> sb.append("include(").append(renderSetRight(right)).append(", ").append(left).append(")");
            case NOT_IN ->
                    sb.append("!include(").append(renderSetRight(right)).append(", ").append(left).append(")");
            default -> throw ValidationException.builder()
                    .field("condition.operator", "不支持的运算符: " + op).build();
        }
    }

    private static void appendBinary(StringBuilder sb, String left, String op, String right) {
        sb.append(left).append(" ").append(op).append(" ").append(right);
    }

    // ---------------------------------------------------------------------
    // 左变量 / 右值解析
    // ---------------------------------------------------------------------

    /** 左变量按 source 解析为上下文取值表达式（取值约定见类注释）。 */
    private static String resolveVariable(Variable v) {
        return switch (v.source()) {
            case FIELD, INDICATOR -> v.ref();
            case MODEL -> MODEL_KEY_PREFIX + v.ref();
            case ASSIGNMENT -> ASSIGNMENT_KEY_PREFIX + v.ref();
        };
    }

    /** 标量右值（CONST 字面量 / VARIABLE 取值），用于比较与字符串函数。 */
    private static String renderScalarRight(RightOperand right) {
        return switch (right.kind()) {
            case CONST -> renderConst(right.value());
            case VARIABLE -> resolveVariable(right.variable());
            case ENUM_LIB -> ENUM_LIB_KEY_PREFIX + right.enumLibCode();
            case IMPORT_SET -> renderStringList(right.importValues());
        };
    }

    /** 集合右值（IN/NOT_IN 用），生成 Aviator 集合表达式。 */
    private static String renderSetRight(RightOperand right) {
        return switch (right.kind()) {
            // ENUM_LIB 的值集合由引擎在执行前以 enumlib_<code> 注入上下文（不在编译期内联）
            case ENUM_LIB -> ENUM_LIB_KEY_PREFIX + right.enumLibCode();
            // 批量导入值在编译期已确定，内联为 seq.list(...)
            case IMPORT_SET -> renderStringList(right.importValues());
            // 变量右值应指向一个集合型上下文变量
            case VARIABLE -> resolveVariable(right.variable());
            // 常量右值可能是单值或集合
            case CONST -> renderConst(right.value());
        };
    }

    /** 把常量值渲染为 Aviator 字面量。 */
    private static String renderConst(Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof Boolean b) {
            return b.toString();
        }
        if (value instanceof Number) {
            // 数值直出（含 Integer/Long/Double/BigDecimal）；用 BigDecimal 规范化避免科学计数法
            return new BigDecimal(value.toString()).toPlainString();
        }
        if (value instanceof Collection<?> c) {
            StringJoiner sj = new StringJoiner(", ", "seq.list(", ")");
            for (Object e : c) {
                sj.add(renderConst(e));
            }
            return sj.toString();
        }
        // 其余按字符串处理
        return quote(value.toString());
    }

    /** 把字符串集合渲染为 {@code seq.list('a','b',...)}。 */
    private static String renderStringList(List<String> values) {
        StringJoiner sj = new StringJoiner(", ", "seq.list(", ")");
        for (String v : values) {
            sj.add(quote(v));
        }
        return sj.toString();
    }

    /** 单引号包裹并转义，生成 Aviator 字符串字面量。 */
    private static String quote(String s) {
        String escaped = s.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    // ---------------------------------------------------------------------
    // 未声明字段/指标校验（R2.7）
    // ---------------------------------------------------------------------

    private static void collectUndeclared(ConditionNode node, Set<String> declaredFields,
                                          Set<String> declaredIndicators, ValidationException.Builder errors) {
        if (node.op() == ConditionOp.LEAF) {
            checkVariable(node.left(), declaredFields, declaredIndicators, errors);
            RightOperand right = node.right();
            if (right != null && right.kind() == RightOperandKind.VARIABLE) {
                checkVariable(right.variable(), declaredFields, declaredIndicators, errors);
            }
            return;
        }
        if (node.children() != null) {
            for (ConditionNode child : node.children()) {
                collectUndeclared(child, declaredFields, declaredIndicators, errors);
            }
        }
    }

    /**
     * 仅校验 FIELD/INDICATOR 来源（其取值来自配置可声明集合）；
     * MODEL/ASSIGNMENT 来源为决策流运行期动态产出的赋值上下文，不在配置期声明集合内，故不校验。
     */
    private static void checkVariable(Variable v, Set<String> declaredFields,
                                      Set<String> declaredIndicators, ValidationException.Builder errors) {
        if (v == null) {
            return;
        }
        switch (v.source()) {
            case FIELD -> {
                if (declaredFields != null && !declaredFields.contains(v.ref())) {
                    errors.field(v.ref(), "未声明的字段");
                }
            }
            case INDICATOR -> {
                if (declaredIndicators != null && !declaredIndicators.contains(v.ref())) {
                    errors.field(v.ref(), "未声明的指标");
                }
            }
            default -> {
                // MODEL/ASSIGNMENT：运行期赋值上下文，跳过编译期声明校验
            }
        }
    }
}
