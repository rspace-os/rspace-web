package com.axiope.model.record.init;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.TestFactory;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class SampleTemplateBuiltInTest {

  public @Rule MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock IBuiltInPersistor m_initializer;

  @InjectMocks AntibodySampleTemplate antibody;

  @Test
  public void test() {

    User anyUser = TestFactory.createAnyUser("any");
    Optional<Sample> opt = antibody.createSampleTemplate(anyUser);
    assertTrue(opt.isPresent());
    assertEquals(10, opt.get().getActiveFields().size());
  }
}
