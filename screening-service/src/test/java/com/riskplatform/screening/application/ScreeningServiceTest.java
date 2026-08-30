package com.riskplatform.screening.application;

import com.riskplatform.screening.domain.ListSource;
import com.riskplatform.screening.domain.ScreeningListEntry;
import com.riskplatform.screening.domain.ScreeningMatcher;
import com.riskplatform.screening.domain.ScreeningOutcome;
import com.riskplatform.screening.domain.ScreeningThreshold;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 筛查应用服务单元测试（R11.4/R11.5/R11.6）。
 */
class ScreeningServiceTest {

    private final ScreeningService service = new ScreeningService(new ScreeningMatcher());

    @Test
    void hit_withinDeadline() {
        ScreeningOutcomeResult r = service.screen(
                "John Doe",
                () -> List.of(new ScreeningListEntry(1L, com.riskplatform.screening.domain.list.ListType.BLACK, "John Doe")),
                ScreeningThreshold.defaultThreshold(),
                500);
        assertThat(r.outcome()).isEqualTo(ScreeningOutcome.HIT);
        assertThat(r.result().listType()).isEqualTo(com.riskplatform.screening.domain.list.ListType.BLACK);
        assertThat(r.result().source()).isEqualTo(ListSource.SANCTION);
    }

    @Test
    void watchFuzzyHit_mapsWatchListType() {
        ScreeningOutcomeResult r = service.screen(
                "Acme Corp",
                () -> List.of(new ScreeningListEntry(2L, com.riskplatform.screening.domain.list.ListType.WATCH, "Acme Corp")),
                ScreeningThreshold.defaultThreshold(),
                500);
        assertThat(r.outcome()).isEqualTo(ScreeningOutcome.HIT);
        assertThat(r.result().listType()).isEqualTo(com.riskplatform.screening.domain.list.ListType.WATCH);
    }

    @Test
    void miss_belowThreshold() {
        ScreeningOutcomeResult r = service.screen(
                "Tencent",
                () -> List.of(new ScreeningListEntry(1L, com.riskplatform.screening.domain.list.ListType.BLACK, "Alibaba")),
                ScreeningThreshold.defaultThreshold(),
                500);
        assertThat(r.outcome()).isEqualTo(ScreeningOutcome.MISS);
    }

    @Test
    void timeout_whenLoaderTooSlow() {
        ScreeningOutcomeResult r = service.screen(
                "X",
                () -> {
                    Thread.sleep(300);
                    return List.of();
                },
                ScreeningThreshold.defaultThreshold(),
                20);
        assertThat(r.outcome()).isEqualTo(ScreeningOutcome.TIMEOUT);
        assertThat(r.reason()).isNotBlank();
    }

    @Test
    void failed_whenLoaderThrows() {
        ScreeningOutcomeResult r = service.screen(
                "X",
                () -> {
                    throw new IllegalStateException("list source down");
                },
                ScreeningThreshold.defaultThreshold(),
                500);
        assertThat(r.outcome()).isEqualTo(ScreeningOutcome.FAILED);
    }

    @Test
    void thresholdOutOfRange_rejected() {
        assertThatThrownBy(() -> new ScreeningThreshold(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
