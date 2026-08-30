package com.riskplatform.screening.adapter.listmgmt;

import com.riskplatform.screening.application.listmgmt.ListMgmtService;
import com.riskplatform.screening.infrastructure.listmgmt.ListAttrDefPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListDimensionPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListEntryPO;
import com.riskplatform.screening.infrastructure.listmgmt.ListLibraryPO;
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
import java.util.Map;

/** 名单维度 REST API。 */
@RestController
@RequestMapping("/api/v1/list-dimensions")
public class ListDimensionController {

    private final ListMgmtService service;

    public ListDimensionController(ListMgmtService service) {
        this.service = service;
    }

    @GetMapping
    public List<DimensionView> list(@RequestParam(required = false) String keyword) {
        return service.listDimensions(keyword).stream().map(DimensionView::from).toList();
    }

    @PostMapping
    public DimensionView create(@RequestBody CreateDimensionRequest req) {
        return DimensionView.from(service.createDimension(
                req.code(), req.name(), req.maskRule(), req.fuzzyEnabled(), req.updatedBy()));
    }

    @PutMapping("/{id}")
    public DimensionView update(@PathVariable Long id, @RequestBody UpdateDimensionRequest req) {
        return DimensionView.from(service.updateDimension(
                id, req.name(), req.maskRule(), req.fuzzyEnabled(), req.updatedBy()));
    }

    @PostMapping("/batch-delete")
    public void deleteBatch(@RequestBody DeleteBatchRequest req) {
        service.deleteDimensions(req.ids());
    }

    public record CreateDimensionRequest(
            @NotBlank String code,
            @NotBlank String name,
            String maskRule,
            boolean fuzzyEnabled,
            String updatedBy) {
    }

    public record UpdateDimensionRequest(String name, String maskRule, Boolean fuzzyEnabled, String updatedBy) {
    }

    public record DeleteBatchRequest(List<Long> ids) {
    }

    public record DimensionView(Long id, String code, String name, String maskRule,
                                boolean fuzzyEnabled, String updatedBy,
                                String createdAt, String updatedAt) {
        static DimensionView from(ListDimensionPO po) {
            return new DimensionView(po.getId(), po.getCode(), po.getName(), po.getMaskRule(),
                    po.getFuzzyEnabled() != null && po.getFuzzyEnabled() == 1,
                    po.getUpdatedBy(),
                    fmt(po.getCreatedAt()), fmt(po.getUpdatedAt()));
        }

        private static String fmt(LocalDateTime t) {
            return t == null ? null : t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
