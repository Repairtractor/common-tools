package com.example.cache;

/**
     * redisson 常量配置
     */
    public interface RedisCacheConstant {

        String CacheKeyComa = ":";

        //redisson 锁的基础时间
        Integer baseLockTime = 40;

        //redisson 锁的增加时间
        Integer bound = 20;

        String PREFIX = "yt:bi:forecast";

        //redisson配置文件路径
        String CACHE_PATH = PREFIX + CacheKeyComa + "cache";


        //redisson set路径

        String SET_PATH = PREFIX + CacheKeyComa + "set";

        //redisson锁路径

        String LOCK_PATH = PREFIX + CacheKeyComa + "lock";

        /**
         * redis stream name
         */

        String STREAM_NAME = PREFIX + CacheKeyComa + "stream";

        /**
         * redis stream consumer group
         */
        String REDIS_STREAM_CONSUMER_GROUP = "redisCacheRetryGroup";

        /**
         * redis stream consumer name
         */
        String CONSUMER_NAME = "redisCacheRetryConsumer";


        /**
         * 队列中的重试次数
         */
        int RETRY_TIME = 2;

        int FAIL_MAX_SIZE = 500;

    }