package com.riskplatform.screening.adapter.listmgmt;

import com.riskplatform.screening.application.listmgmt.ListMgmtService;
import com.riskplatform.screening.infrastructure.listmgmt.ListEntryPO;
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

/** 名单库记录 REST API。 */
@RestController
@RequestMapping("/api/v1/list-entries")
public class ListEntryController {

    private final ListMgmtService service;

    public ListEntryController(ListMgmtService service) {
        this.service = service;
    }

    @GetMapping
    public List<EntryView> list(@RequestParam Long libraryId,
                                @RequestParam(required = false) String dimensionCode,
                                @RequestParam(required = false) String keyword) {
        return service.listEntries(libraryId, dimensionCode, keyword).stream()
                .map(EntryView::from).toList();
    }

    @PostMapping
    public EntryView create(@RequestBody CreateEntryRequest req) {
        return EntryView.from(service.createEntry(
                req.libraryId(), req.dimensionCode(), req.dimensionValue(),
                parseDt(req.effectiveAt()), parseDt(req.expireAt()), req.extraAttrs(), req.remark()));
    }

    @PutMapping("/{id}")
    public EntryView update(@PathVariable Long id, @RequestBody UpdateEntryRequest req) {
        return EntryView.from(service.updateEntry(
                id, req.dimensionValue(), parseDt(req.effectiveAt()), parseDt(req.expireAt()),
                req.enabled(), req.extraAttrs(), req.remark()));
    }

    @PostMapping("/batch-delete")
    public void deleteBatch(@RequestBody DeleteBatchRequest req) {
        service.deleteEntries(req.ids());
    }

    @PostMapping("/batch-enabled")
    public void batchEnabled(@RequestBody BatchEnabledRequest req) {
        service.batchSetEnabled(req.ids(), req.enabled());
    }

    /** 命中判定：按库编码或全库扫描，返回命中记录（不含黑/白语义）。 */
    @GetMapping("/check")
    public CheckView check(@RequestParam String dimensionCode,
                           @RequestParam String value,
                           @RequestParam(required = false) String libraryCode) {
        if (libraryCode != null && !libraryCode.isBlank()) {
            List<ListEntryPO> hits = service.matchEntries(libraryCode, dimensionCode, value);
            return new CheckView(!hits.isEmpty(), hits.stream().map(EntryView::from).toList());
        }
        List<ListMgmtService.LibraryHit> hits = service.matchAllLibraries(dimensionCode, value);
        return new CheckView(!hits.isEmpty(), hits.stream().map(LibraryHitView::from).toList());
    }

    public record CreateEntryRequest(
            Long libraryId,
            @NotBlank String dimensionCode,
            @NotBlank String dimensionValue,
            String effectiveAt,
            String expireAt,
            Map<String, Object> extraAttrs,
            String remark) {
    }

    public record UpdateEntryRequest(
            String dimensionValue, String effectiveAt, String expireAt,
            Boolean enabled, Map<String, Object> extraAttrs, String remark) {
    }

    public record DeleteBatchRequest(List<Long> ids) {
    }

    public record BatchEnabledRequest(List<Long> ids, boolean enabled) {
    }

    public record CheckView(boolean hit, List<?> hits) {
    }

    public record EntryView(Long id, Long libraryId, String dimensionCode, String dimensionValue,
                            String effectiveAt, String expireAt, boolean enabled, String source,
                            String remark, Map<String, Object> extraAttrs,
                            String createdAt, String updatedAt) {
        static EntryView from(ListEntryPO po) {
            return new EntryView(po.getId(), po.getLibraryId(), po.getDimensionCode(),
                    po.getDimensionValue(),
                    fmt(po.getEffectiveAt()), fmt(po.getExpireAt()),
                    po.getEnabled() == null || po.getEnabled() == 1,
                    po.getSource(), po.getRemark(), po.getExtraAttrs(),
                    fmt(po.getCreatedAt()), fmt(po.getUpdatedAt()));
        }

        private static String fmt(LocalDateTime t) {
            return t == null ? null : t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    public record LibraryHitView(Long libraryId, String libraryCode, String libraryName,
                                 Long entryId, String dimensionCode, String dimensionValue) {
        static LibraryHitView from(ListMgmtService.LibraryHit h) {
            return new LibraryHitView(h.libraryId(), h.libraryCode(), h.libraryName(),
                    h.entryId(), h.dimensionCode(), h.dimensionValue());
        }
    }

    private static LocalDateTime parseDt(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
