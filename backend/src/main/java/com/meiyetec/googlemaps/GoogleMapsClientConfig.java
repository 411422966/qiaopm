package com.meiyetec.googlemaps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EXT-17：Google Maps 客户端 Spring 配置
 *
 * <p>把 {@link GoogleMapsGeocodingClient} 暴露为 Spring Bean（无状态），
 * 业务 Service 直接 @Autowired 注入即可。
 */
@Configuration
public class GoogleMapsClientConfig {

    @Bean
    public GoogleMapsGeocodingClient googleMapsGeocodingClient(ObjectMapper jackson) {
        return new GoogleMapsGeocodingClient(jackson);
    }
}
