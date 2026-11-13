package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v2.auth.ApiV2Caller;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

class ApiV2SearchParameterTest {
  @Test
  void runtimeFieldDiscoveryNamesItsSearchParameterQ() throws Exception {
    var method =
        ApiV2CrudController.class.getMethod(
            "runtimeFields",
            String.class,
            String.class,
            ApiV2Caller.class,
            String.class,
            String.class,
            int.class,
            int.class);
    assertEquals("q", method.getParameters()[3].getAnnotation(RequestParam.class).name());
  }
}
