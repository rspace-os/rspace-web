package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.model.User;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class QuantitiesApiControllerMVCIT extends API_MVC_TestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void addTwoQuantities() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/quantities/add", anyUser)
                    .param("value1", "100.5")
                    .param("unitId1", "3")
                    .param("value2", "150.5")
                    .param("unitId2", "3"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(result.getResolvedException());
    ApiQuantityInfo resultQuantity = getFromJsonResponseBody(result, ApiQuantityInfo.class);
    assertEquals("251", resultQuantity.getNumericValue().toString());
    assertEquals(3, resultQuantity.getUnitId());
  }
}
