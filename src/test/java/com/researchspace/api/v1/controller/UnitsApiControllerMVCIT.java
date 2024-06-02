package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class UnitsApiControllerMVCIT extends API_MVC_TestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void getUnits() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);
    MvcResult result =
        mockMvc
            .perform(createBuilderForGet(API_VERSION.ONE, apiKey, "/units", anyUser))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(EnumSet.allOf(RSUnitDef.class).size()))
            .andReturn();

    String contentAsString = result.getResponse().getContentAsString();
    assertTrue(
        contentAsString.indexOf("\"pg\"") < contentAsString.indexOf("\"mg\""),
        "invalid ordering, picogram should be listed before  milligram, but was: "
            + contentAsString);
  }
}
