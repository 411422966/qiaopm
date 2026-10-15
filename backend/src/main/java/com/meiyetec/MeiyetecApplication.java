package com.meiyetec;

import com.meiyetec.cn.domestic.DomesticPayProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.meiyetec.mapper")
@EnableConfigurationProperties({DomesticPayProperties.class})
@EnableAsync
@EnableScheduling
public class MeiyetecApplication {
    public static void main(String[] args) {
        SpringApplication.run(MeiyetecApplication.class, args);
    }
}
