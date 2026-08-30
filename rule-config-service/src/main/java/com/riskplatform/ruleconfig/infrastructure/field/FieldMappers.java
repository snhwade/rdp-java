package com.riskplatform.ruleconfig.infrastructure.field;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** S7 字段库 / 衍生字段 Mapper。 */
public final class FieldMappers {

    private FieldMappers() {
    }

    @Mapper
    public interface FieldDefinitionMapper extends BaseMapper<FieldDefinitionPO> {
    }

    @Mapper
    public interface DerivedFieldMapper extends BaseMapper<DerivedFieldPO> {
    }
}
