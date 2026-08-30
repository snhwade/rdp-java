package com.riskplatform.ruleconfig.adapter.field;

import com.riskplatform.ruleconfig.application.field.FieldService;
import com.riskplatform.ruleconfig.domain.field.DerivedField;
import com.riskplatform.ruleconfig.domain.field.FieldDefinition;
import com.riskplatform.ruleconfig.domain.field.FieldImportResult;
import com.riskplatform.ruleconfig.domain.field.FieldRelations;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 字段库与衍生字段 REST 适配器（S7）。
 *
 * <ul>
 *   <li>字段库：POST/GET/PUT/DELETE /api/v1/fields</li>
 *   <li>衍生字段：POST/GET/PUT/DELETE /api/v1/derived-fields（GET 支持 ?eventTypeCode=）</li>
 *   <li>计算：POST /api/v1/derived-fields/compute（注入衍生字段后的上下文）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class FieldController {

    private final FieldService service;

    public FieldController(FieldService service) {
        this.service = service;
    }

    // —— 字段库 ——

    @PostMapping("/fields")
    public FieldDefinition createField(@RequestBody FieldRequest req) {
        return service.createField(req.code(), req.name(), req.dataType(), req.label());
    }

    @GetMapping("/fields")
    public List<FieldDefinition> listFields() {
        return service.listFields();
    }

    @PutMapping("/fields/{id}")
    public FieldDefinition updateField(@PathVariable("id") Long id, @RequestBody FieldRequest req) {
        return service.updateField(id, req.code(), req.name(), req.dataType(), req.label(),
                req.enabled() == null || req.enabled());
    }

    @DeleteMapping("/fields/{id}")
    public void deleteField(@PathVariable("id") Long id) {
        service.deleteField(id);
    }

    /** 批量导入字段（R3.6）：逐条校验，返回成功与逐条失败原因。 */
    @PostMapping("/fields/import")
    public FieldImportResult importFields(@RequestBody FieldImportRequest req) {
        List<FieldService.FieldImportRecord> records = (req == null || req.records() == null)
                ? List.of()
                : req.records().stream()
                        .map(r -> new FieldService.FieldImportRecord(r.code(), r.name(), r.dataType(), r.label()))
                        .toList();
        return service.importFields(records);
    }

    /** 字段关联关系查询（R3.7）：引用该字段的事件、枚举值与衍生字段。 */
    @GetMapping("/fields/{id}/relations")
    public FieldRelations relations(@PathVariable("id") Long id) {
        return service.relations(id);
    }

    // —— 衍生字段 ——

    @PostMapping("/derived-fields")
    public DerivedField createDerived(@RequestBody DerivedRequest req) {
        return service.createDerived(req.eventTypeCode(), req.name(), req.expression());
    }

    @GetMapping("/derived-fields")
    public List<DerivedField> listDerived(@RequestParam(name = "eventTypeCode", required = false) String eventTypeCode) {
        return service.listDerived(eventTypeCode);
    }

    @PutMapping("/derived-fields/{id}")
    public DerivedField updateDerived(@PathVariable("id") Long id, @RequestBody DerivedRequest req) {
        return service.updateDerived(id, req.name(), req.expression(),
                req.enabled() == null || req.enabled());
    }

    @DeleteMapping("/derived-fields/{id}")
    public void deleteDerived(@PathVariable("id") Long id) {
        service.deleteDerived(id);
    }

    @PostMapping("/derived-fields/compute")
    public Map<String, Object> compute(@RequestBody ComputeRequest req) {
        return service.computeDerived(req.eventTypeCode(), req.context());
    }

    public record FieldRequest(@NotBlank String code, @NotBlank String name, @NotBlank String dataType,
                               String label, Boolean enabled) {
    }

    public record FieldImportRequest(List<FieldImportItem> records) {
    }

    public record FieldImportItem(String code, String name, String dataType, String label) {
    }

    public record DerivedRequest(@NotBlank String eventTypeCode, @NotBlank String name,
                                 @NotBlank String expression, Boolean enabled) {
    }

    public record ComputeRequest(@NotBlank String eventTypeCode, Map<String, Object> context) {
    }
}
