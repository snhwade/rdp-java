package com.riskplatform.engine.domain.rating;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 等级序（引擎执行侧，评级模型共用领域服务）。
 *
 * <p>统一定义"等级高低"的比较依据：依据 {@link GradeBand} 的配置顺序建立等级序——
 * 列表中靠后（分值更高侧）的等级视为"更高等级"。设计文档约定"等级高低比较依据 GradeBand 顺序
 * （或等级 order）定义统一的等级序"。
 *
 * <p>本类型为评分定级（{@link ScoreBasedGrader}）与直接定级（DirectGrader，任务 15.2）共用：
 * 评分定级用其确定越界时的边界等级取向；直接定级用其在多项异级命中时取最高等级（R13.4）。
 *
 * <p>构造时按等级首次出现顺序去重，rank 从 0 递增；未知等级 rank 为 -1（低于任何已知等级）。
 */
public final class GradeOrder {

    private final List<String> gradesAscending;
    private final Map<String, Integer> rankByGrade;

    private GradeOrder(List<String> gradesAscending) {
        this.gradesAscending = List.copyOf(gradesAscending);
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (int i = 0; i < gradesAscending.size(); i++) {
            ranks.putIfAbsent(gradesAscending.get(i), i);
        }
        this.rankByGrade = Map.copyOf(ranks);
    }

    /**
     * 由等级区间列表构建等级序：按区间 {@link GradeBand#minScore()} 升序排列，分值更高侧为更高等级。
     *
     * @param bands 等级区间列表（顺序不限，内部按 minScore 升序归一）
     * @return 等级序
     */
    public static GradeOrder fromBands(List<GradeBand> bands) {
        List<GradeBand> sorted = new ArrayList<>(bands);
        sorted.sort((a, b) -> a.minScore().compareTo(b.minScore()));
        List<String> grades = new ArrayList<>(sorted.size());
        for (GradeBand band : sorted) {
            grades.add(band.grade());
        }
        return new GradeOrder(grades);
    }

    /**
     * 由等级名列表（已按低→高排列）构建等级序。
     *
     * @param gradesLowToHigh 由低到高排列的等级名列表
     * @return 等级序
     */
    public static GradeOrder of(List<String> gradesLowToHigh) {
        return new GradeOrder(gradesLowToHigh);
    }

    /** 等级 rank：越大越高；未知等级返回 -1。 */
    public int rank(String grade) {
        return rankByGrade.getOrDefault(grade, -1);
    }

    /**
     * 取一组等级中的最高等级（依据等级序）。
     *
     * @param grades 等级集合（非空）
     * @return 最高等级；入参为空时返回 null
     */
    public String highest(Collection<String> grades) {
        String best = null;
        int bestRank = Integer.MIN_VALUE;
        for (String grade : grades) {
            int r = rank(grade);
            if (best == null || r > bestRank) {
                best = grade;
                bestRank = r;
            }
        }
        return best;
    }

    /** 最低等级（等级序首位）；无等级时返回 null。 */
    public String lowest() {
        return gradesAscending.isEmpty() ? null : gradesAscending.get(0);
    }

    /** 最高等级（等级序末位）；无等级时返回 null。 */
    public String highest() {
        return gradesAscending.isEmpty() ? null : gradesAscending.get(gradesAscending.size() - 1);
    }

    /** 等级序（低→高），只读副本。 */
    public List<String> gradesAscending() {
        return gradesAscending;
    }
}
