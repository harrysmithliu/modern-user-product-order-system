package com.example.orders.config;

import com.example.orders.security.RequestUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestUserArgumentResolver requestUserArgumentResolver;

    public WebMvcConfig(RequestUserArgumentResolver requestUserArgumentResolver) {
        this.requestUserArgumentResolver = requestUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(requestUserArgumentResolver);
    }
}
