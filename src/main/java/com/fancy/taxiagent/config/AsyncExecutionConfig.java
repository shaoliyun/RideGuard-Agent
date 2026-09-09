package com.fancy.taxiagent.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(AsyncExecutionProperties.class)
public class AsyncExecutionConfig {
    @Bean
    @Qualifier("agentTaskExecutor")
    public Executor agentTaskExecutor(AsyncExecutionProperties properties) {
        return createExecutor(properties, "agent-");
    }

    @Bean
    @Qualifier("ragTaskExecutor")
    public Executor ragTaskExecutor(AsyncExecutionProperties properties) {
        AsyncExecutionProperties ragProperties = new AsyncExecutionProperties();
        ragProperties.setCorePoolSize(properties.getRagCorePoolSize());
        ragProperties.setMaxPoolSize(properties.getRagMaxPoolSize());
        ragProperties.setQueueCapacity(properties.getRagQueueCapacity());
        ragProperties.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        return createExecutor(ragProperties, "rag-");
    }

    private Executor createExecutor(AsyncExecutionProperties properties, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}