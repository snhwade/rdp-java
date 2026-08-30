package com.riskplatform.ruleconfig.application.permission;

import com.riskplatform.ruleconfig.domain.permission.DataPermissionRule;
import com.riskplatform.ruleconfig.domain.permission.PermissionedAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 数据权限过滤器（R11.3/R11.4/R11.5）。
 *
 * <p>对资产列表按「资产所属机构为用户所在机构或其上级 且 用户场景包含资产全部所属场景」过滤，
 * 供后续阶段的资产列表查询（规则包/决策流等）复用。判定逻辑下沉到纯函数
 * {@link DataPermissionRule}，本类仅负责装配用户上下文与「机构字段必填」开关。
 *
 * <p>「机构字段必填」开关由配置 {@code data-permission.org-field-required} 控制，
 * 默认 {@code true}；关闭时资产未设置所属机构则默认全部机构适用（R11.5）。
 *
 * <p>用 {@code @Component} 组件扫描自注册，避免改动共享装配类。
 */
@Component
public class DataPermissionFilter {

    private final UserContextProvider userContextProvider;

    /** 「机构字段必填」开关（R11.5）。关闭时未设置适用机构默认全部机构适用。 */
    private final boolean orgFieldRequired;

    public DataPermissionFilter(UserContextProvider userContextProvider,
                                @Value("${data-permission.org-field-required:true}") boolean orgFieldRequired) {
        this.userContextProvider = userContextProvider;
        this.orgFieldRequired = orgFieldRequired;
    }

    /**
     * 判定单个资产对当前用户是否可见（R11.3/R11.5）。
     *
     * @param ownerOrgId    资产所属机构 id（可空）
     * @param scenarioCodes 资产所属场景编码集合（可空）
     */
    public boolean isVisible(Long ownerOrgId, List<String> scenarioCodes) {
        UserDataContext user = userContextProvider.currentUser();
        if (user.superAdmin()) {
            return true;
        }
        return DataPermissionRule.isVisible(
                user.orgPath(),
                user.scenarioCodes(),
                ownerOrgId,
                scenarioCodes,
                orgFieldRequired);
    }

    /**
     * 过滤实现了 {@link PermissionedAsset} 的资产列表（R11.3）。
     *
     * @param assets 资产列表（可空）
     * @return 当前用户可见的资产子列表（保持原顺序）
     */
    public <T extends PermissionedAsset> List<T> filter(List<T> assets) {
        return filter(assets, PermissionedAsset::ownerOrgId, PermissionedAsset::scenarioCodes);
    }

    /**
     * 过滤任意资产列表（R11.3）：通过函数从资产提取所属机构与所属场景，
     * 无需资产实现特定接口，便于复用到既有/异构资产类型。
     *
     * @param assets         资产列表（可空）
     * @param ownerOrgFn     从资产提取所属机构 id 的函数（可返回 null）
     * @param scenariosFn    从资产提取所属场景编码集合的函数（可返回 null/空）
     */
    public <T> List<T> filter(List<T> assets,
                              Function<T, Long> ownerOrgFn,
                              Function<T, List<String>> scenariosFn) {
        List<T> result = new ArrayList<>();
        if (assets == null || assets.isEmpty()) {
            return result;
        }
        UserDataContext user = userContextProvider.currentUser();
        if (user.superAdmin()) {
            result.addAll(assets);
            return result;
        }
        for (T asset : assets) {
            boolean visible = DataPermissionRule.isVisible(
                    user.orgPath(),
                    user.scenarioCodes(),
                    ownerOrgFn.apply(asset),
                    scenariosFn.apply(asset),
                    orgFieldRequired);
            if (visible) {
                result.add(asset);
            }
        }
        return result;
    }

    /** 暴露当前「机构字段必填」开关取值（便于调用方在查询条件拼装时复用，R11.5）。 */
    public boolean isOrgFieldRequired() {
        return orgFieldRequired;
    }
}
