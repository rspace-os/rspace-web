package com.researchspace.service.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;

/** Wraps a task executor so that exceptions are logged, rather than failing silently. */
public class ExceptionHandlingAsyncTaskExecutor implements AsyncTaskExecutor {
  Logger log = LoggerFactory.getLogger(ExceptionHandlingAsyncTaskExecutor.class);
  private AsyncTaskExecutor executor;

  public ExceptionHandlingAsyncTaskExecutor(AsyncTaskExecutor executor) {
    this.executor = executor;
  }

  public void execute(Runnable task) {
    executor.execute(createWrappedRunnable(task));
  }

  public void execute(Runnable task, long startTimeout) {
    executor.execute(createWrappedRunnable(task), startTimeout);
  }

  public Future<?> submit(Runnable task) {
    return executor.submit(createWrappedRunnable(task));
  }

  public <T> Future<T> submit(final Callable<T> task) {
    return executor.submit(createCallable(task));
  }

  private <T> Callable<T> createCallable(final Callable<T> task) {
    return new Callable<T>() {
      public T call() throws Exception {
        try {
          return task.call();
        } catch (Exception ex) {
          handle(ex);
          throw ex;
        }
      }
    };
  }

  private Runnable createWrappedRunnable(final Runnable task) {
    return new Runnable() {
      public void run() {
        try {
          task.run();
        } catch (Exception ex) {
          handle(ex);
        }
      }
    };
  }

  private void handle(Exception ex) {
    log.warn("Error during @Async execution - operation aborted. {}", ex.getMessage());
  }
}
