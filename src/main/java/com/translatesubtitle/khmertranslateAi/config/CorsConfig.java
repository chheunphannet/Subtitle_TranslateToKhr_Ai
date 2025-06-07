package com.translatesubtitle.khmertranslateAi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // Apply CORS to your /api path
                .allowedOrigins("http://192.168.1.2" 
                                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // OPTIONS is often needed for preflight requests
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}