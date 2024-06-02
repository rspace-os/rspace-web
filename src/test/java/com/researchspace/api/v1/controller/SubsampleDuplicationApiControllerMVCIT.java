package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.model.User;
import com.researchspace.service.inventory.impl.SubSampleDuplicateConfig;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

public class SubsampleDuplicationApiControllerMVCIT extends API_MVC_InventoryTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void duplicateSubsample() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // new basic sample with default subsample
    ApiSampleWithFullSubSamples basicSample = createComplexSampleForUser(anyUser);
    ApiSubSampleInfo basicSubSampleInfo = basicSample.getSubSamples().get(0);

    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    String.format("/subSamples/%d/actions/duplicate/", basicSubSampleInfo.getId()),
                    anyUser))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSubSample copy = getFromJsonResponseBody(result, ApiSubSample.class);
    assertNotNull(copy);

    ApiSample sample = sampleApiMgr.getApiSampleById(basicSample.getId(), anyUser);
    assertEquals(2, sample.getQuantity().getNumericValue().intValue());
  }

  @Test
  public void splitSubsample() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createApiKeyForuser(anyUser);

    // new basic sample with default subsample
    ApiSampleWithFullSubSamples basicSample = createComplexSampleForUser(anyUser);
    assertEquals("1 ml", basicSample.getQuantity().toQuantityInfo().toPlainString());
    ApiSubSampleInfo basicSubSampleInfo = basicSample.getSubSamples().get(0);
    SubSampleDuplicateConfig cfg = SubSampleDuplicateConfig.split(basicSubSampleInfo.getId(), 7);
    MvcResult result =
        this.mockMvc
            .perform(
                createBuilderForPostWithJSONBody(
                    apiKey,
                    String.format("/subSamples/%d/actions/split/", basicSubSampleInfo.getId()),
                    anyUser,
                    cfg))
            .andExpect(status().isCreated())
            .andReturn();
    ApiSubSample[] copy = getFromJsonResponseBody(result, ApiSubSample[].class);
    assertEquals(6, copy.length);

    ApiSample updatedSample = sampleApiMgr.getApiSampleById(basicSample.getId(), anyUser);
    assertEquals("999.999 µl", updatedSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(7, updatedSample.getSubSamples().size());
    assertEquals(
        "142.857 µl",
        updatedSample.getSubSamples().get(0).getQuantity().toQuantityInfo().toPlainString());
  }
}
