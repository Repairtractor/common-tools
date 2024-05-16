package com.example.stream;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface StreamMessageListener {
    String value() default "";

    String topic();

    int consumerCount() default 1;

    String consumerName() default "*";


    String consumerGroup();
}
