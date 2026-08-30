package com.riskplatform.ruleconfig.domain.assetversion;

import java.util.List;

/** 资产版本快照仓储端口（S6）。 */
public interface AssetVersionRepository {

    AssetVersion save(AssetVersion version);

    /** 该资产当前最大版本号（无则 0）。 */
    int currentMaxVersion(String assetType, String assetId);

    /** 历史版本倒序列出。 */
    List<AssetVersion> listVersions(String assetType, String assetId);
}
