package com.researchspace.service;

import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

@Component
public class AsyncImpl implements IASync {

  @Override
  public Future<String> doAsync() {
    try {
      System.err.println("sleeping " + Thread.currentThread().getName());
      Thread.sleep(5000);
      System.err.println("slept");
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return new AsyncResult<String>("returning");
  }
}
