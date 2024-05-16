package com.example.mp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.CharUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

@Slf4j
public class BaseDoService<M extends BaseMapper<P>, P> extends ServiceImpl<M, P> {
    private static final String STATEMENT_NAME = "customerBatchUpdateStatement";
    private static final String ALIAS_MASTER = "a";
    private static final String ALIAS_VIRTUAL = "b";
    private static final Set<String> EXCLUDE_FIELDS = CollUtil.newHashSet("id", "deleted", "createTime", "createUserName", "updateUserName", "updateTime");
    private static CommonMapper commonMapper;

    public BaseDoService(M baseMapper) {
        this.baseMapper = baseMapper;
        this.mapperClass = currentMapperClass();
        BaseDoService.commonMapper = SpringUtil.getBean(CommonMapper.class);
    }

    public BaseDoService(M baseMapper, Class<P> entityClass) {
        this.baseMapper = baseMapper;
        this.entityClass = entityClass;
        //mapperClass在初始化的构造函数中重新赋值
        this.mapperClass = currentMapperClass();
        this.sqlSessionFactory = SpringUtil.getBean(SqlSessionFactory.class);
        BaseDoService.commonMapper = SpringUtil.getBean(CommonMapper.class);
    }


    /**
     * 批量更新
     *
     * @param list    数据列表
     * @param onlyKey 唯一键
     * @return 成功数量
     */
    public int batchUpdate(List<P> list, SFunction<P, ?> onlyKey, boolean nullUpdate, String... updateFieldNames) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);

        try {
            // 创建一个sql
            String sql = generateSql(tableInfo, list, onlyKey, nullUpdate, updateFieldNames);
            return commonMapper.executeSql(sql);
        } catch (Exception e) {
            log.error("批量update失败！ 数据量:{} 唯一key:{} mapper:{} po:{}", list.size(), onlyKey, mapperClass.getName(), entityClass.getName(), e);
        }

        return 0;
    }

    /**
     * 将MappedStatement注册到Configuration中
     *
     * @param configuration mybatis配置
     * @param ms            MappedStatement
     */
    private synchronized void addMappedStatementToConfiguration(Configuration configuration, MappedStatement ms) {
        if (!configuration.hasStatement(ms.getId())) {
            configuration.addMappedStatement(ms);
        }
    }

    /**
     * 生成批量update的sql
     * <p>
     * 批量update原理：使用select传入的数据，生成一个<strong>虚拟表</strong>，然后将指定更新表和虚拟表进行关联，在set每一个字段实现数据的更新
     * </p>
     * <p>
     * 生成格式：
     * </p>
     * <p>
     * null值也更新
     * </p>
     * <pre>
     * {@code
     * update sfo_sku a,(
     *      select 'YT_10100203333' as forecast_sku_code,'Yufire' as update_user_name,'US' as site
     *      union
     *      select 'YT_10100203334' ,'Yufire','US'
     * ) b
     * set a.forecast_sku_code = b.forecast_sku_code,a.update_user_name = b.update_user_name,a.site = b.site
     * where a.forecast_sku_code = b.forecast_sku_code and b.forecast_sku_code is not null and a.deleted = false;
     * }
     * </pre>
     * <p>
     * null值不更新
     * </p>
     * <pre>
     * {@code
     * update sfo_sku a,(
     *     select 'YT_10100203333' as forecast_sku_code,'Yufire' as update_user_name,'US' as site
     *     union
     *     select 'YT_10100203334' ,'Yufire','US'
     * ) b
     * set a.forecast_sku_code = COALESCE(b.forecast_sku_code,a.forecast_sku_code),a.update_user_name = COALESCE(b.update_user_name,a.update_user_name),a.site = COALESCE(b.site,a.site)
     * where a.forecast_sku_code = b.forecast_sku_code and b.forecast_sku_code is not null and a.deleted = false;
     * }
     * </pre>
     *
     * @param tbInfo     表信息
     * @param list       数据列表
     * @param onlyKey    唯一键
     * @param nullUpdate null数据是否更新
     * @return sql
     */
    private String generateSql(TableInfo tbInfo, List<P> list, SFunction<P, ?> onlyKey, boolean nullUpdate, String... updateFieldNames) {
        // 校验参数
        checkParams(tbInfo, list, onlyKey);
        // 移除掉get然后首字母小写就是字段名
        String onlyFieldName = parseOnlyFiled(onlyKey);
        String onlyDbFiled = null;

        StringJoiner sqlJoiner = new StringJoiner(" ");
        sqlJoiner.add("update").add(tbInfo.getTableName()).add(ALIAS_MASTER).add(",(");


        StringJoiner virtualTable = new StringJoiner(" union ");
        StringBuilder setFields = new StringBuilder();

        // 控制是否是首次进入，首次生成虚拟列需要拼接 as xxx，后续union的虚拟列无需拼接as xxx都会以第一次为准，为了是减少sql的体积
        boolean first = true;
        for (P p : list) {
            StringBuilder dataItemSql = new StringBuilder();
            dataItemSql.append("select ");
            MetaObject metaObject = SystemMetaObject.forObject(p);

            // 生成虚拟数据
            for (TableFieldInfo field : tbInfo.getFieldList()) {
                // 排除不需要的字段,id、create_user、update_time 等
                if (EXCLUDE_FIELDS.contains(field.getProperty())) continue;
                // 指定字段更新判断
                if(!Objects.equals(field.getProperty(), onlyFieldName) && ArrayUtil.isNotEmpty(updateFieldNames) && !ArrayUtil.contains(updateFieldNames, field.getProperty())) continue;
                boolean checkOnlyValue = field.getProperty().equals(onlyFieldName);
                // 校验onlyKey的值是否存在
                Object value = metaObject.getValue(field.getProperty());
                if (checkOnlyValue) {
                    onlyDbFiled = field.getColumn();
                    if (ObjectUtil.isNull(value))
                        throw new IllegalArgumentException("唯一更新键：" + onlyFieldName + "没有值！");
                }

                if (value != null) {
                    dataItemSql.append("'").append(value).append("'");
                } else {
                    dataItemSql.append((String) null);
                }

                if (first) {
                    dataItemSql.append(" as ").append(field.getColumn());
                }
                dataItemSql.append(",");

                if (!first) continue;
                // 拼接set赋值部分
                spliceSetTemplate(nullUpdate, field, setFields);
            }
            // 去除最后一个,
            virtualTable.add(dataItemSql.substring(0, dataItemSql.length() - 1));
            first = false;
        }

        sqlJoiner.add(virtualTable.toString()).add(")").add(ALIAS_VIRTUAL);

        // setFields去除最后一个,
        setFields.setLength(setFields.length() - 1);
        // 关联更新部分
        sqlJoiner.add("SET").add(setFields);

        StringBuilder where = new StringBuilder();

        // 拼接唯一更新键的关联关系
        where.append("where ").append(ALIAS_MASTER).append(".").append(onlyDbFiled).append(" = ").append(ALIAS_VIRTUAL).append(".").append(onlyDbFiled);
        // 获取deleted字段
        String deleted = tbInfo.getLogicDeleteFieldInfo().getColumn();
        if (StrUtil.isNotBlank(deleted)) {
            where.append(" and ").append(ALIAS_MASTER).append(".").append(deleted).append(" =false;");
        }
        sqlJoiner.add(where);

        return sqlJoiner.toString();

    }

    /**
     * 解析唯一键的字段名
     *
     * @param onlyKey 唯一键
     * @param <P>     实体类
     * @return 字段名
     */
    private static <P> String parseOnlyFiled(SFunction<P, ?> onlyKey) {
        return StrUtil.lowerFirst(LambdaUtils.extract(onlyKey).getImplMethodName().replaceFirst("get", StrUtil.EMPTY));
    }

    /**
     * 校验参数
     *
     * @param tbInfo  表信息
     * @param list    数据列表
     * @param onlyKey 唯一键
     */
    private void checkParams(TableInfo tbInfo, List<P> list, SFunction<P, ?> onlyKey) {
        if (CollUtil.isEmpty(list)) throw new IllegalArgumentException("数据为空，无法进行修改..");
        if (tbInfo == null) throw new IllegalArgumentException("实体类没有对应的表信息.." + entityClass.getName());
        if (onlyKey == null) throw new IllegalArgumentException("唯一键不能为空.." + entityClass.getName());
    }

    /**
     * 拼接update的关联部分
     * 例子：a.sku=b.sku,a.site=b.site
     * 例子：a.sku=COALESCE(b.sku,a.sku),a.site=COALESCE(b.site,a.site)
     *
     * @param nullUpdate null数据是否更新
     * @param field      需要更新的所有字段
     * @param joinUpdate 拼接的sql
     */
    private static void spliceSetTemplate(boolean nullUpdate, TableFieldInfo field, StringBuilder joinUpdate) {
        if (nullUpdate) {
            joinUpdate.append(ALIAS_MASTER).append(CharUtil.DOT).append(field.getColumn()).append(" = ").append(ALIAS_VIRTUAL).append(CharUtil.DOT).append(field.getColumn()).append(",");
        } else {
            joinUpdate.append(ALIAS_MASTER).append(CharUtil.DOT).append(field.getColumn()).append(" = ").append("COALESCE(").append(ALIAS_VIRTUAL).append(CharUtil.DOT).append(field.getColumn()).append(",").append(ALIAS_MASTER).append(CharUtil.DOT).append(field.getColumn()).append("),");
        }
    }


    /**
     * 获取当前mapper的class
     *
     * @return
     */
    @SneakyThrows
    @Override
    protected Class currentMapperClass() {
        Class pClass = ReflectionKit.getSuperClassGenericType(this.getClass(), ServiceImpl.class, 0);
//        if (pClass.equals(Object.class) && baseMapper != null) {
//            InvocationHandler invocationHandler = Proxy.getInvocationHandler(baseMapper);
//            Object advised = ReflectUtil.getFieldValue(invocationHandler, "advised");
//            ProxyFactory proxyFactory = (ProxyFactory) advised;
//            Object mybatisMapperProxy = ReflectUtil.getFieldValue(proxyFactory.getTargetSource().getTarget(), "h");
//            return ClassUtils.getUserClass(baseMapper);
//        }
//        return Object.class;
        //判断当前的pClass是否为Object.class
        if ((pClass == null || Object.class.equals(pClass)) && baseMapper != null) {
            BaseMapper mp = this.baseMapper;
            Field h = mp.getClass().getSuperclass().getDeclaredField("h");
            h.setAccessible(true);
            InvocationHandler mapperProxy = (InvocationHandler) h.get(mp);
            Field mapperInterface = mapperProxy.getClass().getDeclaredField("mapperInterface");
            mapperInterface.setAccessible(true);
            return (Class) mapperInterface.get(mapperProxy);
        }
        return Object.class;
    }


    protected Class currentModelClass() {
        return ReflectionKit.getSuperClassGenericType(this.getClass(), ServiceImpl.class, 1);
    }


}
