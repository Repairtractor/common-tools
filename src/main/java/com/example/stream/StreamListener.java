package com.example.stream;

import org.redisson.api.StreamMessageId;

/**
 * stream消费的抽象类
 *
 * @param <T> 消费的数据类型
 */
public interface StreamListener<T> {

    /**
     * 消费消息
     *
     * @param messageId 消息id
     * @param message   消息
     */
    void consume(StreamMessageId messageId, T message);
}
