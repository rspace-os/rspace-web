package com.researchspace.api.v1.config;

import com.researchspace.api.v1.controller.JobsApiHandler;
import com.researchspace.api.v1.model.ApiExportJobResult;
import com.researchspace.api.v1.service.impl.ApiExportJobResultFactory;
import com.researchspace.api.v1.service.impl.ExportApiStateTracker;
import com.researchspace.api.v1.service.impl.ExportTasklet;
import com.researchspace.api.v1.service.impl.JobResultFactory;
import com.researchspace.api.v1.service.impl.JobsApiHandlerImpl;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableBatchProcessing
@Slf4j
public class BatchConfig {

  @Autowired
  @Qualifier("archiveTaskExecutor")
  TaskExecutor taskExecutor;

  @Bean
  public Job exportDataJob(JobRepository jobRepository, Step step1) {
    return new JobBuilder(ExportTasklet.EXPORT_JOB_NAME, jobRepository)
        .incrementer(new RunIdIncrementer())
        .preventRestart()
        .start(step1)
        .build();
  }

  @Bean
  public Step step1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
    return new StepBuilder("step1", jobRepository)
        .tasklet(exportTasklet(), transactionManager)
        .startLimit(1)
        .build();
  }

  // @Scope(scopeName="simpleThreadScope", proxyMode=ScopedProxyMode.INTERFACES)
  @Bean
  Tasklet exportTasklet() {
    return new ExportTasklet();
  }

  @Bean
  JobsApiHandler JobsApiHandler() {
    List<JobResultFactory<? extends Object>> jobResultFacs = new ArrayList<>();
    jobResultFacs.add(apiExportJobResultFactory());
    return new JobsApiHandlerImpl(jobResultFacs);
  }

  @Bean
  JobResultFactory<ApiExportJobResult> apiExportJobResultFactory() {
    return new ApiExportJobResultFactory();
  }

  @Bean
  ExportApiStateTracker exportApiIdStore() {
    return new ExportApiStateTracker();
  }

  // runs in bg thread to respond quickly
  @Bean
  public JobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
    TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
    jobLauncher.setJobRepository(jobRepository);
    jobLauncher.setTaskExecutor(taskExecutor);
    jobLauncher.afterPropertiesSet();
    return jobLauncher;
  }
}
