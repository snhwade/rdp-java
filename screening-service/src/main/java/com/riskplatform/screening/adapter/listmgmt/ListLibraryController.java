package com.riskplatform.screening.adapter.listmgmt;

import com.riskplatform.screening.application.listmgmt.ListMgmtService;
import com.riskplatform.screening.infrastructure.listmgmt.ListImportAuditPO;
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

/** 名单库 REST API。 */
@RestController
@RequestMapping("/api/v1/list-libraries")
public class ListLibraryController {

    private final ListMgmtService service;

    public ListLibraryController(ListMgmtService service) {
        this.service = service;
    }

    @GetMapping
    public List<LibraryView> list(@RequestParam(required = false) String keyword) {
        return service.listLibraries(keyword).stream()
                .map(lib -> {
                    ListMgmtService.LibraryStats stats = service.libraryStats(lib.getId());
                    return LibraryView.from(lib, stats);
                })
                .toList();
    }

    @PostMapping
    public LibraryView create(@RequestBody CreateLibraryRequest req) {
        ListLibraryPO po = service.createLibrary(req.code(), req.name(), req.description(), req.remark());
        return LibraryView.from(po, new ListMgmtService.LibraryStats(0, 0, 0, ListMgmtService.EXPIRING_SOON_DAYS));
    }

    @PutMapping("/{id}")
    public LibraryView update(@PathVariable Long id, @RequestBody UpdateLibraryRequest req) {
        ListLibraryPO po = service.updateLibrary(id, req.name(), req.description(), req.remark(), req.enabled());
        return LibraryView.from(po, service.libraryStats(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteLibrary(id);
    }

    @GetMapping("/{id}/references")
    public ReferencesView references(@PathVariable Long id) {
        return new ReferencesView(service.listLibraryReferences(id));
    }

    @GetMapping("/{id}/stats")
    public StatsView stats(@PathVariable Long id) {
        ListMgmtService.LibraryStats s = service.libraryStats(id);
        return new StatsView(s.total(), s.enabled(), s.expiringSoon(), s.expiringSoonDays());
    }

    /** 外部同步占位（LX1）：写审计，不接具体外部源。 */
    @PostMapping("/{id}/sync")
    public SyncView sync(@PathVariable Long id, @RequestBody(required = false) SyncRequest req) {
        SyncRequest body = req == null ? new SyncRequest(null, null, null) : req;
        ListMgmtService.SyncStubResult r = service.syncLibraryStub(id, body.source(), body.batchId(), body.entryCount());
        return new SyncView(r.auditId(), r.batchId(), r.status(), r.message(), r.entryCount());
    }

    @GetMapping("/{id}/import-audits")
    public List<AuditView> importAudits(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "20") int limit) {
        return service.listImportAudits(id, limit).stream().map(AuditView::from).toList();
    }

    public record CreateLibraryRequest(@NotBlank String code, @NotBlank String name,
                                       String description, String remark) {
    }

    public record UpdateLibraryRequest(String name, String description, String remark, Boolean enabled) {
    }

    public record SyncRequest(String source, String batchId, Integer entryCount) {
    }

    public record ReferencesView(List<String> references) {
    }

    public record StatsView(long total, long enabled, long expiringSoon, int expiringSoonDays) {
    }

    public record SyncView(Long auditId, String batchId, String status, String message, int entryCount) {
    }

    public record AuditView(Long id, Long libraryId, String source, String batchId,
                            int entryCount, String status, String message, String createdAt) {
        static AuditView from(ListImportAuditPO po) {
            return new AuditView(po.getId(), po.getLibraryId(), po.getSource(), po.getBatchId(),
                    po.getEntryCount() == null ? 0 : po.getEntryCount(),
                    po.getStatus(), po.getMessage(), fmt(po.getCreatedAt()));
        }
    }

    public record LibraryView(Long id, String code, String name, String description, String remark,
                              boolean enabled, long entryCount, long enabledCount, long expiringSoon,
                              int expiringSoonDays, String createdAt, String updatedAt) {
        static LibraryView from(ListLibraryPO po, ListMgmtService.LibraryStats stats) {
            return new LibraryView(po.getId(), po.getCode(), po.getName(), po.getDescription(), po.getRemark(),
                    po.getEnabled() == null || po.getEnabled() == 1,
                    stats.total(), stats.enabled(), stats.expiringSoon(), stats.expiringSoonDays(),
                    fmt(po.getCreatedAt()), fmt(po.getUpdatedAt()));
        }

        private static String fmt(LocalDateTime t) {
            return t == null ? null : t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
