package com.example.mp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Data
public abstract class QueryMapConvertor {

    /**
     * 转换为查询条件map
     *
     * @return
     */
    public Map<String, Object> toQueryMap() {
        Field[] fields = ReflectUtil.getFields(this.getClass());
        Map<String, Object> result = new HashMap<>(fields.length);

        for (Field field : fields) {
            String fieldName = field.getName();
            Object fieldValue = ReflectUtil.getFieldValue(this, field);

            if (ObjectUtil.isEmpty(fieldValue)) {
                continue;
            }

            if (fieldName.equalsIgnoreCase("size") || fieldName.equalsIgnoreCase("current")) {
                result.put(fieldName, fieldValue);
                continue;
            }


            Class<?> type = field.getType();

            //过滤空集合和空串
            // 如果名称携带特殊后缀,说明指定了查询格式
            if (fieldName.endsWith("_ge")) {
                result.put(fieldName.substring(0, fieldName.length() - 3) + "-ge", fieldValue);
            } else if (fieldName.endsWith("_like")) {
                result.put(fieldName.substring(0, fieldName.length() - 5) + "-like", fieldValue);
            } else if (fieldName.endsWith("_le")) {
                result.put(fieldName.substring(0, fieldName.length() - 3) + "-le", fieldValue);
            } else if (fieldName.endsWith("_in") && CollUtil.isNotEmpty((Collection) fieldValue)) {
                result.put(fieldName.substring(0, fieldName.length() - 3) + "-in", String.join(",", (Collection) fieldValue));
            } else if (Collection.class.isAssignableFrom(type) && CollUtil.isNotEmpty((Collection) fieldValue)) {
                result.put(fieldName + "-in", CollUtil.join((Collection) fieldValue, ","));
            } else if (fieldName.equalsIgnoreCase("sorter_asc")) {
                result.put(fieldName.substring(0, fieldName.length() - 4) + "-asc", fieldValue);
            } else if (fieldName.equalsIgnoreCase("sorter_desc")) {
                result.put(fieldName.substring(0, fieldName.length() - 5) + "-desc", fieldValue);
            } else {
                result.put(fieldName + "-eq", fieldValue);
            }
        }
        return result;
    }

    /**
     * 分页大小
     */
    protected Long size=10L;

    /**
     * 当前页
     */
    protected Long current=1L;

    /**
     * 排序字段
     */
    protected String sorter_asc;

    protected String sorter_desc;
}
