package com.example.cache;

/**
 * 缓存的配置接口，使用缓存请实现该接口，推荐枚举实现
 */
public interface CacheConfig {

    String getCode();

    String getKey();

    /**
     * 缓存最大容量
     */
    int getMaxCapacity();

    long getExpireTime();

    default boolean isRetrySynchronizer(){
        return false;
    }

    default String getCachePath() {
        return getKey() + RedisCacheConstant.CacheKeyComa + getCode();
    }
}
