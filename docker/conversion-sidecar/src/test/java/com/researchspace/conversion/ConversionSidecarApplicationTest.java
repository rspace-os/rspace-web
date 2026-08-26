package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "converter.office-home=/tmp",
      "converter.working-directory=/tmp",
      "converter.sandbox-executable=/usr/bin/true"
    })
class ConversionSidecarApplicationTest {

  @Autowired private ConversionController conversionController;
  @Autowired private ConversionHealthController healthController;

  @Test
  void productionConfigurationWiresTheControllerGraph() {
    assertNotNull(conversionController);
    assertNotNull(healthController);
  }
}
