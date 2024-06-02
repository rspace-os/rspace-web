package com.researchspace.testsandbox;

import static org.junit.Assert.assertEquals;

import com.researchspace.testutils.TestRunnerController;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.Value;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class DelayQueueTest {

  @BeforeClass
  public static void BeforeClass() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  @Value
  static class DelayedItem implements Delayed {

    private String payload;
    private long startTime;

    public DelayedItem(String data, long delayInMilliseconds) {
      super();
      this.payload = data;
      this.startTime = System.currentTimeMillis() + delayInMilliseconds;
    }

    @Override
    public int compareTo(Delayed o) {
      Long diff = (this.startTime - ((DelayedItem) o).startTime);
      return diff.intValue();
    }

    @Override
    public long getDelay(TimeUnit unit) {
      long diff = startTime - System.currentTimeMillis();
      return unit.convert(diff, TimeUnit.MILLISECONDS);
    }
  }

  static class DelayQueueProducer implements Runnable {

    private DelayQueue<DelayedItem> queue;
    private Integer numberOfElementsToProduce;
    private Integer delayOfEachProducedMessageMilliseconds;

    public DelayQueueProducer(
        DelayQueue<DelayedItem> queue,
        Integer numberOfElementsToProduce,
        Integer delayOfEachProducedMessageMilliseconds) {
      super();
      this.queue = queue;
      this.numberOfElementsToProduce = numberOfElementsToProduce;
      this.delayOfEachProducedMessageMilliseconds = delayOfEachProducedMessageMilliseconds;
    }

    Consumer<String> st = s -> s.substring(0, 1);

    @Override
    public void run() {
      for (int i = 0; i < numberOfElementsToProduce; i++) {
        DelayedItem object =
            new DelayedItem(
                RandomStringUtils.randomAlphabetic(10), delayOfEachProducedMessageMilliseconds);
        try {
          queue.put(object);
          Thread.sleep(500);
        } catch (InterruptedException ie) {
          ie.printStackTrace();
        }
      }
    }
  }

  static class DelayQueueConsumer implements Runnable {
    private BlockingQueue<DelayedItem> queue;
    private Integer numberOfElementsToTake;
    public AtomicInteger numberOfConsumedElements = new AtomicInteger();

    // standard constructors
    public DelayQueueConsumer(BlockingQueue<DelayedItem> queue, Integer numberOfElementsToTake) {
      super();
      this.queue = queue;
      this.numberOfElementsToTake = numberOfElementsToTake;
    }

    @Override
    public void run() {
      for (int i = 0; i < numberOfElementsToTake; i++) {
        try {
          DelayedItem object = queue.take();
          numberOfConsumedElements.incrementAndGet();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  @Test
  public void test() throws InterruptedException {
    // given
    ExecutorService executor = Executors.newFixedThreadPool(1);

    DelayQueue<DelayedItem> queue = new DelayQueue<>();
    int numberOfElementsToProduce = 10;
    int delayOfEachProducedMessageMilliseconds = 2000;
    DelayQueueProducer producer =
        new DelayQueueProducer(
            queue, numberOfElementsToProduce, delayOfEachProducedMessageMilliseconds);
    DelayQueueConsumer consumer = new DelayQueueConsumer(queue, numberOfElementsToProduce);

    // when
    executor.submit(producer);
    executor.submit(consumer);

    // then
    executor.awaitTermination(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertEquals(consumer.numberOfConsumedElements.get(), numberOfElementsToProduce);
  }
}
