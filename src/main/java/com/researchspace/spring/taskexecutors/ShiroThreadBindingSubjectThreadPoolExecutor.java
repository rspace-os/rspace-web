package com.researchspace.spring.taskexecutors;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * For service methods that run as background threads using Spring's @Asynch annotation, this can
 * cause problems when doing permission lookups, since the the Shiro Subject is bound to a thread.
 *
 * <p>This class overrides methods where appropriate, in order to bind the subject to the thread to
 * be executed.
 */
public class ShiroThreadBindingSubjectThreadPoolExecutor extends ThreadPoolTaskExecutor {

  private static final long serialVersionUID = -5478877629946489046L;
  // for test introspection
  int configuredQueueCapacity;

  /**
   * Not used, this is just to report a value that is set into a private variable in superclass
   * rspac-2150
   *
   * @return
   */
  public int getConfiguredQueueCapacity() {
    return configuredQueueCapacity;
  }

  public static ThreadPoolTaskExecutor createNewExecutor(
      int corePoolSize, int maxPoolSize, int queueCapacity) {
    ShiroThreadBindingSubjectThreadPoolExecutor rc =
        new ShiroThreadBindingSubjectThreadPoolExecutor();
    rc.setCorePoolSize(corePoolSize);
    rc.setMaxPoolSize(maxPoolSize);
    rc.setQueueCapacity(queueCapacity);
    rc.configuredQueueCapacity = queueCapacity;
    return rc;
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    // associate subject with supplied callable.
    ExecutorService executor = getThreadPoolExecutor();
    Subject subject = SecurityUtils.getSubject();
    task = subject.associateWith(task);
    try {
      return executor.submit(task);
    } catch (RejectedExecutionException ex) {
      throw new TaskRejectedException(
          "Executor [" + executor + "] did not accept task: " + task, ex);
    }
  }
}
