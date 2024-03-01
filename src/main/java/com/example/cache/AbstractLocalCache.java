package com.example.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
public abstract class AbstractLocalCache<V, T> implements Cache<String, V, T> {

    final GetSetter<T, String, V> getSetter;

    LoadingCache<String, V> cache;

    final CacheConfig config;



    protected CacheLoader<String, V> getCacheCallBack() {
        return new CacheLoader<String, V>() {
            @Override
            public V load(String key) throws Exception {
                Map<String, V> kvMap = getCacheKeyAndValue(Collections.singletonList(key));
                if (MapUtil.isEmpty(kvMap)) {
                    return null;
                }
                return kvMap.get(key);
            }

            @Override
            public @NonNull Map<@NonNull String, @NonNull V> loadAll(@NonNull Iterable<? extends @NonNull String> keys) throws Exception {
                List<String> list = new ArrayList<>();
                keys.forEach(list::add);
                return getCacheKeyAndValue(list);
            }

        };
    }


    protected AbstractLocalCache(CacheConfig cacheConfig, GetSetter<T, String, V> getSetter) {
        this(getSetter, cacheConfig);
    }


    protected AbstractLocalCache(GetSetter<T, String, V> getSetter, CacheConfig config) {
        this.getSetter = getSetter;
        this.config = config;
        initCache();
    }


    private void initCache() {
        log.info("Initializing local cache '{}' with size: {}, expiration time: {}ms",
                config.getCode(),
                config.getMaxCapacity(),
                config.getExpireTime());
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.getMaxCapacity())
                .expireAfterWrite(config.getExpireTime(), TimeUnit.MILLISECONDS)
                .build(getCacheCallBack());
    }

    @Override
    public void removeKey(Collection<String> keys) {
        cache.invalidateAll(keys);
    }

    @Override
    public void put(Collection<T> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Map<String, V> kvMap = list.stream().collect(Collectors.toMap(getSetter.keyGetter, getSetter.valueGetter));
        cache.putAll(kvMap);
    }

    @Override
    public Map<String, V> asMap(Collection<String> keys) {
        try {
            return cache.getAll(keys);
        } catch (Exception e) {
            log.error("从缓存获取数据失败了", e);
            return Collections.emptyMap();
        }
    }

    protected Map<String, V> getCacheKeyAndValue(List<String> keys) {
        return sourceAll(keys).stream().collect(Collectors.toMap(getSetter.keyGetter, getSetter.valueGetter));
    }


}
