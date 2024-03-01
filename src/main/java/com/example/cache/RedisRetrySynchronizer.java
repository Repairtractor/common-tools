package com.example.cache;

import java.util.Collection;

/**
 * 同步器，目前是利用阻塞队列实现
 * 后续需要优化为可选择性的消息队列和阻塞队列
 *
 * 要解决的问题：可以使用在重建缓存中，防止缓存重复创建
 *
 * @param <V>
 */
public interface RedisRetrySynchronizer<V> {


    /**
     * 获取集合锁，当查询key有一个不存在当前锁集合时，获取锁成功，如果全部存在，获取锁失败
     * 获取锁失败会将当前线程放入阻塞线程队列中，等待其他线程释放锁
     *
     * @param keys
     * @return
     */
    boolean lock(Collection<String> keys);

    /**
     * 释放锁
     */
    void unLock(Collection<String> keys);

    /**
     * 判断当前key是否可以获取缓存
     *
     * @param keys
     * @param isWaitSynchronizedEnd
     * @param timeout
     * @return
     */
    boolean isReady(Collection<String> keys, boolean isWaitSynchronizedEnd, long timeout);

    /**
     * key value 重试
     * @param k
     * @param v
     */
    void retry(String k, V v);

    /**
     * 判断当前key是否可以获取缓存
     * @param keys
     * @return
     */
    boolean isReady(Collection<String> keys);

}
