package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ApiInventoryVersionFieldTest {

  private final User user = TestFactory.createAnyUser("versionFieldUser");

  @Test
  public void subSampleInfoExposesEntityVersion() {
    Sample sample = TestFactory.createBasicSampleOutsideContainer(user);
    SubSample subSample = sample.getSubSamples().get(0);
    subSample.increaseVersion();

    ApiSubSampleInfo info = new ApiSubSampleInfo(subSample);

    assertEquals(2L, info.getVersion());
    assertFalse(info.isHistoricalVersion());
  }

  @Test
  public void containerInfoExposesEntityVersion() throws IOException {
    Container container = TestFactory.createListContainer(user);
    container.increaseVersion();

    ApiContainerInfo info = new ApiContainerInfo(container);

    assertEquals(2L, info.getVersion());
    assertFalse(info.isHistoricalVersion());
  }
}
