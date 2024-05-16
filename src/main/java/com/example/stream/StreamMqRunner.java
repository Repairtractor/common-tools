package com.example.stream;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StreamMqRunner implements ApplicationRunner {


    @Override
    public void run(ApplicationArguments args) throws Exception {
        StreamManager streamManager = SpringUtil.getBean(StreamManager.class);
        streamManager.start();
        log.info("spring容器初始化完成后======StreamMqRunner");
    }
}
