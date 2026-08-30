package com.riskplatform.ruleconfig.infrastructure.assetversion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.riskplatform.ruleconfig.domain.assetversion.AssetVersion;
import com.riskplatform.ruleconfig.domain.assetversion.AssetVersionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 资产版本快照仓储 MyBatis-Plus 实现（S6）。 */
@Repository
public class AssetVersionRepositoryImpl implements AssetVersionRepository {

    private final AssetVersionMapper mapper;

    public AssetVersionRepositoryImpl(AssetVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AssetVersion save(AssetVersion version) {
        AssetVersionPO po = new AssetVersionPO();
        po.setAssetType(version.assetType());
        po.setAssetId(version.assetId());
        po.setVersion(version.version());
        po.setContent(version.content());
        po.setOperator(version.operator());
        mapper.insert(po);
        return new AssetVersion(po.getId(), po.getAssetType(), po.getAssetId(), po.getVersion(),
                po.getContent(), po.getOperator(), version.createdAt());
    }

    @Override
    public int currentMaxVersion(String assetType, String assetId) {
        AssetVersionPO po = mapper.selectOne(new LambdaQueryWrapper<AssetVersionPO>()
                .eq(AssetVersionPO::getAssetType, assetType)
                .eq(AssetVersionPO::getAssetId, assetId)
                .orderByDesc(AssetVersionPO::getVersion)
                .last("LIMIT 1"));
        return po == null || po.getVersion() == null ? 0 : po.getVersion();
    }

    @Override
    public List<AssetVersion> listVersions(String assetType, String assetId) {
        return mapper.selectList(new LambdaQueryWrapper<AssetVersionPO>()
                        .eq(AssetVersionPO::getAssetType, assetType)
                        .eq(AssetVersionPO::getAssetId, assetId)
                        .orderByDesc(AssetVersionPO::getVersion))
                .stream()
                .map(po -> new AssetVersion(po.getId(), po.getAssetType(), po.getAssetId(),
                        po.getVersion(), po.getContent(), po.getOperator(), po.getCreatedAt()))
                .toList();
    }
}
