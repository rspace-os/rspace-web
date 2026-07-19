package com.axiope.service.cfg;

import static org.junit.Assert.assertEquals;

import com.researchspace.service.impl.EmailBroadcastImpl;
import org.junit.Test;

/**
 * Verifies the configured send rate is exposed by {@link EmailBroadcastImpl#getMaxSendingRate()}.
 */
public class EmailBroadcasterConfigTest {

  @Test
  public void configuredRateIsExposedAsMaxSendingRate() {
    assertEquals(23, new EmailBroadcastImpl(23, 25).getMaxSendingRate().intValue());
  }
}
