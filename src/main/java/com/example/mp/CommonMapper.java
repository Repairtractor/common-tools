package com.example.mp;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author Yufire
 * @date 2024/4/18 下午2:21
 * 公共mapper
 */
@Mapper
public interface CommonMapper {

    @Update("${sql}")
    int executeSql(@Param("sql") String sql);

}
