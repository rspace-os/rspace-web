package com.researchspace.core.util.throttling;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.TimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AllowanceTrackerSourceImplTest {
  AllowanceTrackerSourceImpl allowanceTrackerSourceImpl;
  @Mock TimeSource timesource;

  @BeforeEach
  public void setUp() throws Exception {
    allowanceTrackerSourceImpl =
        new AllowanceTrackerSourceImpl(timesource, new ThrottleDefinitionSet());
  }

  @Test
  public void getAllowanceRequiresNonEmptyId() {
    assertThrows(IllegalArgumentException.class, () -> allowanceTrackerSourceImpl.getAllowance(""));
  }
}
