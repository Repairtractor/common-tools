package com.example.cache;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Yufire
 * @date 2023/8/31 13:23
 * traceId工具类
 */
public class TraceIdUtil {
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);
    private static final MyLong GENERATE_TOTAL = new MyLong(0);
    private static final Long MAX_VALUE = Long.MAX_VALUE - 1;
    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";

    /**
     * 添加traceId到slf4j
     */
    public static void addTraceId() {
        MDC.put(TRACE_ID, SNOWFLAKE.nextIdStr());
        MDC.put(SPAN_ID, Convert.toStr(GENERATE_TOTAL.myGetAndIncrement()));
    }

    /**
     * 添加traceId到slf4j
     */
    public static void addTrace(String traceId, String spanId) {
        MDC.put(TRACE_ID, traceId);
        MDC.put(SPAN_ID, spanId);
    }

    /**
     * 移除traceId
     */
    public static void removeTraceId() {
        MDC.remove(TRACE_ID);
        MDC.remove(SPAN_ID);
    }

    /**
     * 获取traceId
     *
     * @return traceId
     */
    public static String getTraceId() {
        return String.format("%s-%s", MDC.get(TRACE_ID), MDC.get(SPAN_ID));
    }


    /**
     * 获取traceId
     *
     * @return traceId
     */
    public static String getTrace() {
        return MDC.get(TRACE_ID);
    }

    /**
     * 获取traceId
     *
     * @return traceId
     */
    public static String getSpan() {
        return MDC.get(SPAN_ID);
    }

    public static class MyLong extends AtomicLong {

        public MyLong(long initialValue) {
            super(initialValue);
        }

        public long myGetAndIncrement() {
            long val = this.getAndIncrement();
            // 防止long溢出，虽然不太可能会溢出
            if (MAX_VALUE == val) {
                this.set(0L);
            }
            return val;
        }

    }

}
