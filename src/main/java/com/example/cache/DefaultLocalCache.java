package com.example.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

/**
 * 本地缓存的抽象类
 *
 * @param <V>
 * @param <T>
 */
public class DefaultLocalCache<V, T> extends AbstractLocalCache<V, T> {

    private final Function<Collection<String>, List<T>> allSource;

    private static final String DEFAULT_CACHE_CODE = "default_local_cache";


    public static <V, T> Builder<V, T> builder(GetSetter<T, String, V> getSetter) {
        return new Builder<>(getSetter);
    }

    public static class Builder<V, T> {
        private final GetSetter<T, String, V> getSetter;
        private Function<Collection<String>, List<T>> allSource;


        private CacheConfig cacheConfig;


        public Builder(GetSetter<T, String, V> getSetter) {
            this.getSetter = getSetter;
        }

        public DefaultLocalCache<V, T> build() {
            return new DefaultLocalCache<>(getSetter, allSource, cacheConfig);
        }


        public Builder<V, T> allSource(Function<Collection<String>, List<T>> allSource) {
            this.allSource = allSource;
            return this;
        }

        public Builder<V, T> config(CacheConfig config) {
            this.cacheConfig = config;
            return this;
        }

        public Builder<V, T> simpleSource(Function<String, T> simpleSource) {
            if (allSource == null) {
                allSource = (keys) -> keys.stream().map(simpleSource).collect(toList());
            }
            return this;
        }

    }


    private DefaultLocalCache(GetSetter<T, String, V> getSetter, Function<Collection<String>, List<T>> allSource, CacheConfig cacheConstant) {
        super(getSetter, Objects.isNull(cacheConstant) ? new DefaultCacheConfig() : cacheConstant);
        this.allSource = allSource;
    }


    @Override
    public List<T> sourceAll(Collection<String> keys) {
        return allSource.apply(keys);
    }


    protected static class DefaultCacheConfig implements CacheConfig {


        @Override
        public String getCode() {
            return DEFAULT_CACHE_CODE;
        }

        @Override
        public String getKey() {
            return DEFAULT_CACHE_CODE;
        }

        @Override
        public int getMaxCapacity() {
            return 100;
        }

        @Override
        public long getExpireTime() {
            return Duration.ofHours(1).toMillis();
        }

    }

}
