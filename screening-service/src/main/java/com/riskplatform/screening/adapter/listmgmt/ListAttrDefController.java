package com.riskplatform.screening.adapter.listmgmt;

import com.riskplatform.screening.application.listmgmt.ListMgmtService;
import com.riskplatform.screening.infrastructure.listmgmt.ListAttrDefPO;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 名单附加属性 REST API。 */
@RestController
@RequestMapping("/api/v1/list-attr-defs")
public class ListAttrDefController {

    private final ListMgmtService service;

    public ListAttrDefController(ListMgmtService service) {
        this.service = service;
    }

    @GetMapping
    public List<AttrDefView> list(@RequestParam(required = false) String keyword) {
        return service.listAttrDefs(keyword).stream().map(AttrDefView::from).toList();
    }

    @PostMapping
    public AttrDefView create(@RequestBody CreateAttrDefRequest req) {
        return AttrDefView.from(service.createAttrDef(
                req.code(), req.name(), req.inputType(), req.required(), req.multiValue(), req.maskRule()));
    }

    @PutMapping("/{id}")
    public AttrDefView update(@PathVariable Long id, @RequestBody UpdateAttrDefRequest req) {
        return AttrDefView.from(service.updateAttrDef(
                id, req.name(), req.inputType(), req.required(), req.multiValue(), req.maskRule()));
    }

    @PostMapping("/batch-delete")
    public void deleteBatch(@RequestBody DeleteBatchRequest req) {
        service.deleteAttrDefs(req.ids());
    }

    public record CreateAttrDefRequest(
            @NotBlank String code,
            @NotBlank String name,
            String inputType,
            boolean required,
            boolean multiValue,
            String maskRule) {
    }

    public record UpdateAttrDefRequest(
            String name, String inputType, Boolean required, Boolean multiValue, String maskRule) {
    }

    public record DeleteBatchRequest(List<Long> ids) {
    }

    public record AttrDefView(Long id, String code, String name, String inputType,
                              boolean required, boolean multiValue, String maskRule,
                              String createdAt, String updatedAt) {
        static AttrDefView from(ListAttrDefPO po) {
            return new AttrDefView(po.getId(), po.getCode(), po.getName(), po.getInputType(),
                    po.getRequired() != null && po.getRequired() == 1,
                    po.getMultiValue() != null && po.getMultiValue() == 1,
                    po.getMaskRule(),
                    fmt(po.getCreatedAt()), fmt(po.getUpdatedAt()));
        }

        private static String fmt(LocalDateTime t) {
            return t == null ? null : t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
