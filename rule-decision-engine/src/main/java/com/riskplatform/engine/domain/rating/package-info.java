/**
 * rating 子域（引擎执行侧）：评级模型定级能力。
 *
 * <p>承载评分定级（{@link com.riskplatform.engine.domain.rating.ScoreBasedGrader}，R12）与直接定级
 * （DirectGrader，R13）的纯业务逻辑。共用值对象：{@link com.riskplatform.engine.domain.rating.GradeBand}
 * （等级区间）与领域服务 {@link com.riskplatform.engine.domain.rating.GradeOrder}（等级序）。
 *
 * <p>命名中性：本子域不含任何产品厂商专有名词。
 */
package com.riskplatform.engine.domain.rating;
