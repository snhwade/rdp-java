package com.riskplatform.ruleconfig.domain.org;

/**
 * 机构「含下级 / 不含下级」范围判定工具（R11.2/R11.4，对应设计 Property 4）。
 *
 * <p>基于物化路径前缀判定，纯函数、不依赖框架与持久化，便于复用与属性测试：
 * <ul>
 *   <li>含下级：机构 X 属于「机构 A 含下级」当且仅当 {@code orgX.path} 以 {@code orgA.path} 为前缀；</li>
 *   <li>不含下级：机构 X 属于「机构 A 不含下级」当且仅当 {@code orgX.id == orgA.id}。</li>
 * </ul>
 */
public final class OrgScope {

    private OrgScope() {
    }

    /**
     * 判定机构 X 是否在「机构 A 含下级」范围内。
     *
     * @param orgXPath 机构 X 的物化路径，如 {@code /1/4/9/}
     * @param orgAPath 机构 A 的物化路径，如 {@code /1/4/}
     * @return path 前缀匹配则为 true（含 X 等于 A 自身）
     */
    public static boolean withinSubtree(String orgXPath, String orgAPath) {
        if (orgXPath == null || orgAPath == null || orgAPath.isEmpty()) {
            return false;
        }
        return orgXPath.startsWith(orgAPath);
    }

    /**
     * 判定机构 X 是否在「机构 A 不含下级」范围内（即同一机构）。
     *
     * @param orgXId 机构 X 的 id
     * @param orgAId 机构 A 的 id
     * @return 两者 id 相等则为 true
     */
    public static boolean sameOrg(Long orgXId, Long orgAId) {
        return orgXId != null && orgXId.equals(orgAId);
    }

    /**
     * 统一入口：按「是否含下级」判定机构 X 是否落入机构 A 的适用范围。
     *
     * @param orgX        机构 X
     * @param orgA        机构 A（适用机构）
     * @param includeSub  true=含下级（前缀匹配），false=不含下级（同 id）
     */
    public static boolean applies(Org orgX, Org orgA, boolean includeSub) {
        if (orgX == null || orgA == null) {
            return false;
        }
        return includeSub
                ? withinSubtree(orgX.getPath(), orgA.getPath())
                : sameOrg(orgX.getId(), orgA.getId());
    }
}
