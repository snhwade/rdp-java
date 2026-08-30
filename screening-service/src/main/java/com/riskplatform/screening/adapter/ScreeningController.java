package com.riskplatform.screening.adapter;

import com.riskplatform.screening.application.ScreeningOutcomeResult;
import com.riskplatform.screening.application.ScreeningService;
import com.riskplatform.screening.domain.ScreeningListEntry;
import com.riskplatform.screening.domain.ScreeningThreshold;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 筛查 REST 适配器（R11）。
 *
 * <ul>
 *   <li>POST /api/v1/screening 名称筛查（R11.1）</li>
 *   <li>PUT  /api/v1/screening/threshold 配置相似度阈值（R11.4）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/screening")
public class ScreeningController {

    private final ScreeningService screeningService;
    private final ScreeningListProvider listProvider;

    private volatile ScreeningThreshold threshold = ScreeningThreshold.defaultThreshold();

    public ScreeningController(ScreeningService screeningService, ScreeningListProvider listProvider) {
        this.screeningService = screeningService;
        this.listProvider = listProvider;
    }

    @PostMapping
    public ScreenView screen(@RequestBody ScreenRequest req) {
        ScreeningOutcomeResult r = screeningService.screen(
                req.subjectName(),
                () -> listProvider.load(),
                threshold,
                ScreeningService.DEFAULT_TIMEOUT_MS);
        return ScreenView.from(r);
    }

    @PutMapping("/threshold")
    public ThresholdView setThreshold(@RequestBody ThresholdRequest req) {
        // 越界由 ScreeningThreshold 构造校验拒绝（R11.4）
        this.threshold = new ScreeningThreshold(req.value());
        return new ThresholdView(this.threshold.value());
    }

    /** 名单加载端口（由基础设施层从 DB/缓存加载）。 */
    public interface ScreeningListProvider {
        List<ScreeningListEntry> load();
    }

    public record ScreenRequest(@NotBlank String subjectName) {
    }

    public record ThresholdRequest(double value) {
    }

    public record ThresholdView(double value) {
    }

    public record ScreenView(String outcome, String listType, String source, String matchedEntry,
                             Long matchedEntryId, Long libraryId,
                             Double similarity, String reason) {
        static ScreenView from(ScreeningOutcomeResult r) {
            String source = r.result() != null && r.result().source() != null
                    ? r.result().source().name() : null;
            String entry = r.result() != null ? r.result().matchedEntry() : null;
            Long entryId = r.result() != null ? r.result().matchedEntryId() : null;
            Long libraryId = r.result() != null ? r.result().libraryId() : null;
            Double sim = r.result() != null ? r.result().similarity() : null;
            String listType = r.result() != null && r.result().listType() != null
                    ? r.result().listType().name() : null;
            return new ScreenView(r.outcome().name(), listType, source, entry, entryId, libraryId, sim, r.reason());
        }
    }
}
