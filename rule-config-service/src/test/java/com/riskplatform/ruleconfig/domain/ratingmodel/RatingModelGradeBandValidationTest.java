package com.riskplatform.ruleconfig.domain.ratingmodel;

import com.riskplatform.common.error.ValidationException;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel.ExecutionMode;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel.GradeBand;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel.GradingMode;
import com.riskplatform.ruleconfig.domain.ratingmodel.RatingModel.Subject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 等级区间校验单元测试（R11.2/R11.3/R11.4）。
 *
 * <p>覆盖：连续覆盖合法通过、区间重叠拒绝、覆盖缺口拒绝、等级数量不限（任意条数均可），
 * 以及空集合/单区间的边界场景。边界约定为左闭右闭 [min,max]，相邻区间在共享边界点
 * （{@code prev.max == next.min}）处衔接即视为连续覆盖，与引擎侧 {@code GradeBand.contains()} 一致。
 */
class RatingModelGradeBandValidationTest {

    private static GradeBand band(double min, double max, String grade, int order) {
        return new GradeBand(BigDecimal.valueOf(min), BigDecimal.valueOf(max), grade, order);
    }

    private static RatingModel modelWith(List<GradeBand> bands) {
        return RatingModel.create("信用评级", "EVT_RATING", ExecutionMode.REALTIME,
                Subject.MERCHANT, GradingMode.SCORE_BASED, bands, List.of());
    }

    @Test
    void contiguousCoverage_passes() {
        RatingModel m = modelWith(List.of(
                band(0, 60, "三级", 3),
                band(60, 80, "二级", 2),
                band(80, 100, "一级", 1)));
        assertThatCode(m::validateGradeBands).doesNotThrowAnyException();
    }

    @Test
    void overlap_rejected() {
        // [0,60] 与 [50,80] 在 [50,60] 区间重叠
        RatingModel m = modelWith(List.of(
                band(0, 60, "二级", 2),
                band(50, 80, "一级", 1)));
        ValidationException ex = catchThrowableOfType(m::validateGradeBands, ValidationException.class);
        assertThat(ex.getFields()).containsKey("gradeBands.overlap");
    }

    @Test
    void gap_rejected() {
        // (60,70) 未被任何等级覆盖
        RatingModel m = modelWith(List.of(
                band(0, 60, "二级", 2),
                band(70, 100, "一级", 1)));
        ValidationException ex = catchThrowableOfType(m::validateGradeBands, ValidationException.class);
        assertThat(ex.getFields()).containsKey("gradeBands.gap");
    }

    @Test
    void anyNumberOfBands_allowed() {
        // 等级数量不受限制（R11.2）：构造 50 个连续区间均应通过
        java.util.List<GradeBand> bands = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            bands.add(band(i, i + 1, "L" + i, i));
        }
        RatingModel m = modelWith(bands);
        assertThatCode(m::validateGradeBands).doesNotThrowAnyException();
    }

    @Test
    void singleBand_passes() {
        RatingModel m = modelWith(List.of(band(0, 100, "唯一级", 1)));
        assertThatCode(m::validateGradeBands).doesNotThrowAnyException();
    }

    @Test
    void emptyBands_passes() {
        RatingModel m = modelWith(List.of());
        assertThatCode(m::validateGradeBands).doesNotThrowAnyException();
    }

    @Test
    void minGreaterThanMax_rejected() {
        RatingModel m = modelWith(List.of(band(80, 60, "异常级", 1)));
        ValidationException ex = catchThrowableOfType(m::validateGradeBands, ValidationException.class);
        assertThat(ex.getFields()).containsKey("gradeBands[0]");
    }

    @Test
    void blankGrade_rejected() {
        RatingModel m = modelWith(List.of(band(0, 100, "  ", 1)));
        ValidationException ex = catchThrowableOfType(m::validateGradeBands, ValidationException.class);
        assertThat(ex.getFields()).containsKey("gradeBands[0].grade");
    }
}
