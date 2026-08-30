package com.riskplatform.gateway.infrastructure.standalone;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EventTypeReadMapper {

    @Select("""
            SELECT status FROM event_type WHERE code = #{code} LIMIT 1
            """)
    Integer selectStatusByCode(String code);
}
