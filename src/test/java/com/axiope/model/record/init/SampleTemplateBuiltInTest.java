package com.axiope.model.record.init;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SampleTemplateBuiltInTest {

  @Mock IBuiltInPersistor m_initializer;

  @InjectMocks AntibodySampleTemplate antibody;

  @Test
  public void test() {

    User anyUser = TestFactory.createAnyUser("any");
    Optional<SampleTemplate> opt = antibody.createSampleTemplate(anyUser);
    assertThat(opt).isPresent();
    assertThat(opt.get().getActiveFields()).hasSize(10);
  }
}
