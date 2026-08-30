package com.riskplatform.screening.application.listmgmt;

import com.riskplatform.screening.infrastructure.listmgmt.ListLibraryReferenceMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 名单库引用扫描（L1）：在规则包条件 / 决策流节点 JSON 中按约定键匹配 libraryCode。
 */
@Component
public class ListLibraryReferenceChecker {

    private final ListLibraryReferenceMapper referenceMapper;

    public ListLibraryReferenceChecker(ListLibraryReferenceMapper referenceMapper) {
        this.referenceMapper = referenceMapper;
    }

    /** @return 可读引用描述，如「规则包:PKG_X」「决策流:FLOW_Y」 */
    public List<String> findReferences(String libraryCode) {
        if (!StringUtils.hasText(libraryCode)) {
            return List.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        String p1 = "%\"libraryCode\":\"" + libraryCode + "\"%";
        String p2 = "%\"listLibraryCode\":\"" + libraryCode + "\"%";
        String p3 = "%\"listCode\":\"" + libraryCode + "\"%";
        try {
            refs.addAll(referenceMapper.findRulePackageReferences(p1, p2, p3));
            refs.addAll(referenceMapper.findDecisionFlowReferences(p1, p2, p3));
            refs.addAll(referenceMapper.findDecisionFlowVersionReferences(p1, p2, p3));
        } catch (DataAccessException ex) {
            return List.of();
        }
        refs.removeIf(s -> s == null || s.isBlank());
        return new ArrayList<>(refs);
    }

    public boolean isReferenced(String libraryCode) {
        return !findReferences(libraryCode).isEmpty();
    }
}
