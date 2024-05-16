package com.example.cache;



import lombok.Getter;

import java.time.Duration;

/**
 * @Date: 2022/2/16
 * @Author: kim
 * @Email: kim.zjy@bitsun-inc.com
 */
@Getter
public enum CacheConfigEnum implements CacheConfig {


    /**
     * sku主表的forecastSkuCode -> id
     */
    SKU_FORECASTSKUCODE_ID("skuForecastSkuCodeId", RedisCacheConstant.CACHE_PATH,50_000, Duration.ofDays(30)),

    ;

    /**
     * 缓存配置标示
     */
    public final String code;
    /**
     * 缓存key
     */
    public final String key;

    /**
     * 缓存最大容量
     */
    public final int maxCapacity;
    /**
     * 过期时间
     */
    public final long expireTime;

    public final boolean isRetrySynchronizer;



    CacheConfigEnum(String code, String key) {
        this(code, key, -1, -1L);
    }

    public String path() {
        return key + RedisCacheConstant.CacheKeyComa + code;
    }


    CacheConfigEnum(String code, String key, int maxCapacity, Duration expireTime) {
        this(code, key, maxCapacity, expireTime.toMillis());
    }

    CacheConfigEnum(String code, String key, int maxCapacity, long expireTime) {
        this(code, key, maxCapacity, expireTime, false);
    }


    CacheConfigEnum(String code, String key, int maxCapacity, long expireTime, boolean isRetrySynchronizer) {
        this.code = code;
        this.key = key;
        this.maxCapacity = maxCapacity;
        this.expireTime=expireTime;
        this.isRetrySynchronizer = isRetrySynchronizer;
    }






}
