package com.example.orders.config;

import java.util.concurrent.Semaphore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowConcurrencyConfig {

    @Bean(name = "orderWorkflowExecutor")
    public ThreadPoolTaskExecutor orderWorkflowExecutor(WorkflowProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("order-workflow-");
        executor.setCorePoolSize(Math.max(properties.executorCorePoolSize(), 1));
        executor.setMaxPoolSize(Math.max(properties.executorMaxPoolSize(), 1));
        executor.setQueueCapacity(Math.max(properties.executorQueueCapacity(), 10));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "orderExternalCallSemaphore")
    public Semaphore orderExternalCallSemaphore(WorkflowProperties properties) {
        return new Semaphore(Math.max(properties.externalSemaphorePermits(), 1), true);
    }
}
