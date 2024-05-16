package com.example.cache;

import cn.hutool.core.collection.CollUtil;
import lombok.AllArgsConstructor;

import java.util.*;
import java.util.function.Function;

/**
 * 缓存的最高抽象接口，规定了缓存的基本操作
 *
 * @param <K> 缓存的key
 * @param <V> 缓存的value
 * @param <T> 缓存的源数据实体
 */
public interface Cache<K, V, T> {
    /**
     * 删除缓存
     *
     * @param keys
     */
    void removeKey(Collection<K> keys);

    /**
     * 设置缓存
     *
     * @param list
     */
    void put(Collection<T> list);


    default void put(T t) {
        put(Collections.singletonList(t));
    }

    default void put(Map<K, V> map) {
        throw new UnsupportedOperationException("put map");
    }

    default Collection<V> removeAndGet(Collection<K> keys) {
        throw new UnsupportedOperationException("update");
    }


    /**
     * 获取源数据
     *
     * @param keys
     * @return
     */
    List<T> sourceAll(Collection<K> keys);

    /**
     * 将缓存转换为map
     *
     * @param keys
     * @return
     */
    Map<K, V> asMap(Collection<K> keys);

    default Map<K, V> asMap(Collection<K> keys, boolean reSet) {
       return asMap(keys);
    }


    default List<V> get(Collection<K> keys) {
        return new ArrayList<>(asMap(keys).values());
    }

    default List<V> get(Collection<K> keys, boolean reSet) {
        return new ArrayList<>(asMap(keys, reSet).values());
    }


    default Optional<V> optionalGet(K k) {
        return Optional.ofNullable(get(k));
    }
    default Optional<V> optionalGet(K k,boolean reSet) {
        return Optional.ofNullable(get(k,reSet));
    }

    default V get(K k) {
        List<V> vs = get(Collections.singletonList(k));
        if (CollUtil.isEmpty(vs)) {
            return null;
        }
        return vs.get(0);
    }

    default V get(K k, boolean reSet) {
        List<V> vs = get(Collections.singletonList(k),reSet);
        if (CollUtil.isEmpty(vs)) {
            return null;
        }
        return vs.get(0);
    }


    default boolean contains(K k,boolean reSet) {
        return optionalGet(k,reSet).isPresent();
    }


    @AllArgsConstructor
    final class GetSetter<T, K, V> {
        public final Function<T, K> keyGetter;
        public final Function<T, V> valueGetter;
    }

}
