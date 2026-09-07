package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.ModelAndView;

class MVCTestBaseMatchersTest {

  @Test
  void missingModelDoesNotSatisfyNegativeAttributeAssertion() throws Exception {
    MvcResult result = mock(MvcResult.class);
    var matcher = MVCTestBase.modelAttributeDoesNotContain("message", "secret");
    assertThrows(AssertionError.class, () -> matcher.match(result));

    when(result.getModelAndView()).thenReturn(new ModelAndView());
    matcher.match(result);
  }
}
