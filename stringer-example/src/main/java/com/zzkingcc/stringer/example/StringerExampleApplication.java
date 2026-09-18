package com.zzkingcc.stringer.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Stringer 示例 / 联调入口
 * @author zzkingcc
 */
@Slf4j
@SpringBootApplication
public class StringerExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(StringerExampleApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Stringer 示例应用启动成功，联调页面：http://localhost:8080/test.html");
    }
}
