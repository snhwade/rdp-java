package com.riskplatform.gateway.domain;

/**
 * 名称模糊筛查网关端口（R11）。
 *
 * <p>对交易主体名称做相似度筛查；BLACK 命中建议 REJECT，WATCH 命中建议 REVIEW。
 */
public interface ScreeningGateway {

    /** 名称筛查命中类型。 */
    enum HitKind {
        /** 未命中。 */
        NONE,
        /** 黑名单模糊命中 → REJECT。 */
        BLACK,
        /** 关注名单模糊命中 → REVIEW。 */
        WATCH,
        /** 筛查超时/失败/不可用（降级，不影响主决策）。 */
        UNAVAILABLE
    }

    /**
     * 对交易主体名称做模糊筛查。
     *
     * @param subjectName 交易主体名称（为空时直接返回 NONE）
     */
    HitKind screenName(String subjectName);
}
