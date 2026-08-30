package com.riskplatform.gateway.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiDecisionMergerTest {

    @Test
    void advisory_aiReject_escalatesAtMostToReview() {
        AiAdviseOutcome ai = AiAdviseOutcome.ok(new AiAdviseResult("REJECT", 0.9, "x", java.util.List.of()));
        assertThat(AiDecisionMerger.merge(AdoptionMode.ADVISORY, "PASS", ai)).isEqualTo("REVIEW");
        assertThat(AiDecisionMerger.merge(AdoptionMode.ADVISORY, "REVIEW", ai)).isEqualTo("REVIEW");
        assertThat(AiDecisionMerger.merge(AdoptionMode.ADVISORY, "REJECT", ai)).isEqualTo("REJECT");
    }

    @Test
    void strict_takesStrictest() {
        AiAdviseOutcome ai = AiAdviseOutcome.ok(new AiAdviseResult("REJECT", 0.9, "x", java.util.List.of()));
        assertThat(AiDecisionMerger.merge(AdoptionMode.STRICT, "PASS", ai)).isEqualTo("REJECT");
    }

    @Test
    void override_usesAiWhenOk() {
        AiAdviseOutcome ai = AiAdviseOutcome.ok(new AiAdviseResult("REVIEW", 0.8, "x", java.util.List.of()));
        assertThat(AiDecisionMerger.merge(AdoptionMode.OVERRIDE, "PASS", ai)).isEqualTo("REVIEW");
    }

    @Test
    void override_and_strict_fallbackOnAiFailure() {
        AiAdviseOutcome failed = AiAdviseOutcome.failed("boom");
        assertThat(AiDecisionMerger.merge(AdoptionMode.OVERRIDE, "PASS", failed)).isEqualTo("PASS");
        assertThat(AiDecisionMerger.merge(AdoptionMode.STRICT, "REVIEW", failed)).isEqualTo("REVIEW");
        assertThat(AiDecisionMerger.merge(AdoptionMode.STRICT, "PASS", AiAdviseOutcome.timedOut("t")))
                .isEqualTo("PASS");
    }

    @Test
    void normalize_manualReview() {
        assertThat(AiDecisionMerger.normalize("MANUAL_REVIEW")).isEqualTo("REVIEW");
    }
}
