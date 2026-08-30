package com.riskplatform.ruleconfig.domain.permission;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据权限判定纯逻辑（R11.3/R11.4/R11.5，对应设计 Property 4）。
 *
 * <p>本类为纯函数集合，不依赖框架与持久化，便于复用与属性测试。判定输入仅依赖：
 * <ul>
 *   <li>用户所在机构的物化路径（如 {@code /1/4/9/}）；</li>
 *   <li>用户拥有的场景编码集合；</li>
 *   <li>资产的所属机构 id 与所属场景编码集合；</li>
 *   <li>「机构字段必填」开关。</li>
 * </ul>
 *
 * <p>机构可见性核心规则（R11.3）：资产「所属机构为用户所在机构或其上级机构」时可见。
 * 利用物化路径性质——用户路径 {@code /1/4/9/} 中的每个 id（1、4、9）恰好是
 * 用户机构本级（9）及其全部上级（1、4），因此「资产所属机构是用户机构或其上级」
 * 当且仅当 {@code assetOwnerOrgId} 出现在用户路径的某一段中，无需额外查询机构表。
 *
 * <p>场景可见性核心规则（R10.3/R11.3）：用户必须拥有资产全部所属场景的权限，
 * 即资产场景集合是用户场景集合的子集。
 */
public final class DataPermissionRule {

    private DataPermissionRule() {
    }

    /**
     * 解析物化路径中的机构 id 集合（含本级与全部上级）。
     *
     * <p>例：{@code /1/4/9/} -> {1, 4, 9}。非数字段、空段被忽略。
     *
     * @param orgPath 物化路径，可为 null/空（返回空集合）
     */
    public static Set<Long> orgIdsOnPath(String orgPath) {
        Set<Long> ids = new LinkedHashSet<>();
        if (orgPath == null || orgPath.isEmpty()) {
            return ids;
        }
        for (String seg : orgPath.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(seg.trim()));
            } catch (NumberFormatException ignore) {
                // 非数字段忽略，保证判定健壮
            }
        }
        return ids;
    }

    /**
     * 机构维度可见性判定（R11.3/R11.5）。
     *
     * <p>规则：
     * <ul>
     *   <li>资产所属机构为 null 时：开关「机构字段必填」关闭则视为全部机构适用（可见，R11.5）；
     *       开关开启则视为不可见（必填却未设置）。</li>
     *   <li>资产所属机构非 null 时：当且仅当该 id 出现在用户路径段中
     *       （资产机构是用户机构或其上级）才可见。</li>
     * </ul>
     *
     * @param userOrgPath      用户所在机构物化路径（如 {@code /1/4/9/}）
     * @param assetOwnerOrgId  资产所属机构 id，可为 null
     * @param orgFieldRequired 「机构字段必填」开关
     */
    public static boolean orgVisible(String userOrgPath, Long assetOwnerOrgId, boolean orgFieldRequired) {
        if (assetOwnerOrgId == null) {
            // 未设置所属机构：必填关闭=全部机构适用（可见）；必填开启=不可见
            return !orgFieldRequired;
        }
        return orgIdsOnPath(userOrgPath).contains(assetOwnerOrgId);
    }

    /**
     * 场景维度可见性判定（R10.3/R11.3）：用户场景集合必须包含资产全部所属场景。
     *
     * <p>资产无所属场景（空/为 null）时，按空子集处理，视为可见。
     *
     * @param userScenarioCodes  用户拥有的场景编码集合
     * @param assetScenarioCodes 资产所属场景编码集合
     */
    public static boolean scenarioVisible(Collection<String> userScenarioCodes,
                                          Collection<String> assetScenarioCodes) {
        if (assetScenarioCodes == null || assetScenarioCodes.isEmpty()) {
            return true;
        }
        if (userScenarioCodes == null) {
            return false;
        }
        return userScenarioCodes.containsAll(assetScenarioCodes);
    }

    /**
     * 资产综合可见性判定（R11.3）：机构维度与场景维度均满足才可见。
     *
     * @param userOrgPath        用户所在机构物化路径
     * @param userScenarioCodes  用户拥有的场景编码集合
     * @param assetOwnerOrgId    资产所属机构 id（可空）
     * @param assetScenarioCodes 资产所属场景编码集合（可空）
     * @param orgFieldRequired   「机构字段必填」开关（R11.5）
     */
    public static boolean isVisible(String userOrgPath,
                                    Collection<String> userScenarioCodes,
                                    Long assetOwnerOrgId,
                                    Collection<String> assetScenarioCodes,
                                    boolean orgFieldRequired) {
        return orgVisible(userOrgPath, assetOwnerOrgId, orgFieldRequired)
                && scenarioVisible(userScenarioCodes, assetScenarioCodes);
    }
}
