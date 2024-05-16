package com.example.support;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

@Component
@Slf4j
public class RedisLockComponent {

    @Autowired
    private RedissonClient redissonClient;


    @Value("${forecast.redisson.lock.baseLockTime:40}")
    private Integer baseLockTime = 40;

    @Value("${forecast.redisson.lock.bound:20}")
    private Integer bound = 20;


    public Lock lock(String key, long time, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(key);
        lock.lock(time, timeUnit);
        log.info("currentThread:{},The lock was obtained {}", Thread.currentThread().getName(), key);
        return lock;

    }

    public Lock lock(String key) {
        RLock lock = redissonClient.getLock(key);
        lock.lock();
        log.info("currentThread:{},The lock was obtained {}", Thread.currentThread().getName(), key);
        return lock;

    }

    public void unlock(Lock lock) {
        if (lock != null) {
            try {
                //catch 住 由于业务时间过长 锁已经释放 ，此时解锁 会报锁不存在的异常
                log.info(" unlock in currentThread:{} ", Thread.currentThread().getName());
                lock.unlock();
            } catch (Exception e) {
                log.info("catch unlock exception: {}", e);
            }
        }
    }

    public boolean isLocked(String key) {
        return redissonClient.getLock(key).isLocked();
    }

    public Lock multiLock(Collection<String> keys, long time, TimeUnit timeUnit) {
        RLock[] rLocks = keys.stream().distinct().parallel().map(redissonClient::getLock).toArray(RLock[]::new);
        RLock multiLock = redissonClient.getMultiLock(rLocks);
        multiLock.lock(time, timeUnit);
        log.info("currentThread:{},The lock was obtained {}", Thread.currentThread().getName(), keys);
        return multiLock;
    }

    public Lock multiLock(Collection<String> keys) {
        RLock[] rLocks = keys.stream().distinct().parallel().map(redissonClient::getLock).toArray(RLock[]::new);
        RLock multiLock = redissonClient.getMultiLock(rLocks);
        multiLock.lock();
        log.info("currentThread:{},The lock was obtained {}", Thread.currentThread().getName(), keys);
        return multiLock;
    }

}
