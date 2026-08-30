package com.riskplatform.ruleconfig.domain.enums;

import java.util.List;
import java.util.Optional;

/**
 * 枚举值仓储端口（R12.2）。基础设施层用 MyBatis-Plus 持久化到 enum_value 表。
 */
public interface EnumValueRepository {

    EnumValue save(EnumValue enumValue);

    void update(EnumValue enumValue);

    Optional<EnumValue> findById(Long id);

    Optional<EnumValue> findByLibAndValue(Long enumLibId, String value);

    boolean existsByLibAndValue(Long enumLibId, String value);

    /** 按枚举库列出全部枚举值（按 order_no 升序）。 */
    List<EnumValue> findByLibId(Long enumLibId);

    void deleteById(Long id);

    /** 删除某枚举库下全部枚举值（删除枚举库时级联用）。 */
    void deleteByLibId(Long enumLibId);
}
