package com.example.stream;

import cn.hutool.core.bean.BeanUtil;
import com.example.util.ConcurrencyUtil;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamGroup;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreamManager {

    final RedissonClient redisson;
    final ApplicationContext applicationContext;

    Map<String, String> consumerGroupMap=new HashMap<>();
    Map<String, String> topicMap=new HashMap<>();


    public synchronized void start() {
        //首先获取全部的Listener
        Map<String, Object> beansWithAnnotationMap = applicationContext.getBeansWithAnnotation(StreamMessageListener.class);
        beansWithAnnotationMap.forEach((beanName, listener) -> {
            StreamMessageListener annotation = listener.getClass().getAnnotation(StreamMessageListener.class);
            String topic = annotation.topic();
            String consumerGroup = annotation.consumerGroup();
            //启动消费者
            //初始化redis stream
            RStream<String, Object> rStream = redisson.getStream(topic, new StringCodec());
            //检查消费组是否存在，不存在则创建
            if (!rStream.isExists() || !rStream.listGroups().stream().map(StreamGroup::getName).collect(Collectors.toSet()).contains(consumerGroup)) {
                rStream.createGroup(consumerGroup, StreamMessageId.ALL);
            }
            validate(listener.getClass(),annotation);
            consumerGroupMap.put(annotation.consumerGroup(),listener.getClass().getSimpleName());
            topicMap.put(annotation.topic(),annotation.consumerGroup());
            log.info("初始化消费者，消费组：{} 消费者：{} 消费主题：{}", annotation.consumerGroup(), annotation.consumerName(), annotation.topic());

            ConcurrencyUtil.execute(() -> listener(rStream, annotation, listener));
        });
    }

    private void validate(Class<?> classIns, StreamMessageListener annotation) {

        if (StringUtils.isBlank(annotation.topic())) {
            throw new RuntimeException(classIns.getSimpleName() + ":topic不能为空");
        }
        if (StringUtils.isBlank(annotation.consumerGroup())) {
            throw new RuntimeException(classIns.getSimpleName() + ":consumerGroup不能为空");
        }

        if (topicMap.containsKey(annotation.topic())) {
            if(!topicMap.get(annotation.topic()).equalsIgnoreCase(annotation.consumerGroup())){
                throw new RuntimeException(
                        String.format("Topic:【%s】 已经由消费组【%s】监听 请勿重复监听同一Topic", annotation.topic(), topicMap.get(annotation.topic())));
            }
            if (consumerGroupMap.containsKey(annotation.consumerGroup())) {
                throw new RuntimeException(String
                        .format("consumerGroup:【%s】 已经由【%s】监听 请勿重复监听同一Group", annotation.consumerGroup(), classIns.getSimpleName()));
            }
        }

    }

    private void listener(RStream<String, Object> rStream, StreamMessageListener annotation, Object listener) {
        while (true) {
            if (listener instanceof StreamListener) {
                StreamListener streamMessageListener = (StreamListener) listener;
                Map<StreamMessageId, Map<String, Object>> messageIdMapMap = rStream.readGroup(annotation.consumerGroup(), annotation.consumerName(),
                        StreamReadGroupArgs
                                .greaterThan(StreamMessageId.NEVER_DELIVERED)
                                .count(annotation.consumerCount())
                                .timeout(Duration.ofSeconds(0))
                );
                if (messageIdMapMap.isEmpty()) {
                    return;
                }

                try {
                    log.info("开始消费消息，消息id：{}", messageIdMapMap.keySet());
                    messageIdMapMap.forEach((k, body) -> {
                        Type[] types = listener.getClass().getGenericInterfaces();
                        ParameterizedType parameterizedType = (ParameterizedType) types[0];
                        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                        Class clazz = (Class) actualTypeArguments[0];
                        Object messageReqDto = BeanUtil.mapToBean(body,clazz,false);
                        streamMessageListener.consume(k, messageReqDto);
                    });
                    log.info("消息消费成功！进行消息确认，消息id：{}", messageIdMapMap.keySet());
                    rStream.ack(annotation.consumerGroup(), messageIdMapMap.keySet().toArray(new StreamMessageId[0]));
                } catch (Exception exception) {
                    log.info("消息消费异常，消息id：{}", messageIdMapMap.keySet());
                    log.error("消息消费异常，无法正常确认", exception);
                }
            }
        }


    }
}
