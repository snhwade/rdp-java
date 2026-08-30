package com.riskplatform.ruleconfig.application.assetversion;

import com.riskplatform.ruleconfig.domain.assetversion.AssetVersion;
import com.riskplatform.ruleconfig.domain.assetversion.AssetVersionRepository;

import java.util.List;

/**
 * 资产版本快照应用服务（S6）。
 *
 * <p>为资产生成版本快照（版本号自动递增）并提供历史版本查询，支撑上下线与版本回溯。
 */
public class AssetVersionService {

    private final AssetVersionRepository repository;

    public AssetVersionService(AssetVersionRepository repository) {
        this.repository = repository;
    }

    /** 生成新版本快照（版本号 = 当前最大+1）。 */
    public AssetVersion snapshot(String assetType, String assetId, String content, String operator) {
        int next = repository.currentMaxVersion(assetType, assetId) + 1;
        return repository.save(AssetVersion.newSnapshot(assetType, assetId, next, content, operator));
    }

    /** 历史版本倒序列出。 */
    public List<AssetVersion> listVersions(String assetType, String assetId) {
        return repository.listVersions(assetType, assetId);
    }
}
