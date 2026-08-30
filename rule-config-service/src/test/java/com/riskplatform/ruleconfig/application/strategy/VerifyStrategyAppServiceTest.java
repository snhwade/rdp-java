package com.riskplatform.ruleconfig.application.strategy;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.application.strategy.VerifyStrategyAppService.VerifyStrategyRelations;
import com.riskplatform.ruleconfig.domain.strategy.RuleStrategy;
import com.riskplatform.ruleconfig.domain.strategy.ScoreBandStrategy;
import com.riskplatform.ruleconfig.domain.strategy.StrategyCategory;
import com.riskplatform.ruleconfig.domain.strategy.StrategyDef;
import com.riskplatform.ruleconfig.domain.strategy.StrategyScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 验证策略应用服务单元测试（risk-console-redesign / R5.2-R5.8）。
 *
 * <p>覆盖：创建往返、优先级范围校验（边界 1/9999/0/10000）、ANY_SCENARIO 与具体场景作用域、
 * code 重复拒绝、关联关系查询、以及「仅 VERIFY」限制。
 */
class VerifyStrategyAppServiceTest {

    private InMemoryStrategyDefRepository strategyRepo;
    private InMemoryRuleStrategyRepository ruleStrategyRepo;
    private InMemoryScoreBandStrategyRepository scoreBandStrategyRepo;
    private VerifyStrategyAppService service;

    @BeforeEach
    void setUp() {
        strategyRepo = new InMemoryStrategyDefRepository();
        ruleStrategyRepo = new InMemoryRuleStrategyRepository();
        scoreBandStrategyRepo = new InMemoryScoreBandStrategyRepository();
        service = new VerifyStrategyAppService(strategyRepo, ruleStrategyRepo, scoreBandStrategyRepo);
    }

    @Test
    void create_concreteScope_roundTrip() {
        StrategyDef created = service.create("SMS_VERIFY", "短信核身", 100,
                StrategyScope.scenario(7L), "{}");
        assertThat(created.getId()).isNotNull();

        StrategyDef found = service.get(created.getId());
        assertThat(found.getCode()).isEqualTo("SMS_VERIFY");
        assertThat(found.getName()).isEqualTo("短信核身");
        assertThat(found.getPriority()).isEqualTo(100);
        assertThat(found.getCategory()).isEqualTo(StrategyCategory.VERIFY);
        // 验证策略固定为全场景通用，传入的具体 scope 被忽略（R5.4 产品约束）。
        assertThat(found.getScope().isAnyScope()).isTrue();
        assertThat(found.getScope().getScenarioId()).isNull();
    }

    @Test
    void create_anyScenarioScope_roundTrip() {
        StrategyDef created = service.create("ANY_V", "通用核身", 50,
                StrategyScope.anyScenario(), null);
        StrategyDef found = service.get(created.getId());
        assertThat(found.getScope().isAnyScope()).isTrue();
        assertThat(found.getScope().getScenarioId()).isNull();
    }

    @Test
    void create_priorityBoundary1And9999_accepted() {
        assertThat(service.create("V1", "n", 1, StrategyScope.anyScenario(), null).getPriority())
                .isEqualTo(1);
        assertThat(service.create("V9999", "n", 9999, StrategyScope.anyScenario(), null).getPriority())
                .isEqualTo(9999);
    }

    @Test
    void create_priorityZero_rejectedWithRangeError() {
        ValidationException ex = catchThrowableOfType(
                () -> service.create("V0", "n", 0, StrategyScope.anyScenario(), null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("priority");
        // 不应持久化
        assertThat(service.list()).isEmpty();
    }

    @Test
    void create_priority10000_rejectedWithRangeError() {
        ValidationException ex = catchThrowableOfType(
                () -> service.create("V10000", "n", 10000, StrategyScope.anyScenario(), null),
                ValidationException.class);
        assertThat(ex.getFields()).containsKey("priority");
        assertThat(service.list()).isEmpty();
    }

    @Test
    void create_duplicateCodeWithinVerify_rejected() {
        service.create("DUP", "第一", 100, StrategyScope.anyScenario(), null);
        BizException ex = catchThrowableOfType(
                () -> service.create("DUP", "第二", 200, StrategyScope.anyScenario(), null),
                BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.DUPLICATE);
        assertThat(service.list()).hasSize(1);
    }

    @Test
    void create_prefixSimilarCode_notRejected() {
        // R5.7：精确等值唯一校验，互为前缀的相似 code 不应误判为重复。
        service.create("VERIFY", "基础", 100, StrategyScope.anyScenario(), null);
        StrategyDef similar = service.create("VERIFY_SMS", "短信", 100, StrategyScope.anyScenario(), null);
        assertThat(similar.getId()).isNotNull();
        assertThat(service.list()).hasSize(2);
    }

    @Test
    void update_changesNamePriorityScope() {
        StrategyDef created = service.create("V", "旧", 100, StrategyScope.anyScenario(), null);
        StrategyDef updated = service.update(created.getId(), "新", 5, StrategyScope.scenario(9L), "{}");
        assertThat(updated.getName()).isEqualTo("新");
        assertThat(updated.getPriority()).isEqualTo(5);
        assertThat(updated.getScope().isAnyScope()).isTrue();

        StrategyDef found = service.get(created.getId());
        assertThat(found.getName()).isEqualTo("新");
        assertThat(found.getPriority()).isEqualTo(5);
        assertThat(found.getScope().isAnyScope()).isTrue();
    }

    @Test
    void update_priorityOutOfRange_rejected() {
        StrategyDef created = service.create("V", "n", 100, StrategyScope.anyScenario(), null);
        assertThatThrownBy(() -> service.update(created.getId(), "n", 0, StrategyScope.anyScenario(), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void list_returnsOnlyVerifyStrategies() {
        // 注入一个非验证类策略，确认列表与 get 都不会暴露它（R5.2）。
        StrategyDef control = StrategyDef.create(StrategyCategory.CONTROL_STATE, "FREEZE", "冻结", null);
        strategyRepo.save(control);

        service.create("V", "核身", 100, StrategyScope.anyScenario(), null);

        assertThat(service.list()).hasSize(1);
        assertThat(service.list().get(0).getCode()).isEqualTo("V");

        assertThatThrownBy(() -> service.get(control.getId()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void relations_returnsRuleAndScoreBandBindings() {
        StrategyDef created = service.create("V", "核身", 100, StrategyScope.anyScenario(), null);
        Long defId = created.getId();

        ruleStrategyRepo.save(RuleStrategy.create(11L, defId, StrategyCategory.VERIFY, 1, null));
        ruleStrategyRepo.save(RuleStrategy.create(12L, defId, StrategyCategory.VERIFY, 2, null));
        scoreBandStrategyRepo.save(ScoreBandStrategy.create(21L, defId));
        // 另一个策略的绑定不应混入。
        scoreBandStrategyRepo.save(ScoreBandStrategy.create(22L, 999L));

        VerifyStrategyRelations relations = service.relations(defId);
        assertThat(relations.strategyDefId()).isEqualTo(defId);
        assertThat(relations.ruleBindings()).hasSize(2);
        assertThat(relations.scoreBandBindings()).hasSize(1);
        assertThat(relations.scoreBandBindings().get(0).getScoreBandId()).isEqualTo(21L);
    }

    @Test
    void relations_nonExistent_rejected() {
        assertThatThrownBy(() -> service.relations(999L)).isInstanceOf(BizException.class);
    }
}
