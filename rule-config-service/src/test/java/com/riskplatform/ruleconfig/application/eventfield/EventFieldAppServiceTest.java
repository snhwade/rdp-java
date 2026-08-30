package com.riskplatform.ruleconfig.application.eventfield;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.application.eventfield.EventFieldAppService.EventFieldView;
import com.riskplatform.ruleconfig.application.field.InMemoryFieldRepository;
import com.riskplatform.ruleconfig.domain.error.RuleConfigErrorCode;
import com.riskplatform.ruleconfig.domain.eventfield.EventField;
import com.riskplatform.ruleconfig.domain.eventfield.EventFieldReferenceChecker;
import com.riskplatform.ruleconfig.domain.eventtype.EventPurpose;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 事件字段应用服务单元测试（R4.2/R4.3/R4.4/R4.5/R4.6/R4.7）。
 */
class EventFieldAppServiceTest {

    private static final String EVENT = "PAY_EVENT";

    private InMemoryEventFieldRepository repo;
    private InMemoryFieldRepository fieldRepo;
    private EventFieldAppService service;
    private Long fieldId;

    @BeforeEach
    void setUp() {
        repo = new InMemoryEventFieldRepository();
        fieldRepo = new InMemoryFieldRepository();
        // 默认引用检查器：无引用，允许移除（R4.6）。
        service = new EventFieldAppService(repo, fieldRepo, EventFieldReferenceChecker.noop());
        FieldDefinition field = fieldRepo.saveField(
                FieldDefinition.create("txn_amount", "交易金额", "Double", "金额"));
        fieldId = field.id();
    }

    @Test
    void add_roundTrip_persistsAssociationWithPurposesAndDerivedFlag() {
        // R4.2/R4.3/R4.5：添加后可在列表查询读回，用途、衍生标记与字段展示信息一致。
        EventFieldView view = service.add(EVENT, fieldId, Set.of(EventPurpose.COMPUTE), true);

        assertThat(view.id()).isNotNull();
        assertThat(view.eventTypeCode()).isEqualTo(EVENT);
        assertThat(view.fieldId()).isEqualTo(fieldId);
        assertThat(view.fieldCode()).isEqualTo("txn_amount");
        assertThat(view.fieldName()).isEqualTo("交易金额");
        assertThat(view.dataType()).isEqualTo("Double");
        assertThat(view.purposes()).containsExactly("COMPUTE");
        assertThat(view.derived()).isTrue();

        List<EventFieldView> list = service.list(EVENT);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).fieldId()).isEqualTo(fieldId);
        assertThat(list.get(0).derived()).isTrue();
    }

    @Test
    void markDerived_togglesFlag() {
        // R4.5：标记/取消衍生字段标记可往返读回。
        EventFieldView created = service.add(EVENT, fieldId, Set.of(EventPurpose.DECISION), false);
        assertThat(created.derived()).isFalse();

        EventFieldView marked = service.markDerived(created.id(), true);
        assertThat(marked.derived()).isTrue();
        assertThat(service.list(EVENT).get(0).derived()).isTrue();

        EventFieldView unmarked = service.markDerived(created.id(), false);
        assertThat(unmarked.derived()).isFalse();
    }

    @Test
    void add_duplicateAssociation_rejected() {
        // R4.4：同一事件下同一字段重复关联应被拒绝且不创建重复关联。
        service.add(EVENT, fieldId, Set.of(EventPurpose.COMPUTE), false);
        BizException ex = catchThrowableOfType(
                () -> service.add(EVENT, fieldId, Set.of(EventPurpose.DECISION), false),
                BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.DUPLICATE);
        assertThat(service.list(EVENT)).hasSize(1);
    }

    @Test
    void add_sameFieldDifferentEvent_notRejected() {
        // R4.4：唯一性以(event,field)精确判定；同一字段添加到不同事件不应误判为重复。
        service.add(EVENT, fieldId, Set.of(EventPurpose.COMPUTE), false);
        EventFieldView other = service.add("OTHER_EVENT", fieldId, Set.of(EventPurpose.COMPUTE), false);
        assertThat(other.id()).isNotNull();
        assertThat(service.list(EVENT)).hasSize(1);
        assertThat(service.list("OTHER_EVENT")).hasSize(1);
    }

    @Test
    void add_emptyPurposes_rejectedWithFieldName() {
        // R4.3：用途为空集应被拒绝，并返回字段名 purposes。
        ValidationException ex = catchThrowableOfType(
                () -> service.add(EVENT, fieldId, Set.of(), false), ValidationException.class);
        assertThat(ex.getFields()).containsKey("purposes");
        assertThat(service.list(EVENT)).isEmpty();
    }

    @Test
    void add_bothPurposes_accepted() {
        // R4.3：用途为 {COMPUTE, DECISION} 非空子集应被接受。
        EventFieldView view = service.add(EVENT, fieldId,
                Set.of(EventPurpose.COMPUTE, EventPurpose.DECISION), false);
        assertThat(view.purposes()).containsExactlyInAnyOrder("COMPUTE", "DECISION");
    }

    @Test
    void add_fieldNotInLibrary_rejected() {
        // R4.2：所添加字段须在字段库真实存在。
        BizException ex = catchThrowableOfType(
                () -> service.add(EVENT, 9999L, Set.of(EventPurpose.COMPUTE), false),
                BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(RuleConfigErrorCode.REF_NOT_FOUND);
    }

    @Test
    void remove_unreferenced_deletesAssociation() {
        // R4.6：未被引用的事件字段可被移除。
        EventFieldView created = service.add(EVENT, fieldId, Set.of(EventPurpose.COMPUTE), false);
        service.remove(created.id());
        assertThat(service.list(EVENT)).isEmpty();
    }

    @Test
    void remove_referenced_rejectedWithInUseError() {
        // R4.7：仍被规则/评级模型引用时移除应被拒绝并返回 EVENT_FIELD.IN_USE，保留关联。
        EventFieldReferenceChecker referenced = eventField -> List.of("规则");
        service = new EventFieldAppService(repo, fieldRepo, referenced);
        EventFieldView created = service.add(EVENT, fieldId, Set.of(EventPurpose.COMPUTE), false);

        BizException ex = catchThrowableOfType(
                () -> service.remove(created.id()), BizException.class);
        assertThat(ex.getErrorCode()).isEqualTo(RuleConfigErrorCode.EVENT_FIELD_IN_USE);
        assertThat(service.list(EVENT)).hasSize(1);
    }

    @Test
    void remove_nonExistent_rejected() {
        assertThatThrownBy(() -> service.remove(9999L)).isInstanceOf(BizException.class);
    }

    @Test
    void aggregate_rehydrate_preservesState() {
        // 聚合重建路径（仓储读回用）保持状态不变。
        EventField rehydrated = EventField.rehydrate(7L, EVENT, fieldId,
                Set.of(EventPurpose.DECISION), true);
        assertThat(rehydrated.getId()).isEqualTo(7L);
        assertThat(rehydrated.isDerived()).isTrue();
        assertThat(rehydrated.getPurposes()).containsExactly(EventPurpose.DECISION);
    }
}
