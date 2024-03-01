package com.example.cache;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public abstract class AbstratctRedisRetrySynchronizer<V> implements RedisRetrySynchronizer<V> {

    /**
     * 重试标志，false->否 true->是
     * 必须是原子操作，这个主要标志当前是否正在进行重试操作，当多个线程进来的时候，其他线程需要实时的知道当前是否正在进行重试操作
     */
    protected final AtomicBoolean isRetrying = new AtomicBoolean(false);

    //这里存在可能的隐患，当失败队列中有许多删除失败的key和失败重试之后再也没有用过的数据时可能会造成数据溢出,所以数据超过一定数量直接全部删掉
    private final Set<String> failKSet;

    private final String keyPath;

    protected final CacheConfig expire;

    protected final RedissonClient redisson;


    protected class ReTime {
        /**
         * redis key
         */
        public final String redisKey;
        /**
         * 队列重试次数
         */
        public AtomicInteger time;
        /**
         * 原本key值
         */
        public final String code;
        /**
         * 原来value值
         */
        public final V value;

        /**
         * redis value
         */
        public final String redisValue;

        public ReTime(String code, V value) {
            this.code = code;
            time = new AtomicInteger(1);
            this.value = value;
            this.redisKey = keyPath + code;
            this.redisValue = JSONUtil.toJsonStr(value);
        }
    }


    public AbstratctRedisRetrySynchronizer(String keyPath, RedissonClient redisson, CacheConfig expire) {
        this.keyPath = keyPath;
        this.redisson = redisson;
        this.expire = expire;
        this.failKSet = new ConcurrentHashSet<>(RedisCacheConstant.FAIL_MAX_SIZE);
        init();
    }


    /**
     * 初始化程序时就异步的进行失败队列的轮询
     */
    protected void init() {
        //这里只是进行重试，由子类实现consumer来实现通信
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true) {
                try {
                    log.info("Starting the queue retry mechanism, current queue size: {}", retryingSize());
                    Collection<ReTime> reTimes = consume();
                    if (CollUtil.isEmpty(reTimes)) {
                        isRetrying.set(false);
                        continue;
                    }
                    for (ReTime reTime : reTimes) {
                        isRetrying.compareAndSet(false, true);
                        if (Objects.isNull(reTime.value)) {
                            remove(reTime);
                        }
                        add(reTime);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
    }


    protected void add(ReTime reTime) {
        try {
            log.info("当前线程正在添加缓存，当前的缓冲队列为还有{}条数据", retryingSize());

            addRedisValue(reTime.redisKey, reTime.value);
            //添加成功了，删掉失败队列中的key
            removeFailKey(reTime.code);
        } catch (Exception e) {
            if (canNotAdd(reTime)) {
                log.error("重试添加操作失败，key已经超过重试次数，将此key失败集合：{}", reTime.code);
                addFailKey(reTime.code);
                return;
            }
            int time = reTime.time.getAndIncrement();
            log.error("重试添加操作失败，key:{}继续进行重试，当前已重试：{}", reTime.code, time);
            retry(reTime);
        }
    }

    /**
     * 消费队列中的数据
     *
     * @return
     */
    protected abstract Collection<ReTime> consume();


    protected void remove(ReTime reTime) {
        log.info("开始删除redis缓存,keys：{}", reTime.code);
        try {
            deleteRedisValue(reTime.redisKey);
        } catch (Exception e) {
            if (canNotAdd(reTime)) {
                log.info("超过重试次数，丢弃此key：{}", reTime.code);
                addFailKey(reTime.code);
            }
            int time = reTime.time.getAndIncrement();
            log.info("删除redis缓存失败了，key:{},当前重试次数为：{}", reTime.code, time);
            retry(reTime);
        }
    }

    private void deleteRedisValue(String redisKey) {
        redisson.getBucket(redisKey).delete();
    }


    @Override
    public void retry(String k, V v) {
        add(new ReTime(k, v));
    }


    protected void addRedisValue(String k, V v) {
        redisson.getBucket(k).set(v, expire.getExpireTime(), TimeUnit.SECONDS);
    }

    /**
     * 判断当前重试队列是否有数据，如果有阻塞等待直到超时
     *
     * @param timeout 超时时间，单位毫秒
     * @return
     */
    private boolean accessGet(long timeout) {
        long endTime = System.currentTimeMillis() + timeout;
        while (isRetrying.get() && endTime > System.currentTimeMillis()) ;
        return !isRetrying.get();
    }


    /**
     * 判断当前是否可以从缓存取数据
     *
     * @param keys    想要查询的key
     * @param timeout 等待同步器同步结束的超时时间
     * @return
     */
    public boolean isReady(Collection<String> keys, boolean isWaitSynchronizedEnd, long timeout) {
        //如果存在失败队列，需要直接从元数据源取数据
        if (containsFailKey(keys)) {
            return false;
        }
        if (isWaitSynchronizedEnd) {
            return accessGet(timeout);
        }
        return true;
    }

    /**
     * 判断查询的key是否在失败集合中
     *
     * @param keys
     * @return
     */
    private boolean containsFailKey(Collection<String> keys) {
        return keys.stream().anyMatch(failKSet::contains);
    }

    public boolean isReady(Collection<String> keys) {
        return isReady(keys, false, 0L);
    }


    protected void removeFailKey(String code) {
        failKSet.removeIf(k -> Objects.equals(k, code));
    }


    private boolean canNotAdd(ReTime reTime) {
        return RedisCacheConstant.RETRY_TIME <= reTime.time.get();
    }

    private void addFailKey(String key) {
        //这里要注意多线程问题，两个线程都进入了clear，线程1执行完clear之后去添加，而此时线程2还会执行一次clear，那么就会把线程1的数据也删掉
        if (Objects.equals(RedisCacheConstant.FAIL_MAX_SIZE, failKSet.size())) {
            synchronized (failKSet) {
                if (Objects.equals(RedisCacheConstant.FAIL_MAX_SIZE, failKSet.size())) {
                    failKSet.clear();
                    failKSet.add(key);
                    return;
                }
            }
        }
        failKSet.add(key);
    }


    protected abstract void retry(ReTime reTime);

    protected abstract int retryingSize();


}
