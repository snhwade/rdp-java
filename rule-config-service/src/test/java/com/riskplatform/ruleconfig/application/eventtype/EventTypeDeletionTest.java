package com.riskplatform.ruleconfig.application.eventtype;

import com.riskplatform.common.error.BizException;
import com.riskplatform.ruleconfig.domain.eventtype.CompositeEventReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventtype.EventDependencySource;
import com.riskplatform.ruleconfig.domain.eventtype.EventEngineStatusQuery;
import com.riskplatform.ruleconfig.domain.eventtype.EventKind;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.eventtype.EventReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventtype.EventType;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeRepository;
import com.riskplatform.ruleconfig.domain.eventtype.EventTypeStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 事件删除依赖拦截应用服务单元测试（risk-console-redesign R2.8/R2.9，任务 2.3）。
 *
 * <p>以内存仓储 + {@link CompositeEventReferenceChecker}（内存依赖来源假体）验证：
 * <ul>
 *   <li>存在任一类依赖时删除被拒绝、事件保留，返回 {@code EVENT.HAS_DEPENDENCY}（R2.9，Property 6）</li>
 *   <li>无任何依赖时删除成功（R2.8）</li>
 * </ul>
 */
class EventTypeDeletionTest {

    /** 内存事件仓储假体。 */
    private static final class InMemoryEventTypeRepository implements EventTypeRepository {
        private final Map<Long, EventType> store = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong();

        @Override
        public EventType save(EventType eventType) {
            long id = seq.incrementAndGet();
            eventType.assignId(id);
            store.put(id, eventType);
            return eventType;
        }

        @Override
        public void update(EventType eventType) {
            store.put(eventType.getId(), eventType);
        }

        @Override
        public Optional<EventType> findByCode(String code) {
            return store.values().stream().filter(e -> code.equals(e.getCode())).findFirst();
        }

        @Override
        public Optional<EventType> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsByCode(String code) {
            return findByCode(code).isPresent();
        }

        @Override
        public boolean existsByCodeExcludingId(String code, Long excludeId) {
            return store.values().stream()
                    .anyMatch(e -> code.equals(e.getCode()) && !e.getId().equals(excludeId));
        }

        @Override
        public List<EventType> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<EventType> findByScenarioId(Long scenarioId) {
            return store.values().stream()
                    .filter(e -> scenarioId != null && scenarioId.equals(e.getScenarioId()))
                    .toList();
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        boolean exists(Long id) {
            return store.containsKey(id);
        }
    }

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

    private static final EventEngineStatusQuery STATUS_QUERY =
            eventCode -> EventEngineStatusQuery.Status.UNKNOWN;

    private static EventType seedEvent(InMemoryEventTypeRepository repo) {
        EventType event = EventType.create("EVT_DEL", "待删事件", 1L,
                EnumSet.of(EventPurpose.COMPUTE), EventKind.FACT);
        return repo.save(event);
    }

    @Test
    void delete_blockedWhenDependencyExists() {
        InMemoryEventTypeRepository repo = new InMemoryEventTypeRepository();
        EventType event = seedEvent(repo);
        EventReferenceChecker checker = new CompositeEventReferenceChecker(List.of(
                source("规则包", true)));
        EventTypeAppService service = new EventTypeAppService(repo, checker, STATUS_QUERY);

        BizException ex = catchThrowableOfType(
                () -> service.delete(event.getId()), BizException.class);

        assertThat(ex.getErrorCode().code()).isEqualTo("EVENT.HAS_DEPENDENCY");
        assertThat(ex.getMessage()).contains("规则包");
        // 事件被保留
        assertThat(repo.exists(event.getId())).isTrue();
    }

    @Test
    void delete_allowedWhenNoDependency() {
        InMemoryEventTypeRepository repo = new InMemoryEventTypeRepository();
        EventType event = seedEvent(repo);
        EventReferenceChecker checker = new CompositeEventReferenceChecker(List.of(
                source("事件字段", false),
                source("规则包", false),
                source("决策流", false),
                source("评级模型", false)));
        EventTypeAppService service = new EventTypeAppService(repo, checker, STATUS_QUERY);

        service.delete(event.getId());

        assertThat(repo.exists(event.getId())).isFalse();
    }

    @Test
    void delete_nonExistentEvent_notFound() {
        InMemoryEventTypeRepository repo = new InMemoryEventTypeRepository();
        EventReferenceChecker checker = EventReferenceChecker.noop();
        EventTypeAppService service = new EventTypeAppService(repo, checker, STATUS_QUERY);

        assertThat(catchThrowableOfType(() -> service.delete(999L), BizException.class))
                .isNotNull();
    }
}
