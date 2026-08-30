package com.riskplatform.ruleconfig.domain.eventtype;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨子域事件依赖检查领域服务单元测试（risk-console-redesign R2.9，任务 2.3）。
 *
 * <p>以内存假体 {@link EventDependencySource} 验证：存在任一类依赖时返回非空依赖类型列表
 * （删除被拦截），无任何依赖时返回空列表（允许删除）。
 */
class CompositeEventReferenceCheckerTest {

    /** 固定回答的内存依赖来源假体。 */
    private static EventDependencySource source(String type, boolean has) {
        return new EventDependencySource() {
            @Override
            public String dependencyType() {
                return type;
            }

            @Override
            public boolean hasDependency(String eventCode) {
                return has;
            }
        };
    }

    @Test
    void findDependencies_whenAnySourceHasDependency_returnsThoseTypes() {
        CompositeEventReferenceChecker checker = new CompositeEventReferenceChecker(List.of(
                source("事件字段", true),
                source("规则包", false),
                source("决策流", true),
                source("评级模型", false)));

        List<String> deps = checker.findDependencies("EVT1");

        assertThat(deps).containsExactly("事件字段", "决策流");
    }

    @Test
    void findDependencies_whenNoSourceHasDependency_returnsEmpty() {
        CompositeEventReferenceChecker checker = new CompositeEventReferenceChecker(List.of(
                source("事件字段", false),
                source("规则包", false),
                source("决策流", false),
                source("评级模型", false)));

        assertThat(checker.findDependencies("EVT1")).isEmpty();
    }

    @Test
    void findDependencies_blankEventCode_returnsEmpty() {
        CompositeEventReferenceChecker checker = new CompositeEventReferenceChecker(List.of(
                source("事件字段", true)));

        assertThat(checker.findDependencies("  ")).isEmpty();
        assertThat(checker.findDependencies(null)).isEmpty();
    }

    @Test
    void findDependencies_noSources_returnsEmpty() {
        CompositeEventReferenceChecker checker = new CompositeEventReferenceChecker(null);
        assertThat(checker.findDependencies("EVT1")).isEmpty();
    }
}
