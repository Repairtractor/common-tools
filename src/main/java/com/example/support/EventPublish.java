package com.example.support;

import cn.hutool.extra.spring.SpringUtil;
import com.example.util.ConcurrencyUtil;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationContext;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class EventPublish {
    static ApplicationContext applicationContext;

    private static void init() {
        if (applicationContext == null) {
            applicationContext = SpringUtil.getApplicationContext();
        }
    }

    public static void publishEvent(Object event) {
        init();
        applicationContext.publishEvent(event);
    }

    public static void publishEventAsync(Object event) {
        ConcurrencyUtil.executeAfterTransactionCommit(() -> {
            publishEvent(event);
        });
    }


}
