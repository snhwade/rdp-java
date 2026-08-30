package com.riskplatform.ruleconfig.infrastructure.eventtype;

import com.riskplatform.ruleconfig.domain.eventtype.EventReferenceChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 事件依赖检查默认实现装配（risk-console-redesign R2.9）。
 *
 * <p>本期任务 2.2 仅打通事件 CRUD 与删除链路，删除依赖检查默认返回「无依赖」。
 * 任务 2.3 将提供跨子域（事件字段/规则包/决策流/评级模型）的真实
 * {@link EventReferenceChecker} 实现并通过组件注册替换此默认 Bean
 * （{@link ConditionalOnMissingBean} 保证一旦存在真实实现即不再使用本默认实现）。
 */
@Configuration
public class NoopEventReferenceChecker {

    @Bean
    @ConditionalOnMissingBean(EventReferenceChecker.class)
    public EventReferenceChecker defaultEventReferenceChecker() {
        return eventCode -> List.of();
    }
}
