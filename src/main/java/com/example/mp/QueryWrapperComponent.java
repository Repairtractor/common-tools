package com.example.mp;


import cn.hutool.core.lang.Filter;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.mp.QueryWrapperComponent.OperatorConstant.*;

public final class QueryWrapperComponent {


    public interface OperatorConstant {
        String EQ = "-eq";
        String IN = "-in";
        String LIKE = "-like";
        String GE = "-ge";
        String LE = "-le";
        String GT = "-gt";

        String NOT_IN="-notIn";

    }


    public static <T> QueryWrapper<T> getWrapper(Map<String, Object> params) {
        //如果params为空 wrapper查询全部,复制一份map,防止原地修改
        params = MapUtil.filter(params, (Filter<Map.Entry<String, Object>>) entry -> ObjectUtil.isNotEmpty(entry.getValue()));

        // 参数校验
        if (params == null || params.isEmpty()) {
            return new QueryWrapper<>();
        }

        //去掉分页参数
        params.remove("current");
        params.remove("size");
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        //如果有columns
        if (params.containsKey("columns")) {
            wrapper.select(Arrays.stream(params.remove("columns").toString().split(",")).map(it -> StrUtil.toSymbolCase(it, '_')).toArray(String[]::new));
        }

        //默认按照创建时间排序
        if (params.containsKey("sorter-asc")) {
            wrapper.orderByAsc(StrUtil.toSymbolCase(params.remove("sorter-asc").toString(), '_'));
        } else if (params.containsKey("sorter-desc")) {
            wrapper.orderByDesc(StrUtil.toSymbolCase(params.remove("sorter-desc").toString(), '_'));
        }

        //处理后缀参数
        params.forEach((key, value) -> {
            handlePostfix(wrapper, key, value);
        });

        return wrapper;
    }


    private static <T> void handlePostfix(QueryWrapper<T> wrapper, String key, Object value) {
        switch (getPostfix(key)) {
            case EQ:
                wrapper.eq(getColumn(key), value);
                break;
            case IN:
                wrapper.in(getColumn(key), getValues(value));
                break;
            case LIKE:
                wrapper.like(getColumn(key), value);
                break;
            case GE:
                wrapper.ge(getColumn(key), value);
                break;
            case LE:
                wrapper.le(getColumn(key), value);
                break;
            case GT:
                wrapper.gt(getColumn(key), value);
                break;
            case NOT_IN:
                wrapper.notIn(getColumn(key), getValues(value));
                break;
            default:
                throw new RuntimeException(getPostfix(key)+" 操作符暂不支持");
        }
    }

    private static String getColumn(String key) {
        return StrUtil.toSymbolCase(getStrWithoutPostfix(key), '_');
    }

    private static List<?> getValues(Object value) {
        return StrUtil.split(value.toString(), ',');
    }



    private static String getPostfix(String key) {
        return key.substring(key.lastIndexOf("-"));
    }

    private static String getStrWithoutPostfix(String key) {
        return key.substring(0, key.lastIndexOf("-"));
    }



}
