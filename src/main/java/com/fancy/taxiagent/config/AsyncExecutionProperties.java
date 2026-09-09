package com.fancy.taxiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.async.agent")
public class AsyncExecutionProperties {
    private int corePoolSize = 16;
    private int maxPoolSize = 64;
    private int queueCapacity = 200;
    private int keepAliveSeconds = 60;
    private int ragCorePoolSize = 8;
    private int ragMaxPoolSize = 32;
    private int ragQueueCapacity = 100;
}