package com.axiope.service.cfg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskExecutorConfig {

  @Value("${default.taskExecutor.core:2}")
  Integer defaultTaskExecutorCore;

  @Value("${default.taskExecutor.max:5}")
  Integer defaultTaskExecutorMax;

  @Value("${default.taskExecutor.queue:100}")
  Integer defaultTaskExecutorQueue;

  @Value("${index.taskExecutor.queue:5000}")
  Integer indexTaskExecutorQueue;

  @Value("${docConverter.taskExecutor.core:2}")
  Integer docConverterTaskExecutorCore;

  @Value("${docConverter.taskExecutor.max:10}")
  Integer docConverterTaskExecutorMax;

  @Value("${docConverter.taskExecutor.queue:250}")
  Integer docConverterTaskExecutorQueue;
}
