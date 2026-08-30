package com.riskplatform.ruleconfig.domain.field;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合多个 {@link FieldReferenceSource} 的字段引用检查器。
 */
public class CompositeFieldReferenceChecker implements FieldReferenceChecker {

    private final List<FieldReferenceSource> sources;

    public CompositeFieldReferenceChecker(List<FieldReferenceSource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    @Override
    public List<String> findReferences(Long fieldId, String fieldCode) {
        if (fieldId == null && (fieldCode == null || fieldCode.isBlank())) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        for (FieldReferenceSource source : sources) {
            if (source.isReferenced(fieldId, fieldCode)) {
                refs.add(source.referenceType());
            }
        }
        return refs;
    }
}
