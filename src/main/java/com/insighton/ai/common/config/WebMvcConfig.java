package com.insighton.ai.common.config;

import com.insighton.ai.adapter.client.interceptor.GroupMembershipInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final GroupMembershipInterceptor groupMembershipInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(groupMembershipInterceptor).addPathPatterns("/api/**");
    }
}
