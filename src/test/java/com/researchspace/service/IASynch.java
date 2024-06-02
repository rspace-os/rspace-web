package com.researchspace.service;

import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;

interface IASync {

  @Async(value = "indexTaskExecutor")
  Future<String> doAsync();
}
