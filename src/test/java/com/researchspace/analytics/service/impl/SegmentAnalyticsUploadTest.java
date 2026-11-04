package com.researchspace.analytics.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.testutils.StubHttpServer;
import com.segment.analytics.Analytics;
import com.segment.analytics.Callback;
import com.segment.analytics.messages.Message;
import com.segment.analytics.messages.TrackMessage;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Sends a real event to a local server, so Segment's okhttp and Kotlin runtime are exercised. */
class SegmentAnalyticsUploadTest {

  private StubHttpServer server;
  private Analytics analytics;

  @BeforeEach
  void setUp() throws Exception {
    server = new StubHttpServer();
  }

  @AfterEach
  void tearDown() {
    if (analytics != null) {
      analytics.shutdown();
    }
    server.stop();
  }

  @Test
  void trackEventIsUploadedToConfiguredEndpoint() throws Exception {
    server.post("/v1/import/").respond("{}");
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Message> sent = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    analytics =
        Analytics.builder("test-key")
            .endpoint(server.getBaseUrl())
            .log(new SegmentAnalyticsLogAdapter())
            .callback(
                new Callback() {
                  @Override
                  public void success(Message message) {
                    sent.set(message);
                    done.countDown();
                  }

                  @Override
                  public void failure(Message message, Throwable throwable) {
                    failure.set(throwable);
                    done.countDown();
                  }
                })
            .build();

    analytics.enqueue(TrackMessage.builder("test-event").userId("user1").properties(Map.of()));
    analytics.flush();

    assertTrue(done.await(10, TimeUnit.SECONDS), "upload did not finish");
    assertNull(failure.get());
    assertEquals("user1", sent.get().userId());
  }
}
