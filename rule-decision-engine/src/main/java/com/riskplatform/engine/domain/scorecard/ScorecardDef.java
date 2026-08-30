package com.riskplatform.engine.domain.scorecard;

import com.riskplatform.engine.domain.asset.AssetRef;

import java.util.List;

/**
 * 评分卡定义（引擎执行侧，S3）。与 rule-config 配置同构（精简至执行所需）。
 *
 * <p><b>关联资产（扩展阶段 11.2，R9.3）</b>：{@code referencedAssets} 声明本评分卡执行前需先级联
 * 执行的下级资产（如某评分变量取自另一资产的产出）。本执行器自身只读上下文字段值、不会自动级联，
 * 故由 {@code DecisionToolNodeHandler} 在执行本卡前按声明先执行被引用资产并把产出注入上下文。
 * 旧调用与旧 JSON 缺省该字段时为空列表（无级联）。
 *
 * @param id               评分卡 id（命中决策的来源标识）
 * @param name             名称
 * @param variables        评分变量
 * @param levels           等级区间
 * @param referencedAssets 被引用的下级关联资产（自动级联执行，R9.3，可空）
 */
public record ScorecardDef(Long id, String name, List<Variable> variables, List<Level> levels,
                           List<AssetRef> referencedAssets) {

    /** 兼容旧签名构造器（无关联资产声明）：既有 4 参调用点（如 ScorecardEvalController）无需改动。 */
    public ScorecardDef(Long id, String name, List<Variable> variables, List<Level> levels) {
        this(id, name, variables, levels, List.of());
    }

    public enum Op { GT, GE, LT, LE, EQ, NE, BETWEEN, IN }

    public record Variable(String var, String source, double weight, double defaultScore, List<Bin> bins) {
    }

    public record Bin(Op op, Double value, Double value2, List<String> values, double score) {
    }

    public record Level(double minScore, double maxScore, String level, String decision, int priority) {
    }
}
