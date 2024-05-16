package com.example.stream;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.codec.StringCodec;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public final class StreamPublisher {
    final static RedissonClient redisson = SpringUtil.getBean(RedissonClient.class);


    public static void publish(String topic, List<?> values) {
        values.forEach(it -> publish(topic, it));
    }

    public static StreamMessageId publish(String topic, Object message) {
        return publish(topic, BeanUtil.beanToMap(message,false,true));
    }


    /**
     * 发布redis 消息
     *
     * @param topic   标题，redis中的stream流
     * @param message 消息
     */
    public static StreamMessageId publish(String topic, Map<String, Object> message) {
        RStream<String, Object> stream = redisson.getStream(topic, new StringCodec());

        StreamMessageId id = stream.add(StreamAddArgs.entries(message)
                .trimNonStrict()
                .maxLen(100000)
                .noLimit());
        log.info("发送消息成功！消息id：{}", id);
        return id;
    }

}
