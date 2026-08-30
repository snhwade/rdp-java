package com.riskplatform.ruleconfig.domain.field;

import java.util.List;

/**
 * 字段批量导入结果（R3.6）：持久化成功的字段，以及每条校验未通过记录的失败原因。
 *
 * @param imported 校验通过并已持久化的字段
 * @param failures 校验未通过的记录及其原因
 */
public record FieldImportResult(List<FieldDefinition> imported, List<Failure> failures) {

    /** 导入成功条数。 */
    public int successCount() {
        return imported == null ? 0 : imported.size();
    }

    /** 导入失败条数。 */
    public int failureCount() {
        return failures == null ? 0 : failures.size();
    }

    /**
     * 单条导入失败记录。
     *
     * @param index  原始记录在导入集合中的下标（从 0 起）
     * @param code   该记录的字段 code（可能为空）
     * @param reason 失败原因
     */
    public record Failure(int index, String code, String reason) {
    }
}
