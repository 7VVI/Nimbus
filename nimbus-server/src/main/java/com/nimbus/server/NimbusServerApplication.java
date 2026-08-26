package com.nimbus.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 统一启动入口: 装配所有业务模块(com.nimbus.**)与框架组件
 */
@SpringBootApplication(scanBasePackages = "com.nimbus")
@MapperScan("com.nimbus.**.mapper")
public class NimbusServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NimbusServerApplication.class, args);
    }
}