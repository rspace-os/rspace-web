package com.researchspace.service;

import static org.junit.Assert.assertEquals;

import com.researchspace.testutils.SpringTransactionalTest;
import java.util.HashMap;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ui.velocity.VelocityEngineUtils;

// this overrides default property values for testing
// so Velocity will be configured to look in this folder for additional templates
// this configuration is in BaseConfig VEngineFactoryBean
@TestPropertySource(properties = {"velocity.ext.dir=src/test/resources/TestResources"})
public class VelocityConfigTest extends SpringTransactionalTest {

  @Autowired VelocityEngine velocity;

  @Test
  public void testCanResolveFileLocation() {
    String text =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "test.vm", "UTF-8", new HashMap<String, Object>());
    assertEquals("Test template", text);
  }
}
