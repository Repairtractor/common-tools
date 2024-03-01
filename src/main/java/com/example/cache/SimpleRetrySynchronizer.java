package com.example.cache;



import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.LockSupport;


/**
 * jvm内程序锁
 * @param <V>
 */
public class SimpleRetrySynchronizer<V> extends AbstratctRedisRetrySynchronizer<V> {


    private final BlockingQueue<ReTime> redisTaskRetryQueue;

    private final Set<String> lockSet = new ConcurrentHashSet<>();

    private final Set<Thread> threads = new ConcurrentHashSet<>();


    public SimpleRetrySynchronizer(String keyPath, RedissonClient redisson, CacheConfig config) {
        super(keyPath, redisson, config);
        this.redisTaskRetryQueue = new LinkedBlockingQueue<>();
        init();
    }


    /**
     * 判断当前查询可以key是否有其他线程正在进行重试操作
     * 全部包含说明其他线程正在进行重试操作，当前线程需要等待，否则不需要等待 直接进行重试
     *
     * @param codes
     * @return
     */
    private synchronized boolean tryLock(Collection<String> codes) {
        //判断是否包含全部
        boolean hasAllKeys = lockSet.containsAll(codes);
        if (!hasAllKeys) {
            lockSet.addAll(codes);
            return true;
        }
        return false;
    }

    /**
     * 获取集合锁，当查询key有一个不存在当前锁集合时，获取锁成功，如果全部存在，获取锁失败
     * 获取锁失败会将当前线程放入阻塞线程队列中，等待其他线程释放锁
     *
     * @param keys
     * @return
     */
    public boolean lock(Collection<String> keys) {
        if (tryLock(keys)) {
            return true;
        }
        threads.add(Thread.currentThread());
        LockSupport.park();
        return false;
    }

    /**
     * 释放其他线程的锁
     */
    public synchronized void unLock(Collection<String> keys) {
        keys.forEach(lockSet::remove);
        threads.forEach(LockSupport::unpark);
        threads.clear();
    }


    @Override
    protected List<ReTime> consume() {
        List<ReTime> reTimes = CollUtil.newArrayList();
        while (true) {
            ReTime reTime = null;
            try {
                reTime = redisTaskRetryQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (Objects.isNull(reTime)) {
                break;
            }
            reTimes.add(reTime);
        }
        return reTimes;
    }

    @Override
    protected void retry(ReTime reTime) {
        redisTaskRetryQueue.add(reTime);
    }


    @Override
    protected int retryingSize() {
        return redisTaskRetryQueue.size();
    }
}
