package com.axiope.service.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.service.IMediaFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

//
// tests that configuration is passed in correctly and defaults are set.
public class EcatMediaFactorySetupTest {

  @Nested
  public class EcatMediaFactorySetupTestDefault extends SpringTransactionalTest {
    private @Autowired IMediaFactory factory;

    @Test
    public void test() {
      // default in the active RSDev profile for running tests
      assertEquals(100000, factory.getMaxImageMemorySize());
    }
  }

  @Nested
  @TestPropertySource(properties = {"max.tiff.conversionSize=100"})
  public class EcatMediaFactorySetupTestNonDefault extends SpringTransactionalTest {
    private @Autowired IMediaFactory factory;

    @Test
    public void test() {
      assertEquals(100, factory.getMaxImageMemorySize());
    }
  }
}
