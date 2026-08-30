package com.riskplatform.ruleconfig.domain.assetversion;

import java.time.LocalDateTime;

/**
 * 资产版本快照（S6）。
 *
 * <p>每次资产（规则/决策表/评分卡/决策流）上线或变更生成一条快照，保留内容与操作人，
 * 便于版本回溯与对比。版本号按 (assetType, assetId) 递增。
 *
 * @param id        主键
 * @param assetType 资产类型
 * @param assetId   资产标识
 * @param version   版本号（该资产内递增）
 * @param content   内容快照(JSON)
 * @param operator  操作人
 * @param createdAt 生成时间
 */
public record AssetVersion(
        Long id,
        String assetType,
        String assetId,
        int version,
        String content,
        String operator,
        LocalDateTime createdAt) {

    public static AssetVersion newSnapshot(String assetType, String assetId, int version,
                                           String content, String operator) {
        return new AssetVersion(null, assetType, assetId, version, content, operator, LocalDateTime.now());
    }
}
