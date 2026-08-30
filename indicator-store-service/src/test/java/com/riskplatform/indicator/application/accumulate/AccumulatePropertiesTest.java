package com.riskplatform.indicator.application.accumulate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccumulatePropertiesTest {

    @Test
    void defaultsToFlinkMode() {
        AccumulateProperties props = new AccumulateProperties();
        assertThat(props.resolvedMode()).isEqualTo(AccumulateMode.FLINK);
        assertThat(props.isFlinkMode()).isTrue();
        assertThat(props.isServiceConsumerActive()).isFalse();
    }

    @Test
    void serviceModeEnablesKafkaConsumer() {
        AccumulateProperties props = new AccumulateProperties();
        props.setMode("service");
        assertThat(props.resolvedMode()).isEqualTo(AccumulateMode.SERVICE);
        assertThat(props.isServiceConsumerActive()).isTrue();
        assertThat(props.isFlinkMode()).isFalse();
    }

    @Test
    void rejectsInvalidMode() {
        AccumulateProperties props = new AccumulateProperties();
        assertThatThrownBy(() -> props.setMode("kafka"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flink | service");
    }
}
