package com.riskplatform.ruleconfig.domain.strategy;

/**
 * 验证策略作用域（risk-console-redesign / R5.4）。
 *
 * <p>作用域取「某个具体业务场景」或「不限业务场景（ANY_SCENARIO）」二者之一：
 * <ul>
 *   <li>{@code anyScope=true}：不限业务场景，此时 {@code scenarioId} 为空。</li>
 *   <li>{@code anyScope=false}：限定某个具体业务场景，此时 {@code scenarioId} 必填。</li>
 * </ul>
 *
 * <p>持久化映射：{@code any_scope}（0/1）+ {@code scope_scenario_id}（NULL+any_scope=1 表示不限）。
 *
 * <p>为不可变值对象，非法组合在 {@link #scenario(Long)} 工厂处即拒绝。
 */
public final class StrategyScope {

    /** 不限业务场景的单例。 */
    private static final StrategyScope ANY = new StrategyScope(true, null);

    private final boolean anyScope;
    private final Long scenarioId;

    private StrategyScope(boolean anyScope, Long scenarioId) {
        this.anyScope = anyScope;
        this.scenarioId = scenarioId;
    }

    /** 不限业务场景（ANY_SCENARIO）。 */
    public static StrategyScope anyScenario() {
        return ANY;
    }

    /** 限定某个具体业务场景。scenarioId 为空时视为非法，返回 {@code null} 由上层归集字段错误。 */
    public static StrategyScope scenario(Long scenarioId) {
        if (scenarioId == null) {
            return null;
        }
        return new StrategyScope(false, scenarioId);
    }

    /**
     * 从持久化字段重建作用域。
     *
     * @param anyScope   是否不限业务场景
     * @param scenarioId 作用域场景ID（不限时可为空）
     */
    public static StrategyScope rehydrate(boolean anyScope, Long scenarioId) {
        if (anyScope) {
            return ANY;
        }
        return new StrategyScope(false, scenarioId);
    }

    public boolean isAnyScope() {
        return anyScope;
    }

    public Long getScenarioId() {
        return scenarioId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StrategyScope other)) {
            return false;
        }
        if (anyScope != other.anyScope) {
            return false;
        }
        return scenarioId == null ? other.scenarioId == null : scenarioId.equals(other.scenarioId);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(anyScope);
        result = 31 * result + (scenarioId == null ? 0 : scenarioId.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return anyScope ? "ANY_SCENARIO" : ("SCENARIO(" + scenarioId + ")");
    }
}
