package com.riskplatform.ruleconfig.domain.enums;

import java.util.List;
import java.util.Optional;

/**
 * 枚举库仓储端口（R12.2）。基础设施层用 MyBatis-Plus 持久化到 enum_lib 表。
 */
public interface EnumLibRepository {

    EnumLib save(EnumLib enumLib);

    void update(EnumLib enumLib);

    Optional<EnumLib> findById(Long id);

    Optional<EnumLib> findByCode(String code);

    boolean existsByCode(String code);

    List<EnumLib> findAll();

    void deleteById(Long id);
}
