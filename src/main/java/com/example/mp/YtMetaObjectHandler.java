package com.example.mp;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Objects;

@Component
@Primary
public class YtMetaObjectHandler implements MetaObjectHandler {

    public static final Snowflake snowflake = IdUtil.createSnowflake(1, 1);


    @Override
    public void insertFill(MetaObject metaObject) {
        Object originalObject = metaObject.getOriginalObject();
        String name = originalObject.getClass().getName();
        //判断包名是否包含forecast

        Object id = getFieldValByName("id", metaObject);
        if (id == null && hasInputId(originalObject.getClass())) {
            setFieldValByName("id", snowflake.nextId(), metaObject);
        }


        String username = "admin";
        Object createUserName = getFieldValByName("createUserName", metaObject);
        if (createUserName == null) {
            setFieldValByName("createUserName", username, metaObject);
        }
        Object deleted = getFieldValByName("deleted", metaObject);
        if (Objects.isNull(deleted)) {
            setFieldValByName("deleted", false, metaObject);
        }
        Object updateUserName = getFieldValByName("updateUserName", metaObject);
        if (updateUserName == null) {
            setFieldValByName("updateUserName", username, metaObject);
        }

        setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);


    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Object originalObject = metaObject.getOriginalObject();
        String name = originalObject.getClass().getName();
        //判断包名是否包含forecast
        String username = "admin";
        if (getFieldValByName("updateUserName", metaObject) == null) {
            setFieldValByName("updateUserName", username, metaObject);
        }
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);

    }

    /**
     * 这个DO的id列是否是输入id的方式
     *
     * @param clazz class对象
     * @return 是否是input
     */
    private boolean hasInputId(Class<?> clazz) {
        Field field = ReflectUtil.getField(clazz, "id");
        if (ObjectUtil.isNull(field)) {
            return true;
        }
        TableId annotation = field.getAnnotation(TableId.class);
        if (ObjectUtil.isNull(annotation)) {
            return true;
        }
        return annotation.type().equals(IdType.INPUT);
    }
}
