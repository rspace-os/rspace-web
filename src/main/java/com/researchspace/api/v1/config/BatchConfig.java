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
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

@Configuration
@EnableBatchProcessing
@Slf4j
public class BatchConfig extends DefaultBatchConfigurer { // implements BeanFactoryAware {

  @Autowired JobBuilderFactory jobBuilderFactory;
  @Autowired StepBuilderFactory stepBuilderFactory;

  @Autowired
  @Qualifier("archiveTaskExecutor")
  TaskExecutor taskExecutor;

  @Bean
  public Job exportDataJob() {
    return jobBuilderFactory
        .get(ExportTasklet.EXPORT_JOB_NAME)
        .incrementer(new RunIdIncrementer())
        .preventRestart() //
        .flow(step1())
        .end()
        .build();
  }

  @Bean
  public Step step1() {
    return stepBuilderFactory.get("step1").tasklet(exportTasklet()).startLimit(1).build();
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

  // runs ib bg thread to respond quickly
  @Override
  protected JobLauncher createJobLauncher() throws Exception {
    SimpleJobLauncher jl = (SimpleJobLauncher) super.createJobLauncher();
    jl.setTaskExecutor(taskExecutor);
    return jl;
  }
}
