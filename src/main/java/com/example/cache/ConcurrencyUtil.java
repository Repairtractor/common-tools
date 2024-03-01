package com.example.cache;


import com.alibaba.ttl.TtlRunnable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class ConcurrencyUtil {


    /**
     * 初始化线程
     * 10个核心线程，20个最大线程数，核心线程之外的空闲线程最大存活时间为1分钟，任务会放入缓存队列中,线程名称前缀为"forecast-threadPool"
     */
    @Getter
    private static final Executor pool = new ThreadPoolExecutor(10, 20, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000), new DefaultThreadFactory("forecast-threadPool"));

    private ConcurrencyUtil() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    /**
     * 执行线程
     *
     * @param runnable 使用TtlRunnable包装的runnable，避免子线程线程池互相污染
     */
    public static void execute(Runnable runnable) {
        String traceId = TraceIdUtil.getTrace();
        String span=TraceIdUtil.getSpan();
        pool.execute(Objects.requireNonNull(TtlRunnable.get(()->{
            TraceIdUtil.addTrace(traceId,span);
            runnable.run();
        })));
    }

    public static void executeAfterTransactionCommit(Runnable runnable) {
        //事务提交之后，异步发送事件
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execute(runnable);
                }
            });
        } else {
            execute(runnable);
        }
    }

    @RequiredArgsConstructor
    static class DefaultThreadFactory implements ThreadFactory {

        private static final AtomicInteger poolId = new AtomicInteger();

        private final String threadName;

        //设置线程抛出异常时的处理回调机制
        private static void uncaughtException(Thread t, Throwable e) {
            log.error("线程异常抛出 name:{}", t.getName(), e);
            throw new RuntimeException(e);
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = new Thread(r, threadName + "-" + poolId.incrementAndGet());

            thread.setUncaughtExceptionHandler(DefaultThreadFactory::uncaughtException);
            return thread;
        }
    }


}
