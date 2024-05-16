package com.example.util;


import com.alibaba.ttl.TtlCallable;
import com.alibaba.ttl.TtlRunnable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
public final class ConcurrencyUtil {


    /**
     * 初始化线程
     * 10个核心线程，20个最大线程数，核心线程之外的空闲线程最大存活时间为1分钟，任务会放入缓存队列中,线程名称前缀为"forecast-threadPool"
     */
    @Getter
    private static final Executor pool = new ThreadPoolExecutor(20, 40, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000), new DefaultThreadFactory("forecast-threadPool"));

    private ConcurrencyUtil() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    /**
     * 执行线程
     *
     * @param runnable 使用TtlRunnable包装的runnable，避免子线程线程池互相污染
     */
    public static CompletableFuture<Void> execute(Runnable runnable) {
        return CompletableFuture.runAsync(wrapper(runnable), pool);
    }


    /**
     * 执行线程
     *
     * @param supplier 使用TtlCallable包装的supplier，避免子线程线程池互相污染
     */
    public static <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(wrapper(supplier), pool);
    }


    public static void executeAfterTransactionCommit(Runnable runnable) {
        //事务提交之后，异步发送事件
        transactionAfterCommitWrapper(()->execute(runnable));
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
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, threadName + "-" + poolId.incrementAndGet());

            thread.setUncaughtExceptionHandler(DefaultThreadFactory::uncaughtException);
            return thread;
        }

    }

    public static Runnable wrapper(Runnable runnable) {
        String traceId = TraceIdUtil.getTrace();
        String span = TraceIdUtil.getSpan();
        return Objects.requireNonNull(TtlRunnable.get(() -> {
            TraceIdUtil.addTrace(traceId, span);
            runnable.run();
        }));
    }

    public static <T> Supplier<T> wrapper(Supplier<T> supplier) {
        String traceId = TraceIdUtil.getTrace();
        String span = TraceIdUtil.getSpan();
        return () -> {
            TtlCallable<T> tTtlCallable = TtlCallable.get(supplier::get);
            TraceIdUtil.addTrace(traceId, span);
            try {
                return tTtlCallable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static void transactionAfterCommitWrapper(Runnable runnable){
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

}
