package com.meiyetec.config;

import com.meiyetec.interceptor.JwtInterceptor;
import com.meiyetec.security.DataScopeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final DataScopeInterceptor dataScopeInterceptor;

    @Value("${app.storage.dir:./storage/dcep/qualification}")
    private String storageDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // JWT 拦截：必须先于数据范围
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/logout",
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/static/**",
                        "/static/**",
                        "/error",
                        // Webhook 路径全部放行（I037 CRITICAL-P0 修复）
                        // 有赞 / 海外支付 / 国内支付 / BNPL / 开放平台 / 支付 / 预订 / 团购 / ERP / 海外预约
                        "/webhook/**",
                        "/overseas-payment/webhook/**",
                        "/ecommerce/webhook/**",
                        "/pos/webhook/**",
                        "/payment/webhook/**",
                        "/booking/webhook/**",
                        "/platform/webhook/**",
                        "/intl-booking/webhook/**",
                        // Post-Audit 2.2-A5b 扩展：补 BNPL + 国内支付
                        "/bnpl/webhook/**",
                        "/domestic/webhook/**",
                        // Gate 3 1.4a：actuator 监控端点放行（health/prometheus/info）
                        "/actuator/**"
                );

        // 数据范围拦截：仅对业务接口生效
        registry.addInterceptor(dataScopeInterceptor)
                .addPathPatterns(
                        "/member/**",
                        "/wallet/**",
                        "/packages/**",
                        "/booking/**",
                        "/pos/**",
                        "/stock/**",
                        "/commission/**",
                        "/market/**",
                        "/report/**",
                        "/employee/**",
                        "/store/**",
                        "/room/**",
                        "/category/**",
                        "/sys/**",
                        "/crm/**"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + (storageDir.endsWith("/") ? storageDir : storageDir + "/");
        registry.addResourceHandler("/api/static/dcep/qualification/**")
                .addResourceLocations(location);
    }
}
